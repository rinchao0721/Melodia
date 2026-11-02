#pragma once

#include <windows.h>
#include <shlobj.h>

#include <filesystem>
#include <string>
#include <string_view>
#include <vector>

namespace sys {

namespace fs = std::filesystem;

fs::path ModulePath();
fs::path EnvPath(const wchar_t* name);
fs::path KnownFolder(const KNOWNFOLDERID& id);
fs::path TempDir();
fs::path SystemExe(const wchar_t* name);

// 与 DesktopPaths 保持一致：数据在 %APPDATA%\Melodia，默认缓存在 %LOCALAPPDATA%\Melodia\cache
fs::path DataDir();
fs::path LocalMelodiaDir();
fs::path DefaultCacheRoot();
fs::path PrefsFile();

fs::path NormalizePath(const fs::path& p);
bool SamePath(const fs::path& a, const fs::path& b);
bool IsSubPath(const fs::path& child, const fs::path& parent);
fs::path StripTrailingSeparators(const fs::path& p);

std::wstring Utf8ToWide(std::string_view utf8);
std::wstring QuoteArg(const std::wstring& arg);
std::wstring SystemErrorText(DWORD code);
std::wstring Trim(const std::wstring& s);

// 按点分数字比较，返回 <0 / 0 / >0
int CompareVersions(const std::wstring& a, const std::wstring& b);

bool IsProcessElevated();
bool IsMelodiaAppDir(const fs::path& dir);
bool IsDirectoryWritable(const fs::path& dir);

// 返回 true 表示目标已不存在
bool RemoveTreeWithRetry(const fs::path& target);
// 只删 Melodia 程序自身的文件与子目录，目录空了再移除
bool RemoveAppEntries(const fs::path& appDir);

std::vector<fs::path> ReadCustomCacheRoots(const fs::path& prefsFile);
// 清除配置与缓存；返回 true 表示全部清理干净
bool CleanUserData(const std::vector<fs::path>& customCacheRoots);

// 只处理可执行文件位于 appDir 内的 Melodia 进程；返回 true 表示已无此类进程
bool StopMelodiaUnder(const fs::path& appDir);
bool LaunchMelodia(const fs::path& appDir);

bool SetAutoStart(const fs::path& exePath);
// Run 项指向 appDir 内时才删除
void RemoveAutoStartIfUnder(const fs::path& appDir);

bool DesktopShortcutExists(bool perMachine);

// 临时运行目录须以 prefix 开头且位于 %TEMP% 下才会被删除
void ScheduleDeleteTempDir(const fs::path& dir, const std::wstring& prefix);

}  // namespace sys
