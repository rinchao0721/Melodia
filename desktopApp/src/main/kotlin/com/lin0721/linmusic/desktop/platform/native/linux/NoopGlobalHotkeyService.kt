package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.desktop.platform.native.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformStub

// Linux 全局快捷键尚未实现（X11 XGrabKey / Wayland 需另行处理）；先全部 no-op。
@PlatformStub(DesktopPlatform.LINUX, "X11 XGrabKey / Wayland 待实现")
class NoopGlobalHotkeyService : GlobalHotkeyService {

    override var onAction: ((HotkeyAction) -> Unit)? = null

    override val failed: StateFlow<Set<HotkeyAction>> = MutableStateFlow(emptySet())

    override fun start() = Unit

    override fun stop() = Unit

    override fun apply(custom: Map<HotkeyAction, HotkeyCombo?>, mediaKeys: Boolean) = Unit

    override fun pause() = Unit

    override fun resume() = Unit

    // Linux 全局热键尚未实现，键码空间（X11 keysym）也待定，暂不提供转换
    override fun toHotkeyCombo(awtKeyCode: Int, ctrl: Boolean, alt: Boolean, shift: Boolean): HotkeyCombo? = null
}
