package com.lin0721.linmusic.desktop.ui.library

import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.buildLocalLibraryIndex
import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.desktop.data.local.LOCAL_ALL_TRACKS_ID
import com.lin0721.linmusic.desktop.data.local.LOCAL_DOWNLOADS_ID
import com.lin0721.linmusic.desktop.data.local.LOCAL_LIKED_ID
import com.lin0721.linmusic.desktop.data.local.LocalPlaylistSummary
import com.lin0721.linmusic.desktop.ui.local.LocalLibraryFilter
import com.lin0721.linmusic.desktop.ui.local.LocalLibrarySortOrder
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

    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String,
        folder: String,
        addedMs: Long,
        source: LocalTrackSource = LocalTrackSource.EXTERNAL,
        hasCover: Boolean = false
    ) = LocalTrack(
        location = "$folder/$title.mp3",
        mediaStoreId = id,
        songId = null,
        title = title,
        artist = artist,
        album = album,
        durationMs = 180_000L,
        sizeBytes = 1024L,
        path = "$folder/$title.mp3",
        dateAddedMs = addedMs,
        dateModifiedMs = addedMs,
        source = source,
        hasEmbeddedCover = hasCover,
        folder = folder
    )

    private val localTracks = listOf(
        track(1, "晴天", "周杰伦", "叶惠美", "/music/jay", addedMs = 100L, hasCover = true),
        track(2, "夜曲", "周杰伦", "十一月的萧邦", "/music/jay", addedMs = 300L, source = LocalTrackSource.DOWNLOAD, hasCover = true),
        track(3, "十年", "陈奕迅", "黑白灰", "/music/eason", addedMs = 200L)
    )

    private val localIndex = buildLocalLibraryIndex(localTracks)

    private fun coverOf(track: LocalTrack): String? =
        if (track.hasEmbeddedCover) "file:///${track.location}.jpg" else null

    private val localModeSummaries = summaries.map {
        if (it.id < 0L) it.copy(isPinned = true) else it
    }

    @Test
    fun localModeKeepsFixedPinnedTopThreeRegardlessOfFilter() {
        LocalLibraryFilter.entries.forEach { filter ->
            val items = buildLocalModeLibraryItems(
                summaries = localModeSummaries,
                index = localIndex,
                downloadTrackCount = 1,
                downloadCoverUri = "file:///dl.jpg",
                coverForTrack = ::coverOf,
                filter = filter,
                query = "",
                sort = LocalLibrarySortOrder.CUSTOM
            )
            assertEquals(LocalPlaylist.LIKED_PLAYLIST_NAME, items[0].title)
            assertEquals("local:$LOCAL_LIKED_ID", items[0].id)
            assertTrue(items[0].isPinned)
            assertTrue(items[0].isLikedSongs)
            assertEquals("全部歌曲", items[1].title)
            assertEquals("local:$LOCAL_ALL_TRACKS_ID", items[1].id)
            assertTrue(items[1].isPinned)
            assertEquals("已下载", items[2].title)
            assertEquals("local:$LOCAL_DOWNLOADS_ID", items[2].id)
            assertTrue(items[2].isPinned)
        }
    }

    @Test
    fun localModeFiltersByCategory() {
        val albums = buildLocalModeLibraryItems(
            localModeSummaries, localIndex, 1, null, ::coverOf,
            LocalLibraryFilter.ALBUM, "", LocalLibrarySortOrder.NAME
        )
        assertEquals(
            listOf("local:$LOCAL_LIKED_ID", "local:$LOCAL_ALL_TRACKS_ID", "local:$LOCAL_DOWNLOADS_ID", "local_album:黑白灰|陈奕迅", "local_album:十一月的萧邦|周杰伦", "local_album:叶惠美|周杰伦"),
            albums.map { it.id }
        )

        val artists = buildLocalModeLibraryItems(
            localModeSummaries, localIndex, 1, null, ::coverOf,
            LocalLibraryFilter.ARTIST, "", LocalLibrarySortOrder.NAME
        )
        assertEquals(
            listOf("local:$LOCAL_LIKED_ID", "local:$LOCAL_ALL_TRACKS_ID", "local:$LOCAL_DOWNLOADS_ID", "local_artist:陈奕迅", "local_artist:周杰伦"),
            artists.map { it.id }
        )
        assertTrue(artists.drop(3).all { it.type == LibraryItemType.ARTIST })

        val folders = buildLocalModeLibraryItems(
            localModeSummaries, localIndex, 1, null, ::coverOf,
            LocalLibraryFilter.FOLDER, "", LocalLibrarySortOrder.NAME
        )
        assertEquals(
            listOf("local:$LOCAL_LIKED_ID", "local:$LOCAL_ALL_TRACKS_ID", "local:$LOCAL_DOWNLOADS_ID", "local_folder:/music/eason", "local_folder:/music/jay"),
            folders.map { it.id }
        )
    }

    @Test
    fun localModeSortsByRecentlyAddedAndSearchFilters() {
        val recent = buildLocalModeLibraryItems(
            localModeSummaries, localIndex, 1, null, ::coverOf,
            LocalLibraryFilter.ALBUM, "", LocalLibrarySortOrder.RECENTLY_ADDED
        )
        assertEquals(
            listOf("十一月的萧邦", "黑白灰", "叶惠美"),
            recent.drop(3).map { it.title }
        )

        val searched = buildLocalModeLibraryItems(
            localModeSummaries, localIndex, 1, null, ::coverOf,
            null, "周杰伦", LocalLibrarySortOrder.NAME
        )
        assertEquals(listOf("local_artist:周杰伦"), searched.map { it.id })
    }

    @Test
    fun localModePinnedUserPlaylistStaysBelowFixedTopThree() {
        val withLiked = localModeSummaries + LocalPlaylistSummary(
            id = 300L,
            name = LocalPlaylist.LIKED_PLAYLIST_NAME,
            trackCount = 2,
            coverUri = "file:///liked.jpg",
            isPinned = true,
            lastPlayedAt = 0L,
            createdAt = 3000L,
            systemKind = LocalPlaylist.SYSTEM_KIND_LIKED
        )
        val items = buildLocalModeLibraryItems(
            withLiked, localIndex, 1, null, ::coverOf,
            LocalLibraryFilter.PLAYLIST, "", LocalLibrarySortOrder.CUSTOM
        )
        assertEquals(
            listOf("local:300", "local:$LOCAL_ALL_TRACKS_ID", "local:$LOCAL_DOWNLOADS_ID", "local:200", "local:100"),
            items.map { it.id }
        )
        assertTrue(items.first().isLikedSongs)
        assertTrue(items.first().isPinned)
    }

    @Test
    fun localModeUnpinnedFixedItemsMixIntoRegularListAndRespectFilter() {
        val mixed = listOf(
            LocalPlaylistSummary(LOCAL_ALL_TRACKS_ID, "全部歌曲", 3, null, isPinned = false, lastPlayedAt = 0L, createdAt = 0L),
            LocalPlaylistSummary(LOCAL_DOWNLOADS_ID, "已下载", 1, null, isPinned = true, lastPlayedAt = 0L, createdAt = 0L),
            LocalPlaylistSummary(300L, LocalPlaylist.LIKED_PLAYLIST_NAME, 2, null, isPinned = false, lastPlayedAt = 0L, createdAt = 3000L, systemKind = LocalPlaylist.SYSTEM_KIND_LIKED),
            LocalPlaylistSummary(100L, "开车听", 2, null, isPinned = false, lastPlayedAt = 0L, createdAt = 1000L)
        )
        // 「全部歌曲」与「我喜欢的音乐」取消置顶后，仅「已下载」留在顶部置顶区
        val allItems = buildLocalModeLibraryItems(
            mixed, localIndex, 1, null, ::coverOf,
            null, "", LocalLibrarySortOrder.NAME
        )
        assertEquals("local:$LOCAL_DOWNLOADS_ID", allItems.first().id)
        assertTrue(allItems.drop(1).any { it.id == "local:$LOCAL_ALL_TRACKS_ID" && !it.isPinned })
        assertTrue(allItems.drop(1).any { it.id == "local:300" && !it.isPinned })

        // 切到专辑筛选时，取消置顶的固定项也随歌单类型被筛选掉
        val albumItems = buildLocalModeLibraryItems(
            mixed, localIndex, 1, null, ::coverOf,
            LocalLibraryFilter.ALBUM, "", LocalLibrarySortOrder.NAME
        )
        assertEquals("local:$LOCAL_DOWNLOADS_ID", albumItems.first().id)
        assertTrue(albumItems.none { it.id == "local:$LOCAL_ALL_TRACKS_ID" || it.id == "local:300" })
    }

    @Test
    fun tracksForLocalCollectionResolvesAlbumArtistAndFolder() {
        val albumItem = LibraryItem("local_album:叶惠美|周杰伦", "叶惠美", "", "", LibraryItemType.ALBUM)
        val artistItem = LibraryItem("local_artist:周杰伦", "周杰伦", "", "", LibraryItemType.ARTIST)
        val folderItem = LibraryItem("local_folder:/music/eason", "eason", "", "", LibraryItemType.PLAYLIST)

        assertEquals(listOf("晴天"), tracksForLocalCollection(albumItem, localIndex).map { it.title })
        assertEquals(listOf("夜曲", "晴天"), tracksForLocalCollection(artistItem, localIndex).map { it.title })
        assertEquals(listOf("十年"), tracksForLocalCollection(folderItem, localIndex).map { it.title })
    }
}
