package com.lin0721.linmusic.core.playlistexternal

import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistUnifiedExternalTracksTest {

    private fun kugouTrack(hash: String, name: String = "酷狗-$hash", fileId: Long = 0L) = UnifiedTrack(
        id = TrackId.kugou(hash),
        name = name,
        artists = listOf(UnifiedArtist(name = "周杰伦")),
        artistNames = "周杰伦",
        album = UnifiedAlbum(id = AlbumId.kugou("8801"), name = "叶惠美"),
        durationMs = 240_000L,
        kugouMeta = KugouTrackMeta(
            hash = hash,
            mixSongId = 91001L,
            albumAudioId = 32001L,
            albumId = "8801",
            playlistFileId = fileId.takeIf { it > 0L }
        )
    )

    private fun neteaseTrack(id: Long, name: String = "网易-$id") = UnifiedTrack(
        id = TrackId.netease(id),
        name = name,
        artists = listOf(UnifiedArtist(name = "林俊杰")),
        artistNames = "林俊杰",
        durationMs = 220_000L
    )

    @Test
    fun `支持按 PlaylistId 存储与去重 UnifiedTrack 并完整保留酷狗扩展元数据`() = runBlocking {
        val store = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val neteasePlaylist = PlaylistId.netease(1001L)
        val kugouPlaylist = PlaylistId.kugou("collection_3_8899_1_0")

        val addedNetease = store.add(
            neteasePlaylist,
            listOf(kugouTrack("AABB1122", fileId = 55L), kugouTrack("aabb1122")),
            now = 500L
        )
        assertEquals(1, addedNetease)

        val addedKugou = store.add(kugouPlaylist, listOf(neteaseTrack(2002L)), now = 600L)
        assertEquals(1, addedKugou)

        val entriesN = store.entriesOf(neteasePlaylist).first()
        assertEquals(1, entriesN.size)
        assertEquals("kugou:aabb1122", entriesN.first().track.id.canonical)
        assertEquals(91001L, entriesN.first().track.kugouMeta?.mixSongId)
        assertEquals(55L, entriesN.first().track.kugouMeta?.playlistFileId)
        assertTrue(store.contains(neteasePlaylist, TrackId.kugou("AABB1122")))

        val entriesK = store.entriesOf(kugouPlaylist).first()
        assertEquals(listOf(TrackId.netease(2002L)), entriesK.map { it.track.id })
    }

    @Test
    fun `旧版 Long 存储与新版 Unified 存储兼容读取且删除不复活`() = runBlocking {
        val store = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val legacyExt = ExternalTrack(id = "LEGACY_HASH", name = "旧版外部曲", artists = "歌手A", platform = MusicPlatform.KUGOU)
        store.add(10L, listOf(legacyExt), now = 100L)

        val pid = PlaylistId.netease(10L)
        assertEquals(1, store.add(pid, listOf(kugouTrack("NEW_HASH")), now = 200L))
        // 同 hash 即使平台大小写不同也应被旧版记录识别为重复
        assertEquals(0, store.add(pid, listOf(kugouTrack("legacy_hash")), now = 300L))

        val combined = store.entriesOf(pid).first()
        assertEquals(listOf("kugou:new_hash", "kugou:legacy_hash"), combined.map { it.track.id.canonical })

        // 删除旧版和新版各自条目后，两处均不残留
        store.remove(pid, setOf(TrackId.kugou("legacy_hash"), TrackId.kugou("new_hash")))
        assertFalse(store.contains(pid, TrackId.kugou("legacy_hash")))
        assertTrue(store.entriesOf(pid).first().isEmpty())
        assertTrue(store.entriesOf(10L).first().isEmpty())
    }

    @Test
    fun `clearPlaylist 同时清理新版与旧版同歌单数据`() = runBlocking {
        val store = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val pid = PlaylistId.netease(88L)
        store.add(88L, listOf(ExternalTrack(id = "x", name = "X", artists = "A", platform = MusicPlatform.QQ)), now = 10L)
        store.add(pid, listOf(kugouTrack("y")), now = 20L)

        store.clearPlaylist(pid)
        assertTrue(store.entriesOf(pid).first().isEmpty())
        assertTrue(store.entriesOf(88L).first().isEmpty())
    }

    @Test
    fun `mergeUnifiedPlaylistTracks 按时间戳插队并去重已存在曲目`() {
        val base1 = neteaseTrack(1L)
        val base2 = neteaseTrack(2L)
        val existingKugou = kugouTrack("dup001")
        val addedAt = mapOf(
            base1.id to 300L,
            base2.id to 100L,
            existingKugou.id to 50L
        )
        val entries = listOf(
            PlaylistUnifiedEntry(kugouTrack("oldest"), addedAt = 10L),
            PlaylistUnifiedEntry(kugouTrack("newest"), addedAt = 400L),
            PlaylistUnifiedEntry(kugouTrack("middle"), addedAt = 200L),
            PlaylistUnifiedEntry(existingKugou, addedAt = 500L)
        )

        val partial = mergeUnifiedPlaylistTracks(
            baseTracks = listOf(base1, base2, existingKugou),
            trackAddedAt = addedAt,
            entries = entries,
            fullyLoaded = false
        )
        assertEquals(
            listOf("kugou:newest", "netease:1", "kugou:middle", "netease:2", "kugou:dup001"),
            partial.map { it.id.canonical }
        )

        val full = mergeUnifiedPlaylistTracks(
            baseTracks = listOf(base1, base2, existingKugou),
            trackAddedAt = addedAt,
            entries = entries,
            fullyLoaded = true
        )
        assertEquals("kugou:oldest", full.last().id.canonical)
        assertEquals(6, full.size)
    }
}
