// Melodia 安装程序：内嵌 MSI 并自绘向导界面，按用户安装在本进程执行，按机器安装交给提权子进程
#include <windows.h>
#include <objbase.h>
#include <shellapi.h>
#include <shobjidl.h>

#include "Elevate.h"
#include "MsiEngine.h"
#include "SetupConfig.h"
#include "Sys.h"
#include "Theme.h"
#include "Ui.h"

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cwctype>
#include <functional>
#include <mutex>
#include <optional>
#include <string>
#include <thread>

namespace {

namespace fs = std::filesystem;

constexpr int IDI_APP = 101;
constexpr int IDR_MSI = 201;
constexpr int IDR_LICENSE = 202;
constexpr UINT kMsgProgress = WM_APP + 1;
constexpr UINT kMsgFinished = WM_APP + 2;
constexpr wchar_t kRunnerPrefix[] = L"melodia_setup_";
constexpr wchar_t kWindowTitle[] = L"Melodia 安装程序";

constexpr int kExitOk = 0;
constexpr int kExitBadArgs = 1;
constexpr int kExitNeedsAdmin = 2;
constexpr int kExitAlreadyInstalled = 3;
constexpr int kExitFailed = 4;

constexpr int kIdCancel = 1;
constexpr int kIdBack = 2;
constexpr int kIdNext = 3;
constexpr int kIdBrowse = 4;
constexpr int kIdScopeUser = 5;
constexpr int kIdScopeMachine = 6;
constexpr int kIdDesktop = 7;
constexpr int kIdAutostart = 8;
constexpr int kIdLaunch = 9;
constexpr int kIdDir = 10;
constexpr int kIdLicense = 11;
constexpr int kIdOpenLog = 12;
constexpr int kIdOpenApp = 13;
constexpr int kIdFinish = 14;

constexpr int kScopeGroup = 1;
constexpr float kWindowWidth = 560.0f;
constexpr float kWindowHeight = 420.0f;
constexpr size_t kMaxDirLength = 150;

struct Options {
    fs::path dir;
    bool perMachine = false;
    bool desktop = true;
    bool autostart = false;
    bool launch = true;
};

struct InstallRun {
    msi::Result result;
    bool elevationCancelled = false;
};

using Reporter = std::function<void(int percent, const std::wstring& status)>;
using ElevatedRunner = std::function<InstallRun()>;

struct Args {
    bool silent = false;
    bool allUsers = false;
    bool noDesktop = false;
    bool autostart = false;
    bool run = false;
    bool elevatedChild = false;
    HWND parent = nullptr;
    fs::path dir;
    fs::path log;
    bool valid = true;
};

// /D= 与 NSIS 一致：必须是最后一个参数，路径含空格也不加引号
Args ParseArgs() {
    Args args;
    std::wstring raw = GetCommandLineW();
    std::wstring head = raw;
    size_t dpos = raw.find(L" /D=");
    if (dpos != std::wstring::npos) {
        args.dir = sys::Trim(raw.substr(dpos + 4));
        head = raw.substr(0, dpos);
    }
    int argc = 0;
    LPWSTR* argv = CommandLineToArgvW(head.c_str(), &argc);
    if (!argv) return args;
    auto next = [&](int& i) -> const wchar_t* {
        if (i + 1 >= argc) {
            args.valid = false;
            return nullptr;
        }
        return argv[++i];
    };
    for (int i = 1; i < argc; ++i) {
        const wchar_t* a = argv[i];
        if (wcscmp(a, L"/S") == 0) {
            args.silent = true;
        } else if (_wcsicmp(a, L"/allusers") == 0) {
            args.allUsers = true;
        } else if (_wcsicmp(a, L"/nodesktop") == 0 || wcscmp(a, L"--nodesktop") == 0) {
            args.noDesktop = true;
        } else if (_wcsicmp(a, L"/autostart") == 0) {
            args.autostart = true;
        } else if (_wcsicmp(a, L"/run") == 0) {
            args.run = true;
        } else if (wcscmp(a, L"--elevated-install") == 0) {
            args.elevatedChild = true;
        } else if (wcscmp(a, L"--parent") == 0) {
            if (const wchar_t* v = next(i)) args.parent = elevate::ParseWindowHandle(v);
        } else if (wcscmp(a, L"--dir") == 0) {
            if (const wchar_t* v = next(i)) args.dir = v;
        } else if (wcscmp(a, L"--log") == 0) {
            if (const wchar_t* v = next(i)) args.log = v;
        } else {
            args.valid = false;
        }
    }
    LocalFree(argv);
    return args;
}

fs::path SetupLogPath() {
    fs::path temp = sys::TempDir();
    return temp.empty() ? fs::path() : temp / L"Melodia-setup.log";
}

bool LoadResourceBytes(int id, const void*& data, DWORD& size) {
    HRSRC res = FindResourceW(nullptr, MAKEINTRESOURCEW(id), RT_RCDATA);
    if (!res) return false;
    size = SizeofResource(nullptr, res);
    HGLOBAL handle = LoadResource(nullptr, res);
    data = handle ? LockResource(handle) : nullptr;
    return data != nullptr && size > 0;
}

std::wstring LoadLicenseText() {
    const void* data = nullptr;
    DWORD size = 0;
    if (!LoadResourceBytes(IDR_LICENSE, data, size)) return L"MIT License";
    std::wstring text = sys::Utf8ToWide(std::string_view(static_cast<const char*>(data), size));
    // 原文按 80 列硬换行，合并成段落交给编辑框自动折行
    std::wstring out;
    out.reserve(text.size() + 64);
    bool paragraphOpen = false;
    size_t pos = 0;
    while (pos <= text.size()) {
        size_t end = text.find(L'\n', pos);
        std::wstring line = sys::Trim(text.substr(pos, end == std::wstring::npos ? std::wstring::npos : end - pos));
        if (line.empty()) {
            paragraphOpen = false;
        } else {
            if (paragraphOpen) {
                out.push_back(L' ');
            } else if (!out.empty()) {
                out += L"\r\n\r\n";
            }
            out += line;
            paragraphOpen = true;
        }
        if (end == std::wstring::npos) break;
        pos = end + 1;
    }
    return out;
}

// 内嵌 MSI 解压到独立临时目录，安装期间保持只读共享打开，防止被其他进程改写
class PackageFile {
public:
    PackageFile() = default;
    PackageFile(const PackageFile&) = delete;
    PackageFile& operator=(const PackageFile&) = delete;

