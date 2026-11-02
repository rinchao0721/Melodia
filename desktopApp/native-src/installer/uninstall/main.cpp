// Melodia 卸载程序：复制到临时目录运行以释放应用目录，区分安装版与免安装版，机器范围的 MSI 交给提权子进程卸载
#include <windows.h>
#include <objbase.h>
#include <shellapi.h>

#include "Elevate.h"
#include "MsiEngine.h"
#include "Sys.h"
#include "Theme.h"
#include "Ui.h"

#include <algorithm>
#include <atomic>
#include <functional>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <vector>

namespace {

namespace fs = std::filesystem;

constexpr int IDI_UNINSTALL = 101;
constexpr int IDI_APP = 102;
constexpr UINT kMsgProgress = WM_APP + 1;
constexpr UINT kMsgFinished = WM_APP + 2;
constexpr wchar_t kRunnerPrefix[] = L"melodia_uninst_";
constexpr wchar_t kWindowTitle[] = L"卸载 Melodia";

constexpr int kExitOk = 0;
constexpr int kExitBadArgs = 1;
constexpr int kExitNeedsAdmin = 2;
constexpr int kExitFailed = 4;

constexpr int kIdCancel = 1;
constexpr int kIdUninstall = 2;
constexpr int kIdCleanData = 3;
constexpr int kIdFinish = 4;
constexpr int kIdOpenLog = 5;

constexpr float kWindowWidth = 480.0f;
constexpr float kWindowHeight = 270.0f;

struct Target {
    fs::path appDir;
    std::optional<msi::InstalledProduct> product;  // 为空表示免安装版
    bool PerMachine() const { return product && product->PerMachine(); }
};

struct Outcome {
    bool ok = false;
    bool elevationCancelled = false;
    bool rebootRequired = false;
    std::vector<std::wstring> failures;
};

using Reporter = std::function<void(int percent, const std::wstring& status)>;
using MsiRunner = std::function<msi::Result(const Reporter& report)>;

struct Args {
    bool fromTemp = false;
    bool silent = false;
    bool cleanData = false;
    bool elevatedChild = false;
    fs::path appDir;
    HWND parent = nullptr;
    std::wstring product;
    fs::path log;
    bool valid = true;
};

Args ParseArgs() {
    Args args;
    int argc = 0;
    LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &argc);
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
        if (wcscmp(a, L"--run-from-temp") == 0) {
            if (const wchar_t* v = next(i)) {
                args.fromTemp = true;
                args.appDir = v;
            }
        } else if (wcscmp(a, L"--silent") == 0 || _wcsicmp(a, L"/S") == 0 || _wcsicmp(a, L"/quiet") == 0) {
            args.silent = true;
        } else if (wcscmp(a, L"--clean-data") == 0) {
            args.cleanData = true;
        } else if (wcscmp(a, L"--elevated-uninstall") == 0) {
            args.elevatedChild = true;
        } else if (wcscmp(a, L"--parent") == 0) {
            if (const wchar_t* v = next(i)) args.parent = elevate::ParseWindowHandle(v);
        } else if (wcscmp(a, L"--product") == 0) {
            if (const wchar_t* v = next(i)) args.product = v;
        } else if (wcscmp(a, L"--appdir") == 0) {
            if (const wchar_t* v = next(i)) args.appDir = v;
        } else if (wcscmp(a, L"--log") == 0) {
            if (const wchar_t* v = next(i)) args.log = v;
        } else {
            args.valid = false;
        }
    }
    LocalFree(argv);
    if (!args.appDir.empty()) args.appDir = sys::StripTrailingSeparators(args.appDir);
    return args;
}

