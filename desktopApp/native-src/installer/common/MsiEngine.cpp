#include "MsiEngine.h"

#include "Sys.h"

#include <msiquery.h>

#include <algorithm>
#include <cstdint>

namespace msi {

namespace {

// 与 build.gradle.kts 中 upgradeUuid 保持一致
constexpr wchar_t kUpgradeCode[] = L"{6F0B8A52-3C1E-4D7A-9B25-8E4C2F1D7A93}";
constexpr wchar_t kUninstallRegKey[] = L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Melodia";
constexpr wchar_t kVendorRegKey[] = L"Software\\Melodia\\Melodia";

constexpr DWORD kVerboseLogMode = INSTALLLOGMODE_FATALEXIT | INSTALLLOGMODE_ERROR | INSTALLLOGMODE_WARNING |
                                  INSTALLLOGMODE_USER | INSTALLLOGMODE_INFO | INSTALLLOGMODE_RESOLVESOURCE |
                                  INSTALLLOGMODE_OUTOFDISKSPACE | INSTALLLOGMODE_ACTIONSTART |
                                  INSTALLLOGMODE_ACTIONDATA | INSTALLLOGMODE_COMMONDATA |
                                  INSTALLLOGMODE_PROPERTYDUMP | INSTALLLOGMODE_VERBOSE;

constexpr DWORD kUiFilter = INSTALLLOGMODE_PROGRESS | INSTALLLOGMODE_ACTIONDATA | INSTALLLOGMODE_ERROR |
                            INSTALLLOGMODE_FATALEXIT;

std::optional<std::wstring> QueryProductInfo(const std::wstring& code, MSIINSTALLCONTEXT context, const wchar_t* prop) {
    DWORD len = 0;
    UINT r = MsiGetProductInfoExW(code.c_str(), nullptr, context, prop, nullptr, &len);
    if (r != ERROR_SUCCESS) return std::nullopt;
    std::wstring value(static_cast<size_t>(len) + 1, L'\0');
    DWORD size = len + 1;
    r = MsiGetProductInfoExW(code.c_str(), nullptr, context, prop, value.data(), &size);
    if (r != ERROR_SUCCESS) return std::nullopt;
    value.resize(size);
    return value;
}

std::wstring ReadRegistryLocation(bool perMachine) {
    HKEY root = perMachine ? HKEY_LOCAL_MACHINE : HKEY_CURRENT_USER;
    DWORD size = 0;
    if (RegGetValueW(root, kUninstallRegKey, L"InstallLocation", RRF_RT_REG_SZ, nullptr, nullptr, &size) != ERROR_SUCCESS ||
        size == 0) {
        return {};
    }
    std::wstring value(size / sizeof(wchar_t) + 1, L'\0');
    DWORD bytes = static_cast<DWORD>(value.size() * sizeof(wchar_t));
    if (RegGetValueW(root, kUninstallRegKey, L"InstallLocation", RRF_RT_REG_SZ, nullptr, value.data(), &bytes) != ERROR_SUCCESS) {
        return {};
    }
    value.resize(wcsnlen(value.c_str(), value.size()));
    return value;
}

std::wstring QuoteProperty(const std::wstring& value) {
    std::wstring out = L"\"";
    for (wchar_t c : value) {
        if (c == L'"') out.push_back(L'"');
        out.push_back(c);
    }
    out.push_back(L'"');
    return out;
}

struct UiContext {
    const ProgressFn* onProgress = nullptr;
    std::wstring lastError;
    int64_t total = 0;
    int64_t complete = 0;
    int64_t step = 0;
    bool forward = true;
    bool actionDataEnabled = false;
    bool creatingScript = false;
    int lastPercent = -1;
};

int RecordInt(MSIHANDLE rec, UINT field) {
    int value = MsiRecordGetInteger(rec, field);
    return value == MSI_NULL_INTEGER ? 0 : value;
}

void ReportProgress(UiContext& ctx) {
    if (ctx.total <= 0 || !ctx.onProgress || !*ctx.onProgress) return;
    double fraction = static_cast<double>(ctx.complete) / static_cast<double>(ctx.total);
    fraction = (std::clamp)(fraction, 0.0, 1.0);
    // 生成脚本阶段占前 10%，执行阶段占其余
    int percent = ctx.creatingScript ? static_cast<int>(fraction * 10.0) : 10 + static_cast<int>(fraction * 90.0);
    percent = (std::max)(percent, ctx.lastPercent);
    if (percent == ctx.lastPercent) return;
    ctx.lastPercent = percent;
    (*ctx.onProgress)(percent);
}

INT CALLBACK RecordHandler(LPVOID context, UINT messageType, MSIHANDLE record) {
    auto* ctx = static_cast<UiContext*>(context);
    if (!ctx || !record) return 0;
    auto type = static_cast<INSTALLMESSAGE>(0xFF000000 & messageType);
    switch (type) {
        case INSTALLMESSAGE_PROGRESS: {
            switch (RecordInt(record, 1)) {
                case 0:
                    ctx->total = RecordInt(record, 2);
                    ctx->forward = RecordInt(record, 3) == 0;
                    ctx->complete = ctx->forward ? 0 : ctx->total;
                    ctx->actionDataEnabled = false;
                    ctx->creatingScript = RecordInt(record, 4) == 1;
                    break;
                case 1:
                    if (RecordInt(record, 3) == 1) {
                        ctx->step = RecordInt(record, 2);
                        ctx->actionDataEnabled = true;
                    } else {
                        ctx->actionDataEnabled = false;
                    }
                    break;
                case 2:
                    ctx->complete += ctx->forward ? RecordInt(record, 2) : -RecordInt(record, 2);
                    break;
                case 3:
                    ctx->total += RecordInt(record, 2);
                    break;
                default:
                    break;
            }
            ReportProgress(*ctx);
            return IDOK;
        }
        case INSTALLMESSAGE_ACTIONDATA:
            if (ctx->actionDataEnabled) {
                ctx->complete += ctx->forward ? ctx->step : -ctx->step;
                ReportProgress(*ctx);
            }
            return IDOK;
        case INSTALLMESSAGE_ERROR:
        case INSTALLMESSAGE_FATALEXIT: {
            DWORD len = 0;
            if (MsiFormatRecordW(0, record, nullptr, &len) == ERROR_SUCCESS && len > 0) {
                std::wstring text(static_cast<size_t>(len) + 1, L'\0');
                DWORD size = len + 1;
                if (MsiFormatRecordW(0, record, text.data(), &size) == ERROR_SUCCESS) {
                    text.resize(size);
                    ctx->lastError = sys::Trim(text);
                }
            }
            return 0;
        }
        default:
            return 0;
    }
}

void DeleteRemoveFolderValues(HKEY root) {
    HKEY parent = nullptr;
    if (RegOpenKeyExW(root, kVendorRegKey, 0, KEY_READ, &parent) != ERROR_SUCCESS) return;
    std::vector<std::wstring> subkeys;
    wchar_t name[256]{};
    for (DWORD i = 0;; ++i) {
        DWORD len = 256;
        if (RegEnumKeyExW(parent, i, name, &len, nullptr, nullptr, nullptr, nullptr) != ERROR_SUCCESS) break;
        subkeys.emplace_back(name, len);
    }
    for (const std::wstring& sub : subkeys) {
        HKEY key = nullptr;
        if (RegOpenKeyExW(parent, sub.c_str(), 0, KEY_READ | KEY_SET_VALUE, &key) != ERROR_SUCCESS) continue;
        std::vector<std::wstring> doomed;
        wchar_t valueName[16384]{};
        for (DWORD i = 0;; ++i) {
            DWORD len = 16384;
            if (RegEnumValueW(key, i, valueName, &len, nullptr, nullptr, nullptr, nullptr) != ERROR_SUCCESS) break;
            std::wstring value(valueName, len);
            if (value.rfind(L"RM_RF", 0) == 0) doomed.push_back(value);
        }
        for (const std::wstring& value : doomed) RegDeleteValueW(key, value.c_str());
        RegCloseKey(key);
    }
    RegCloseKey(parent);
}

// 旧版包的 RemoveFolderEx 按这些注册表值整目录删除 INSTALLDIR，提前删值使其失效
void NeutralizeLegacyFolderRemoval() {
    DeleteRemoveFolderValues(HKEY_CURRENT_USER);
    DeleteRemoveFolderValues(HKEY_LOCAL_MACHINE);
}

Result RunWithUi(const fs::path& log, const ProgressFn& onProgress, const std::function<UINT()>& action) {
    NeutralizeLegacyFolderRemoval();
    UiContext ctx;
    ctx.onProgress = &onProgress;
    MsiSetInternalUI(INSTALLUILEVEL_NONE, nullptr);
    MsiSetExternalUIRecord(RecordHandler, kUiFilter, &ctx, nullptr);
    if (!log.empty()) MsiEnableLogW(kVerboseLogMode, log.c_str(), INSTALLLOGATTRIBUTES_FLUSHEACHLINE);

    Result result;
    result.code = action();
    result.message = ctx.lastError;

    MsiSetExternalUIRecord(nullptr, 0, nullptr, nullptr);
    if (!log.empty()) MsiEnableLogW(0, nullptr, 0);
    if (result.Ok() && onProgress) onProgress(100);
    return result;
}

}  // namespace

bool Result::Ok() const {
    return code == ERROR_SUCCESS || RebootRequired();
}

bool Result::RebootRequired() const {
    return code == ERROR_SUCCESS_REBOOT_REQUIRED || code == ERROR_SUCCESS_REBOOT_INITIATED;
}

std::wstring Result::Describe() const {
    std::wstring text = message.empty() ? sys::SystemErrorText(code) : message;
    if (text.empty()) text = L"未知错误";
    return text + L"（错误代码 " + std::to_wstring(code) + L"）";
}

std::vector<InstalledProduct> FindInstalledProducts() {
    std::vector<std::wstring> codes;
    wchar_t code[39]{};
    for (DWORD idx = 0; MsiEnumRelatedProductsW(kUpgradeCode, 0, idx, code) == ERROR_SUCCESS; ++idx) {
        codes.emplace_back(code);
    }

    std::vector<InstalledProduct> products;
    const MSIINSTALLCONTEXT contexts[] = {
        MSIINSTALLCONTEXT_USERUNMANAGED,
        MSIINSTALLCONTEXT_USERMANAGED,
        MSIINSTALLCONTEXT_MACHINE,
    };
    for (const std::wstring& productCode : codes) {
        for (MSIINSTALLCONTEXT ctx : contexts) {
            auto version = QueryProductInfo(productCode, ctx, INSTALLPROPERTY_VERSIONSTRING);
            if (!version) continue;
            InstalledProduct product;
            product.code = productCode;
            product.context = ctx;
            product.version = *version;
            auto location = QueryProductInfo(productCode, ctx, INSTALLPROPERTY_INSTALLLOCATION);
            std::wstring loc = location ? *location : std::wstring();
            if (loc.empty()) loc = ReadRegistryLocation(product.PerMachine());
            if (!loc.empty()) product.location = sys::StripTrailingSeparators(fs::path(loc));
            products.push_back(std::move(product));
            break;
        }
    }
    return products;
}

std::optional<InstalledProduct> FindByLocation(const std::vector<InstalledProduct>& products, const fs::path& dir) {
    for (const InstalledProduct& p : products) {
        if (!p.location.empty() && sys::SamePath(p.location, dir)) return p;
    }
    return std::nullopt;
}

std::optional<InstalledProduct> Newest(const std::vector<InstalledProduct>& products) {
    std::optional<InstalledProduct> best;
    for (const InstalledProduct& p : products) {
        if (!best || sys::CompareVersions(p.version, best->version) > 0) best = p;
    }
    return best;
}

fs::path DefaultInstallDir(bool perMachine) {
    if (perMachine) {
        fs::path programFiles = sys::KnownFolder(FOLDERID_ProgramFiles);
        if (programFiles.empty()) programFiles = sys::EnvPath(L"ProgramFiles");
        return programFiles.empty() ? fs::path() : programFiles / L"Melodia";
    }
    return sys::LocalMelodiaDir();
}

std::wstring InstallProperties(const fs::path& dir, bool perMachine, bool desktopShortcut) {
    std::wstring props = L"INSTALLDIR=" + QuoteProperty(dir.wstring());
    props += perMachine ? L" ALLUSERS=1" : L" ALLUSERS=2 MSIINSTALLPERUSER=1";
    props += desktopShortcut ? L" JP_INSTALL_DESKTOP_SHORTCUT=1" : L" JP_INSTALL_DESKTOP_SHORTCUT=\"\"";
    props += L" REBOOT=ReallySuppress";
    return props;
}

Result Install(const fs::path& package, const std::wstring& properties, const fs::path& log, const ProgressFn& onProgress) {
    return RunWithUi(log, onProgress, [&]() { return MsiInstallProductW(package.c_str(), properties.c_str()); });
}

Result Uninstall(const std::wstring& productCode, const fs::path& log, const ProgressFn& onProgress) {
    return RunWithUi(log, onProgress, [&]() {
        return MsiConfigureProductExW(productCode.c_str(), INSTALLLEVEL_DEFAULT, INSTALLSTATE_ABSENT, L"REBOOT=ReallySuppress");
    });
}

}  // namespace msi
