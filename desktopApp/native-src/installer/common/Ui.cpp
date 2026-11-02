#include "Ui.h"

#include "Theme.h"

#include <commctrl.h>
#include <dwmapi.h>
#include <shellapi.h>
#include <uxtheme.h>
#include <windowsx.h>

#include <algorithm>

namespace mui {

namespace {

constexpr wchar_t kWindowClass[] = L"MelodiaInstallerWindow";
constexpr float kButtonTextSize = 13.0f;
constexpr float kBoxSize = 16.0f;
constexpr float kShieldSize = 16.0f;
constexpr float kEditTextSize = 13.0f;

// DWMWA_USE_IMMERSIVE_DARK_MODE / BORDER_COLOR / CAPTION_COLOR / WINDOW_CORNER_PREFERENCE
constexpr DWORD kDwmDarkMode = 20;
constexpr DWORD kDwmCornerPreference = 33;
constexpr DWORD kDwmBorderColor = 34;
constexpr DWORD kDwmCaptionColor = 35;

void ApplyWindowFrame(HWND hwnd) {
    BOOL dark = TRUE;
    DwmSetWindowAttribute(hwnd, kDwmDarkMode, &dark, sizeof(dark));
    DWORD corner = 2;
    DwmSetWindowAttribute(hwnd, kDwmCornerPreference, &corner, sizeof(corner));
    COLORREF caption = theme::kCaption;
    DwmSetWindowAttribute(hwnd, kDwmCaptionColor, &caption, sizeof(caption));
    COLORREF border = theme::kBorder;
    DwmSetWindowAttribute(hwnd, kDwmBorderColor, &border, sizeof(border));
}

HICON LoadIconSized(HINSTANCE instance, int id, int size) {
    if (id == 0 || size <= 0) return nullptr;
    HICON icon = nullptr;
    if (SUCCEEDED(LoadIconWithScaleDown(instance, MAKEINTRESOURCEW(id), size, size, &icon)) && icon) return icon;
    return static_cast<HICON>(LoadImageW(instance, MAKEINTRESOURCEW(id), IMAGE_ICON, size, size, LR_DEFAULTCOLOR));
}

void AddRoundPath(Gdiplus::GraphicsPath& path, const Gdiplus::RectF& r, float radius) {
    float d = (std::min)(radius * 2.0f, (std::min)(r.Width, r.Height));
    if (d <= 0.0f) {
        path.AddRectangle(r);
        return;
    }
    path.AddArc(r.X, r.Y, d, d, 180.0f, 90.0f);
    path.AddArc(r.GetRight() - d, r.Y, d, d, 270.0f, 90.0f);
    path.AddArc(r.GetRight() - d, r.GetBottom() - d, d, d, 0.0f, 90.0f);
    path.AddArc(r.X, r.GetBottom() - d, d, d, 90.0f, 90.0f);
    path.CloseFigure();
}

Gdiplus::RectF Inflated(const Gdiplus::RectF& r, float d) {
    return Gdiplus::RectF(r.X - d, r.Y - d, r.Width + d * 2.0f, r.Height + d * 2.0f);
}

}  // namespace

Painter::Painter(Gdiplus::Graphics& g, float scale)
    : g_(g), scale_(scale), family_(theme::kFontFamily), activeFamily_(&family_) {
    if (family_.GetLastStatus() != Gdiplus::Ok || !family_.IsAvailable()) {
        activeFamily_ = Gdiplus::FontFamily::GenericSansSerif();
    }
}

Gdiplus::RectF Painter::Px(const Gdiplus::RectF& r) const {
    return Gdiplus::RectF(r.X * scale_, r.Y * scale_, r.Width * scale_, r.Height * scale_);
}

void Painter::Fill(const Gdiplus::RectF& r, Gdiplus::ARGB color) {
    Gdiplus::SolidBrush brush{Gdiplus::Color(color)};
    g_.FillRectangle(&brush, Px(r));
}

void Painter::FillRound(const Gdiplus::RectF& r, float radius, Gdiplus::ARGB color) {
    Gdiplus::GraphicsPath path;
    AddRoundPath(path, Px(r), radius * scale_);
    Gdiplus::SolidBrush brush{Gdiplus::Color(color)};
    g_.FillPath(&brush, &path);
}

void Painter::StrokeRound(const Gdiplus::RectF& r, float radius, float width, Gdiplus::ARGB color) {
    float inset = width * 0.5f;
    Gdiplus::RectF px = Px(Gdiplus::RectF(r.X + inset, r.Y + inset, r.Width - width, r.Height - width));
    Gdiplus::GraphicsPath path;
    AddRoundPath(path, px, radius * scale_);
    Gdiplus::Pen pen(Gdiplus::Color(color), width * scale_);
    g_.DrawPath(&pen, &path);
}

void Painter::FillCircle(float cx, float cy, float radius, Gdiplus::ARGB color) {
    Gdiplus::SolidBrush brush{Gdiplus::Color(color)};
    g_.FillEllipse(&brush, (cx - radius) * scale_, (cy - radius) * scale_, radius * 2.0f * scale_, radius * 2.0f * scale_);
}

void Painter::StrokeCircle(float cx, float cy, float radius, float width, Gdiplus::ARGB color) {
    Gdiplus::Pen pen(Gdiplus::Color(color), width * scale_);
    float r = radius - width * 0.5f;
    g_.DrawEllipse(&pen, (cx - r) * scale_, (cy - r) * scale_, r * 2.0f * scale_, r * 2.0f * scale_);
}

void Painter::Line(float x1, float y1, float x2, float y2, float width, Gdiplus::ARGB color) {
    Gdiplus::Pen pen(Gdiplus::Color(color), width * scale_);
    pen.SetStartCap(Gdiplus::LineCapRound);
    pen.SetEndCap(Gdiplus::LineCapRound);
    g_.DrawLine(&pen, x1 * scale_, y1 * scale_, x2 * scale_, y2 * scale_);
}

float Painter::Text(const std::wstring& text,
                    const Gdiplus::RectF& r,
                    float size,
                    bool bold,
                    Gdiplus::ARGB color,
                    Gdiplus::StringAlignment align,
                    Gdiplus::StringAlignment lineAlign,
                    bool singleLine) {
    if (text.empty()) return 0.0f;
    Gdiplus::Font font(activeFamily_, size * scale_, bold ? Gdiplus::FontStyleBold : Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
    Gdiplus::StringFormat fmt;
    fmt.SetAlignment(align);
    fmt.SetLineAlignment(lineAlign);
    if (singleLine) {
        fmt.SetFormatFlags(Gdiplus::StringFormatFlagsNoWrap);
        fmt.SetTrimming(Gdiplus::StringTrimmingEllipsisPath);
    } else {
        fmt.SetTrimming(Gdiplus::StringTrimmingEllipsisCharacter);
    }
    Gdiplus::SolidBrush brush{Gdiplus::Color(color)};
    Gdiplus::RectF px = Px(r);
    INT len = static_cast<INT>(text.size());
    g_.DrawString(text.c_str(), len, &font, px, &fmt, &brush);
    Gdiplus::RectF bound;
    g_.MeasureString(text.c_str(), len, &font, px, &fmt, &bound);
    return bound.Height / scale_;
}

float Painter::MeasureWidth(const std::wstring& text, float size, bool bold) {
    if (text.empty()) return 0.0f;
    Gdiplus::Font font(activeFamily_, size * scale_, bold ? Gdiplus::FontStyleBold : Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
    Gdiplus::RectF bound;
    g_.MeasureString(text.c_str(), static_cast<INT>(text.size()), &font, Gdiplus::PointF(0.0f, 0.0f), &bound);
    return bound.Width / scale_;
}

void Painter::Icon(HICON icon, float x, float y, float size) {
    if (!icon) return;
    HDC hdc = g_.GetHDC();
    if (!hdc) return;
    int px = static_cast<int>(size * scale_ + 0.5f);
    DrawIconEx(hdc, static_cast<int>(x * scale_ + 0.5f), static_cast<int>(y * scale_ + 0.5f), icon, px, px, 0, nullptr, DI_NORMAL);
    g_.ReleaseHDC(hdc);
}

Window::Window() {
    Gdiplus::GdiplusStartupInput input;
    Gdiplus::GdiplusStartup(&gdiplusToken_, &input, nullptr);
}

Window::~Window() {
    if (hwnd_) {
        SetWindowLongPtrW(hwnd_, GWLP_USERDATA, 0);
        DestroyWindow(hwnd_);
        hwnd_ = nullptr;
    }
    DestroyIcons();
    if (editFont_) DeleteObject(editFont_);
    if (editBrush_) DeleteObject(editBrush_);
    if (gdiplusToken_) Gdiplus::GdiplusShutdown(gdiplusToken_);
}

bool Window::Create(HINSTANCE instance,
                    const wchar_t* title,
                    float logicalWidth,
                    float logicalHeight,
                    int windowIconId,
                    int headerIconId) {
    instance_ = instance;
    logicalWidth_ = logicalWidth;
    logicalHeight_ = logicalHeight;
    windowIconId_ = windowIconId;
    headerIconId_ = headerIconId;

    INITCOMMONCONTROLSEX icc{};
    icc.dwSize = sizeof(icc);
    icc.dwICC = ICC_STANDARD_CLASSES;
    InitCommonControlsEx(&icc);

    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = StaticProc;
    wc.hInstance = instance;
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.lpszClassName = kWindowClass;
    if (!RegisterClassExW(&wc) && GetLastError() != ERROR_CLASS_ALREADY_EXISTS) return false;

    UINT dpi = GetDpiForSystem();
    float scale = dpi > 0 ? static_cast<float>(dpi) / 96.0f : 1.0f;
    DWORD style = WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX | WS_CLIPCHILDREN;
    RECT rect{0, 0, static_cast<LONG>(logicalWidth * scale + 0.5f), static_cast<LONG>(logicalHeight * scale + 0.5f)};
    AdjustWindowRectExForDpi(&rect, style, FALSE, 0, dpi);
    int width = rect.right - rect.left;
    int height = rect.bottom - rect.top;

    RECT work{};
    SystemParametersInfoW(SPI_GETWORKAREA, 0, &work, 0);
    int x = work.left + ((work.right - work.left) - width) / 2;
    int y = work.top + ((work.bottom - work.top) - height) / 2;

    hwnd_ = CreateWindowExW(0, kWindowClass, title, style, x, y, width, height, nullptr, nullptr, instance, this);
    return hwnd_ != nullptr;
}

int Window::Run() {
    if (!hwnd_) return 1;
    ShowWindow(hwnd_, SW_SHOW);
    UpdateWindow(hwnd_);
    SetForegroundWindow(hwnd_);
    MSG msg{};
    while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    return static_cast<int>(msg.wParam);
}

float Window::Scale() const {
    UINT dpi = hwnd_ ? GetDpiForWindow(hwnd_) : GetDpiForSystem();
    return dpi > 0 ? static_cast<float>(dpi) / 96.0f : 1.0f;
}

Control& Window::Add(const Control& c) {
    controls_.push_back(c);
    return controls_.back();
}

Control* Window::Find(int id) {
    for (Control& c : controls_) {
        if (c.id == id) return &c;
    }
    return nullptr;
}

void Window::ClearControls() {
    bool nativeHadFocus = false;
    HWND focus = GetFocus();
    for (Control& c : controls_) {
        if (c.native) {
            if (focus == c.native) nativeHadFocus = true;
            DestroyWindow(c.native);
            c.native = nullptr;
        }
    }
    controls_.clear();
    hoveredId_ = 0;
    pressedId_ = 0;
    focusedId_ = 0;
    defaultId_ = 0;
    if (nativeHadFocus && hwnd_) SetFocus(hwnd_);
    Invalidate();
}

void Window::FocusControl(int id) {
    SetFocusTo(id);
}

void Window::Invalidate() {
    if (hwnd_) InvalidateRect(hwnd_, nullptr, FALSE);
}

void Window::SetClosable(bool closable) {
    if (!hwnd_) return;
    HMENU menu = GetSystemMenu(hwnd_, FALSE);
    if (menu) EnableMenuItem(menu, SC_CLOSE, MF_BYCOMMAND | (closable ? MF_ENABLED : MF_GRAYED));
}

HWND Window::AddEdit(int id, const Gdiplus::RectF& rect, bool multiline, bool readOnly) {
    if (!hwnd_) return nullptr;
    DWORD style = WS_CHILD | WS_VISIBLE | WS_TABSTOP;
    style |= multiline ? (ES_MULTILINE | ES_AUTOVSCROLL | WS_VSCROLL) : ES_AUTOHSCROLL;
    if (readOnly) style |= ES_READONLY;
    HWND edit = CreateWindowExW(0,
                                L"EDIT",
                                L"",
                                style,
                                0,
                                0,
                                0,
                                0,
                                hwnd_,
                                reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)),
                                instance_,
                                nullptr);
    if (!edit) return nullptr;
    SendMessageW(edit, WM_SETFONT, reinterpret_cast<WPARAM>(editFont_), FALSE);
    SetWindowTheme(edit, L"DarkMode_Explorer", nullptr);
    SetWindowSubclass(edit, EditSubclassProc, static_cast<UINT_PTR>(id), reinterpret_cast<DWORD_PTR>(this));
    if (!multiline) SendMessageW(edit, EM_SETLIMITTEXT, 240, 0);

    Control c;
    c.id = id;
    c.kind = Kind::Edit;
    c.rect = rect;
    c.native = edit;
    Add(c);
    LayoutEdits();
    return edit;
}

std::wstring Window::EditText(int id) const {
    for (const Control& c : controls_) {
        if (c.id != id || !c.native) continue;
        int len = GetWindowTextLengthW(c.native);
        if (len <= 0) return {};
        std::wstring text(static_cast<size_t>(len) + 1, L'\0');
        int got = GetWindowTextW(c.native, text.data(), len + 1);
        text.resize(got > 0 ? static_cast<size_t>(got) : 0);
        return text;
    }
    return {};
}

void Window::SetEditText(int id, const std::wstring& text) {
    Control* c = Find(id);
    if (!c || !c->native) return;
    suppressEditNotify_ = true;
    SetWindowTextW(c->native, text.c_str());
    suppressEditNotify_ = false;
}

LRESULT CALLBACK Window::StaticProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    Window* self = nullptr;
    if (msg == WM_NCCREATE) {
        auto* cs = reinterpret_cast<CREATESTRUCTW*>(lParam);
        self = static_cast<Window*>(cs->lpCreateParams);
        self->hwnd_ = hwnd;
        SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(self));
    } else {
        self = reinterpret_cast<Window*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
    }
    if (!self) return DefWindowProcW(hwnd, msg, wParam, lParam);
    return self->Proc(msg, wParam, lParam);
}

