#include <winsock2.h>
#include <ws2tcpip.h>

#include "Sys.h"

#include <tlhelp32.h>

#include <algorithm>
#include <cstdint>
#include <cwctype>
#include <fstream>
#include <iterator>

namespace sys {

namespace {

constexpr wchar_t kAppName[] = L"Melodia";
constexpr wchar_t kCacheSubdir[] = L"MelodiaCache";
constexpr wchar_t kRunRegKey[] = L"Software\\Microsoft\\Windows\\CurrentVersion\\Run";

const wchar_t* const kCacheFolderNames[] = {
    L"audio_cache",
    L"meta_cache",
    L"image_cache",
    L"lyrics",
    L"local_covers",
    L"download_tmp",
};

void RunDelayed(const std::wstring& action);

// 不含 cache：默认安装目录与默认缓存根同在 %LOCALAPPDATA%\Melodia 下
const wchar_t* const kAppDirFiles[] = {
    L"Melodia.exe",
    L"Melodia.ico",
};
constexpr wchar_t kUninstallerName[] = L"uninstall.exe";

// 子目录名过于通用，带 jpackage 特征文件才删，防止安装目录选在共享位置时误删同名目录
struct AppSubdir {
    const wchar_t* name;
    const wchar_t* marker;
};
const AppSubdir kAppSubdirs[] = {
    {L"app", L"Melodia.cfg"},
    {L"runtime", L"release"},
};

bool ReadVarint(const uint8_t*& cur, const uint8_t* end, uint64_t& value) {
    value = 0;
    int shift = 0;
    while (cur < end && shift < 64) {
        uint8_t byte = *cur++;
        value |= static_cast<uint64_t>(byte & 0x7F) << shift;
        if ((byte & 0x80) == 0) return true;
        shift += 7;
    }
    return false;
}

bool SkipField(const uint8_t*& cur, const uint8_t* end, uint32_t wireType) {
    uint64_t val = 0;
    switch (wireType) {
        case 0: return ReadVarint(cur, end, val);
        case 1:
            if (end - cur < 8) return false;
            cur += 8;
            return true;
        case 2:
            if (!ReadVarint(cur, end, val) || val > static_cast<uint64_t>(end - cur)) return false;
            cur += static_cast<size_t>(val);
            return true;
        case 5:
            if (end - cur < 4) return false;
            cur += 4;
            return true;
        default:
            return false;
    }
}

// DataStore Value 消息中的 string 字段（field 5）
std::string ParseValueString(const uint8_t* cur, const uint8_t* end) {
    while (cur < end) {
        uint64_t tag = 0;
        if (!ReadVarint(cur, end, tag)) break;
        uint32_t fieldNum = static_cast<uint32_t>(tag >> 3);
        uint32_t wireType = static_cast<uint32_t>(tag & 0x07);
        if (fieldNum == 5 && wireType == 2) {
            uint64_t len = 0;
            if (!ReadVarint(cur, end, len) || len > static_cast<uint64_t>(end - cur)) break;
            return std::string(reinterpret_cast<const char*>(cur), static_cast<size_t>(len));
        }
        if (!SkipField(cur, end, wireType)) break;
    }
    return {};
}

std::vector<DWORD> FindMelodiaUnder(const fs::path& appDir) {
    std::vector<DWORD> pids;
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return pids;
    PROCESSENTRY32W pe{};
    pe.dwSize = sizeof(pe);
    DWORD selfPid = GetCurrentProcessId();
    if (Process32FirstW(snap, &pe)) {
        do {
            if (pe.th32ProcessID == selfPid || _wcsicmp(pe.szExeFile, L"Melodia.exe") != 0) continue;
            HANDLE proc = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pe.th32ProcessID);
            if (!proc) continue;
            std::wstring image(32768, L'\0');
            DWORD size = static_cast<DWORD>(image.size());
            if (QueryFullProcessImageNameW(proc, 0, image.data(), &size)) {
                image.resize(size);
                if (IsSubPath(fs::path(image), appDir)) pids.push_back(pe.th32ProcessID);
            }
            CloseHandle(proc);
        } while (Process32NextW(snap, &pe));
    }
    CloseHandle(snap);
    return pids;
}

// 通过 SingleInstance 端口请求主程序优雅退出
bool SendQuitToSingleInstance(const fs::path& dataDir) {
    std::ifstream in(dataDir / L"instance.port");
    if (!in) return false;
    int port = 0;
    in >> port;
    if (port <= 0 || port > 65535) return false;

    WSADATA wsa{};
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) return false;
    bool ok = false;
    SOCKET sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock != INVALID_SOCKET) {
        DWORD timeout = 800;
        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, reinterpret_cast<const char*>(&timeout), sizeof(timeout));
        setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, reinterpret_cast<const char*>(&timeout), sizeof(timeout));
        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(static_cast<u_short>(port));
        addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        if (connect(sock, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) == 0) {
            const char cmd[] = "QUIT\n";
            send(sock, cmd, sizeof(cmd) - 1, 0);
            char buf[16]{};
            int n = recv(sock, buf, sizeof(buf) - 1, 0);
            ok = n >= 2 && buf[0] == 'O' && buf[1] == 'K';
        }
        closesocket(sock);
    }
    WSACleanup();
    return ok;
}