    ~PackageFile() {
        if (lock_ != INVALID_HANDLE_VALUE) CloseHandle(lock_);
        if (!dir_.empty()) {
            std::error_code ec;
            fs::remove_all(dir_, ec);
        }
    }

    bool Extract() {
        const void* data = nullptr;
        DWORD size = 0;
        if (!LoadResourceBytes(IDR_MSI, data, size)) return false;
        fs::path temp = sys::TempDir();
        if (temp.empty()) return false;
        dir_ = temp / (std::wstring(kRunnerPrefix) + std::to_wstring(GetCurrentProcessId()) + L"_" + std::to_wstring(GetTickCount64()));
        std::error_code ec;
        fs::create_directories(dir_, ec);
        if (ec) return false;
        path_ = dir_ / L"Melodia.msi";

        HANDLE out = CreateFileW(path_.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (out == INVALID_HANDLE_VALUE) return false;
        const auto* bytes = static_cast<const uint8_t*>(data);
        DWORD remaining = size;
        bool ok = true;
        while (remaining > 0) {
            DWORD chunk = (std::min)(remaining, static_cast<DWORD>(8 * 1024 * 1024));
            DWORD written = 0;
            if (!WriteFile(out, bytes, chunk, &written, nullptr) || written != chunk) {
                ok = false;
                break;
            }
            bytes += chunk;
            remaining -= chunk;
        }
        if (!FlushFileBuffers(out)) ok = false;
        CloseHandle(out);
        if (!ok) return false;

        lock_ = CreateFileW(path_.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
        return lock_ != INVALID_HANDLE_VALUE;
    }

    const fs::path& Path() const { return path_; }

private:
    fs::path dir_;
    fs::path path_;
    HANDLE lock_ = INVALID_HANDLE_VALUE;
};

msi::Result InstallMsi(const Options& o, const fs::path& log, const msi::ProgressFn& onProgress) {
    PackageFile package;
    if (!package.Extract()) {
        msi::Result r;
        r.code = ERROR_WRITE_FAULT;
        r.message = L"无法将安装文件解压到临时目录，请检查磁盘空间";
        return r;
    }
    return msi::Install(package.Path(), msi::InstallProperties(o.dir, o.perMachine, o.desktop), log, onProgress);
}

InstallRun RunInstall(const Options& o, bool fresh, const fs::path& log, const Reporter& report, const ElevatedRunner& runElevated) {
    InstallRun run;
    report(3, L"正在关闭运行中的 Melodia…");
    if (!sys::StopMelodiaUnder(o.dir)) {
        run.result.code = ERROR_INSTALL_FAILURE;
        run.result.message = L"无法关闭正在运行的 Melodia，请手动退出后重试";
        return run;
    }

    const wchar_t* status = fresh ? L"正在安装…" : L"正在升级…";
    if (runElevated) {
        report(8, status);
        run = runElevated();
    } else {
        report(5, L"正在准备安装文件…");
        run.result = InstallMsi(o, log, [&](int pct) { report(8 + pct * 87 / 100, status); });
    }
    if (run.elevationCancelled || !run.result.Ok()) return run;

    report(97, L"正在完成设置…");
    if (fresh && o.autostart) sys::SetAutoStart(o.dir / L"Melodia.exe");
    report(100, fresh ? L"安装完成" : L"升级完成");
    return run;
}

bool HasDriveRoot(const std::wstring& s) {
    return s.size() >= 3 && std::iswalpha(s[0]) && s[1] == L':' && (s[2] == L'\\' || s[2] == L'/');
}

// 返回空字符串表示校验通过
std::wstring ValidateDir(const fs::path& dir, bool perMachine) {
    std::wstring s = dir.wstring();
    if (s.empty()) return L"请选择安装位置";
    if (!HasDriveRoot(s)) return L"请输入完整的安装路径，例如 D:\\Melodia";
    if (!dir.has_relative_path()) return L"不能直接安装到磁盘根目录，请选择一个文件夹";
    if (s.find_first_of(L"<>\"|?*", 2) != std::wstring::npos || s.find(L':', 2) != std::wstring::npos) {
        return L"路径中包含无效字符";
    }
    if (s.size() > kMaxDirLength) return L"路径过长，请选择更短的位置";
    std::error_code ec;
    if (fs::exists(dir, ec) && !fs::is_directory(dir, ec)) return L"该路径已存在同名文件";
    if (!perMachine && !sys::IsDirectoryWritable(dir)) return L"当前账户无法写入该位置，请更换目录或选择“所有用户”";
    return {};
}

fs::path NormalizeInputDir(const std::wstring& text) {
    std::wstring s = sys::Trim(text);
    for (wchar_t& c : s) {
        if (c == L'/') c = L'\\';
    }
    return sys::StripTrailingSeparators(fs::path(s));
}

fs::path AppendAppFolder(const fs::path& dir) {
    fs::path clean = sys::StripTrailingSeparators(dir);
    if (_wcsicmp(clean.filename().c_str(), L"Melodia") == 0) return clean;
    return clean / L"Melodia";
}

std::optional<fs::path> BrowseForFolder(HWND owner, const fs::path& current) {
    IFileOpenDialog* dialog = nullptr;
    if (FAILED(CoCreateInstance(CLSID_FileOpenDialog, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&dialog))) || !dialog) {
        return std::nullopt;
    }
    std::optional<fs::path> result;
    DWORD options = 0;
    if (SUCCEEDED(dialog->GetOptions(&options))) {
        dialog->SetOptions(options | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST);
    }
    dialog->SetTitle(L"选择安装位置");

    std::error_code ec;
    fs::path start = current;
    while (!start.empty() && !fs::is_directory(start, ec)) {
        fs::path parent = start.parent_path();
        if (parent == start) {
            start.clear();
            break;
        }
        start = parent;
    }
    if (!start.empty()) {
        IShellItem* folder = nullptr;
        if (SUCCEEDED(SHCreateItemFromParsingName(start.c_str(), nullptr, IID_PPV_ARGS(&folder))) && folder) {
            dialog->SetFolder(folder);
            folder->Release();
        }
    }

    if (SUCCEEDED(dialog->Show(owner))) {
        IShellItem* item = nullptr;
        if (SUCCEEDED(dialog->GetResult(&item)) && item) {
            PWSTR picked = nullptr;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &picked)) && picked) {
                result = fs::path(picked);
                CoTaskMemFree(picked);
            }
            item->Release();
        }
    }
    dialog->Release();
    return result;
}

