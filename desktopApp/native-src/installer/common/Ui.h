#pragma once

#include <windows.h>
#include <objidl.h>
#include <gdiplus.h>

#include <string>
#include <vector>

namespace mui {

enum class Kind {
    Button,
    PrimaryButton,
    Checkbox,
    Radio,
    Edit,
};

struct Control {
    int id = 0;
    Kind kind = Kind::Button;
    Gdiplus::RectF rect;  // 96 DPI 逻辑坐标
    std::wstring text;
    bool checked = false;
    bool enabled = true;
    bool shield = false;
    int group = 0;
    HWND native = nullptr;
};

// 所有坐标与字号均为逻辑值，内部按 DPI 缩放
class Painter {
public:
    Painter(Gdiplus::Graphics& g, float scale);

    float Scale() const { return scale_; }
    Gdiplus::RectF Px(const Gdiplus::RectF& r) const;

    void Fill(const Gdiplus::RectF& r, Gdiplus::ARGB color);
    void FillRound(const Gdiplus::RectF& r, float radius, Gdiplus::ARGB color);
    void StrokeRound(const Gdiplus::RectF& r, float radius, float width, Gdiplus::ARGB color);
    void FillCircle(float cx, float cy, float radius, Gdiplus::ARGB color);
    void StrokeCircle(float cx, float cy, float radius, float width, Gdiplus::ARGB color);
    void Line(float x1, float y1, float x2, float y2, float width, Gdiplus::ARGB color);
    // 返回实际占用高度
    float Text(const std::wstring& text,
               const Gdiplus::RectF& r,
               float size,
               bool bold,
               Gdiplus::ARGB color,
               Gdiplus::StringAlignment align = Gdiplus::StringAlignmentNear,
               Gdiplus::StringAlignment lineAlign = Gdiplus::StringAlignmentNear,
               bool singleLine = false);
    float MeasureWidth(const std::wstring& text, float size, bool bold);
    void Icon(HICON icon, float x, float y, float size);

private:
    Gdiplus::Graphics& g_;
    float scale_;
    Gdiplus::FontFamily family_;
    const Gdiplus::FontFamily* activeFamily_;
};

class Window {
public:
    Window();
    virtual ~Window();
    Window(const Window&) = delete;
    Window& operator=(const Window&) = delete;

    bool Create(HINSTANCE instance,
                const wchar_t* title,
                float logicalWidth,
                float logicalHeight,
                int windowIconId,
                int headerIconId);
    int Run();
    HWND Hwnd() const { return hwnd_; }
    float Scale() const;
    float Width() const { return logicalWidth_; }
    float Height() const { return logicalHeight_; }

protected:
    virtual void Paint(Painter& p) = 0;
    virtual void OnCommand(int id) = 0;
    virtual void OnEscape() {}
    virtual void OnEditChanged(int /*id*/) {}
    virtual bool CanClose() { return true; }
    // 返回 true 表示消息已处理
    virtual bool HandleMessage(UINT /*msg*/, WPARAM /*wParam*/, LPARAM /*lParam*/, LRESULT& /*result*/) { return false; }

    Control& Add(const Control& c);
    Control* Find(int id);
    void ClearControls();
    void SetDefaultButton(int id) { defaultId_ = id; }
    void FocusControl(int id);
    void Invalidate();
    void SetClosable(bool closable);
    HICON HeaderIcon() const { return headerIcon_; }

    HWND AddEdit(int id, const Gdiplus::RectF& rect, bool multiline, bool readOnly);
    std::wstring EditText(int id) const;
    void SetEditText(int id, const std::wstring& text);

private:
    static LRESULT CALLBACK StaticProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
    static LRESULT CALLBACK EditSubclassProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam, UINT_PTR idSubclass, DWORD_PTR refData);
    LRESULT Proc(UINT msg, WPARAM wParam, LPARAM lParam);

    void PaintWindow();
    void DrawControl(Painter& p, const Control& c);
    int HitTest(int x, int y) const;
    bool Focusable(const Control& c) const;
    void SetFocusTo(int id);
    void MoveFocus(bool forward);
    void Activate(int id);
    void HandleKey(WPARAM key);
    void RecreateEditFont();
    void LayoutEdits();
    void LoadIcons();
    void DestroyIcons();

    HWND hwnd_ = nullptr;
    HINSTANCE instance_ = nullptr;
    std::vector<Control> controls_;
    float logicalWidth_ = 0.0f;
    float logicalHeight_ = 0.0f;
    int hoveredId_ = 0;
    int pressedId_ = 0;
    int focusedId_ = 0;
    int defaultId_ = 0;
    bool keyboardCues_ = false;
    bool trackingMouse_ = false;
    bool suppressEditNotify_ = false;
    int windowIconId_ = 0;
    int headerIconId_ = 0;
    HICON bigIcon_ = nullptr;
    HICON smallIcon_ = nullptr;
    HICON headerIcon_ = nullptr;
    HICON shieldIcon_ = nullptr;
    HFONT editFont_ = nullptr;
    HBRUSH editBrush_ = nullptr;
    ULONG_PTR gdiplusToken_ = 0;
};

// 安装与卸载窗口共用的页眉、页脚布局
constexpr float kBodyTop = 92.0f;
void PaintHeader(Painter& p, HICON icon, const std::wstring& title, const std::wstring& subtitle, float width);
void PaintFooter(Painter& p, float width, float height, int stepCount, int currentStep);
// slot 0 为最右侧主按钮，向左依次递增
Gdiplus::RectF FooterButtonRect(float width, float height, int slot);

}  // namespace mui