BOOL CALLBACK PostCloseToProcessWindows(HWND hwnd, LPARAM lParam) {
    DWORD winPid = 0;
    GetWindowThreadProcessId(hwnd, &winPid);
    if (winPid == static_cast<DWORD>(lParam)) PostMessageW(hwnd, WM_CLOSE, 0, 0);
    return TRUE;
}

bool WaitUntilGone(const fs::path& appDir, int rounds) {
    for (int i = 0; i < rounds; ++i) {
        if (FindMelodiaUnder(appDir).empty()) return true;
        Sleep(100);
    }
    return FindMelodiaUnder(appDir).empty();
}

}  // namespace

fs::path ModulePath() {
    std::wstring buf(MAX_PATH, L'\0');
    while (buf.size() <= 32768) {
        DWORD len = GetModuleFileNameW(nullptr, buf.data(), static_cast<DWORD>(buf.size()));
        if (len == 0) return {};
        if (len < buf.size() - 1) {
            buf.resize(len);
            return fs::path(buf);
        }
        buf.resize(buf.size() * 2);
    }
    return {};
}

fs::path EnvPath(const wchar_t* name) {
    DWORD needed = GetEnvironmentVariableW(name, nullptr, 0);
    if (needed <= 1) return {};
    std::wstring val(needed, L'\0');
    DWORD len = GetEnvironmentVariableW(name, val.data(), needed);
    if (len == 0 || len >= needed) return {};
    val.resize(len);
    return fs::path(val);
}

fs::path KnownFolder(const KNOWNFOLDERID& id) {
    PWSTR raw = nullptr;
    fs::path result;
    if (SUCCEEDED(SHGetKnownFolderPath(id, KF_FLAG_DEFAULT, nullptr, &raw)) && raw) {
        result = fs::path(raw);
    }
    if (raw) CoTaskMemFree(raw);
    return result;
}

fs::path TempDir() {
    wchar_t buf[MAX_PATH + 1]{};
    DWORD len = GetTempPathW(MAX_PATH + 1, buf);
    if (len == 0 || len > MAX_PATH) return {};
    return fs::path(buf);
}

fs::path SystemExe(const wchar_t* name) {
    wchar_t buf[MAX_PATH]{};
    UINT len = GetSystemDirectoryW(buf, MAX_PATH);
    if (len == 0 || len >= MAX_PATH) return fs::path(L"C:\\Windows\\System32") / name;
    return fs::path(buf) / name;
}

fs::path DataDir() {
    fs::path base = EnvPath(L"APPDATA");
    if (base.empty()) base = EnvPath(L"USERPROFILE");
    return base.empty() ? fs::path() : base / kAppName;
}

fs::path LocalMelodiaDir() {
    fs::path base = EnvPath(L"LOCALAPPDATA");
    return base.empty() ? fs::path() : base / kAppName;
}

