package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.library.data.LibraryPreferences
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

private const val TAG = "DesktopLibraryPrefs"

// java.util.prefs 在 Windows 落到注册表 HKCU\Software\JavaSoft\Prefs
class DesktopLibraryPreferences : LibraryPreferences {

    private val prefs: Preferences = Preferences.userRoot().node("melodia/library")

    override fun pinnedIds(uid: Long): Set<String> {
        val key = accountKey(KEY_PINNED_IDS, uid)
        if (prefs.get(key, null) == null) claimLegacy(uid)
        return splitList(prefs.get(key, "")).toSet()
    }

    override fun setPinnedIds(uid: Long, ids: Set<String>) =
        put(accountKey(KEY_PINNED_IDS, uid), ids.joinToString(SEPARATOR))

    override fun customPlaylistOrder(uid: Long): List<String> {
        val key = accountKey(KEY_CUSTOM_ORDER, uid)
        if (prefs.get(key, null) == null) claimLegacy(uid)
        return splitList(prefs.get(key, ""))
    }

    override fun setCustomPlaylistOrder(uid: Long, order: List<String>) =
        put(accountKey(KEY_CUSTOM_ORDER, uid), order.joinToString(SEPARATOR))

    override fun sortOrderName(): String? = prefs.get(KEY_SORT_ORDER, null)

    override fun setSortOrderName(name: String) = put(KEY_SORT_ORDER, name)

    override fun isGridView(): Boolean = prefs.getBoolean(KEY_GRID_VIEW, false)

    override fun setGridView(isGrid: Boolean) {
        prefs.putBoolean(KEY_GRID_VIEW, isGrid)
        flush()
    }

    // 旧版全局置顶与顺序归首个读取的账户（启动时即迁移出的那个账户），领取后删除旧键
    @Synchronized
    private fun claimLegacy(uid: Long) {
        val legacyPinned = prefs.get(KEY_PINNED_IDS, null)
        val legacyOrder = prefs.get(KEY_CUSTOM_ORDER, null)
        if (legacyPinned == null && legacyOrder == null) return
        legacyPinned?.let { prefs.put(accountKey(KEY_PINNED_IDS, uid), it) }
        legacyOrder?.let { prefs.put(accountKey(KEY_CUSTOM_ORDER, uid), it) }
        prefs.remove(KEY_PINNED_IDS)
        prefs.remove(KEY_CUSTOM_ORDER)
        flush()
    }

    private fun accountKey(base: String, uid: Long) = "${base}_$uid"

    private fun put(key: String, value: String) {
        prefs.put(key, value)
        flush()
    }

    private fun flush() {
        try {
            prefs.flush()
        } catch (e: BackingStoreException) {
            AppLogger.w(TAG, "音乐库偏好落盘失败", e)
        }
    }

    private fun splitList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split(SEPARATOR).filter { it.isNotBlank() }

    private companion object {
        const val SEPARATOR = ","
        const val KEY_PINNED_IDS = "pinned_ids"
        const val KEY_CUSTOM_ORDER = "custom_playlist_order"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_GRID_VIEW = "is_grid_view"
    }
}
