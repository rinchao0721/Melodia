package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.desktop.platform.native.WindowDecoration
import java.awt.Window
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformStub

// Linux 无边框窗口外观：交给窗口管理器/CSD，暂不干预。
@PlatformStub(DesktopPlatform.LINUX, "交给窗口管理器 / CSD 待接入")
class NoopWindowDecoration : WindowDecoration {

    override fun enableSystemAnimations(window: Window) = Unit

    override fun restoreWindowProc(window: Window) = Unit

    override fun applyFrame(window: Window, maximized: Boolean, borderRgb: Int) = Unit
}
