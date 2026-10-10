package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.native.AwtTrayIcon
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl
import com.lin0721.linmusic.desktop.platform.native.TrayIntegration
import com.lin0721.linmusic.desktop.platform.native.TrayMenuModel
import com.lin0721.linmusic.desktop.platform.native.UiScale
import com.lin0721.linmusic.desktop.platform.native.linux.WaylandOutputInfo
import com.lin0721.linmusic.desktop.platform.native.linux.parseWaylandInfo
import com.lin0721.linmusic.desktop.platform.native.linux.readWaylandInfo
import com.lin0721.linmusic.desktop.platform.native.linux.sni.DBusNameOwner
import com.lin0721.linmusic.desktop.platform.native.linux.sni.DbusMenu
import com.lin0721.linmusic.desktop.platform.native.linux.sni.IconPixmap
import com.lin0721.linmusic.desktop.platform.native.linux.sni.SniDbusMenu
import com.lin0721.linmusic.desktop.platform.native.linux.sni.SniToolTip
import com.lin0721.linmusic.desktop.platform.native.linux.sni.StatusNotifierItem
import com.lin0721.linmusic.desktop.platform.native.linux.sni.StatusNotifierWatcher
import com.lin0721.linmusic.desktop.platform.native.linux.sni.hostPointToAwt
import com.lin0721.linmusic.desktop.platform.native.linux.sni.iconPixmapsFromPng
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.SystemTray
import kotlin.math.roundToInt

private const val TAG = "LinuxTray"

private const val SNI_ID = "melodia"
private const val SNI_TITLE = "Melodia"
private const val SNI_CATEGORY = "ApplicationStatus"
private const val SNI_STATUS = "Active"

// Linux 托盘：优先 SNI（D-Bus 直连，点击与透明图标都可靠），无 SNI 宿主时回退 AWT（仅图标可见）。
@PlatformImpl(DesktopPlatform.LINUX)
class LinuxTrayIntegration : TrayIntegration {

    private var item: SniItem? = null
    private var menu: SniDbusMenu? = null
    private var fallback: AwtTrayIcon? = null

    // 菜单内容可能先于 SNI 安装送达；先缓存，安装时一次性应用
    @Volatile
    private var pendingMenu: TrayMenuModel? = null

    // 与应用同生命周期的会话总线连接；SNI 注册失败时保持未使用状态
    private val connection: DBusConnection? by lazy {
        runCatching { DBusConnectionBuilder.forSessionBus().build() }
            .onFailure { AppLogger.w(TAG, "连接会话总线失败", it) }
            .getOrNull()
    }

    override val supported: Boolean
        get() = watcherAvailable || runCatching { SystemTray.isSupported() }.getOrDefault(false)

    // 托盘宿主（Plasma/Waybar 等）会提供 org.kde.StatusNotifierWatcher
    private val watcherAvailable: Boolean by lazy {
        val conn = connection ?: return@lazy false
        runCatching {
            conn.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBusNameOwner::class.java)
                .NameHasOwner(StatusNotifierWatcher.BUS_NAME)
        }.getOrDefault(false)
    }

    // 会话输出布局（wayland-info，仅 Wayland 会话可得）与 AWT 物理屏幕，进程内各取一次
    private val waylandLayouts: List<WaylandOutputInfo> by lazy {
        readWaylandInfo()?.let { parseWaylandInfo(it) }.orEmpty()
    }
    private val awtScreens: List<Rectangle> by lazy {
        runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
        }.getOrDefault(emptyList())
    }

    override fun install(
        tooltip: String,
        iconPng: ByteArray,
        onActivate: (x: Int, y: Int) -> Unit,
        onContextMenu: (x: Int, y: Int) -> Unit
    ) {
        // 宿主坐标未必是规范里的逻辑屏幕坐标（Plasma 实测为"逻辑原点+输出内物理偏移"），
        // 先按两种模型换算回本应用物理像素；换算不出时退回 AWT 指针（XWayland 下会滞留在
        // 进入面板处），最后才按缩放系数硬算
        val menuAnchor: (Int, Int) -> Unit = { x, y ->
            val pointer = MouseInfo.getPointerInfo()?.location
            val mapped = hostPointToAwt(Point(x, y), waylandLayouts, awtScreens, pointer)
            AppLogger.i(TAG, "托盘菜单：宿主坐标=($x, $y) 指针=$pointer 换算=$mapped")
            val anchor = mapped
                ?: pointer
                ?: Point((x * UiScale.current.factor).roundToInt(), (y * UiScale.current.factor).roundToInt())
            onContextMenu(anchor.x, anchor.y)
        }
        if (installSni(tooltip, iconPng, onActivate, menuAnchor)) return

        AppLogger.i(TAG, "无 SNI 宿主，回退 AWT 托盘（仅图标可见）")
        fallback = AwtTrayIcon(iconPng, tooltip, { onActivate(0, 0) }, onContextMenu).also { it.install() }
    }

    private fun installSni(
        tooltip: String,
        iconPng: ByteArray,
        onActivate: (x: Int, y: Int) -> Unit,
        onContextMenu: (x: Int, y: Int) -> Unit
    ): Boolean {
        val conn = connection ?: return false
        if (!watcherAvailable) return false
        return runCatching {
            val sniItem = SniItem(
                tooltip = tooltip,
                pixmaps = iconPixmapsFromPng(iconPng),
                onActivate = onActivate,
                onContextMenu = onContextMenu
            )
            val dbusMenu = SniDbusMenu()
            pendingMenu?.let { dbusMenu.update(it) }
            // 先导出对象再向 Watcher 注册：宿主收到注册后会立刻读属性、解析 Menu 指向的菜单
            conn.exportObject(StatusNotifierItem.OBJECT_PATH, sniItem)
            conn.exportObject(DbusMenu.OBJECT_PATH, dbusMenu)
            conn.getRemoteObject(
                StatusNotifierWatcher.BUS_NAME,
                "/StatusNotifierWatcher",
                StatusNotifierWatcher::class.java
            ).RegisterStatusNotifierItem(conn.uniqueName)
            item = sniItem
            menu = dbusMenu
            AppLogger.i(TAG, "SNI 已注册：${conn.uniqueName}${StatusNotifierItem.OBJECT_PATH}")
            true
        }.onFailure { AppLogger.w(TAG, "SNI 注册失败", it) }.getOrDefault(false)
    }

    override fun updateMenu(menu: TrayMenuModel) {
        pendingMenu = menu
        val server = this.menu ?: return
        server.update(menu)
        runCatching { connection?.sendMessage(server.layoutUpdatedSignal()) }
    }

    override fun updateTooltip(tooltip: String) {
        item?.updateTooltip(tooltip)?.let { signal ->
            runCatching { connection?.sendMessage(signal) }
        }
        fallback?.updateTooltip(tooltip)
    }

    override fun uninstall() {
        if (item != null) {
            runCatching { connection?.unExportObject(StatusNotifierItem.OBJECT_PATH) }
        }
        if (menu != null) {
            runCatching { connection?.unExportObject(DbusMenu.OBJECT_PATH) }
        }
        item = null
        menu = null
        fallback?.uninstall()
        fallback = null
    }
}

