package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.desktop.platform.native.AutoStartManager
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformStub

// Linux XDG autostart 尚未实现；先返回不支持，后续写 ~/.config/autostart 下的 .desktop。
@PlatformStub(DesktopPlatform.LINUX, "XDG autostart 待实现")
class LinuxAutoStartManager : AutoStartManager {

    override val isSupported: Boolean = false

    override fun isEnabled(): Boolean = false

    override fun setEnabled(enabled: Boolean): Boolean = false
}
