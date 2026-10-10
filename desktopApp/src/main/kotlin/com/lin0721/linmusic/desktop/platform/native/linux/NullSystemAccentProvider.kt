package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.desktop.platform.native.SystemAccentProvider
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformStub

// Linux 暂不读取桌面主题强调色；返回 null 让窗口边框退回中性线。
@PlatformStub(DesktopPlatform.LINUX, "桌面主题强调色待接入")
class NullSystemAccentProvider : SystemAccentProvider {

    override fun windowBorderAccent(): Int? = null
}
