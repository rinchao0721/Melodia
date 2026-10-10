package com.lin0721.linmusic.desktop.platform.native.linux.sni

import com.lin0721.linmusic.desktop.platform.native.TrayMenuModel
import com.lin0721.linmusic.desktop.platform.native.TrayMenuNode
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

// 托盘菜单模型 → DBusMenu 布局（纯函数，便于单测）。
// id 从 1 起递增、根为 0；只有可点击项登记回调
internal data class BuiltMenu(
    val root: MenuLayoutItem,
    val callbacks: Map<Int, () -> Unit>,
)

internal fun buildMenuLayout(model: TrayMenuModel): BuiltMenu {
    val children = mutableListOf<Variant<*>>()
    val callbacks = mutableMapOf<Int, () -> Unit>()
    var nextId = 1

    // “正在播放”行始终占位（无曲目时用 visible=false 隐藏）：
    // 宿主按 id 复用菜单项，结构变化会让后续所有条目错位（实测 Plasma 会把标签与勾选套到别的行上）
    val header = model.header?.takeIf { it.isNotBlank() }
    children += menuChild(
        MenuLayoutItem(
            nextId++,
            labelProps(header.orEmpty(), enabled = false, visible = header != null),
            emptyArray(),
        )
    )
    children += menuChild(MenuLayoutItem(nextId++, separatorProps(header != null), emptyArray()))

    for (node in model.nodes) {
        when (node) {
            is TrayMenuNode.Action -> {
                callbacks[nextId] = node.onSelect
                children += menuChild(MenuLayoutItem(nextId++, labelProps(node.label, node.enabled), emptyArray()))
            }
            is TrayMenuNode.Toggle -> {
                callbacks[nextId] = node.onSelect
                children += menuChild(MenuLayoutItem(nextId++, toggleProps(node.label, node.checked), emptyArray()))
            }
            TrayMenuNode.Separator -> children += menuChild(MenuLayoutItem(nextId++, separatorProps(), emptyArray()))
        }
    }

    val root = MenuLayoutItem(
        id = 0,
        properties = mapOf(DbusMenu.ITEM_CHILDREN_DISPLAY to Variant("submenu", "s")),
        children = children.toTypedArray(),
    )
    return BuiltMenu(root, callbacks)
}

// DBusMenu 的 label 用下划线声明快捷键助记符，字面下划线需写成两个
internal fun escapeMenuLabel(label: String): String = label.replace("_", "__")

private fun menuChild(item: MenuLayoutItem): Variant<*> = Variant(item, MenuLayoutItem.SIGNATURE)

private fun labelProps(label: String, enabled: Boolean, visible: Boolean = true): Map<String, Variant<*>> = mapOf(
    DbusMenu.ITEM_LABEL to Variant(escapeMenuLabel(label), "s"),
    DbusMenu.ITEM_ENABLED to Variant(enabled, "b"),
    DbusMenu.ITEM_VISIBLE to Variant(visible, "b"),
)

private fun toggleProps(label: String, checked: Boolean): Map<String, Variant<*>> = mapOf(
    DbusMenu.ITEM_LABEL to Variant(escapeMenuLabel(label), "s"),
    DbusMenu.ITEM_ENABLED to Variant(true, "b"),
    DbusMenu.ITEM_VISIBLE to Variant(true, "b"),
    DbusMenu.ITEM_TOGGLE_TYPE to Variant(DbusMenu.TOGGLE_CHECKMARK, "s"),
    DbusMenu.ITEM_TOGGLE_STATE to Variant(if (checked) 1 else 0, "i"),
)

private fun separatorProps(visible: Boolean = true): Map<String, Variant<*>> = buildMap {
    put(DbusMenu.ITEM_TYPE, Variant(DbusMenu.TYPE_SEPARATOR, "s"))
    if (!visible) put(DbusMenu.ITEM_VISIBLE, Variant(false, "b"))
}