fs::path DefaultCacheRoot() {
    fs::path local = LocalMelodiaDir();
    if (!local.empty()) return local / L"cache";
    fs::path data = DataDir();
    return data.empty() ? fs::path() : data / L"cache";
}

fs::path PrefsFile() {
    fs::path data = DataDir();
    return data.empty() ? fs::path() : data / L"datastore" / L"desktop_prefs.preferences_pb";
}

fs::path StripTrailingSeparators(const fs::path& p) {
    std::wstring s = p.wstring();
    while (s.size() > 3 && (s.back() == L'\\' || s.back() == L'/')) s.pop_back();
    return fs::path(s);
}

fs::path NormalizePath(const fs::path& p) {
    if (p.empty()) return {};
    std::error_code ec;
    fs::path abs = fs::weakly_canonical(p, ec);
    if (ec) {
        ec.clear();
        abs = fs::absolute(p, ec);
        if (ec) abs = p;
    }
    std::wstring s = StripTrailingSeparators(abs.make_preferred()).wstring();
    std::transform(s.begin(), s.end(), s.begin(), [](wchar_t c) { return static_cast<wchar_t>(std::towlower(c)); });
    return fs::path(s);
}

bool SamePath(const fs::path& a, const fs::path& b) {
    if (a.empty() || b.empty()) return false;
    return NormalizePath(a) == NormalizePath(b);
}

bool IsSubPath(const fs::path& child, const fs::path& parent) {
    std::wstring c = NormalizePath(child).wstring();
    std::wstring p = NormalizePath(parent).wstring();
    if (c.empty() || p.empty()) return false;
    if (c == p) return true;
    if (p.back() != L'\\') p.push_back(L'\\');
    return c.rfind(p, 0) == 0;
}

std::wstring Utf8ToWide(std::string_view utf8) {
    if (utf8.empty() || utf8.size() > static_cast<size_t>(INT32_MAX)) return {};
    int len = MultiByteToWideChar(CP_UTF8, 0, utf8.data(), static_cast<int>(utf8.size()), nullptr, 0);
    if (len <= 0) return {};
    std::wstring out(static_cast<size_t>(len), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, utf8.data(), static_cast<int>(utf8.size()), out.data(), len);
    return out;
}

// 按 CommandLineToArgvW 的转义规则加引号
std::wstring QuoteArg(const std::wstring& arg) {
    if (!arg.empty() && arg.find_first_of(L" \t\n\v\"") == std::wstring::npos) return arg;
    std::wstring out = L"\"";
    for (size_t i = 0;; ++i) {
        size_t backslashes = 0;
        while (i < arg.size() && arg[i] == L'\\') {
            ++i;
            ++backslashes;
        }
        if (i == arg.size()) {
            out.append(backslashes * 2, L'\\');
            break;
        }
        if (arg[i] == L'"') {
            out.append(backslashes * 2 + 1, L'\\');
        } else {
            out.append(backslashes, L'\\');
        }
        out.push_back(arg[i]);
    }
    out.push_back(L'"');
    return out;
}

std::wstring SystemErrorText(DWORD code) {
    wchar_t* buf = nullptr;
    DWORD len = FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                               nullptr,
                               code,
                               0,
                               reinterpret_cast<LPWSTR>(&buf),
                               0,
                               nullptr);
    std::wstring text;
    if (len > 0 && buf) text.assign(buf, len);
    if (buf) LocalFree(buf);
    return Trim(text);
}

std::wstring Trim(const std::wstring& s) {
    size_t begin = 0;
    size_t end = s.size();
    while (begin < end && std::iswspace(s[begin])) ++begin;
    while (end > begin && std::iswspace(s[end - 1])) --end;
    return s.substr(begin, end - begin);
}