Outcome RunUninstall(const Target& target, bool cleanData, const Reporter& report, const MsiRunner& runMsi) {
    Outcome out;
    std::vector<fs::path> customRoots = sys::ReadCustomCacheRoots(sys::PrefsFile());

    report(8, L"正在关闭 Melodia…");
    if (!sys::StopMelodiaUnder(target.appDir)) {
        out.failures.push_back(L"无法关闭正在运行的 Melodia，请手动退出后重试");
        return out;
    }

    if (target.product) {
        report(15, L"正在移除程序组件…");
        msi::Result r = runMsi(report);
        if (r.code == ERROR_CANCELLED) {
            out.elevationCancelled = true;
            return out;
        }
        if (!r.Ok()) {
            out.failures.push_back(L"移除程序组件失败：" + r.Describe());
            return out;
        }
        out.rebootRequired = r.RebootRequired();
        // 机器范围且未提权时，残留文件已由提权子进程清理
        if (!target.PerMachine() || sys::IsProcessElevated()) {
            if (!sys::RemoveAppEntries(target.appDir)) out.failures.push_back(L"部分程序文件未能删除：" + target.appDir.wstring());
        }
    } else {
        report(40, L"正在删除程序文件…");
        if (!sys::RemoveAppEntries(target.appDir)) out.failures.push_back(L"部分程序文件未能删除：" + target.appDir.wstring());
    }

    sys::RemoveAutoStartIfUnder(target.appDir);

    if (cleanData) {
        report(85, L"正在清除个人数据…");
        if (!sys::CleanUserData(customRoots)) out.failures.push_back(L"部分个人数据未能删除");
    }
    RemoveDirectoryW(target.appDir.c_str());

    report(100, L"卸载完成");
    out.ok = out.failures.empty();
    return out;
}

msi::Result UninstallInProcess(const Target& target, const fs::path& log, const Reporter& report) {
    return msi::Uninstall(target.product->code, log, [&](int pct) {
        report(15 + pct * 65 / 100, L"正在移除程序组件…");
    });
}

fs::path UninstallLogPath() {
    fs::path temp = sys::TempDir();
    return temp.empty() ? fs::path() : temp / L"Melodia-uninstall.log";
}

// 提权子进程：只卸载 MSI 并清理残留程序文件
int RunElevatedChild(const Args& args) {
    if (!args.valid || args.product.empty() || args.appDir.empty()) return kExitBadArgs;
    if (!sys::IsProcessElevated()) return kExitNeedsAdmin;

    // 只接受确实安装在该目录的产品，避免借提权进程删除任意目录
    std::vector<msi::InstalledProduct> products = msi::FindInstalledProducts();
    std::optional<msi::InstalledProduct> match;
    for (const msi::InstalledProduct& p : products) {
        if (_wcsicmp(p.code.c_str(), args.product.c_str()) == 0 && sys::SamePath(p.location, args.appDir)) match = p;
    }
    if (!match) {
        elevate::SendPacket(args.parent, elevate::PacketKind::Error, 0, L"未找到对应的已安装版本");
        return static_cast<int>(ERROR_UNKNOWN_PRODUCT);
    }

    msi::Result r = msi::Uninstall(match->code, args.log, [&](int pct) {
        elevate::SendPacket(args.parent, elevate::PacketKind::Progress, pct, L"");
    });
    if (!r.Ok()) {
        elevate::SendPacket(args.parent, elevate::PacketKind::Error, 0, r.message);
        return static_cast<int>(r.code);
    }
    sys::RemoveAppEntries(args.appDir);
    return static_cast<int>(r.code);
}

class UninstallWindow : public mui::Window {
public:
    UninstallWindow(Target target, fs::path selfPath, fs::path log)
        : target_(std::move(target)), selfPath_(std::move(selfPath)), log_(std::move(log)) {}