LRESULT CALLBACK Window::EditSubclassProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam, UINT_PTR idSubclass, DWORD_PTR refData) {
    auto* self = reinterpret_cast<Window*>(refData);
    switch (msg) {
        case WM_KEYDOWN:
            if (self && (wParam == VK_TAB || wParam == VK_RETURN || wParam == VK_ESCAPE)) {
                self->HandleKey(wParam);
                return 0;
            }
            break;
        case WM_CHAR:
            if (wParam == L'\t' || wParam == L'\r' || wParam == 0x1B) return 0;
            break;
        case WM_SETFOCUS:
            if (self) {
                self->focusedId_ = static_cast<int>(idSubclass);
                self->Invalidate();
            }
            break;
        case WM_KILLFOCUS:
            if (self) self->Invalidate();
            break;
        case WM_NCDESTROY:
            RemoveWindowSubclass(hwnd, EditSubclassProc, idSubclass);
            break;
        default:
            break;
    }
    return DefSubclassProc(hwnd, msg, wParam, lParam);
}

LRESULT Window::Proc(UINT msg, WPARAM wParam, LPARAM lParam) {
    HWND hwnd = hwnd_;
    LRESULT handled = 0;
    if (HandleMessage(msg, wParam, lParam, handled)) return handled;

    switch (msg) {
        case WM_CREATE:
            ApplyWindowFrame(hwnd_);
            editBrush_ = CreateSolidBrush(theme::kEditBackground);
            RecreateEditFont();
            LoadIcons();
            return 0;
        case WM_ERASEBKGND:
            return 1;
        case WM_PAINT:
            PaintWindow();
            return 0;
        case WM_SETCURSOR:
            if (LOWORD(lParam) == HTCLIENT && reinterpret_cast<HWND>(wParam) == hwnd_) {
                POINT pt{};
                GetCursorPos(&pt);
                ScreenToClient(hwnd_, &pt);
                if (HitTest(pt.x, pt.y) != 0) {
                    SetCursor(LoadCursorW(nullptr, IDC_HAND));
                    return TRUE;
                }
            }
            break;
        case WM_MOUSEMOVE: {
            if (!trackingMouse_) {
                TRACKMOUSEEVENT tme{};
                tme.cbSize = sizeof(tme);
                tme.dwFlags = TME_LEAVE;
                tme.hwndTrack = hwnd_;
                trackingMouse_ = TrackMouseEvent(&tme) != FALSE;
            }
            int hit = HitTest(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
            if (hit != hoveredId_) {
                hoveredId_ = hit;
                Invalidate();
            }
            return 0;
        }
        case WM_MOUSELEAVE:
            trackingMouse_ = false;
            if (hoveredId_ != 0) {
                hoveredId_ = 0;
                Invalidate();
            }
            return 0;
        case WM_LBUTTONDOWN: {
            SetFocus(hwnd_);
            int hit = HitTest(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
            pressedId_ = hit;
            if (hit != 0) {
                focusedId_ = hit;
                keyboardCues_ = false;
                SetCapture(hwnd_);
            }
            Invalidate();
            return 0;
        }
        case WM_LBUTTONUP: {
            int pressed = pressedId_;
            pressedId_ = 0;
            if (GetCapture() == hwnd_) ReleaseCapture();
            int hit = HitTest(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
            Invalidate();
            if (pressed != 0 && hit == pressed) Activate(pressed);
            return 0;
        }
        case WM_CAPTURECHANGED:
            if (pressedId_ != 0) {
                pressedId_ = 0;
                Invalidate();
            }
            return 0;
        case WM_KEYDOWN:
            HandleKey(wParam);
            return 0;
        case WM_SETFOCUS:
        case WM_KILLFOCUS:
            Invalidate();
            return 0;
        case WM_COMMAND:
            if (HIWORD(wParam) == EN_CHANGE && !suppressEditNotify_) OnEditChanged(LOWORD(wParam));
            return 0;
        case WM_CTLCOLOREDIT:
        case WM_CTLCOLORSTATIC: {
            HDC dc = reinterpret_cast<HDC>(wParam);
            SetTextColor(dc, theme::kEditText);
            SetBkColor(dc, theme::kEditBackground);
            return reinterpret_cast<LRESULT>(editBrush_);
        }
        case WM_DPICHANGED: {
            auto* suggested = reinterpret_cast<RECT*>(lParam);
            SetWindowPos(hwnd_,
                         nullptr,
                         suggested->left,
                         suggested->top,
                         suggested->right - suggested->left,
                         suggested->bottom - suggested->top,
                         SWP_NOZORDER | SWP_NOACTIVATE);
            RecreateEditFont();
            DestroyIcons();
            LoadIcons();
            LayoutEdits();
            Invalidate();
            return 0;
        }
        case WM_CLOSE:
            if (CanClose()) DestroyWindow(hwnd_);
            return 0;
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
        case WM_NCDESTROY:
            SetWindowLongPtrW(hwnd, GWLP_USERDATA, 0);
            hwnd_ = nullptr;
            break;
        default:
            break;
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

void Window::PaintWindow() {
    PAINTSTRUCT ps{};
    HDC hdc = BeginPaint(hwnd_, &ps);
    RECT rc{};
    GetClientRect(hwnd_, &rc);
    int w = rc.right - rc.left;
    int h = rc.bottom - rc.top;
    if (w <= 0 || h <= 0) {
        EndPaint(hwnd_, &ps);
        return;
    }

    HDC memDc = CreateCompatibleDC(hdc);
    HBITMAP memBmp = CreateCompatibleBitmap(hdc, w, h);
    HGDIOBJ oldBmp = SelectObject(memDc, memBmp);
    {
        Gdiplus::Graphics g(memDc);
        g.SetSmoothingMode(Gdiplus::SmoothingModeAntiAlias);
        g.SetPixelOffsetMode(Gdiplus::PixelOffsetModeHalf);
        g.SetTextRenderingHint(Gdiplus::TextRenderingHintClearTypeGridFit);
        g.Clear(Gdiplus::Color(theme::kPane));
        Painter painter(g, Scale());
        Paint(painter);
        for (const Control& c : controls_) DrawControl(painter, c);
    }
    BitBlt(hdc, 0, 0, w, h, memDc, 0, 0, SRCCOPY);
    SelectObject(memDc, oldBmp);
    DeleteObject(memBmp);
    DeleteDC(memDc);
    EndPaint(hwnd_, &ps);
}

void Window::DrawControl(Painter& p, const Control& c) {
    const Gdiplus::RectF& r = c.rect;
    bool hover = c.enabled && c.id == hoveredId_;
    bool pressed = hover && c.id == pressedId_;
    bool showFocus = keyboardCues_ && c.id == focusedId_ && GetFocus() == hwnd_ && c.kind != Kind::Edit;

    switch (c.kind) {
        case Kind::PrimaryButton:
        case Kind::Button: {
            bool primary = c.kind == Kind::PrimaryButton;
            Gdiplus::ARGB bg;
            if (!c.enabled) {
                bg = theme::kSurface;
            } else if (primary) {
                bg = pressed ? theme::kAccentPressed : (hover ? theme::kAccentHover : theme::kAccent);
            } else {
                bg = (hover || pressed) ? theme::kSurfaceLight : theme::kSurface;
            }
            Gdiplus::ARGB fg = c.enabled ? theme::kTextPrimary : theme::kTextDisabled;
            p.FillRound(r, theme::kControlRadius, bg);
            float textW = p.MeasureWidth(c.text, kButtonTextSize, false);
            bool withShield = c.shield && shieldIcon_;
            float total = textW + (withShield ? kShieldSize + 6.0f : 0.0f);
            float x = r.X + (r.Width - total) / 2.0f;
            if (withShield) {
                p.Icon(shieldIcon_, x, r.Y + (r.Height - kShieldSize) / 2.0f, kShieldSize);
                x += kShieldSize + 6.0f;
            }
            p.Text(c.text,
                   Gdiplus::RectF(x, r.Y, textW + 4.0f, r.Height),
                   kButtonTextSize,
                   false,
                   fg,
                   Gdiplus::StringAlignmentNear,
                   Gdiplus::StringAlignmentCenter);
            if (showFocus) p.StrokeRound(Inflated(r, 3.0f), theme::kControlRadius + 3.0f, 1.5f, theme::kTextPrimary);
            break;
        }
        case Kind::Checkbox: {
            Gdiplus::RectF box(r.X, r.Y + (r.Height - kBoxSize) / 2.0f, kBoxSize, kBoxSize);
            if (c.checked) {
                p.FillRound(box, 3.0f, c.enabled ? theme::kAccent : theme::kSurfaceLight);
                p.Line(box.X + 3.6f, box.Y + 8.2f, box.X + 6.8f, box.Y + 11.4f, 1.8f, theme::kTextPrimary);
                p.Line(box.X + 6.8f, box.Y + 11.4f, box.X + 12.4f, box.Y + 4.9f, 1.8f, theme::kTextPrimary);
            } else {
                p.StrokeRound(box, 3.0f, 1.3f, hover ? theme::kTextPrimary : theme::kTextGray);
            }
            p.Text(c.text,
                   Gdiplus::RectF(r.X + kBoxSize + 10.0f, r.Y, r.Width - kBoxSize - 10.0f, r.Height),
                   theme::kBodySize,
                   false,
                   c.enabled ? theme::kTextPrimary : theme::kTextDisabled,
                   Gdiplus::StringAlignmentNear,
                   Gdiplus::StringAlignmentCenter);
            if (showFocus) p.StrokeRound(Inflated(r, 3.0f), 5.0f, 1.5f, theme::kTextPrimary);
            break;
        }
        case Kind::Radio: {
            float cx = r.X + kBoxSize / 2.0f;
            float cy = r.Y + r.Height / 2.0f;
            if (c.checked) {
                p.StrokeCircle(cx, cy, kBoxSize / 2.0f, 1.5f, c.enabled ? theme::kAccent : theme::kSurfaceLight);
                p.FillCircle(cx, cy, 4.0f, c.enabled ? theme::kAccent : theme::kSurfaceLight);
            } else {
                p.StrokeCircle(cx, cy, kBoxSize / 2.0f, 1.3f, hover ? theme::kTextPrimary : theme::kTextGray);
            }
            p.Text(c.text,
                   Gdiplus::RectF(r.X + kBoxSize + 10.0f, r.Y, r.Width - kBoxSize - 10.0f, r.Height),
                   theme::kBodySize,
                   false,
                   c.enabled ? theme::kTextPrimary : theme::kTextDisabled,
                   Gdiplus::StringAlignmentNear,
                   Gdiplus::StringAlignmentCenter);
            if (showFocus) p.StrokeRound(Inflated(r, 3.0f), 5.0f, 1.5f, theme::kTextPrimary);
            break;
        }
        case Kind::Edit: {
            p.FillRound(r, theme::kControlRadius, theme::kSurface);
            bool focused = c.native && GetFocus() == c.native;
            p.StrokeRound(r, theme::kControlRadius, focused ? 1.5f : 1.0f, focused ? theme::kAccent : theme::kSurfaceLight);
            break;
        }
    }
}

int Window::HitTest(int x, int y) const {
    float scale = Scale();
    float lx = static_cast<float>(x) / scale;
    float ly = static_cast<float>(y) / scale;
    for (const Control& c : controls_) {
        if (c.kind == Kind::Edit || !c.enabled) continue;
        if (c.rect.Contains(lx, ly)) return c.id;
    }
    return 0;
}

bool Window::Focusable(const Control& c) const {
    if (!c.enabled) return false;
    if (c.kind == Kind::Edit) return c.native && IsWindowVisible(c.native);
    return true;
}

void Window::SetFocusTo(int id) {
    Control* c = Find(id);
    if (!c || !Focusable(*c)) return;
    focusedId_ = id;
    if (c->kind == Kind::Edit) {
        SetFocus(c->native);
        SendMessageW(c->native, EM_SETSEL, 0, -1);
    } else {
        SetFocus(hwnd_);
    }
    Invalidate();
}

void Window::MoveFocus(bool forward) {
    std::vector<int> order;
    for (const Control& c : controls_) {
        if (Focusable(c)) order.push_back(c.id);
    }
    if (order.empty()) return;
    auto it = std::find(order.begin(), order.end(), focusedId_);
    size_t idx = 0;
    if (it == order.end()) {
        idx = forward ? 0 : order.size() - 1;
    } else {
        size_t cur = static_cast<size_t>(it - order.begin());
        idx = forward ? (cur + 1) % order.size() : (cur + order.size() - 1) % order.size();
    }
    SetFocusTo(order[idx]);
}

void Window::Activate(int id) {
    Control* c = Find(id);
    if (!c || !c->enabled || c->kind == Kind::Edit) return;
    if (c->kind == Kind::Checkbox) {
        c->checked = !c->checked;
    } else if (c->kind == Kind::Radio) {
        int group = c->group;
        for (Control& other : controls_) {
            if (other.kind == Kind::Radio && other.group == group) other.checked = other.id == id;
        }
    }
    Invalidate();
    // 回调里可能重建整页控件，之后不能再访问 c
    OnCommand(id);
}

void Window::HandleKey(WPARAM key) {
    switch (key) {
        case VK_TAB:
            keyboardCues_ = true;
            MoveFocus(GetKeyState(VK_SHIFT) >= 0);
            break;
        case VK_SPACE: {
            Control* c = Find(focusedId_);
            if (c && c->kind != Kind::Edit) {
                keyboardCues_ = true;
                Activate(c->id);
            }
            break;
        }
        case VK_RETURN: {
            Control* c = Find(focusedId_);
            if (c && (c->kind == Kind::Button || c->kind == Kind::PrimaryButton)) {
                Activate(c->id);
            } else if (defaultId_ != 0) {
                Activate(defaultId_);
            }
            break;
        }
        case VK_ESCAPE:
            OnEscape();
            break;
        default:
            break;
    }
}

void Window::RecreateEditFont() {
    HFONT old = editFont_;
    int height = -static_cast<int>(kEditTextSize * Scale() + 0.5f);
    editFont_ = CreateFontW(height,
                            0,
                            0,
                            0,
                            FW_NORMAL,
                            FALSE,
                            FALSE,
                            FALSE,
                            DEFAULT_CHARSET,
                            OUT_DEFAULT_PRECIS,
                            CLIP_DEFAULT_PRECIS,
                            CLEARTYPE_QUALITY,
                            DEFAULT_PITCH | FF_DONTCARE,
                            theme::kFontFamily);
    for (const Control& c : controls_) {
        if (c.native) SendMessageW(c.native, WM_SETFONT, reinterpret_cast<WPARAM>(editFont_), TRUE);
    }
    if (old) DeleteObject(old);
}

void Window::LayoutEdits() {
    if (!hwnd_) return;
    float scale = Scale();
    int lineHeight = static_cast<int>(18.0f * scale);
    HDC dc = GetDC(hwnd_);
    if (dc) {
        HGDIOBJ oldFont = SelectObject(dc, editFont_);
        TEXTMETRICW tm{};
        if (GetTextMetricsW(dc, &tm)) lineHeight = tm.tmHeight + 2;
        SelectObject(dc, oldFont);
        ReleaseDC(hwnd_, dc);
    }
    for (const Control& c : controls_) {
        if (c.kind != Kind::Edit || !c.native) continue;
        bool multiline = (GetWindowLongPtrW(c.native, GWL_STYLE) & ES_MULTILINE) != 0;
        int x = static_cast<int>((c.rect.X + 10.0f) * scale);
        int w = static_cast<int>((c.rect.Width - 14.0f) * scale);
        if (multiline) {
            int y = static_cast<int>((c.rect.Y + 8.0f) * scale);
            int h = static_cast<int>((c.rect.Height - 16.0f) * scale);
            MoveWindow(c.native, x, y, w, h, TRUE);
        } else {
            int frameTop = static_cast<int>(c.rect.Y * scale);
            int frameH = static_cast<int>(c.rect.Height * scale);
            int y = frameTop + (frameH - lineHeight) / 2;
            MoveWindow(c.native, x, y, static_cast<int>((c.rect.Width - 20.0f) * scale), lineHeight, TRUE);
        }
    }
}

void Window::LoadIcons() {
    UINT dpi = GetDpiForWindow(hwnd_);
    if (dpi == 0) dpi = 96;
    bigIcon_ = LoadIconSized(instance_, windowIconId_, GetSystemMetricsForDpi(SM_CXICON, dpi));
    smallIcon_ = LoadIconSized(instance_, windowIconId_, GetSystemMetricsForDpi(SM_CXSMICON, dpi));
    headerIcon_ = LoadIconSized(instance_, headerIconId_, static_cast<int>(theme::kHeaderIconSize * dpi / 96.0f + 0.5f));
    if (bigIcon_) SendMessageW(hwnd_, WM_SETICON, ICON_BIG, reinterpret_cast<LPARAM>(bigIcon_));
    if (smallIcon_) SendMessageW(hwnd_, WM_SETICON, ICON_SMALL, reinterpret_cast<LPARAM>(smallIcon_));

    SHSTOCKICONINFO sii{};
    sii.cbSize = sizeof(sii);
    if (SUCCEEDED(SHGetStockIconInfo(SIID_SHIELD, SHGSI_ICON, &sii))) shieldIcon_ = sii.hIcon;
}

void PaintHeader(Painter& p, HICON icon, const std::wstring& title, const std::wstring& subtitle, float width) {
    float iconY = 26.0f;
    p.Icon(icon, theme::kMargin, iconY, theme::kHeaderIconSize);
    float textX = theme::kMargin + theme::kHeaderIconSize + 14.0f;
    float textW = width - textX - theme::kMargin;
    p.Text(title, Gdiplus::RectF(textX, 22.0f, textW, 28.0f), theme::kTitleSize, true, theme::kTextPrimary,
           Gdiplus::StringAlignmentNear, Gdiplus::StringAlignmentNear, true);
    p.Text(subtitle, Gdiplus::RectF(textX, 49.0f, textW, 20.0f), theme::kSubtitleSize, false, theme::kTextGray,
           Gdiplus::StringAlignmentNear, Gdiplus::StringAlignmentNear, true);
}

void PaintFooter(Painter& p, float width, float height, int stepCount, int currentStep) {
    float top = height - theme::kFooterHeight;
    p.Fill(Gdiplus::RectF(0.0f, top, width, theme::kFooterHeight), theme::kWindowBackground);
    float cy = top + theme::kFooterHeight / 2.0f;
    for (int i = 0; i < stepCount; ++i) {
        bool active = i == currentStep;
        float cx = theme::kMargin + 3.0f + static_cast<float>(i) * 16.0f;
        if (active) {
            p.FillRound(Gdiplus::RectF(cx - 3.0f, cy - 3.0f, 14.0f, 6.0f), 3.0f, theme::kAccent);
        } else {
            p.FillCircle(cx + (i > currentStep ? 8.0f : 0.0f), cy, 3.0f, theme::kSurfaceLight);
        }
    }
}

Gdiplus::RectF FooterButtonRect(float width, float height, int slot) {
    float y = height - theme::kFooterHeight + (theme::kFooterHeight - theme::kButtonHeight) / 2.0f;
    float right = width - theme::kMargin;
    float x = right - theme::kPrimaryButtonWidth;
    float w = theme::kPrimaryButtonWidth;
    for (int i = 1; i <= slot; ++i) {
        w = theme::kButtonWidth;
        x -= theme::kButtonGap + w;
    }
    return Gdiplus::RectF(x, y, w, theme::kButtonHeight);
}

void Window::DestroyIcons() {
    for (HICON* icon : {&bigIcon_, &smallIcon_, &headerIcon_, &shieldIcon_}) {
        if (*icon) {
            DestroyIcon(*icon);
            *icon = nullptr;
        }
    }
}

}  // namespace mui