int CompareVersions(const std::wstring& a, const std::wstring& b) {
    auto parts = [](const std::wstring& v) {
        std::vector<unsigned long> out;
        size_t pos = 0;
        while (pos <= v.size()) {
            size_t dot = v.find(L'.', pos);
            std::wstring piece = v.substr(pos, dot == std::wstring::npos ? std::wstring::npos : dot - pos);
            unsigned long num = 0;
            for (wchar_t c : piece) {
                if (c < L'0' || c > L'9') break;
                num = num * 10 + static_cast<unsigned long>(c - L'0');
                if (num > 0xFFFFFF) break;
            }
            out.push_back(num);
            if (dot == std::wstring::npos) break;
            pos = dot + 1;
        }
        return out;
    };
    std::vector<unsigned long> pa = parts(a);
    std::vector<unsigned long> pb = parts(b);
    size_t n = (std::max)(pa.size(), pb.size());
    for (size_t i = 0; i < n; ++i) {
        unsigned long x = i < pa.size() ? pa[i] : 0;
        unsigned long y = i < pb.size() ? pb[i] : 0;
        if (x != y) return x < y ? -1 : 1;
    }
    return 0;
}

bool IsProcessElevated() {
    HANDLE token = nullptr;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) return false;
    TOKEN_ELEVATION elevation{};
    DWORD size = sizeof(elevation);
    bool elevated = GetTokenInformation(token, TokenElevation, &elevation, sizeof(elevation), &size) && elevation.TokenIsElevated;
    CloseHandle(token);
    return elevated;
}

bool IsMelodiaAppDir(const fs::path& dir) {
    if (dir.empty()) return false;
    std::error_code ec;
    return fs::is_regular_file(dir / L"Melodia.exe", ec) || fs::is_regular_file(dir / L"app" / L"Melodia.cfg", ec);
}

bool IsDirectoryWritable(const fs::path& dir) {
    std::error_code ec;
    fs::path probe = dir;
    while (!probe.empty() && !fs::is_directory(probe, ec)) {
        fs::path parent = probe.parent_path();
        if (parent == probe) return false;
        probe = parent;
    }
    if (probe.empty()) return false;
    fs::path testFile = probe / (L".melodia_write_test_" + std::to_wstring(GetCurrentProcessId()));
    HANDLE h = CreateFileW(testFile.c_str(),
                           GENERIC_WRITE,
                           0,
                           nullptr,
                           CREATE_NEW,
                           FILE_ATTRIBUTE_TEMPORARY | FILE_ATTRIBUTE_HIDDEN | FILE_FLAG_DELETE_ON_CLOSE,
                           nullptr);
    if (h == INVALID_HANDLE_VALUE) return false;
    CloseHandle(h);
    return true;
}

bool RemoveTreeWithRetry(const fs::path& target) {
    if (target.empty()) return true;
    std::error_code ec;
    if (!fs::exists(fs::symlink_status(target, ec))) return true;
    for (int attempt = 0; attempt < 6; ++attempt) {
        ec.clear();
        fs::remove_all(target, ec);
        if (!fs::exists(fs::symlink_status(target, ec))) return true;
        Sleep(150);
    }
    return false;
}

bool RemoveAppEntries(const fs::path& appDir) {
    if (appDir.empty()) return true;
    bool ok = true;
    for (const wchar_t* file : kAppDirFiles) {
        ok = RemoveTreeWithRetry(appDir / file) && ok;
    }
    // 静默卸载时最初启动的 uninstall.exe 会等临时副本结束，文件仍被占用
    fs::path uninstaller = appDir / kUninstallerName;
    if (!RemoveTreeWithRetry(uninstaller)) {
        RunDelayed(L"del /f /q " + QuoteArg(uninstaller.wstring()) + L" & rmdir " + QuoteArg(appDir.wstring()));
    }
    std::error_code ec;
    for (const AppSubdir& sub : kAppSubdirs) {
        fs::path dir = appDir / sub.name;
        if (fs::is_regular_file(dir / sub.marker, ec)) ok = RemoveTreeWithRetry(dir) && ok;
    }
    RemoveDirectoryW(appDir.c_str());
    return ok;
}