// 导出的 SNI 对象：属性读自字段，宿主回调直接转给 UI
internal class SniItem(
    tooltip: String,
    private val pixmaps: Array<IconPixmap>,
    private val onActivate: (Int, Int) -> Unit,
    private val onContextMenu: (Int, Int) -> Unit
) : StatusNotifierItem {

    @Volatile
    private var tooltip: String = tooltip

    override fun isRemote(): Boolean = false

    override fun Activate(x: Int, y: Int) = onActivate(x, y)

    override fun SecondaryActivate(x: Int, y: Int) = Unit

    override fun ContextMenu(x: Int, y: Int) = onContextMenu(x, y)

    override fun Scroll(delta: Int, orientation: String) = Unit

    // 返回需要广播的 NewToolTip 信号，宿主据此刷新悬浮提示
    fun updateTooltip(text: String): NewToolTip {
        tooltip = text
        return NewToolTip(StatusNotifierItem.OBJECT_PATH)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(iface: String, prop: String): A = when (iface) {
        StatusNotifierItem.INTERFACE -> when (prop) {
            StatusNotifierItem.PROP_CATEGORY -> SNI_CATEGORY as A
            StatusNotifierItem.PROP_ID -> SNI_ID as A
            StatusNotifierItem.PROP_TITLE -> SNI_TITLE as A
            StatusNotifierItem.PROP_STATUS -> SNI_STATUS as A
            StatusNotifierItem.PROP_ICON_NAME -> "" as A
            StatusNotifierItem.PROP_ITEM_IS_MENU -> false as A
            StatusNotifierItem.PROP_WINDOW_ID -> 0 as A
            StatusNotifierItem.PROP_MENU -> Variant(DbusMenu.OBJECT_PATH, "o") as A
            StatusNotifierItem.PROP_ICON_PIXMAP -> Variant(pixmaps, "a(iiay)") as A
            StatusNotifierItem.PROP_TOOL_TIP -> Variant(toolTipStruct(), "(sa(iiay)ss)") as A
            else -> null as A
        }
        else -> null as A
    }

    override fun GetAll(iface: String): Map<String, Variant<*>> =
        if (iface != StatusNotifierItem.INTERFACE) {
            emptyMap()
        } else {
            mapOf(
                StatusNotifierItem.PROP_CATEGORY to Variant(SNI_CATEGORY),
                StatusNotifierItem.PROP_ID to Variant(SNI_ID),
                StatusNotifierItem.PROP_TITLE to Variant(SNI_TITLE),
                StatusNotifierItem.PROP_STATUS to Variant(SNI_STATUS),
                StatusNotifierItem.PROP_ICON_NAME to Variant(""),
                StatusNotifierItem.PROP_ITEM_IS_MENU to Variant(false),
                StatusNotifierItem.PROP_WINDOW_ID to Variant(0),
                StatusNotifierItem.PROP_MENU to Variant(DbusMenu.OBJECT_PATH, "o"),
                StatusNotifierItem.PROP_ICON_PIXMAP to Variant(pixmaps, "a(iiay)"),
                StatusNotifierItem.PROP_TOOL_TIP to Variant(toolTipStruct(), "(sa(iiay)ss)")
            )
        }

    override fun <A : Any?> Set(iface: String, prop: String, value: A) = Unit

    private fun toolTipStruct(): SniToolTip = SniToolTip("", pixmaps, SNI_TITLE, tooltip)

    // 与 MPRIS 同样的 8 参构造：接口名不能退化为 body 参数
    class NewToolTip(objectPath: String) : DBusSignal(
        ENDIANESS, null, objectPath, StatusNotifierItem.INTERFACE, "NewToolTip", ""
    )

    private companion object {
        const val ENDIANESS: Byte = 0
    }
}
