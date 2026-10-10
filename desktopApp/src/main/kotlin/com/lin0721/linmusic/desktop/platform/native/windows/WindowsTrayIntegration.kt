package com.lin0721.linmusic.desktop.platform.native.windows

import com.lin0721.linmusic.desktop.platform.native.AwtTrayIcon
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl
import com.lin0721.linmusic.desktop.platform.native.TrayIntegration
import com.lin0721.linmusic.desktop.platform.native.TrayMenuModel
import java.awt.SystemTray

// Windows：AWT 托盘即可，系统会派发左键激活与右键菜单事件
@PlatformImpl(DesktopPlatform.WINDOWS)
class WindowsTrayIntegration : TrayIntegration {

    private var impl: AwtTrayIcon? = null

    override val supported: Boolean
        get() = runCatching { SystemTray.isSupported() }.getOrDefault(false)

    override fun install(
        tooltip: String,
        iconPng: ByteArray,
        onActivate: (x: Int, y: Int) -> Unit,
        onContextMenu: (x: Int, y: Int) -> Unit
    ) {
        impl = AwtTrayIcon(iconPng, tooltip, { onActivate(0, 0) }, onContextMenu).also { it.install() }
    }

    override fun updateTooltip(tooltip: String) {
        impl?.updateTooltip(tooltip)
    }

    override fun updateMenu(menu: TrayMenuModel) = Unit

    override fun uninstall() {
        impl?.uninstall()
        impl = null
    }
}