enum class Mode {
    Fresh,
    Upgrade,
    UpToDate,
};

struct InstallState {
    Mode mode = Mode::Fresh;
    std::optional<msi::InstalledProduct> installed;
    Options options;
};

InstallState DetectInstallState() {
    InstallState state;
    state.options.dir = msi::DefaultInstallDir(false);
    std::optional<msi::InstalledProduct> newest = msi::Newest(msi::FindInstalledProducts());
    if (!newest) return state;
    state.installed = newest;
    if (sys::CompareVersions(newest->version, MELODIA_VERSION) >= 0) {
        state.mode = Mode::UpToDate;
    } else {
        state.mode = Mode::Upgrade;
    }
    state.options.perMachine = newest->PerMachine();
    state.options.dir = newest->location.empty() ? msi::DefaultInstallDir(state.options.perMachine) : newest->location;
    state.options.desktop = sys::DesktopShortcutExists(state.options.perMachine);
    return state;
}

class SetupWindow : public mui::Window {
public:
    SetupWindow(InstallState state, fs::path selfPath)
        : state_(std::move(state)), selfPath_(std::move(selfPath)), log_(SetupLogPath()) {}

    void Start() {
        if (state_.mode == Mode::UpToDate) {
            ShowUpToDate();
        } else {
            ShowWelcome();
        }
    }

protected:
    void Paint(mui::Painter& p) override {
        float w = Width();
        float h = Height();
        float bodyW = w - mui::theme::kMargin * 2.0f;
        bool fresh = state_.mode == Mode::Fresh;
        int step = -1;
        switch (page_) {
            case Page::Welcome: {
                step = 0;
                if (fresh) {
                    mui::PaintHeader(p, HeaderIcon(), L"安装 Melodia", L"版本 " + std::wstring(MELODIA_DISPLAY_VERSION), w);
                    p.Text(L"欢迎使用 Melodia 安装向导，接下来将引导你完成安装。\n\n安装过程中会自动关闭正在运行的 Melodia。",
                           Gdiplus::RectF(mui::theme::kMargin, mui::kBodyTop + 8.0f, bodyW, 120.0f), mui::theme::kBodySize, false,
                           mui::theme::kTextGray);
                } else {
                    mui::PaintHeader(p, HeaderIcon(), L"升级 Melodia",
                                     L"版本 " + state_.installed->version + L" → " + MELODIA_DISPLAY_VERSION, w);
                    std::wstring body = L"检测到此电脑已安装 Melodia " + state_.installed->version + L"，将原地升级到 " +
                                        MELODIA_DISPLAY_VERSION + L"。\n\n安装位置：" + state_.options.dir.wstring() +
                                        L"\n安装范围：" + (state_.options.perMachine ? L"所有用户" : L"仅为我") +
                                        L"\n\n个人配置与数据会保留，升级时会自动关闭正在运行的 Melodia。";
                    p.Text(body, Gdiplus::RectF(mui::theme::kMargin, mui::kBodyTop + 8.0f, bodyW, 180.0f), mui::theme::kBodySize, false,
                           mui::theme::kTextGray);
                }
                PaintNote(p, 300.0f);
                break;
            }
            case Page::License:
                step = 1;
                mui::PaintHeader(p, HeaderIcon(), L"许可协议", L"Melodia 基于 MIT 许可证开源，请阅读以下条款", w);
                break;
            case Page::Options:
                step = 2;
                mui::PaintHeader(p, HeaderIcon(), L"安装选项", L"选择安装位置与附加选项", w);
                PaintLabel(p, L"安装位置", 96.0f);
                PaintNote(p, 158.0f);
                PaintLabel(p, L"安装范围", 184.0f);
                PaintLabel(p, L"附加选项", 242.0f);
                break;
            case Page::Running: {
                step = fresh ? 3 : 1;
                mui::PaintHeader(p, HeaderIcon(), fresh ? L"正在安装 Melodia" : L"正在升级 Melodia", L"请稍候，不要关闭此窗口", w);
                std::wstring status;
                int pct = progress_.load();
                {
                    std::lock_guard<std::mutex> lock(mutex_);
                    status = status_;
                }
                float y = mui::kBodyTop + 24.0f;
                p.Text(status, Gdiplus::RectF(mui::theme::kMargin, y, bodyW - 50.0f, 20.0f), mui::theme::kBodySize, false,
                       mui::theme::kTextGray);
                p.Text(std::to_wstring(pct) + L"%", Gdiplus::RectF(mui::theme::kMargin, y, bodyW, 20.0f), mui::theme::kBodySize, false,
                       mui::theme::kTextPrimary, Gdiplus::StringAlignmentFar);
                Gdiplus::RectF track(mui::theme::kMargin, y + 32.0f, bodyW, 6.0f);
                p.FillRound(track, 3.0f, mui::theme::kSurface);
                float fillW = (std::max)(6.0f, bodyW * static_cast<float>(pct) / 100.0f);
                p.FillRound(Gdiplus::RectF(track.X, track.Y, (std::min)(bodyW, fillW), track.Height), 3.0f, mui::theme::kAccent);
                break;
            }
            case Page::Done: {
                step = fresh ? 3 : 1;
                mui::PaintHeader(p, HeaderIcon(), fresh ? L"安装完成" : L"升级完成", L"Melodia " + std::wstring(MELODIA_DISPLAY_VERSION), w);
                std::wstring body = L"Melodia 已安装到：\n" + state_.options.dir.wstring();
                if (result_.RebootRequired()) body += L"\n\n部分文件将在重启电脑后更新。";
                p.Text(body, Gdiplus::RectF(mui::theme::kMargin, mui::kBodyTop + 8.0f, bodyW, 80.0f), mui::theme::kBodySize, false,
                       mui::theme::kTextGray);
                break;
            }
            case Page::Failed:
                mui::PaintHeader(p, HeaderIcon(), fresh ? L"安装失败" : L"升级失败", L"本次改动已撤销", w);
                p.Text(result_.Describe(),
                       Gdiplus::RectF(mui::theme::kMargin, mui::kBodyTop + 8.0f, bodyW, h - mui::theme::kFooterHeight - mui::kBodyTop - 16.0f),
                       mui::theme::kBodySize, false, mui::theme::kTextGray);
                break;
            case Page::UpToDate:
                mui::PaintHeader(p, HeaderIcon(), L"Melodia 已是最新版本", L"版本 " + state_.installed->version, w);
                p.Text(L"此电脑已安装 Melodia " + state_.installed->version + L"，不低于本安装程序的版本 " + MELODIA_DISPLAY_VERSION +
                           L"，无需安装。",
                       Gdiplus::RectF(mui::theme::kMargin, mui::kBodyTop + 8.0f, bodyW, 80.0f), mui::theme::kBodySize, false,
                       mui::theme::kTextGray);
                break;
        }
        int steps = fresh ? 4 : 2;
        mui::PaintFooter(p, w, h, step >= 0 ? steps : 0, step);
    }

