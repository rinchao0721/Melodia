package com.lin0721.linmusic.desktop.platform.native

import com.lin0721.linmusic.core.log.AppLogger
import java.awt.Image
import java.awt.MouseInfo
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

private const val TAG = "AwtTrayIcon"

// AWT 托盘图标：Windows 的主路径，也是 Linux 无 SNI Watcher 时的兜底。
// Linux/XEmbed 下只保证图标可见，点击事件收不到（原因见 TrayIntegration 注释）。
internal class AwtTrayIcon(
    private val iconPng: ByteArray,
    private val tooltip: String,
    private val onActivate: () -> Unit,
    private val onContextMenu: (x: Int, y: Int) -> Unit
) {
    private var trayIcon: TrayIcon? = null

    fun install(): Boolean = runCatching {
        val image: Image = ImageIO.read(ByteArrayInputStream(iconPng)) ?: return false
        val icon = TrayIcon(image, tooltip).apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = maybeShowMenu(e)

                override fun mouseReleased(e: MouseEvent) = maybeShowMenu(e)

                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) onActivate()
                }

                // TrayIcon 事件坐标在高 DPI 下不可靠，统一取指针的逻辑坐标
                private fun maybeShowMenu(e: MouseEvent) {
                    if (!e.isPopupTrigger) return
                    val location = MouseInfo.getPointerInfo()?.location ?: return
                    onContextMenu(location.x, location.y)
                }
            })
        }
        SystemTray.getSystemTray().add(icon)
        trayIcon = icon
        true
    }.onFailure { AppLogger.w(TAG, "添加 AWT 托盘图标失败", it) }.getOrDefault(false)

    fun updateTooltip(tooltip: String) {
        trayIcon?.toolTip = tooltip
    }

    fun uninstall() {
        val icon = trayIcon ?: return
        trayIcon = null
        runCatching { SystemTray.getSystemTray().remove(icon) }
    }
}
