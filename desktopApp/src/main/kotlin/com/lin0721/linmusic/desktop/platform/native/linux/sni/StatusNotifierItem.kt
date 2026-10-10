package com.lin0721.linmusic.desktop.platform.native.linux.sni

import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties

// org.kde.StatusNotifierItem（SNI）协议声明：KDE Plasma、Waybar、GNOME(AppIndicator 扩展) 等
// 托盘宿主都按这套 D-Bus 接口与后台应用交互。
// 规范：https://www.freedesktop.org/wiki/Specifications/StatusNotifierItem/
//
// 属主须同时实现 org.freedesktop.DBus.Properties（Get/Set/GetAll），故继承 Properties。
@DBusInterfaceName("org.kde.StatusNotifierItem")
interface StatusNotifierItem : DBusInterface, Properties {

    // x/y 为宿主给出的图标位置（屏幕坐标），应用据此弹出菜单
    fun Activate(x: Int, y: Int)

    fun SecondaryActivate(x: Int, y: Int)

    fun ContextMenu(x: Int, y: Int)

    fun Scroll(delta: Int, orientation: String)

    override fun getObjectPath(): String = OBJECT_PATH

    companion object {
        const val OBJECT_PATH = "/StatusNotifierItem"
        const val INTERFACE = "org.kde.StatusNotifierItem"

        const val PROP_CATEGORY = "Category"
        const val PROP_ID = "Id"
        const val PROP_TITLE = "Title"
        const val PROP_STATUS = "Status"
        const val PROP_WINDOW_ID = "WindowId"
        const val PROP_ITEM_IS_MENU = "ItemIsMenu"
        const val PROP_ICON_NAME = "IconName"
        const val PROP_ICON_PIXMAP = "IconPixmap"
        const val PROP_MENU = "Menu"
        const val PROP_TOOL_TIP = "ToolTip"
    }
}

// org.kde.StatusNotifierWatcher：托盘宿主提供的注册中心
@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcher : DBusInterface {

    fun RegisterStatusNotifierItem(service: String)

    override fun getObjectPath(): String = "/StatusNotifierWatcher"

    companion object {
        const val BUS_NAME = "org.kde.StatusNotifierWatcher"
    }
}

// org.freedesktop.DBus 上的名称查询（探测托盘宿主是否存在）
@DBusInterfaceName("org.freedesktop.DBus")
interface DBusNameOwner : DBusInterface {

    fun NameHasOwner(name: String): Boolean

    override fun getObjectPath(): String = "/org/freedesktop/DBus"
}

// IconPixmap：(iiay) —— 宽、高与 ARGB32 大端像素（行优先）
// 字段必须标 @Position：dbus-java 5 靠它推导结构体签名，缺了会得到空参数数组（IndexOutOfBounds）
class IconPixmap(
    @field:Position(0) @JvmField val width: Int,
    @field:Position(1) @JvmField val height: Int,
    @field:Position(2) @JvmField val pixels: ByteArray
) : Struct()

// ToolTip：(sa(iiay)ss) —— 图标名、图标、标题、正文
class SniToolTip(
    @field:Position(0) @JvmField val iconName: String,
    @field:Position(1) @JvmField val iconPixmap: Array<IconPixmap>,
    @field:Position(2) @JvmField val title: String,
    @field:Position(3) @JvmField val text: String
) : Struct()