    void Start() {
        ShowConfirm();
    }

protected:
    void Paint(mui::Painter& p) override {
        float w = Width();
        float h = Height();
        float bodyW = w - mui::theme::kMargin * 2.0f;
        switch (page_) {
            case Page::Confirm: {
                std::wstring subtitle = target_.product
                                            ? L"版本 " + target_.product->version + (target_.PerMachine() ? L" · 所有用户" : L"")
                                            : L"免安装版 · " + target_.appDir.wstring();
                mui::PaintHeader(p, HeaderIcon(), L"卸载 Melodia", subtitle, w);
                std::wstring body = target_.product ? L"Melodia 将从此电脑中移除，已下载的音乐不会被删除。"
                                                    : L"将删除此目录中的 Melodia 程序文件，已下载的音乐不会被删除。";
                p.Text(body, Gdiplus::RectF(mui::theme::kMargin, mui::kBodyTop + 4.0f, bodyW, 40.0f), mui::theme::kBodySize, false,
                       mui::theme::kTextGray);
                if (!note_.empty()) {
                    p.Text(note_, Gdiplus::RectF(mui::theme::kMargin, 178.0f, bodyW, 20.0f), 12.0f, false, mui::theme::kError);
                }
                break;
            }
            case Page::Running: {
                mui::PaintHeader(p, HeaderIcon(), L"正在卸载 Melodia", L"请稍候，不要关闭此窗口", w);
                std::wstring status;
                int pct = progress_.load();
                {
                    std::lock_guard<std::mutex> lock(mutex_);
                    status = status_;
                }
                float y = mui::kBodyTop + 10.0f;
                p.Text(status, Gdiplus::RectF(mui::theme::kMargin, y, bodyW - 50.0f, 20.0f), mui::theme::kBodySize, false,
                       mui::theme::kTextGray);
                p.Text(std::to_wstring(pct) + L"%", Gdiplus::RectF(mui::theme::kMargin, y, bodyW, 20.0f), mui::theme::kBodySize,
                       false, mui::theme::kTextPrimary, Gdiplus::StringAlignmentFar);
                Gdiplus::RectF track(mui::theme::kMargin, y + 32.0f, bodyW, 6.0f);
                p.FillRound(track, 3.0f, mui::theme::kSurface);
                float fillW = (std::max)(6.0f, bodyW * static_cast<float>(pct) / 100.0f);
                p.FillRound(Gdiplus::RectF(track.X, track.Y, (std::min)(bodyW, fillW), track.Height), 3.0f, mui::theme::kAccent);
                break;
            }
            case Page::Done: {
                mui::PaintHeader(p, HeaderIcon(), L"Melodia 已卸载", L"感谢使用 Melodia", w);
                std::wstring body = cleanData_ ? L"Melodia 及其个人数据已从此电脑中移除。"
                                               : L"Melodia 已从此电脑中移除。个人数据已保留，重新安装后可继续使用。";
                if (outcome_.rebootRequired) body += L"\n部分文件将在重启电脑后删除。";
                p.Text(body, Gdiplus::RectF(mui::theme::kMargin, mui::kBodyTop + 4.0f, bodyW, 80.0f), mui::theme::kBodySize, false,
                       mui::theme::kTextGray);
                break;
            }
            case Page::Failed: {
                mui::PaintHeader(p, HeaderIcon(), L"卸载未完成", L"以下步骤没有成功", w);
                std::wstring body;
                for (const std::wstring& f : outcome_.failures) {
                    if (!body.empty()) body += L"\n";
                    body += L"· " + f;
                }
                p.Text(body,
                       Gdiplus::RectF(mui::theme::kMargin, mui::kBodyTop + 4.0f, bodyW, h - mui::theme::kFooterHeight - mui::kBodyTop - 12.0f),
                       mui::theme::kBodySize, false, mui::theme::kTextGray);
                break;
            }
        }
        mui::PaintFooter(p, w, h, 0, 0);
    }

