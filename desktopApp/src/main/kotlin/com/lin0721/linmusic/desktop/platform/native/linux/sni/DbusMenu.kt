package com.lin0721.linmusic.desktop.platform.native.linux.sni

import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.DBusSerializable
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

// com.canonical.dbusmenu（DBusMenu）协议声明：应用只发布菜单结构，
// 托盘宿主（Plasma/libdbusmenu-qt、Waybar、GNOME AppIndicator 扩展等）用系统样式渲染，
// 点击通过 Event(id, "clicked") 回调给应用。
// 规范：https://github.com/ubuntu/indicator-application 仓库中的 com.canonical.dbusmenu.xml
@DBusInterfaceName("com.canonical.dbusmenu")
interface DbusMenu : DBusInterface, Properties {

    // 返回整棵菜单树（recursionDepth<0 表示不限层数）；父节点通常是 0（根）
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): MenuLayoutReply

    fun GetGroupProperties(ids: IntArray, propertyNames: List<String>): Array<MenuGroupProperty>

    fun GetProperty(id: Int, name: String): Variant<*>

    // 用户点击某个菜单项：eventId == "clicked"
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)

    fun EventGroup(events: Array<MenuEventItem>): IntArray

    // 菜单即将展示（用于动态菜单），返回 true 表示需要重新取布局
    fun AboutToShow(id: Int): Boolean

    fun AboutToShowGroup(ids: IntArray): MenuAboutToShowReply

    override fun getObjectPath(): String = OBJECT_PATH

    companion object {
        const val OBJECT_PATH = "/MenuBar"
        const val INTERFACE = "com.canonical.dbusmenu"

        const val PROP_VERSION = "Version"
        const val PROP_TEXT_DIRECTION = "TextDirection"
        const val PROP_STATUS = "Status"
        const val PROP_ICON_THEME_PATH = "IconThemePath"

        // 菜单项属性名
        const val ITEM_LABEL = "label"
        const val ITEM_ENABLED = "enabled"
        const val ITEM_VISIBLE = "visible"
        const val ITEM_TYPE = "type"
        const val ITEM_TOGGLE_TYPE = "toggle-type"
        const val ITEM_TOGGLE_STATE = "toggle-state"
        const val ITEM_CHILDREN_DISPLAY = "children-display"

        const val TYPE_STANDARD = "standard"
        const val TYPE_SEPARATOR = "separator"
        const val TOGGLE_CHECKMARK = "checkmark"
        const val EVENT_CLICKED = "clicked"
    }
}

// (ia{sv}av)：菜单项 —— id、属性、子项（子项用 Variant 包一层，对应 av 中的 v）
class MenuLayoutItem(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
    @field:Position(2) @JvmField val children: Array<Variant<*>>,
) : Struct() {
    companion object {
        const val SIGNATURE = "(ia{sv}av)"
    }
}

// GetLayout 的两个 out 参数（u + 菜单结构）。
// dbus-java 用 DBusSerializable 的 serialize() 提供多返回值，用 deserialize() 的形参推导 out 签名。
class MenuLayoutReply(
    private val revision: UInt32,
    private val layout: MenuLayoutItem,
) : DBusSerializable {

    override fun serialize(): Array<Any> = arrayOf(revision, layout)

    // 只用于签名推导（dbus-java 读取形参类型），实际不会被调用
    @Suppress("unused", "UNUSED_PARAMETER")
    fun deserialize(revision: UInt32, layout: MenuLayoutItem): Unit = Unit
}

// a(ia{sv})：批量属性查询的结果项
class MenuGroupProperty(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

// (isvu)：批量事件中的单个事件
class MenuEventItem(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val eventId: String,
    @field:Position(2) @JvmField val data: Variant<*>,
    @field:Position(3) @JvmField val timestamp: UInt32,
) : Struct()

// AboutToShowGroup 的两个 out 参数（ai + ai）
class MenuAboutToShowReply(
    private val updatesNeeded: IntArray,
    private val idErrors: IntArray,
) : DBusSerializable {

    override fun serialize(): Array<Any> = arrayOf(updatesNeeded, idErrors)

    @Suppress("unused", "UNUSED_PARAMETER")
    fun deserialize(updatesNeeded: IntArray, idErrors: IntArray): Unit = Unit
}
