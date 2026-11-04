#pragma once

#include <windows.h>
#include <msi.h>

#include <filesystem>
#include <functional>
#include <optional>
#include <string>
#include <vector>

#include "Version.h"

namespace msi {

namespace fs = std::filesystem;

struct InstalledProduct {
    std::wstring code;
    MSIINSTALLCONTEXT context = MSIINSTALLCONTEXT_USERUNMANAGED;
    // MSI 三位数字版本，预发布版与正式版无法区分
    std::wstring version;
    // 完整版本（MelodiaVersion 注册表值），早期安装包没有该值
    std::wstring fullVersion;
    std::optional<ver::Release> release;
    fs::path location;

    bool PerMachine() const { return context == MSIINSTALLCONTEXT_MACHINE; }
};

struct Result {
    UINT code = ERROR_SUCCESS;
    std::wstring message;

    bool Ok() const;
    bool RebootRequired() const;
    std::wstring Describe() const;
};

using ProgressFn = std::function<void(int percent)>;

// 按 UpgradeCode 枚举当前用户与本机范围内已安装的 Melodia
std::vector<InstalledProduct> FindInstalledProducts();
std::optional<InstalledProduct> FindByLocation(const std::vector<InstalledProduct>& products, const fs::path& dir);
std::optional<InstalledProduct> Newest(const std::vector<InstalledProduct>& products);

fs::path DefaultInstallDir(bool perMachine);
std::wstring InstallProperties(const fs::path& dir, bool perMachine, bool desktopShortcut);

Result Install(const fs::path& package, const std::wstring& properties, const fs::path& log, const ProgressFn& onProgress);
Result Uninstall(const std::wstring& productCode, const fs::path& log, const ProgressFn& onProgress);
// 数字版本相同的覆盖安装：先卸载旧产品再安装新包，日志追加写入同一文件
Result Replace(const std::wstring& oldProductCode, const fs::path& package, const std::wstring& properties, const fs::path& log,
               const ProgressFn& onProgress);

}  // namespace msi