    void OnCommand(int id) override {
        switch (id) {
            case kIdCancel:
                PostMessageW(Hwnd(), WM_CLOSE, 0, 0);
                break;
            case kIdBack:
                if (page_ == Page::License) {
                    ShowWelcome();
                } else if (page_ == Page::Options) {
                    CaptureOptions();
                    ShowLicense();
                } else if (page_ == Page::Failed) {
                    ShowOptions();
                }
                break;
            case kIdNext:
                OnNext();
                break;
            case kIdBrowse: {
                fs::path current = NormalizeInputDir(EditText(kIdDir));
                if (std::optional<fs::path> picked = BrowseForFolder(Hwnd(), current)) {
                    SetEditText(kIdDir, AppendAppFolder(*picked).wstring());
                    dirEdited_ = true;
                    note_.clear();
                    Invalidate();
                }
                break;
            }
            case kIdScopeUser:
            case kIdScopeMachine:
                OnScopeChanged(id == kIdScopeMachine);
                break;
            case kIdOpenLog:
                ShellExecuteW(Hwnd(), L"open", log_.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
                break;
            case kIdOpenApp:
                if (state_.installed) sys::LaunchMelodia(state_.installed->location);
                DestroyWindow(Hwnd());
                break;
            case kIdFinish: {
                bool launch = page_ == Page::Done && LaunchRequested();
                fs::path dir = state_.options.dir;
                DestroyWindow(Hwnd());
                if (launch) sys::LaunchMelodia(dir);
                break;
            }
            default:
                break;
        }
    }

    void OnEditChanged(int id) override {
        if (id != kIdDir) return;
        dirEdited_ = true;
        if (!note_.empty()) {
            note_.clear();
            Invalidate();
        }
    }

    void OnEscape() override {
        PostMessageW(Hwnd(), WM_CLOSE, 0, 0);
    }

    bool CanClose() override {
        if (page_ == Page::Running) return false;
        if (page_ == Page::Welcome || page_ == Page::License || page_ == Page::Options) {
            return MessageBoxW(Hwnd(), L"确定要退出 Melodia 安装程序吗？", kWindowTitle, MB_YESNO | MB_ICONQUESTION | MB_DEFBUTTON2) ==
                   IDYES;
        }
        return true;
    }

    bool HandleMessage(UINT msg, WPARAM /*wParam*/, LPARAM lParam, LRESULT& result) override {
        switch (msg) {
            case WM_COPYDATA: {
                elevate::Packet packet;
                if (page_ == Page::Running && elevate::ParsePacket(lParam, packet)) {
                    if (packet.kind == elevate::PacketKind::Progress) {
                        Report(8 + packet.percent * 87 / 100, state_.mode == Mode::Fresh ? L"正在安装…" : L"正在升级…");
                    } else {
                        std::lock_guard<std::mutex> lock(mutex_);
                        childError_ = packet.text;
                    }
                    result = TRUE;
                } else {
                    result = FALSE;
                }
                return true;
            }
            case kMsgProgress:
                Invalidate();
                result = 0;
                return true;
            case kMsgFinished:
                OnFinished();
                result = 0;
                return true;
            default:
                return false;
        }
    }

private:
    enum class Page {
        Welcome,
        License,
        Options,
        Running,
        Done,
        Failed,
        UpToDate,
    };

    static mui::Control Button(int id, mui::Kind kind, const Gdiplus::RectF& rect, const wchar_t* text) {
        mui::Control c;
        c.id = id;
        c.kind = kind;
        c.rect = rect;
        c.text = text;
        return c;
    }

    void PaintLabel(mui::Painter& p, const wchar_t* text, float y) {
        p.Text(text, Gdiplus::RectF(mui::theme::kMargin, y, 200.0f, 20.0f), 12.5f, true, mui::theme::kTextGray);
    }

    void PaintNote(mui::Painter& p, float y) {
        if (note_.empty()) return;
        p.Text(note_, Gdiplus::RectF(mui::theme::kMargin, y, Width() - mui::theme::kMargin * 2.0f, 20.0f), 12.0f, false,
               mui::theme::kError);
    }

    bool NeedsElevation() const {
        return state_.options.perMachine && !sys::IsProcessElevated();
    }

    void BeginPage(Page page, bool closable) {
        page_ = page;
        ClearControls();
        SetClosable(closable);
    }

    void ShowWelcome() {
        BeginPage(Page::Welcome, true);
        float w = Width();
        float h = Height();
        Add(Button(kIdCancel, mui::Kind::Button, mui::FooterButtonRect(w, h, 1), L"取消"));
        mui::Control next = Button(kIdNext, mui::Kind::PrimaryButton, mui::FooterButtonRect(w, h, 0),
                                   state_.mode == Mode::Fresh ? L"下一步" : L"升级");
        next.shield = state_.mode == Mode::Upgrade && NeedsElevation();
        Add(next);
        SetDefaultButton(kIdNext);
        FocusControl(kIdNext);
    }

    void ShowLicense() {
        BeginPage(Page::License, true);
        float w = Width();
        float h = Height();
        if (licenseText_.empty()) licenseText_ = LoadLicenseText();
        float bodyW = w - mui::theme::kMargin * 2.0f;
        AddEdit(kIdLicense, Gdiplus::RectF(mui::theme::kMargin, mui::kBodyTop, bodyW, h - mui::theme::kFooterHeight - mui::kBodyTop - 16.0f),
                true, true);
        SetEditText(kIdLicense, licenseText_);
        Add(Button(kIdCancel, mui::Kind::Button, mui::FooterButtonRect(w, h, 2), L"取消"));
        Add(Button(kIdBack, mui::Kind::Button, mui::FooterButtonRect(w, h, 1), L"上一步"));
        Add(Button(kIdNext, mui::Kind::PrimaryButton, mui::FooterButtonRect(w, h, 0), L"同意并继续"));
        SetDefaultButton(kIdNext);
        FocusControl(kIdNext);
    }

    void ShowOptions() {
        BeginPage(Page::Options, true);
        float w = Width();
        float h = Height();
        float m = mui::theme::kMargin;
        float bodyW = w - m * 2.0f;
        float browseW = mui::theme::kButtonWidth;

        AddEdit(kIdDir, Gdiplus::RectF(m, 118.0f, bodyW - browseW - 10.0f, 34.0f), false, false);
        SetEditText(kIdDir, state_.options.dir.wstring());
        Add(Button(kIdBrowse, mui::Kind::Button, Gdiplus::RectF(w - m - browseW, 118.0f, browseW, 34.0f), L"浏览…"));

        mui::Control user = Button(kIdScopeUser, mui::Kind::Radio, Gdiplus::RectF(m, 206.0f, 120.0f, 26.0f), L"仅为我");
        user.group = kScopeGroup;
        user.checked = !state_.options.perMachine;
        Add(user);
        mui::Control machine = Button(kIdScopeMachine, mui::Kind::Radio, Gdiplus::RectF(m + 132.0f, 206.0f, 260.0f, 26.0f),
                                      L"所有用户（需要管理员权限）");
        machine.group = kScopeGroup;
        machine.checked = state_.options.perMachine;
        Add(machine);

        mui::Control desktop = Button(kIdDesktop, mui::Kind::Checkbox, Gdiplus::RectF(m, 264.0f, bodyW, 26.0f), L"创建桌面快捷方式");
        desktop.checked = state_.options.desktop;
        Add(desktop);
        mui::Control autostart = Button(kIdAutostart, mui::Kind::Checkbox, Gdiplus::RectF(m, 292.0f, bodyW, 26.0f), L"开机自动启动");
        autostart.checked = state_.options.autostart;
        Add(autostart);
        mui::Control launch = Button(kIdLaunch, mui::Kind::Checkbox, Gdiplus::RectF(m, 320.0f, bodyW, 26.0f), L"安装完成后启动 Melodia");
        launch.checked = state_.options.launch;
        Add(launch);

        Add(Button(kIdCancel, mui::Kind::Button, mui::FooterButtonRect(w, h, 2), L"取消"));
        Add(Button(kIdBack, mui::Kind::Button, mui::FooterButtonRect(w, h, 1), L"上一步"));
        mui::Control install = Button(kIdNext, mui::Kind::PrimaryButton, mui::FooterButtonRect(w, h, 0), L"安装");
        install.shield = NeedsElevation();
        Add(install);
        SetDefaultButton(kIdNext);
        FocusControl(kIdNext);
    }

    void ShowRunning() {
        BeginPage(Page::Running, false);
    }

    void ShowResult() {
        bool ok = result_.Ok();
        BeginPage(ok ? Page::Done : Page::Failed, true);
        float w = Width();
        float h = Height();
        if (ok) {
            if (state_.mode == Mode::Upgrade) {
                mui::Control launch = Button(kIdLaunch, mui::Kind::Checkbox,
                                             Gdiplus::RectF(mui::theme::kMargin, 180.0f, w - mui::theme::kMargin * 2.0f, 26.0f),
                                             L"立即启动 Melodia");
                launch.checked = true;
                Add(launch);
            }
            Add(Button(kIdFinish, mui::Kind::PrimaryButton, mui::FooterButtonRect(w, h, 0), L"完成"));
        } else {
            int slot = 1;
            std::error_code ec;
            if (!log_.empty() && fs::is_regular_file(log_, ec)) {
                Add(Button(kIdOpenLog, mui::Kind::Button, mui::FooterButtonRect(w, h, slot++), L"查看日志"));
            }
            if (state_.mode == Mode::Fresh) {
                Add(Button(kIdBack, mui::Kind::Button, mui::FooterButtonRect(w, h, slot), L"返回"));
            }
            Add(Button(kIdFinish, mui::Kind::PrimaryButton, mui::FooterButtonRect(w, h, 0), L"关闭"));
        }
        SetDefaultButton(kIdFinish);
        FocusControl(kIdFinish);
    }

    void ShowUpToDate() {
        BeginPage(Page::UpToDate, true);
        float w = Width();
        float h = Height();
        bool canOpen = state_.installed && sys::IsMelodiaAppDir(state_.installed->location);
        if (canOpen) {
            Add(Button(kIdCancel, mui::Kind::Button, mui::FooterButtonRect(w, h, 1), L"退出"));
            Add(Button(kIdOpenApp, mui::Kind::PrimaryButton, mui::FooterButtonRect(w, h, 0), L"打开 Melodia"));
            SetDefaultButton(kIdOpenApp);
            FocusControl(kIdOpenApp);
        } else {
            Add(Button(kIdCancel, mui::Kind::PrimaryButton, mui::FooterButtonRect(w, h, 0), L"退出"));
            SetDefaultButton(kIdCancel);
            FocusControl(kIdCancel);
        }
    }

    bool LaunchRequested() {
        if (state_.mode == Mode::Upgrade) {
            mui::Control* launch = Find(kIdLaunch);
            return launch && launch->checked;
        }
        return state_.options.launch;
    }

    void CaptureOptions() {
        state_.options.dir = NormalizeInputDir(EditText(kIdDir));
        if (mui::Control* c = Find(kIdScopeMachine)) state_.options.perMachine = c->checked;
        if (mui::Control* c = Find(kIdDesktop)) state_.options.desktop = c->checked;
        if (mui::Control* c = Find(kIdAutostart)) state_.options.autostart = c->checked;
        if (mui::Control* c = Find(kIdLaunch)) state_.options.launch = c->checked;
    }

    void OnScopeChanged(bool perMachine) {
        state_.options.perMachine = perMachine;
        if (!dirEdited_) SetEditText(kIdDir, msi::DefaultInstallDir(perMachine).wstring());
        if (mui::Control* install = Find(kIdNext)) install->shield = NeedsElevation();
        note_.clear();
        Invalidate();
    }

    void OnNext() {
        switch (page_) {
            case Page::Welcome:
                note_.clear();
                if (state_.mode == Mode::Fresh) {
                    ShowLicense();
                } else {
                    StartInstall();
                }
                break;
            case Page::License:
                ShowOptions();
                break;
            case Page::Options: {
                CaptureOptions();
                std::wstring error = ValidateDir(state_.options.dir, state_.options.perMachine);
                if (!error.empty()) {
                    note_ = error;
                    Invalidate();
                    FocusControl(kIdDir);
                    return;
                }
                note_.clear();
                StartInstall();
                break;
            }
            default:
                break;
        }
    }

    void Report(int percent, const std::wstring& status) {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            status_ = status;
        }
        int clamped = (std::clamp)(percent, 0, 100);
        int prev = progress_.load();
        while (clamped > prev && !progress_.compare_exchange_weak(prev, clamped)) {
        }
        HWND hwnd = Hwnd();
        if (hwnd) PostMessageW(hwnd, kMsgProgress, 0, 0);
    }

