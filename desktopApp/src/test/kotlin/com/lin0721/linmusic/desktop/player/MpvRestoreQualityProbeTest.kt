package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MpvRestoreQualityProbeTest {

    private fun neteaseItem(
        songId: Long = 1001L,
        localUri: String? = null,
        radioId: Long = 0L,
        external: ExternalTrack? = null
    ) = QueueItem(
        songId = songId,
        title = "Song",
        artist = "Artist",
        coverUrl = "",
        localUri = localUri,
        radioId = radioId,
        external = external
    )

    @Test
    fun isRestorableNeteaseTrackFiltersOutLocalExternalAndPodcast() {
        assertTrue(isRestorableNeteaseTrack(neteaseItem(), null))
        assertTrue(isRestorableNeteaseTrack(neteaseItem(), PlaybackController.CONTEXT_PERSONAL_FM))
        assertTrue(isRestorableNeteaseTrack(neteaseItem(), PlaybackController.CONTEXT_INTELLIGENCE))

        assertFalse(isRestorableNeteaseTrack(null, null))
        assertFalse(isRestorableNeteaseTrack(neteaseItem(songId = -1L), null))
        assertFalse(isRestorableNeteaseTrack(neteaseItem(songId = 0L), null))
        assertFalse(isRestorableNeteaseTrack(neteaseItem(localUri = "D:/music/a.flac"), null))
        assertFalse(isRestorableNeteaseTrack(neteaseItem(radioId = 99L), null))
        assertFalse(isRestorableNeteaseTrack(neteaseItem(), PlaybackController.CONTEXT_PODCAST))
        assertFalse(
            isRestorableNeteaseTrack(
                neteaseItem(external = ExternalTrack(id = "1", name = "S", artists = "A", platform = MusicPlatform.QQ)),
                null
            )
        )
    }

    @Test
    fun shouldApplyRestoredLevelRequiresSameUnloadedTrack() {
        assertTrue(
            shouldApplyRestoredLevel(
                restoredSongId = 1001L,
                currentMediaId = "1001",
                loadedSongId = null,
                isPlaybackLoading = false
            )
        )
        // 已切歌或清空队列
        assertFalse(shouldApplyRestoredLevel(1001L, "1002", null, false))
        assertFalse(shouldApplyRestoredLevel(1001L, null, null, false))
        // 已开始播放或载入引擎，由 playIndex 接管
        assertFalse(shouldApplyRestoredLevel(1001L, "1001", 1001L, false))
        assertFalse(shouldApplyRestoredLevel(1001L, "1001", null, true))
    }

    @Test
    fun findOfflineNeteaseMatchFollowsDownloadThenCacheOrder() = runBlocking {
        val item = neteaseItem(1001L)
        var wifiChecked = false
        var cacheChecked = false

        val downloaded = findOfflineNeteaseMatch(
            item = item,
            localAudioOf = { "D:/download/1001.flac" },
            wifiQuality = {
                wifiChecked = true
                "lossless"
            },
            cachedFileOf = { _, _ ->
                cacheChecked = true
                File("cache.mka")
            }
        )
        assertEquals("D:/download/1001.flac", downloaded.path)
        assertFalse(wifiChecked)
        assertFalse(cacheChecked)

        val cached = findOfflineNeteaseMatch(
            item = item,
            localAudioOf = { null },
            wifiQuality = { "hires" },
            cachedFileOf = { id, level ->
                assertEquals(1001L, id)
                assertEquals("hires", level)
                File("1001_hires.mka")
            }
        )
        assertTrue(cached.path?.endsWith("1001_hires.mka") == true)
        assertEquals("hires", cached.preferredLevel)

        val miss = findOfflineNeteaseMatch(
            item = item,
            localAudioOf = { null },
            wifiQuality = { "exhigh" },
            cachedFileOf = { _, _ -> null }
        )
        assertNull(miss.path)
        assertEquals("exhigh", miss.preferredLevel)
    }

    @Test
    fun probeRestoredDeclaredLevelRespectsCacheFailureAndTrackSwitch() = runBlocking {
        val item = neteaseItem(1001L)

        // 1. 本地/缓存命中时不请求接口
        var fetchCalls = 0
        var resolved: String? = null
        probeRestoredDeclaredLevel(
            item = item,
            playContext = null,
            findOffline = { OfflineNeteaseMatch("D:/cache/1001.mka", "lossless") },
            fetchPlaybackInfo = {
                fetchCalls++
                Result.success(SongPlaybackInfo("http://example/1.flac", level = "lossless"))
            },
            canApply = { true },
            onLevelResolved = { resolved = it }
        )
        assertEquals(0, fetchCalls)
        assertNull(resolved)

        // 2. 非网易歌曲不查本地也不查接口
        var offlineLookups = 0
        probeRestoredDeclaredLevel(
            item = neteaseItem(songId = -10L),
            playContext = null,
            findOffline = {
                offlineLookups++
                OfflineNeteaseMatch(null, "lossless")
            },
            fetchPlaybackInfo = {
                fetchCalls++
                Result.success(SongPlaybackInfo("http://example/1.flac", level = "lossless"))
            },
            canApply = { true },
            onLevelResolved = { resolved = it }
        )
        assertEquals(0, offlineLookups)
        assertEquals(0, fetchCalls)

        // 3. 在线网易曲目只请求一次并回填 declaredLevel
        probeRestoredDeclaredLevel(
            item = item,
            playContext = null,
            findOffline = { OfflineNeteaseMatch(null, "hires") },
            fetchPlaybackInfo = {
                fetchCalls++
                Result.success(SongPlaybackInfo("http://example/1.flac", level = "hires"))
            },
            canApply = { true },
            onLevelResolved = { resolved = it }
        )
        assertEquals(1, fetchCalls)
        assertEquals("hires", resolved)

        // 4. 请求失败或返回空档位时静默忽略
        resolved = null
        probeRestoredDeclaredLevel(
            item = item,
            playContext = null,
            findOffline = { OfflineNeteaseMatch(null, "hires") },
            fetchPlaybackInfo = { Result.failure(IllegalStateException("timeout")) },
            canApply = { true },
            onLevelResolved = { resolved = it }
        )
        assertNull(resolved)

        probeRestoredDeclaredLevel(
            item = item,
            playContext = null,
            findOffline = { OfflineNeteaseMatch(null, "hires") },
            fetchPlaybackInfo = { Result.success(SongPlaybackInfo("http://example/1.mp3", level = "  ")) },
            canApply = { true },
            onLevelResolved = { resolved = it }
        )
        assertNull(resolved)

        // 5. 请求途中切歌或开始播放则丢弃结果
        var switchedDuringRequest = false
        probeRestoredDeclaredLevel(
            item = item,
            playContext = null,
            findOffline = { OfflineNeteaseMatch(null, "lossless") },
            fetchPlaybackInfo = {
                switchedDuringRequest = true
                Result.success(SongPlaybackInfo("http://example/1.flac", level = "lossless"))
            },
            canApply = { !switchedDuringRequest },
            onLevelResolved = { resolved = it }
        )
        assertNull(resolved)

        // 6. 协程取消必须继续向外传播
        var cancelled = false
        try {
            probeRestoredDeclaredLevel(
                item = item,
                playContext = null,
                findOffline = { OfflineNeteaseMatch(null, "lossless") },
                fetchPlaybackInfo = { throw CancellationException("cancelled") },
                canApply = { true },
                onLevelResolved = { resolved = it }
            )
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }
}
