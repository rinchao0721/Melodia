package com.lin0721.linmusic.feature.library.data

import android.content.Context

// 沿用原 library_prefs 文件与键名，升级后置顶和排序不丢
class AndroidLibraryPreferences(context: Context) : LibraryPreferences {

    private val prefs = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    override fun pinnedIds(uid: Long): Set<String> {
        val key = accountKey(KEY_PINNED_IDS, uid)
        if (!prefs.contains(key)) claimLegacy(uid)
        return prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    }

    override fun setPinnedIds(uid: Long, ids: Set<String>) {
        prefs.edit().putStringSet(accountKey(KEY_PINNED_IDS, uid), ids).apply()
    }

    override fun customPlaylistOrder(uid: Long): List<String> {
        val key = accountKey(KEY_CUSTOM_ORDER, uid)
        if (!prefs.contains(key)) claimLegacy(uid)
        val raw = prefs.getString(key, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }
    }

    override fun setCustomPlaylistOrder(uid: Long, order: List<String>) {
        prefs.edit().putString(accountKey(KEY_CUSTOM_ORDER, uid), order.joinToString(",")).apply()
    }

    override fun sortOrderName(): String? = prefs.getString(KEY_SORT_ORDER, null)

    override fun setSortOrderName(name: String) {
        prefs.edit().putString(KEY_SORT_ORDER, name).apply()
    }

    override fun isGridView(): Boolean = prefs.getBoolean(KEY_GRID_VIEW, false)

    override fun setGridView(isGrid: Boolean) {
        prefs.edit().putBoolean(KEY_GRID_VIEW, isGrid).apply()
    }

    // 旧版全局置顶与顺序归首个读取的账户（启动时即迁移出的那个账户），领取后删除旧键
    @Synchronized
    private fun claimLegacy(uid: Long) {
        if (!prefs.contains(KEY_PINNED_IDS) && !prefs.contains(KEY_CUSTOM_ORDER)) return
        val editor = prefs.edit()
        prefs.getStringSet(KEY_PINNED_IDS, null)?.let {
            editor.putStringSet(accountKey(KEY_PINNED_IDS, uid), it.toSet())
        }
        prefs.getString(KEY_CUSTOM_ORDER, null)?.let {
            editor.putString(accountKey(KEY_CUSTOM_ORDER, uid), it)
        }
        editor.remove(KEY_PINNED_IDS).remove(KEY_CUSTOM_ORDER).commit()
    }

    private fun accountKey(base: String, uid: Long) = "${base}_$uid"

    private companion object {
        const val KEY_PINNED_IDS = "pinned_ids"
        const val KEY_CUSTOM_ORDER = "custom_playlist_order"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_GRID_VIEW = "is_grid_view"
    }
}