    void StartInstall() {
        progress_ = 0;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            status_ = L"准备中…";
            childError_.clear();
        }
        ShowRunning();

        HWND hwnd = Hwnd();
        Options options = state_.options;
        bool fresh = state_.mode == Mode::Fresh;
        bool elevated = NeedsElevation();
        worker_ = std::jthread([this, hwnd, options, fresh, elevated]() {
            HRESULT hr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);
            Reporter reporter = [this](int pct, const std::wstring& status) { Report(pct, status); };
            ElevatedRunner runner;
            if (elevated) runner = [this, hwnd, &options]() { return RunElevated(hwnd, options); };
            InstallRun run = RunInstall(options, fresh, log_, reporter, runner);
            {
                std::lock_guard<std::mutex> lock(mutex_);
                pendingRun_ = std::move(run);
            }
            if (SUCCEEDED(hr)) CoUninitialize();
            PostMessageW(hwnd, kMsgFinished, 0, 0);
        });
    }

    InstallRun RunElevated(HWND hwnd, const Options& options) {
        std::wstring args = L"--elevated-install --parent " + elevate::FormatWindowHandle(hwnd) + L" --dir " +
                            sys::QuoteArg(options.dir.wstring());
        if (!options.desktop) args += L" --nodesktop";
        if (!log_.empty()) args += L" --log " + sys::QuoteArg(log_.wstring());
        DWORD exitCode = 0;
        elevate::LaunchResult launch = elevate::RunElevatedAndWait(hwnd, selfPath_, args, exitCode);
        InstallRun run;
        if (launch == elevate::LaunchResult::Cancelled) {
            run.elevationCancelled = true;
            run.result.code = ERROR_CANCELLED;
            return run;
        }
        if (launch == elevate::LaunchResult::Failed) {
            run.result.code = ERROR_ACCESS_DENIED;
            run.result.message = L"无法以管理员身份启动安装进程";
            return run;
        }
        run.result.code = exitCode;
        std::lock_guard<std::mutex> lock(mutex_);
        run.result.message = childError_;
        return run;
    }

    void OnFinished() {
        if (worker_.joinable()) worker_.join();
        InstallRun run;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            run = pendingRun_;
        }
        if (run.elevationCancelled) {
            note_ = state_.mode == Mode::Fresh ? L"需要管理员授权才能为所有用户安装" : L"需要管理员授权才能升级为所有用户安装的 Melodia";
            if (state_.mode == Mode::Fresh) {
                ShowOptions();
            } else {
                ShowWelcome();
            }
            return;
        }
        result_ = run.result;
        ShowResult();
    }

    InstallState state_;
    fs::path selfPath_;
    fs::path log_;
    Page page_ = Page::Welcome;
    bool dirEdited_ = false;
    std::wstring note_;
    std::wstring licenseText_;
    std::atomic<int> progress_{0};
    std::mutex mutex_;
    std::wstring status_;
    std::wstring childError_;
    InstallRun pendingRun_;
    msi::Result result_;
    std::jthread worker_;
};

