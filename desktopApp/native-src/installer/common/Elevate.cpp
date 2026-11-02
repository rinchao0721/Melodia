#include "Elevate.h"

#include <shellapi.h>

#include <cstdio>
#include <cstdlib>
#include <cwchar>

namespace elevate {

namespace {

constexpr ULONG_PTR kPacketMagic = 0x4D454C31;
constexpr size_t kMaxTextChars = 512;

struct WirePacket {
    int32_t kind;
    int32_t percent;
    wchar_t text[kMaxTextChars];
};

}  // namespace

void SendPacket(HWND parent, PacketKind kind, int percent, const std::wstring& text) {
    if (!parent || !IsWindow(parent)) return;
    WirePacket wire{};
    wire.kind = static_cast<int32_t>(kind);
    wire.percent = percent;
    wcsncpy_s(wire.text, kMaxTextChars, text.c_str(), _TRUNCATE);
    COPYDATASTRUCT cds{};
    cds.dwData = kPacketMagic;
    cds.cbData = sizeof(wire);
    cds.lpData = &wire;
    DWORD_PTR result = 0;
    SendMessageTimeoutW(parent, WM_COPYDATA, 0, reinterpret_cast<LPARAM>(&cds), SMTO_ABORTIFHUNG, 2000, &result);
}

bool ParsePacket(LPARAM lParam, Packet& out) {
    auto* cds = reinterpret_cast<const COPYDATASTRUCT*>(lParam);
    if (!cds || cds->dwData != kPacketMagic || cds->cbData != sizeof(WirePacket) || !cds->lpData) return false;
    const auto* wire = static_cast<const WirePacket*>(cds->lpData);
    if (wire->kind != static_cast<int32_t>(PacketKind::Progress) && wire->kind != static_cast<int32_t>(PacketKind::Error)) {
        return false;
    }
    out.kind = static_cast<PacketKind>(wire->kind);
    out.percent = wire->percent < 0 ? 0 : (wire->percent > 100 ? 100 : wire->percent);
    out.text.assign(wire->text, wcsnlen(wire->text, kMaxTextChars));
    return true;
}

HWND ParseWindowHandle(const wchar_t* text) {
    if (!text || !*text) return nullptr;
    wchar_t* end = nullptr;
    unsigned long long value = wcstoull(text, &end, 16);
    if (!end || *end != L'\0' || value == 0) return nullptr;
    HWND hwnd = reinterpret_cast<HWND>(static_cast<uintptr_t>(value));
    return IsWindow(hwnd) ? hwnd : nullptr;
}

std::wstring FormatWindowHandle(HWND hwnd) {
    wchar_t buf[32]{};
    swprintf_s(buf, L"%llx", static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(hwnd)));
    return buf;
}

LaunchResult RunElevatedAndWait(HWND owner, const std::filesystem::path& exe, const std::wstring& args, DWORD& exitCode) {
    exitCode = 0;
    std::wstring file = exe.wstring();
    std::filesystem::path workDir = exe.parent_path();
    std::wstring dir = workDir.wstring();
    SHELLEXECUTEINFOW sei{};
    sei.cbSize = sizeof(sei);
    sei.fMask = SEE_MASK_NOCLOSEPROCESS | SEE_MASK_NOASYNC | SEE_MASK_FLAG_NO_UI;
    sei.hwnd = owner;
    sei.lpVerb = L"runas";
    sei.lpFile = file.c_str();
    sei.lpParameters = args.c_str();
    sei.lpDirectory = dir.c_str();
    sei.nShow = SW_HIDE;
    if (!ShellExecuteExW(&sei)) {
        return GetLastError() == ERROR_CANCELLED ? LaunchResult::Cancelled : LaunchResult::Failed;
    }
    if (!sei.hProcess) return LaunchResult::Failed;
    WaitForSingleObject(sei.hProcess, INFINITE);
    DWORD code = 0;
    bool ok = GetExitCodeProcess(sei.hProcess, &code) != FALSE;
    CloseHandle(sei.hProcess);
    if (!ok) return LaunchResult::Failed;
    exitCode = code;
    return LaunchResult::Finished;
}

}  // namespace elevate
