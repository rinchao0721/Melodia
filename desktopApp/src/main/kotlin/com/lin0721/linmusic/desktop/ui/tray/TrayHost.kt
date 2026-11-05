package com.lin0721.linmusic.desktop.ui.tray

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.imageio.ImageIO

private const val TAG = "TrayHost"

// Windows 托盘提示最长 127 个字符
private const val TOOLTIP_MAX_LENGTH = 127

private val MenuWidth = 240.dp
private val MenuShape = RoundedCornerShape(8.dp)

sealed interface TrayMenuEntry {
    data class Action(val label: String, val onClick: () -> Unit) : TrayMenuEntry
    data class Toggle(val label: String, val checked: Boolean, val onChange: (Boolean) -> Unit) : TrayMenuEntry
    data object Divider : TrayMenuEntry
}

val isTraySupported: Boolean
    get() = runCatching { SystemTray.isSupported() }.getOrDefault(false)

// AWT 原生托盘菜单在 UTF-8 默认编码下无法显示中文，菜单改由 Compose 绘制
@Composable
fun TrayHost(
    tooltip: String,
    header: String?,
    entries: List<TrayMenuEntry>,
    onOpenMain: () -> Unit
) {
    if (!isTraySupported) return
    var menuAnchor by remember { mutableStateOf<Point?>(null) }
    val openMain by rememberUpdatedState(onOpenMain)

    val trayIcon = remember {
        TrayIcon(loadTrayImage(), "Melodia").apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = maybeShowMenu(e)

                override fun mouseReleased(e: MouseEvent) = maybeShowMenu(e)

                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) openMain()
                }

                // TrayIcon 事件坐标在高 DPI 下不可靠，统一取指针的逻辑坐标
                private fun maybeShowMenu(e: MouseEvent) {
                    if (e.isPopupTrigger) menuAnchor = MouseInfo.getPointerInfo()?.location
                }
            })
        }
    }

    DisposableEffect(Unit) {
        runCatching { SystemTray.getSystemTray().add(trayIcon) }
            .onFailure { AppLogger.e(TAG, "添加托盘图标失败", it) }
        onDispose { runCatching { SystemTray.getSystemTray().remove(trayIcon) } }
    }
    LaunchedEffect(tooltip) {
        trayIcon.toolTip = tooltip.take(TOOLTIP_MAX_LENGTH)
    }

    menuAnchor?.let { anchor ->
        TrayMenuWindow(anchor, header, entries, onDismiss = { menuAnchor = null })
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TrayMenuWindow(
    anchor: Point,
    header: String?,
    entries: List<TrayMenuEntry>,
    onDismiss: () -> Unit
) {
    val dismiss by rememberUpdatedState(onDismiss)
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(size = DpSize.Unspecified),
        title = "Melodia",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        onKeyEvent = { event ->
            if (event.key == Key.Escape) {
                dismiss()
                true
            } else {
                false
            }
        }
    ) {
        DisposableEffect(window) {
            // 点击菜单以外区域时窗口失焦，随即收起
            val listener = object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent) = dismiss()
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        LaunchedEffect(anchor) {
            window.pack()
            window.location = menuLocation(anchor, window.width, window.height)
            window.toFront()
            window.requestFocus()
        }

        // 外层留白给阴影
        Box(Modifier.padding(8.dp)) {
            Column(
                Modifier.width(MenuWidth)
                    .themedShadow(8.dp, MenuShape)
                    .clip(MenuShape)
                    .background(DesktopColors.PopupSurface)
                    .floatingOutline(MenuShape)
                    .padding(vertical = 4.dp)
            ) {
                if (header != null) {
                    Text(
                        header,
                        color = DesktopColors.TextGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    MenuDivider()
                }
                entries.forEach { entry ->
                    when (entry) {
                        is TrayMenuEntry.Action -> MenuRow(entry.label, checked = null) {
                            dismiss()
                            entry.onClick()
                        }
                        is TrayMenuEntry.Toggle -> MenuRow(entry.label, checked = entry.checked) {
                            dismiss()
                            entry.onChange(!entry.checked)
                        }
                        TrayMenuEntry.Divider -> MenuDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuRow(label: String, checked: Boolean?, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) DesktopColors.Hover else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (checked == true) Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp))
        }
        Text(label, color = DesktopColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = DesktopColors.SurfaceLight)
}

// 默认出现在光标左上方，超出所在显示器可用区域（不含任务栏）时向内收
private fun menuLocation(anchor: Point, width: Int, height: Int): Point {
    val area = usableBounds(anchor)
    var x = anchor.x - width
    var y = anchor.y - height
    if (x < area.x) x = anchor.x
    if (y < area.y) y = anchor.y
    x = x.coerceIn(area.x, (area.x + area.width - width).coerceAtLeast(area.x))
    y = y.coerceIn(area.y, (area.y + area.height - height).coerceAtLeast(area.y))
    return Point(x, y)
}

private fun usableBounds(point: Point): Rectangle {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val config = env.screenDevices.map { it.defaultConfiguration }.firstOrNull { it.bounds.contains(point) }
        ?: env.defaultScreenDevice.defaultConfiguration
    val bounds = config.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
    return Rectangle(
        bounds.x + insets.left,
        bounds.y + insets.top,
        bounds.width - insets.left - insets.right,
        bounds.height - insets.top - insets.bottom
    )
}

private fun loadTrayImage(): Image {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("melodia.png")
    return stream?.use { runCatching { ImageIO.read(it) }.getOrNull() }
        ?: java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
}