// 提权子进程：只执行机器范围的 MSI 安装
int RunElevatedChild(const Args& args) {
    if (!args.valid || args.dir.empty()) return kExitBadArgs;
    if (!sys::IsProcessElevated()) return kExitNeedsAdmin;
    Options options;
    options.dir = sys::StripTrailingSeparators(args.dir);
    options.perMachine = true;
    options.desktop = !args.noDesktop;
    if (!ValidateDir(options.dir, true).empty()) return kExitBadArgs;

    msi::Result r = InstallMsi(options, args.log, [&](int pct) {
        elevate::SendPacket(args.parent, elevate::PacketKind::Progress, pct, L"");
    });
    if (!r.Ok()) elevate::SendPacket(args.parent, elevate::PacketKind::Error, 0, r.message);
    return static_cast<int>(r.code);
}

int RunSilent(const Args& args) {
    if (!args.valid) return kExitBadArgs;
    InstallState state = DetectInstallState();
    if (state.mode == Mode::UpToDate) return kExitAlreadyInstalled;

    Options& o = state.options;
    bool fresh = state.mode == Mode::Fresh;
    if (fresh) {
        o.perMachine = args.allUsers;
        o.dir = args.dir.empty() ? msi::DefaultInstallDir(o.perMachine) : NormalizeInputDir(args.dir.wstring());
        o.desktop = !args.noDesktop;
        o.autostart = args.autostart;
        if (!ValidateDir(o.dir, o.perMachine).empty()) return kExitBadArgs;
    }
    if (o.perMachine && !sys::IsProcessElevated()) return kExitNeedsAdmin;

    Reporter noop = [](int, const std::wstring&) {};
    InstallRun run = RunInstall(o, fresh, SetupLogPath(), noop, ElevatedRunner());
    if (!run.result.Ok()) return run.result.code != ERROR_SUCCESS ? static_cast<int>(run.result.code) : kExitFailed;
    if (args.run) sys::LaunchMelodia(o.dir);
    return kExitOk;
}

}  // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int) {
    HRESULT com = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);
    Args args = ParseArgs();
    int exitCode = kExitOk;

    if (args.elevatedChild) {
        exitCode = RunElevatedChild(args);
    } else if (args.silent) {
        exitCode = RunSilent(args);
    } else {
        SetupWindow window(DetectInstallState(), sys::ModulePath());
        if (window.Create(instance, kWindowTitle, kWindowWidth, kWindowHeight, IDI_APP, IDI_APP)) {
            window.Start();
            window.Run();
        } else {
            exitCode = kExitFailed;
        }
    }

    if (SUCCEEDED(com)) CoUninitialize();
    return exitCode;
}