// 导出的 com.canonical.dbusmenu 对象：布局按 id 索引，宿主点击时按 id 找到回调。
// 内容更新只推进版本号，由调用方广播 LayoutUpdated 让宿主重新拉取。
internal class SniDbusMenu : DbusMenu {

    @Volatile
    private var revision = UInt32(1)

    @Volatile
    private var built = buildMenuLayout(TrayMenuModel(header = null, nodes = emptyList()))

    @Volatile
    private var itemsById: Map<Int, MenuLayoutItem> = index(built.root)

    fun update(model: TrayMenuModel): UInt32 {
        built = buildMenuLayout(model)
        revision = UInt32(revision.toLong() + 1)
        itemsById = index(built.root)
        return revision
    }

    override fun isRemote(): Boolean = false

    override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): MenuLayoutReply {
        val item = itemsById[parentId] ?: built.root
        val layout = if (recursionDepth == 0) MenuLayoutItem(item.id, item.properties, emptyArray()) else item
        return MenuLayoutReply(revision, layout)
    }

    override fun GetGroupProperties(ids: IntArray, propertyNames: List<String>): Array<MenuGroupProperty> {
        val wanted = if (ids.isEmpty()) itemsById.values else ids.map { itemsById[it] }.filterNotNull()
        return wanted.map { MenuGroupProperty(it.id, it.properties) }.toTypedArray()
    }

    override fun GetProperty(id: Int, name: String): Variant<*> =
        itemsById[id]?.properties?.get(name) ?: Variant("", "s")

    override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
        if (eventId == DbusMenu.EVENT_CLICKED) built.callbacks[id]?.invoke()
    }

    override fun EventGroup(events: Array<MenuEventItem>): IntArray {
        events.forEach { Event(it.id, it.eventId, it.data, it.timestamp) }
        return IntArray(0)
    }

    override fun AboutToShow(id: Int): Boolean = false

    override fun AboutToShowGroup(ids: IntArray): MenuAboutToShowReply =
        MenuAboutToShowReply(IntArray(0), IntArray(0))

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(iface: String, prop: String): A = when (iface) {
        DbusMenu.INTERFACE -> when (prop) {
            DbusMenu.PROP_VERSION -> Variant(UInt32(3), "u") as A
            DbusMenu.PROP_TEXT_DIRECTION -> "ltr" as A
            DbusMenu.PROP_STATUS -> "normal" as A
            DbusMenu.PROP_ICON_THEME_PATH -> Variant(emptyArray<String>(), "as") as A
            else -> null as A
        }
        else -> null as A
    }

    override fun GetAll(iface: String): Map<String, Variant<*>> =
        if (iface != DbusMenu.INTERFACE) {
            emptyMap()
        } else {
            mapOf(
                DbusMenu.PROP_VERSION to Variant(UInt32(3), "u"),
                DbusMenu.PROP_TEXT_DIRECTION to Variant("ltr"),
                DbusMenu.PROP_STATUS to Variant("normal"),
                DbusMenu.PROP_ICON_THEME_PATH to Variant(emptyArray<String>(), "as"),
            )
        }

    override fun <A : Any?> Set(iface: String, prop: String, value: A) = Unit

    // 内容更新信号：宿主收到后重新 GetLayout
    fun layoutUpdatedSignal(): LayoutUpdated = LayoutUpdated(DbusMenu.OBJECT_PATH, revision)

    class LayoutUpdated(objectPath: String, revision: UInt32) : DBusSignal(
        ENDIANESS, null, objectPath, DbusMenu.INTERFACE, "LayoutUpdated", "ui",
        revision, 0,
    )

    private companion object {
        const val ENDIANESS: Byte = 0

        fun index(root: MenuLayoutItem): Map<Int, MenuLayoutItem> {
            val map = mutableMapOf(root.id to root)
            fun walk(item: MenuLayoutItem) {
                item.children.forEach { child ->
                    val node = child.value as? MenuLayoutItem ?: return@forEach
                    map[node.id] = node
                    walk(node)
                }
            }
            walk(root)
            return map
        }
    }
}