std::vector<fs::path> ReadCustomCacheRoots(const fs::path& prefsFile) {
    std::vector<fs::path> result;
    std::error_code ec;
    if (prefsFile.empty() || !fs::is_regular_file(prefsFile, ec)) return result;
    auto size = fs::file_size(prefsFile, ec);
    if (ec || size == 0 || size > 16 * 1024 * 1024) return result;

    std::ifstream in(prefsFile, std::ios::binary);
    if (!in) return result;
    std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    if (bytes.empty()) return result;

    const uint8_t* cur = bytes.data();
    const uint8_t* end = cur + bytes.size();
    while (cur < end) {
        uint64_t tag = 0;
        if (!ReadVarint(cur, end, tag)) break;
        uint32_t fieldNum = static_cast<uint32_t>(tag >> 3);
        uint32_t wireType = static_cast<uint32_t>(tag & 0x07);
        if (fieldNum != 1 || wireType != 2) {
            if (!SkipField(cur, end, wireType)) break;
            continue;
        }
        uint64_t entryLen = 0;
        if (!ReadVarint(cur, end, entryLen) || entryLen > static_cast<uint64_t>(end - cur)) break;
        const uint8_t* entryCur = cur;
        const uint8_t* entryEnd = cur + static_cast<size_t>(entryLen);
        cur = entryEnd;

        std::string key;
        std::string strVal;
        while (entryCur < entryEnd) {
            uint64_t entryTag = 0;
            if (!ReadVarint(entryCur, entryEnd, entryTag)) break;
            uint32_t eField = static_cast<uint32_t>(entryTag >> 3);
            uint32_t eWire = static_cast<uint32_t>(entryTag & 0x07);
            if ((eField == 1 || eField == 2) && eWire == 2) {
                uint64_t len = 0;
                if (!ReadVarint(entryCur, entryEnd, len) || len > static_cast<uint64_t>(entryEnd - entryCur)) break;
                if (eField == 1) {
                    key.assign(reinterpret_cast<const char*>(entryCur), static_cast<size_t>(len));
                } else {
                    strVal = ParseValueString(entryCur, entryCur + static_cast<size_t>(len));
                }
                entryCur += static_cast<size_t>(len);
            } else if (!SkipField(entryCur, entryEnd, eWire)) {
                break;
            }
        }
        if (strVal.empty()) continue;
        std::wstring wide = Utf8ToWide(strVal);
        if (wide.empty()) continue;
        if (key == "cache_root") {
            result.push_back(fs::path(wide) / kCacheSubdir);
        } else if (key == "cache_cleanup") {
            fs::path cleanupPath(wide);
            if (_wcsicmp(cleanupPath.filename().c_str(), kCacheSubdir) == 0) result.push_back(cleanupPath);
        }
    }
    return result;
}

bool CleanUserData(const std::vector<fs::path>& customCacheRoots) {
    bool ok = true;
    // 自定义缓存目录可能与用户文件混放，只删已知子目录
    for (const fs::path& root : customCacheRoots) {
        if (_wcsicmp(root.filename().c_str(), kCacheSubdir) != 0) continue;
        std::error_code ec;
        if (!fs::is_directory(root, ec)) continue;
        for (const wchar_t* sub : kCacheFolderNames) {
            ok = RemoveTreeWithRetry(root / sub) && ok;
        }
        RemoveDirectoryW(root.c_str());
    }
    ok = RemoveTreeWithRetry(DefaultCacheRoot()) && ok;
    ok = RemoveTreeWithRetry(DataDir()) && ok;
    fs::path local = LocalMelodiaDir();
    if (!local.empty()) RemoveDirectoryW(local.c_str());
    return ok;
}

bool StopMelodiaUnder(const fs::path& appDir) {
    if (FindMelodiaUnder(appDir).empty()) return true;

    // 数据目录共用一把单实例锁，同一时刻只会有一个实例在运行
    SendQuitToSingleInstance(DataDir());
    if (WaitUntilGone(appDir, 30)) return true;

    for (DWORD pid : FindMelodiaUnder(appDir)) {
        EnumWindows(PostCloseToProcessWindows, static_cast<LPARAM>(pid));
    }
    if (WaitUntilGone(appDir, 20)) return true;

    for (DWORD pid : FindMelodiaUnder(appDir)) {
        HANDLE proc = OpenProcess(PROCESS_TERMINATE | SYNCHRONIZE, FALSE, pid);
        if (!proc) continue;
        TerminateProcess(proc, 0);
        WaitForSingleObject(proc, 3000);
        CloseHandle(proc);
    }
    return WaitUntilGone(appDir, 10);
}

