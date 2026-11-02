package com.lin0721.linmusic.desktop.ui.library

import com.lin0721.linmusic.desktop.data.local.LOCAL_ALL_TRACKS_ID
import com.lin0721.linmusic.desktop.data.local.LOCAL_DOWNLOADS_ID
import com.lin0721.linmusic.desktop.data.local.LocalPlaylistSummary
import com.lin0721.linmusic.feature.library.ui.LibraryFilter
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.library.ui.LibrarySortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryItemsTest {

    private fun cloud(id: String, title: String, pinned: Boolean = false, played: Long = 0, type: LibraryItemType = LibraryItemType.PLAYLIST) =
        LibraryItem(id = id, title = title, subtitle = "", coverUrl = "", type = type, isPinned = pinned, lastPlayTime = played)

    private fun local(id: Long, name: String, count: Int = 3, pinned: Boolean = false, played: Long = 0, created: Long = id) =
        LocalPlaylistSummary(id, name, count, null, pinned, played, created)

    private val summaries = listOf(
        local(LOCAL_ALL_TRACKS_ID, "全部本地歌曲", created = 0),
        local(LOCAL_DOWNLOADS_ID, "已下载", count = 0, created = 0),
        local(100, "本地B", created = 100),
        local(200, "本地A", pinned = true, created = 200)
    )

    @Test
    fun localChipShowsOnlyLocalIncludingBuiltins() {
        val items = mergeLibraryItems(listOf(cloud("1", "云")), summaries, null, localOnly = true, query = "", sort = LibrarySortOrder.CUSTOM)
        assertTrue(items.all { it.isLocal })
        assertEquals(listOf(200L, LOCAL_ALL_TRACKS_ID, LOCAL_DOWNLOADS_ID, 100L), items.map { it.localPlaylistId() })
    }

    @Test
    fun playlistFilterMixesLocalWithoutSummaryOrEmptyDownloads() {
        val items = mergeLibraryItems(listOf(cloud("1", "云")), summaries, LibraryFilter.PLAYLIST, false, "", LibrarySortOrder.CUSTOM)
        assertEquals(listOf("local:200", "1", "local:100"), items.map { it.id })
    }

    @Test
    fun albumFilterExcludesLocal() {
        val album = cloud("9", "专辑", type = LibraryItemType.ALBUM)
        val items = mergeLibraryItems(listOf(album), summaries, LibraryFilter.ALBUM, false, "", LibrarySortOrder.NAME)
        assertEquals(listOf("9"), items.map { it.id })
    }

    @Test
    fun recentlyPlayedInterleavesByPlayTime() {
        val cloudItems = listOf(cloud("1", "新", played = 300), cloud("2", "旧", played = 100))
        val locals = listOf(local(100, "中间", played = 200))
        val items = mergeLibraryItems(cloudItems, locals, null, false, "", LibrarySortOrder.RECENTLY_PLAYED)
        assertEquals(listOf("1", "local:100", "2"), items.map { it.id })
    }

    @Test
    fun queryFiltersLocalTitles() {
        val items = mergeLibraryItems(emptyList(), summaries, null, true, "本地A", LibrarySortOrder.CUSTOM)
        assertEquals(listOf("local:200"), items.map { it.id })
    }
}
