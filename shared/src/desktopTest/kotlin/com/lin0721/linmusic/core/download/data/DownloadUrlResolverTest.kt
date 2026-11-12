package com.lin0721.linmusic.core.download.data

import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadUrlResolverTest {

    private class Fixture(
        fallback: Boolean,
        response: SongDownloadUrlResponse,
        info: SongPlaybackInfo? = null,
        playbackThrows: Boolean = false
    ) {
        var playbackCalls = 0
            private set

        val resolver = DownloadUrlResolver(
            fallbackEnabled = { fallback },
            official = { _, _ -> response },
            playback = {
                playbackCalls++
                if (playbackThrows) throw IllegalStateException("playback failed")
                info
            }
        )
    }

    private fun ready(outcome: DownloadUrlOutcome): DownloadSource {
        assertTrue(outcome is DownloadUrlOutcome.Ready)
        return (outcome as DownloadUrlOutcome.Ready).source
    }

    private fun rejected(outcome: DownloadUrlOutcome): String {
        assertTrue(outcome is DownloadUrlOutcome.Rejected)
        return (outcome as DownloadUrlOutcome.Rejected).reason
    }

    @Test
    fun officialUrlIsUsedWithoutTouchingPlayback() = runTest {
        val fixture = Fixture(
            fallback = true,
            response = SongDownloadUrlResponse(
                code = 200,
                data = SongDownloadUrlItem(url = "https://music.163.com/a.mp3", size = 4096, level = "exhigh", encodeType = "mp3")
            )
        )

        val source = ready(fixture.resolver.resolve(1L, "exhigh"))

        assertEquals("https://music.163.com/a.mp3", source.url)
        assertEquals("mp3", source.encodeType)
        assertEquals(4096L, source.size)
        assertEquals("exhigh", source.level)
        assertFalse(source.fromFallback)
        assertEquals(0, fixture.playbackCalls)
    }

    @Test
    fun officialFailureDoesNotFallbackWhenSwitchIsOff() = runTest {
        val fixture = Fixture(fallback = false, response = SongDownloadUrlResponse(code = -1))

        assertEquals("获取下载链接失败", rejected(fixture.resolver.resolve(2L, "standard")))
        assertEquals(0, fixture.playbackCalls)
    }

    @Test
    fun officialFailureFallsBackToPlaybackUrl() = runTest {
        val fixture = Fixture(
            fallback = true,
            response = SongDownloadUrlResponse(code = -1),
            info = SongPlaybackInfo(url = "https://cdn.example.com/song.flac?token=abc")
        )

        val source = ready(fixture.resolver.resolve(3L, "hires"))

        assertEquals("https://cdn.example.com/song.flac?token=abc", source.url)
        assertEquals("flac", source.encodeType)
        assertEquals(0L, source.size)
        assertEquals("standard", source.level)
        assertTrue(source.fromFallback)
        assertEquals(1, fixture.playbackCalls)
    }

    @Test
    fun trialOfficialUrlFallsBackToPlaybackUrl() = runTest {
        val fixture = Fixture(
            fallback = true,
            response = SongDownloadUrlResponse(
                code = 200,
                data = SongDownloadUrlItem(url = "https://music.163.com/trial.mp3", time = 30_000)
            ),
            info = SongPlaybackInfo(url = "https://cdn.example.com/song.mp3")
        )

        assertTrue(ready(fixture.resolver.resolve(4L, "standard")).fromFallback)
    }

    @Test
    fun trialOfficialUrlKeepsTrialReasonWhenFallbackMisses() = runTest {
        val fixture = Fixture(
            fallback = true,
            response = SongDownloadUrlResponse(
                code = 200,
                data = SongDownloadUrlItem(url = "https://music.163.com/trial.mp3", time = 30_000)
            )
        )

        assertEquals(
            "该音质仅支持试听，需要 VIP/购买后才能完整下载",
            rejected(fixture.resolver.resolve(5L, "standard"))
        )
    }

    @Test
    fun trialPlaybackUrlIsNotDownloaded() = runTest {
        val fixture = Fixture(
            fallback = true,
            response = SongDownloadUrlResponse(code = -1),
            info = SongPlaybackInfo(url = "https://musicrep-ts.example.com/a.mp3")
        )

        assertEquals("获取下载链接失败", rejected(fixture.resolver.resolve(6L, "standard")))
    }

    @Test
    fun freeTrialPlaybackInfoIsNotDownloaded() = runTest {
        val fixture = Fixture(
            fallback = true,
            response = SongDownloadUrlResponse(code = -1),
            info = SongPlaybackInfo(url = "https://cdn.example.com/a.mp3", isFreeTrial = true, trialFallback = true)
        )

        assertEquals("获取下载链接失败", rejected(fixture.resolver.resolve(7L, "standard")))
    }

    @Test
    fun silentlyDowngradedLosslessKeepsMembershipReason() = runTest {
        val fixture = Fixture(
            fallback = true,
            response = SongDownloadUrlResponse(
                code = 200,
                data = SongDownloadUrlItem(url = "https://music.163.com/a.mp3", encodeType = "mp3")
            ),
            info = SongPlaybackInfo(url = "https://cdn.example.com/a.flac")
        )

        assertEquals(
            "需要更高会员等级才能下载该音质",
            rejected(fixture.resolver.resolve(8L, "lossless"))
        )
        assertEquals(0, fixture.playbackCalls)
    }

    @Test
    fun unknownUrlSuffixIsSavedAsMp3() = runTest {
        val fixture = Fixture(
            fallback = true,
            response = SongDownloadUrlResponse(code = -1),
            info = SongPlaybackInfo(url = "https://cdn.example.com/stream?id=9")
        )

        assertEquals("mp3", ready(fixture.resolver.resolve(9L, "standard")).encodeType)
    }

    @Test
    fun playbackErrorIsTreatedAsNoResult() = runTest {
        val fixture = Fixture(
            fallback = true,
            response = SongDownloadUrlResponse(code = -1),
            playbackThrows = true
        )

        assertEquals("获取下载链接失败", rejected(fixture.resolver.resolve(10L, "standard")))
    }
}
