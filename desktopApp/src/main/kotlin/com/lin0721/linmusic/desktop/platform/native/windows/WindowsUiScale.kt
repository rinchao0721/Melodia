package com.lin0721.linmusic.desktop.platform.native.windows

import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl
import com.lin0721.linmusic.desktop.platform.native.UiScale

// Windows 的 AWT 会按系统 DPI 正确上报 defaultTransform，无需额外干预
@PlatformImpl(DesktopPlatform.WINDOWS)
class WindowsUiScale : UiScale {
    override val detected: Float? = null
}
