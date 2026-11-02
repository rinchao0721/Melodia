package com.lin0721.linmusic.core.playlistexternal

import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistExternalTracksTest {

    private fun external(id: String, platform: MusicPlatform = MusicPlatform.KUGOU) =
        ExternalTrack(id = id, name = "外部$id", artists = "歌手", platform = platform)

    private fun netease(id: Long) = Track(id = id, name = "网易$id")

    @Test
    fun addDeduplicatesByResolveKeyAndPrependsNewest() = runBlocking {
        val store = PlaylistExternalTrackStore(InMemoryPreferencesStore())

        assertEquals(1, store.add(10L, listOf(external("a")), now = 100L))
        assertEquals(1, store.add(10L, listOf(external("a"), external("b")), now = 200L))
        // 同 id 不同平台不算重复
        assertEquals(1, store.add(10L, listOf(external("a", MusicPlatform.KUWO)), now = 300L))

        val entries = store.entriesOf(10L).first()
        assertEquals(listOf("kuwo:a", "kugou:b", "kugou:a"), entries.map { it.track.resolveKey })
        assertEquals(listOf(300L, 200L, 100L), entries.map { it.addedAt })
    }

    @Test
    fun removeAndClearOnlyAffectTargetPlaylist() = runBlocking {
        val store = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val a = external("a")
        val b = external("b")
        store.add(10L, listOf(a, b))
        store.add(11L, listOf(a))

        store.remove(10L, setOf(a.queueSongId))
        assertEquals(listOf(b.resolveKey), store.entriesOf(10L).first().map { it.track.resolveKey })
        assertTrue(store.contains(11L, a))

        store.clearPlaylist(11L)
        assertTrue(store.entriesOf(11L).first().isEmpty())
        assertEquals(1, store.entriesOf(10L).first().size)
    }

    @Test
    fun mergeInsertsByAddedTimeAndHoldsBackUntilLoaded() {
        // 网易云歌单新加的在前：at 递减
        val tracks = listOf(netease(1), netease(2), netease(3))
        val trackIds = listOf(PlaylistTrackId(1, 300), PlaylistTrackId(2, 200), PlaylistTrackId(3, 100), PlaylistTrackId(4, 50))
        val newest = PlaylistExternalEntry(external("x"), addedAt = 400)
        val middle = PlaylistExternalEntry(external("y"), addedAt = 150)
        val oldest = PlaylistExternalEntry(external("z"), addedAt = 10)

        val partial = mergeExternalTracks(tracks, trackIds, listOf(oldest, newest, middle), fullyLoaded = false)
        assertEquals(
            listOf(newest.track.queueSongId, 1L, 2L, middle.track.queueSongId, 3L),
            partial.map { it.id }
        )

        val full = mergeExternalTracks(tracks + netease(4), trackIds, listOf(oldest, newest, middle), fullyLoaded = true)
        assertEquals(oldest.track.queueSongId, full.last().id)
        assertEquals(7, full.size)
    }

    @Test
    fun externalTrackMapsToNegativeIdTrackWithoutNeteaseLinks() {
        val track = ExternalTrack(
            id = "m", name = "歌", artists = "甲 / 乙", artistList = listOf("甲", "乙"),
            albumName = "专辑", durationMs = 1000, coverUrl = "http://c", platform = MusicPlatform.QQ
        ).toPlaylistTrack()

        assertTrue(track.id < 0L)
        assertEquals(listOf("甲", "乙"), track.ar.map { it.name })
        assertTrue(track.ar.all { it.id == 0L })
        assertEquals(0L, track.al.id)
        assertEquals("http://c", track.al.picUrl)
    }
}
