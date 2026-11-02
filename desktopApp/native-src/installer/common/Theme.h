#pragma once

#include <windows.h>
#include <objidl.h>
#include <gdiplus.h>

// 配色取自 desktopApp 的 DesktopColors，安装与卸载界面固定暗色
namespace mui::theme {

constexpr Gdiplus::ARGB kWindowBackground = 0xFF000000;
constexpr Gdiplus::ARGB kPane = 0xFF121212;
constexpr Gdiplus::ARGB kSurface = 0xFF2A2A2A;
constexpr Gdiplus::ARGB kSurfaceLight = 0xFF3E3E3E;
constexpr Gdiplus::ARGB kTextPrimary = 0xFFFFFFFF;
constexpr Gdiplus::ARGB kTextGray = 0xFFB3B3B3;
constexpr Gdiplus::ARGB kTextDisabled = 0xFF6A6A6A;
constexpr Gdiplus::ARGB kAccent = 0xFFC20C0C;
constexpr Gdiplus::ARGB kAccentHover = 0xFFD81E1E;
constexpr Gdiplus::ARGB kAccentPressed = 0xFFA20A0A;
constexpr Gdiplus::ARGB kError = 0xFFF26B6B;

constexpr COLORREF kEditText = RGB(0xFF, 0xFF, 0xFF);
constexpr COLORREF kEditBackground = RGB(0x2A, 0x2A, 0x2A);
constexpr COLORREF kCaption = RGB(0x12, 0x12, 0x12);
constexpr COLORREF kBorder = RGB(0x2A, 0x2A, 0x2A);

constexpr wchar_t kFontFamily[] = L"Microsoft YaHei UI";

// 以下均为 96 DPI 下的逻辑尺寸
constexpr float kMargin = 28.0f;
constexpr float kFooterHeight = 64.0f;
constexpr float kButtonHeight = 34.0f;
constexpr float kButtonWidth = 92.0f;
constexpr float kPrimaryButtonWidth = 112.0f;
constexpr float kButtonGap = 10.0f;
constexpr float kControlRadius = 6.0f;
constexpr float kTitleSize = 18.0f;
constexpr float kSubtitleSize = 12.5f;
constexpr float kBodySize = 13.0f;
constexpr float kHeaderIconSize = 40.0f;

}  // namespace mui::theme
