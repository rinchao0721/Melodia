#pragma once

#include <windows.h>

#include <cstdint>
#include <filesystem>
#include <string>

// 界面进程保持普通权限，需要写机器范围时拉起无界面的提权子进程，进度经 WM_COPYDATA 回传
namespace elevate {

enum class PacketKind : int32_t {
    Progress = 0,
    Error = 1,
};

struct Packet {
    PacketKind kind = PacketKind::Progress;
    int percent = 0;
    std::wstring text;
};

void SendPacket(HWND parent, PacketKind kind, int percent, const std::wstring& text);
bool ParsePacket(LPARAM lParam, Packet& out);

HWND ParseWindowHandle(const wchar_t* text);
std::wstring FormatWindowHandle(HWND hwnd);

enum class LaunchResult {
    Finished,
    Cancelled,
    Failed,
};

// 阻塞直到子进程退出；调用线程需已初始化 COM
LaunchResult RunElevatedAndWait(HWND owner, const std::filesystem::path& exe, const std::wstring& args, DWORD& exitCode);

}  // namespace elevate