bool LaunchMelodia(const fs::path& appDir) {
    fs::path exe = appDir / L"Melodia.exe";
    std::error_code ec;
    if (!fs::is_regular_file(exe, ec)) return false;
    std::wstring cmd = QuoteArg(exe.wstring());
    STARTUPINFOW si{};
    si.cb = sizeof(si);
    PROCESS_INFORMATION pi{};
    if (!CreateProcessW(exe.c_str(), cmd.data(), nullptr, nullptr, FALSE, 0, nullptr, appDir.c_str(), &si, &pi)) {
        return false;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return true;
}

bool SetAutoStart(const fs::path& exePath) {
    // 与 WindowsAutoStart 写入格式一致
    std::wstring value = L"\"" + exePath.wstring() + L"\"";
    LSTATUS st = RegSetKeyValueW(HKEY_CURRENT_USER,
                                 kRunRegKey,
                                 kAppName,
                                 REG_SZ,
                                 value.c_str(),
                                 static_cast<DWORD>((value.size() + 1) * sizeof(wchar_t)));
    return st == ERROR_SUCCESS;
}

void RemoveAutoStartIfUnder(const fs::path& appDir) {
    DWORD size = 0;
    if (RegGetValueW(HKEY_CURRENT_USER, kRunRegKey, kAppName, RRF_RT_REG_SZ, nullptr, nullptr, &size) != ERROR_SUCCESS || size == 0) {
        return;
    }
    std::wstring value(size / sizeof(wchar_t) + 1, L'\0');
    DWORD bytes = static_cast<DWORD>(value.size() * sizeof(wchar_t));
    if (RegGetValueW(HKEY_CURRENT_USER, kRunRegKey, kAppName, RRF_RT_REG_SZ, nullptr, value.data(), &bytes) != ERROR_SUCCESS) {
        return;
    }
    value.resize(wcsnlen(value.c_str(), value.size()));
    value = Trim(value);
    std::wstring exe;
    if (!value.empty() && value.front() == L'"') {
        size_t close = value.find(L'"', 1);
        exe = value.substr(1, close == std::wstring::npos ? std::wstring::npos : close - 1);
    } else {
        exe = value.substr(0, value.find(L' '));
    }
    if (!exe.empty() && IsSubPath(fs::path(exe), appDir)) {
        RegDeleteKeyValueW(HKEY_CURRENT_USER, kRunRegKey, kAppName);
    }
}

bool DesktopShortcutExists(bool perMachine) {
    fs::path desktop = KnownFolder(perMachine ? FOLDERID_PublicDesktop : FOLDERID_Desktop);
    if (desktop.empty()) return false;
    std::error_code ec;
    return fs::is_regular_file(desktop / L"Melodia.lnk", ec);
}

void ScheduleDeleteTempDir(const fs::path& dir, const std::wstring& prefix) {
    if (dir.empty() || dir.filename().wstring().rfind(prefix, 0) != 0) return;
    fs::path temp = TempDir();
    if (temp.empty() || !IsSubPath(dir, temp) || SamePath(dir, temp)) return;
    RunDelayed(L"rmdir /s /q " + QuoteArg(dir.wstring()));
}

namespace {

// 等当前进程及其父进程退出后再执行，用于删除仍被占用的文件
void RunDelayed(const std::wstring& action) {
    fs::path cmdExe = SystemExe(L"cmd.exe");
    fs::path pingExe = SystemExe(L"PING.EXE");
    std::wstring cmd = QuoteArg(cmdExe.wstring()) + L" /d /c \"" + QuoteArg(pingExe.wstring()) +
                       L" 127.0.0.1 -n 4 > nul & " + action + L"\"";
    fs::path sysDir = cmdExe.parent_path();
    STARTUPINFOW si{};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi{};
    if (CreateProcessW(cmdExe.c_str(), cmd.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, sysDir.c_str(), &si, &pi)) {
        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);
    }
}

}  // namespace

}  // namespace sys
