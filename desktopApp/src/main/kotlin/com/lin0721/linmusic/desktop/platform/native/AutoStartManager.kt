package com.lin0721.linmusic.desktop.platform.native

// 开机自启：Windows 写注册表 Run 项，Linux 走 XDG autostart。
// 阻塞调用，需在 IO 线程执行。
@RequireAllPlatforms
interface AutoStartManager {

    val isSupported: Boolean

    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean): Boolean
}