    void OnCommand(int id) override {
        switch (id) {
            case kIdCancel:
            case kIdFinish:
                DestroyWindow(Hwnd());
                break;
            case kIdUninstall:
                StartUninstall();
                break;
            case kIdOpenLog:
                ShellExecuteW(Hwnd(), L"open", log_.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
                break;
            default:
                break;
        }
    }

    void OnEscape() override {
        if (page_ != Page::Running) DestroyWindow(Hwnd());
    }

    bool CanClose() override {
        return page_ != Page::Running;
    }

    bool HandleMessage(UINT msg, WPARAM /*wParam*/, LPARAM lParam, LRESULT& result) override {
        switch (msg) {
            case WM_COPYDATA: {
                elevate::Packet packet;
                if (page_ == Page::Running && elevate::ParsePacket(lParam, packet)) {
                    if (packet.kind == elevate::PacketKind::Progress) {
                        Report(15 + packet.percent * 65 / 100, L"正在移除程序组件…");
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
        Confirm,
        Running,
        Done,
        Failed,
    };

    void ShowConfirm() {
        page_ = Page::Confirm;
        ClearControls();
        SetClosable(true);
        float w = Width();
        float h = Height();
        mui::Control check;
        check.id = kIdCleanData;
        check.kind = mui::Kind::Checkbox;
        check.rect = Gdiplus::RectF(mui::theme::kMargin, 144.0f, w - mui::theme::kMargin * 2.0f, 26.0f);
        check.text = L"同时清除个人数据（配置、登录状态与缓存）";
        check.checked = cleanData_;
        Add(check);

        mui::Control cancel;
        cancel.id = kIdCancel;
        cancel.kind = mui::Kind::Button;
        cancel.rect = mui::FooterButtonRect(w, h, 1);
        cancel.text = L"取消";
        Add(cancel);

        mui::Control ok;
        ok.id = kIdUninstall;
        ok.kind = mui::Kind::PrimaryButton;
        ok.rect = mui::FooterButtonRect(w, h, 0);
        ok.text = L"卸载";
        ok.shield = target_.PerMachine() && !sys::IsProcessElevated();
        Add(ok);
        SetDefaultButton(kIdUninstall);
        FocusControl(kIdCancel);
    }

    void ShowRunning() {
        page_ = Page::Running;
        ClearControls();
        SetClosable(false);
    }

    void ShowResult() {
        page_ = outcome_.ok ? Page::Done : Page::Failed;
        ClearControls();
        SetClosable(true);
        float w = Width();
        float h = Height();
        std::error_code ec;
        if (page_ == Page::Failed && !log_.empty() && fs::is_regular_file(log_, ec)) {
            mui::Control log;
            log.id = kIdOpenLog;
            log.kind = mui::Kind::Button;
            log.rect = mui::FooterButtonRect(w, h, 1);
            log.text = L"查看日志";
            Add(log);
        }
        mui::Control finish;
        finish.id = kIdFinish;
        finish.kind = mui::Kind::PrimaryButton;
        finish.rect = mui::FooterButtonRect(w, h, 0);
        finish.text = page_ == Page::Done ? L"完成" : L"关闭";
        Add(finish);
        SetDefaultButton(kIdFinish);
        FocusControl(kIdFinish);
    }

    void Report(int percent, const std::wstring& status) {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            status_ = status;
        }
        int clamped = percent < 0 ? 0 : (percent > 100 ? 100 : percent);
        int prev = progress_.load();
        while (clamped > prev && !progress_.compare_exchange_weak(prev, clamped)) {
        }
        HWND hwnd = Hwnd();
        if (hwnd) PostMessageW(hwnd, kMsgProgress, 0, 0);
    }

    void StartUninstall() {
        mui::Control* check = Find(kIdCleanData);
        cleanData_ = check && check->checked;
        note_.clear();
        progress_ = 0;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            status_ = L"准备卸载…";
            childError_.clear();
        }
        ShowRunning();

        HWND hwnd = Hwnd();
        bool clean = cleanData_;
        worker_ = std::jthread([this, hwnd, clean]() {
            HRESULT hr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);
            Reporter reporter = [this](int pct, const std::wstring& status) { Report(pct, status); };
            MsiRunner runner = [this, hwnd](const Reporter& report) { return RunMsi(hwnd, report); };
            Outcome result = RunUninstall(target_, clean, reporter, runner);
            {
                std::lock_guard<std::mutex> lock(mutex_);
                outcome_ = std::move(result);
            }
            if (SUCCEEDED(hr)) CoUninitialize();
            PostMessageW(hwnd, kMsgFinished, 0, 0);
        });
    }

    msi::Result RunMsi(HWND hwnd, const Reporter& report) {
        if (!target_.PerMachine() || sys::IsProcessElevated()) return UninstallInProcess(target_, log_, report);

        std::wstring args = L"--elevated-uninstall --parent " + elevate::FormatWindowHandle(hwnd) + L" --product " +
                            sys::QuoteArg(target_.product->code) + L" --appdir " + sys::QuoteArg(target_.appDir.wstring());
        if (!log_.empty()) args += L" --log " + sys::QuoteArg(log_.wstring());
        DWORD exitCode = 0;
        elevate::LaunchResult launch = elevate::RunElevatedAndWait(hwnd, selfPath_, args, exitCode);
        msi::Result r;
        if (launch == elevate::LaunchResult::Cancelled) {
            r.code = ERROR_CANCELLED;
            return r;
        }
        if (launch == elevate::LaunchResult::Failed) {
            r.code = ERROR_ACCESS_DENIED;
            r.message = L"无法以管理员身份启动卸载进程";
            return r;
        }
        r.code = exitCode;
        std::lock_guard<std::mutex> lock(mutex_);
        r.message = childError_;
        return r;
    }

    void OnFinished() {
        if (worker_.joinable()) worker_.join();
        bool cancelled = false;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            cancelled = outcome_.elevationCancelled;
        }
        if (cancelled) {
            note_ = L"需要管理员授权才能卸载为所有用户安装的 Melodia";
            ShowConfirm();
            return;
        }
        ShowResult();
    }

    Target target_;
    fs::path selfPath_;
    fs::path log_;
    Page page_ = Page::Confirm;
    bool cleanData_ = false;
    std::wstring note_;
    std::atomic<int> progress_{0};
    std::mutex mutex_;
    std::wstring status_;
    std::wstring childError_;
    Outcome outcome_;
    std::jthread worker_;
};

// 复制自身到独立临时目录运行；静默模式等待子进程并透传退出码
std::optional<int> RelaunchFromTemp(const fs::path& selfPath, const Args& args) {
    fs::path temp = sys::TempDir();
    if (selfPath.empty() || temp.empty()) return std::nullopt;
    fs::path runnerDir = temp / (std::wstring(kRunnerPrefix) + std::to_wstring(GetCurrentProcessId()) + L"_" +
                                 std::to_wstring(GetTickCount64()));
    std::error_code ec;
    fs::create_directories(runnerDir, ec);
    if (ec) return std::nullopt;
    fs::path tempExe = runnerDir / L"uninstall.exe";
    fs::copy_file(selfPath, tempExe, fs::copy_options::overwrite_existing, ec);
    if (ec) {
        fs::remove_all(runnerDir, ec);
        return std::nullopt;
    }

    std::wstring cmd = sys::QuoteArg(tempExe.wstring()) + L" --run-from-temp " + sys::QuoteArg(args.appDir.wstring());
    if (args.silent) cmd += L" --silent";
    if (args.cleanData) cmd += L" --clean-data";

    STARTUPINFOW si{};
    si.cb = sizeof(si);
    PROCESS_INFORMATION pi{};
    if (!CreateProcessW(tempExe.c_str(), cmd.data(), nullptr, nullptr, FALSE, 0, nullptr, temp.c_str(), &si, &pi)) {
        fs::remove_all(runnerDir, ec);
        return std::nullopt;
    }
    CloseHandle(pi.hThread);
    int code = kExitOk;
    if (args.silent) {
        WaitForSingleObject(pi.hProcess, INFINITE);
        DWORD exitCode = 0;
        code = GetExitCodeProcess(pi.hProcess, &exitCode) ? static_cast<int>(exitCode) : kExitFailed;
    }
    CloseHandle(pi.hProcess);
    return code;
}

int RunSilent(const Target& target, bool cleanData) {
    if (target.PerMachine() && !sys::IsProcessElevated()) return kExitNeedsAdmin;
    fs::path log = UninstallLogPath();
    Reporter noop = [](int, const std::wstring&) {};
    MsiRunner runner = [&](const Reporter& report) { return UninstallInProcess(target, log, report); };
    Outcome out = RunUninstall(target, cleanData, noop, runner);
    return out.ok ? kExitOk : kExitFailed;
}

}  // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int) {
    HRESULT com = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);
    Args args = ParseArgs();
    fs::path selfPath = sys::ModulePath();
    int exitCode = kExitOk;

    if (args.elevatedChild) {
        exitCode = RunElevatedChild(args);
    } else if (!args.valid) {
        exitCode = kExitBadArgs;
    } else {
        if (!args.fromTemp) {
            args.appDir = sys::StripTrailingSeparators(selfPath.parent_path());
            if (std::optional<int> relaunched = RelaunchFromTemp(selfPath, args)) {
                if (SUCCEEDED(com)) CoUninitialize();
                return *relaunched;
            }
        }

        fs::path temp = sys::TempDir();
        if (!temp.empty()) SetCurrentDirectoryW(temp.c_str());

        Target target;
        target.appDir = args.appDir;
        target.product = msi::FindByLocation(msi::FindInstalledProducts(), args.appDir);

        if (!target.product && !sys::IsMelodiaAppDir(args.appDir)) {
            if (!args.silent) {
                std::wstring text = L"未在以下位置找到 Melodia：\n" + args.appDir.wstring();
                MessageBoxW(nullptr, text.c_str(), kWindowTitle, MB_ICONWARNING | MB_OK);
            }
            exitCode = kExitBadArgs;
        } else if (args.silent) {
            exitCode = RunSilent(target, args.cleanData);
        } else {
            UninstallWindow window(target, selfPath, UninstallLogPath());
            if (window.Create(instance, kWindowTitle, kWindowWidth, kWindowHeight, IDI_UNINSTALL, IDI_APP)) {
                window.Start();
                window.Run();
            } else {
                exitCode = kExitFailed;
            }
        }

        if (args.fromTemp) sys::ScheduleDeleteTempDir(selfPath.parent_path(), kRunnerPrefix);
    }

    if (SUCCEEDED(com)) CoUninitialize();
    return exitCode;
}
