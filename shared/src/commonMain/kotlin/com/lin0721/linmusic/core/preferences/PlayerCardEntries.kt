package com.lin0721.linmusic.core.preferences

data class PluginCardRef(val key: String, val title: String)

// 卡片编辑器里的一行：内置卡片 card 非空，插件卡片 card 为空
data class PlayerCardEntry(
    val key: String,
    val title: String,
    val visible: Boolean,
    val card: FullPlayerCard? = null
)

// 插件卡片与内置卡片混排在同一个配置串里，token 为 plugin/<插件 id>/<页面 id>；
// FullPlayerCardLayout.decode 会跳过这些 token，所以内置卡片的读取与旧配置都不受影响
object PlayerCardEntries {

    private const val PREFIX = "plugin/"

    fun pluginKey(pluginId: String, pageId: String): String = "$PREFIX$pluginId/$pageId"

    fun merge(raw: String?, plugins: List<PluginCardRef>): List<PlayerCardEntry> {
        val builtIn = FullPlayerCardLayout.decode(raw).associateBy { it.card.key }
        val available = plugins.associateBy { it.key }
        val entries = LinkedHashMap<String, PlayerCardEntry>()
        tokens(raw).forEach { (key, visible) ->
            val setting = builtIn[key]
            val plugin = available[key]
            when {
                setting != null -> entries.putIfAbsent(key, setting.toEntry())
                plugin != null -> entries.putIfAbsent(key, PlayerCardEntry(key, plugin.title, visible))
            }
        }
        builtIn.values.forEach { entries.putIfAbsent(it.card.key, it.toEntry()) }
        plugins.forEach { entries.putIfAbsent(it.key, PlayerCardEntry(it.key, it.title, true)) }
        return entries.values.toList()
    }

    // 暂时停用的插件卡片不在 entries 里，保留它们的记录，重新启用后设置还在
    fun encode(entries: List<PlayerCardEntry>, previousRaw: String?): String {
        val current = entries.map { "${it.key}:${if (it.visible) 1 else 0}" }
        val keys = entries.mapTo(HashSet()) { it.key }
        return (current + keptPluginTokens(previousRaw, keys)).joinToString(",")
    }

    fun withPluginTokens(encoded: String, previousRaw: String?): String =
        (listOf(encoded) + keptPluginTokens(previousRaw, emptySet())).joinToString(",")

    fun defaults(entries: List<PlayerCardEntry>): List<PlayerCardEntry> {
        val builtIn = FullPlayerCardLayout.DEFAULT.map { it.toEntry() }
        return builtIn + entries.filter { it.card == null }.map { it.copy(visible = true) }
    }

    // 编辑器只列出当前端支持的卡片，其余保持原位
    fun mergeOrdered(
        full: List<PlayerCardEntry>,
        ordered: List<PlayerCardEntry>,
        isEditable: (PlayerCardEntry) -> Boolean
    ): List<PlayerCardEntry> {
        if (ordered.size != full.count(isEditable)) return full
        val iterator = ordered.iterator()
        return full.map { if (isEditable(it)) iterator.next() else it }
    }

    fun setVisible(full: List<PlayerCardEntry>, key: String, visible: Boolean): List<PlayerCardEntry> =
        full.map { if (it.key == key) it.copy(visible = visible) else it }

    // 把内置卡片的显示结果与插件卡片按用户顺序合起来；前面还有内置卡片没出结论时，插件卡片先不出现，避免位置跳动
    fun ordered(
        entries: List<PlayerCardEntry>,
        shown: List<FullPlayerCard>,
        allSettled: Boolean
    ): List<PlayerCardEntry> {
        val result = mutableListOf<PlayerCardEntry>()
        var blocked = false
        for (entry in entries) {
            if (!entry.visible) continue
            val card = entry.card
            when {
                card == null -> if (!blocked) result += entry
                card in shown -> result += entry
                !allSettled -> blocked = true
            }
        }
        return result
    }

    private fun FullPlayerCardSetting.toEntry() = PlayerCardEntry(card.key, card.title, visible, card)

    private fun tokens(raw: String?): List<Pair<String, Boolean>> =
        raw.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { token ->
            val parts = token.split(":", limit = 2)
            parts[0].trim() to (parts.getOrNull(1)?.trim() != "0")
        }

    private fun keptPluginTokens(raw: String?, except: Set<String>): List<String> =
        tokens(raw).filter { (key, _) -> key.startsWith(PREFIX) && key !in except }
            .map { (key, visible) -> "$key:${if (visible) 1 else 0}" }
}
