package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlaybackControllerTest {

    @Test
    fun playbackStateSyncInterpolatesPositionAndRespectsClockSkew() {
        var nowMs = 10_000L
        val sent = mutableListOf<LanLinkMessage>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = RemotePlaybackController(
                sendCommand = { sent += it },
                onUserMessage = {},
                scope = scope,
                clockMs = { nowMs }
            )

            // 同机或对齐时钟下存在 120ms 传输延迟：初始位置应补偿 120ms
            controller.onPlaybackState(
                LanLinkMessage.RemotePlaybackState(
                    isPlaying = true,
                    playWhenReady = true,
                    nowPlaying = RemoteNowPlaying("1", "Song A", "Artist A", "cover"),
                    positionMs = 5_000L,
                    sampledAtMs = 9_880L,
                    durationMs = 20_000L,
                    playMode = PlayMode.SHUFFLE,
                    currentIndex = 0,
                    volumePercent = 72,
                    queueVersion = 2L
                )
            )

            assertEquals(5_120L, controller.currentPositionNow())
            assertTrue(controller.isPlaying.value)
            assertEquals(72, controller.volume.value)
            assertEquals(72, controller.lastAudibleVolume.value)
            assertTrue(sent.any { it is LanLinkMessage.RemoteRequestSync })

            // 时间前进 2 秒后，本地插值应前进到 7_120ms
            nowMs = 12_000L
            assertEquals(7_120L, controller.currentPositionNow())

            // 若对端系统时钟超前或严重滞后（超出容忍窗口），不进行错误补偿且不超过 duration
            nowMs = 30_000L
            controller.onPlaybackState(
                LanLinkMessage.RemotePlaybackState(
                    isPlaying = true,
                    playWhenReady = true,
                    nowPlaying = RemoteNowPlaying("1", "Song A", "Artist A", "cover"),
                    positionMs = 19_500L,
                    sampledAtMs = 50_000L, // 对端时钟超前 20 秒
                    durationMs = 20_000L,
                    playMode = PlayMode.SHUFFLE,
                    currentIndex = 0,
                    volumePercent = 0,
                    queueVersion = 2L
                )
            )
            assertEquals(19_500L, controller.currentPositionNow())
            assertEquals(0, controller.volume.value)
            // 静音后 lastAudibleVolume 保持上一次非零音量
            assertEquals(72, controller.lastAudibleVolume.value)

            nowMs = 32_000L
            assertEquals(20_000L, controller.currentPositionNow())

            // 暂停后位置不再往前漂移
            controller.onPlaybackState(
                LanLinkMessage.RemotePlaybackState(
                    isPlaying = false,
                    playWhenReady = false,
                    nowPlaying = RemoteNowPlaying("1", "Song A", "Artist A", "cover"),
                    positionMs = 8_000L,
                    sampledAtMs = 32_000L,
                    durationMs = 20_000L,
                    queueVersion = 2L
                )
            )
            nowMs = 40_000L
            assertEquals(8_000L, controller.currentPositionNow())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun filtersLocalOnlyTracksAndEmitsWarning() {
        val sent = mutableListOf<LanLinkMessage>()
        val warnings = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = RemotePlaybackController(
                sendCommand = { sent += it },
                onUserMessage = { warnings += it },
                scope = scope
            )

            val localUriItem = QueueItem(10L, "Local", "Artist", "", localUri = "content://media/1")
            val negLocalItem = QueueItem(-5L, "NegLocal", "Artist", "")
            val onlineItem = QueueItem(20L, "Online", "Artist", "")
            val pluginItem = QueueItem(
                songId = -100L,
                title = "Plugin",
                artist = "Artist",
                coverUrl = "",
                external = ExternalTrack(id = "abc", name = "Plugin", artists = "Artist", platform = MusicPlatform.KUWO)
            )

            // 选中本地曲目起播时应提示且不发送指令
            controller.playQueue(listOf(localUriItem, onlineItem), startIndex = 0)
            assertTrue(sent.isEmpty())
            assertEquals(listOf("本地歌曲无法在远程设备播放"), warnings)

            // 混合队列且选中在线曲目时过滤掉本地曲目并重映射 startIndex
            controller.playQueue(
                items = listOf(localUriItem, negLocalItem, onlineItem, pluginItem),
                startIndex = 3,
                playContext = "歌单",
                source = PlaySource(PlaySource.Kind.PLAYLIST, 1L, "Test"),
                startPositionMs = 1_500L
            )
            val playQueueMsg = sent.single() as LanLinkMessage.RemotePlayQueue
            assertEquals(listOf(onlineItem, pluginItem), playQueueMsg.items)
            assertEquals(1, playQueueMsg.startIndex)
            assertEquals(1_500L, playQueueMsg.startPositionMs)

            // playAudio 拦截 file:// 与 content://
            sent.clear()
            warnings.clear()
            controller.playAudio(1L, "file:///sdcard/song.mp3", "Local", "A", "")
            assertTrue(sent.isEmpty())
            assertEquals(1, warnings.size)

            controller.playAudio(2L, "https://cdn.example/song.mp3", "Net", "A", "")
            assertTrue(sent.single() is LanLinkMessage.RemotePlayAudio)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun commandsVolumeSeekAndResetWorkCorrectly() {
        val sent = mutableListOf<LanLinkMessage>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = RemotePlaybackController(
                sendCommand = { sent += it },
                onUserMessage = {},
                scope = scope
            )
            controller.onPlaybackState(
                RemotePlaybackStateCompat(
                    isPlaying = false,
                    playWhenReady = false,
                    nowPlaying = NowPlaying("1", "Title", "Artist", null),
                    positionMs = 1_000L,
                    sampledAtMs = 100L,
                    durationMs = 10_000L,
                    playMode = PlayMode.LIST_LOOP,
                    playContext = null,
                    playSource = null,
                    currentIndex = 0,
                    volumePercent = 50,
                    queueVersion = 0L
                )
            )
            controller.seekTo(15_000L)
            assertEquals(10_000L, controller.currentPosition.value)
            val seekCmd = sent.last() as LanLinkMessage.RemoteCommand
            assertEquals(RemoteCommandAction.SEEK, seekCmd.action)
            assertEquals(10_000L, seekCmd.positionMs)

            controller.setVolume(120)
            assertEquals(100, controller.volume.value)
            val volCmd = sent.last() as LanLinkMessage.RemoteCommand
            assertEquals(RemoteCommandAction.SET_VOLUME, volCmd.action)
            assertEquals(100, volCmd.volumePercent)

            controller.switchPersonalFmMode(PersonalFmMode.FAMILIAR)
            val fmCmd = sent.last() as LanLinkMessage.RemoteCommand
            assertEquals(RemoteCommandAction.SWITCH_FM_MODE, fmCmd.action)
            assertEquals(PersonalFmMode.FAMILIAR, fmCmd.personalFmMode)

            controller.onQueueSync(
                LanLinkMessage.RemoteQueueSync(
                    queueVersion = 7L,
                    items = listOf(QueueItem(1L, "Title", "Artist", "")),
                    currentIndex = 0
                )
            )
            assertEquals(1, controller.queue.value.size)

            controller.reset()
            assertNull(controller.nowPlaying.value)
            assertFalse(controller.isPlaying.value)
            assertTrue(controller.queue.value.isEmpty())
            assertEquals(-1, controller.currentIndex.value)
            assertEquals(0L, controller.duration.value)
        } finally {
            scope.cancel()
        }
    }
}
