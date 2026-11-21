package com.lin0721.linmusic.core.playlistexternal

import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PlaylistExternalSyncTest {

    private lateinit var dir: File
    private var fakeNow = 1_000_000L
    private lateinit var store: PlaylistExternalTrackStore

    private fun track(id: String, platform: MusicPlatform = MusicPlatform.QQ) = ExternalTrack(
        id = id,
        name = "Song $id",
        artists = "Artist $id",
        platform = platform
    )

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-external-sync-test").toFile()
        fakeNow = 1_000_000L
        store = PlaylistExternalTrackStore(
            dataStore = PreferencesStores.get(File(dir, "playlist_external.preferences_pb")),
            nowMs = { fakeNow }
        )
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun removeAndClearCreateTombstonesAndReAddClearsTombstone() = runBlocking {
        val t1 = track("q1")
        val t2 = track("q2")
        val likedKey = likedExternalKey(10086L)

        fakeNow = 10_000L
        assertEquals(2, store.add(likedKey, listOf(t1, t2)))
        assertEquals(1, store.add(200L, listOf(t1)))

        fakeNow = 20_000L
        store.remove(likedKey, setOf(t1.queueSongId))

        var snap = store.snapshot()
        assertEquals(listOf(t2.resolveKey), snap.entries[likedKey]?.map { it.track.resolveKey })
        assertEquals(1, snap.tombstones.size)
        assertEquals(PlaylistExternalTombstone(likedKey, t1.resolveKey, 20_000L), snap.tombstones.single())

        // 重新加回同一首歌应清掉对应墓碑
        fakeNow = 30_000L
        assertEquals(1, store.add(likedKey, listOf(t1)))
        snap = store.snapshot()
        assertFalse(snap.tombstones.any { it.playlistId == likedKey && it.resolveKey == t1.resolveKey })

        fakeNow = 40_000L
        store.clearPlaylist(200L)
        snap = store.snapshot()
        assertTrue(snap.entries[200L].isNullOrEmpty())
        assertTrue(snap.tombstones.contains(PlaylistExternalTombstone(200L, t1.resolveKey, 40_000L)))
    }

    @Test
    fun mergeResolvesAddVsDeleteByTimestampAndPrunesExpiredTombstones() {
        val t1 = track("s1")
        val t2 = track("s2")
        val t3 = track("s3")
        val now = 100L * 24 * 60 * 60 * 1000

        val local = PlaylistExternalSnapshot(
            entries = mapOf(
                10L to listOf(
                    PlaylistExternalEntry(t1, addedAt = now - 5_000L),
                    PlaylistExternalEntry(t3, addedAt = now - 2_000L)
                )
            ),
            tombstones = listOf(
                // 超过 30 天的过期墓碑应被裁剪
                PlaylistExternalTombstone(10L, "qq:expired", removedAt = now - PLAYLIST_EXTERNAL_TOMBSTONE_TTL_MS - 1L)
            )
        )
        val remote = PlaylistExternalSnapshot(
            entries = mapOf(
                10L to listOf(PlaylistExternalEntry(t2, addedAt = now - 1_000L))
            ),
            tombstones = listOf(
                // 比本地 t1 更晚的删除应胜出
                PlaylistExternalTombstone(10L, t1.resolveKey, removedAt = now - 1_000L),
                // 比本地 t3 更早的删除应败给重新添加
                PlaylistExternalTombstone(10L, t3.resolveKey, removedAt = now - 4_000L)
            )
        )

        val merged = mergePlaylistExternalSnapshots(local, remote, now = now)
        val mergedKeys = merged.entries[10L].orEmpty().map { it.track.resolveKey }
        assertEquals(listOf(t2.resolveKey, t3.resolveKey), mergedKeys)
        assertEquals(listOf(PlaylistExternalTombstone(10L, t1.resolveKey, now - 1_000L)), merged.tombstones)
    }

    @Test
    fun applyMergedRemoteSnapshotIsIdempotentAndReturnsFalseWhenUnchanged() = runBlocking {
        val t1 = track("a1")
        val t2 = track("a2", MusicPlatform.KUWO)
        val likedKey = likedExternalKey(999L)

        fakeNow = 50_000L
        store.add(likedKey, listOf(t1))

        val remote = PlaylistExternalSnapshot(
            entries = mapOf(
                likedKey to listOf(PlaylistExternalEntry(t2, addedAt = 60_000L))
            )
        )

        assertTrue(store.applyMergedRemoteSnapshot(remote, now = 60_000L))
        assertEquals(listOf(t2.resolveKey, t1.resolveKey), store.entriesOf(likedKey).first().map { it.track.resolveKey })

        // 再次应用完全相同的远端快照应返回 false，防止回推循环
        assertFalse(store.applyMergedRemoteSnapshot(remote, now = 60_000L))
        assertFalse(store.applyMergedRemoteSnapshot(store.snapshot(), now = 60_000L))
    }
}
