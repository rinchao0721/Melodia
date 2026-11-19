package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanLinkProtocolRemoteTest {

    @Test
    fun remoteMessagesRoundtripWithTypeDiscriminator() {
        val sampleQueue = listOf(
            QueueItem(songId = 101L, title = "晴天", artist = "周杰伦", coverUrl = "https://img.example/1.jpg"),
            QueueItem(songId = 202L, title = "七里香", artist = "周杰伦", coverUrl = "https://img.example/2.jpg")
        )
        val source = PlaySource(PlaySource.Kind.PLAYLIST, 888L, "我喜欢的音乐", "https://img.example/pl.jpg")

        val messages: List<LanLinkMessage> = listOf(
            LanLinkMessage.RemotePlaybackHello(
                deviceId = "pixel-9",
                deviceName = "Pixel 9",
                isPlaying = true,
                playWhenReady = true,
                hasTrack = true,
                lastActiveAtMs = 1_700_000_000_000L,
                isHost = false
            ),
            LanLinkMessage.RemoteActiveDevice(
                activeDeviceId = "pixel-9",
                activeDeviceName = "Pixel 9",
                epoch = 3L,
                timestampMs = 1_700_000_000_500L
            ),
            LanLinkMessage.RemoteHandoffPlay(
                targetDeviceId = "desktop-1",
                item = sampleQueue.first(),
                startPositionMs = 15_000L,
                epoch = 4L,
                timestampMs = 1_700_000_001_000L
            ),
            LanLinkMessage.RemoteRequestSync,
            LanLinkMessage.RemoteCommand(
                action = RemoteCommandAction.SWITCH_FM_MODE,
                index = 3,
                fromIndex = 1,
                toIndex = 2,
                positionMs = 45_000L,
                volumePercent = 65,
                personalFmMode = PersonalFmMode.EXERCISE
            ),
            LanLinkMessage.RemotePlayQueue(
                items = sampleQueue,
                startIndex = 1,
                playContext = "歌单",
                source = source,
                startPositionMs = 12_000L
            ),
            LanLinkMessage.RemotePlayAudio(
                songId = 303L,
                url = "https://audio.example/303.mp3",
                title = "电台节目",
                artist = "主播",
                coverUrl = "https://img.example/3.jpg",
                startPosition = 5_000L,
                playContext = "podcast"
            ),
            LanLinkMessage.RemoteAddToPlayNext(sampleQueue),
            LanLinkMessage.RemotePlayNextNow(sampleQueue.first(), startPositionMs = 3_000L),
            LanLinkMessage.RemoteAppendToQueue(sampleQueue),
            LanLinkMessage.RemoteExtendQueue(source, sampleQueue),
            LanLinkMessage.RemoteStartPersonalFm(sampleQueue),
            LanLinkMessage.RemotePlaybackState(
                isPlaying = true,
                playWhenReady = true,
                nowPlaying = RemoteNowPlaying("101", "晴天", "周杰伦", "https://img.example/1.jpg"),
                positionMs = 20_000L,
                sampledAtMs = 1_700_000_000_000L,
                durationMs = 269_000L,
                playMode = PlayMode.SHUFFLE,
                playContext = "歌单",
                playSource = source,
                currentIndex = 0,
                volumePercent = 80,
                queueVersion = 4L
            ),
            LanLinkMessage.RemoteQueueSync(
                queueVersion = 4L,
                items = sampleQueue,
                currentIndex = 0,
                playContext = "歌单",
                playSource = source
            )
        )

        for (original in messages) {
            val encoded = LanLinkJson.encodeToString<LanLinkMessage>(original)
            assertTrue("Missing type discriminator in $encoded", encoded.contains("\"type\":"))
            val decoded = LanLinkJson.decodeFromString<LanLinkMessage>(encoded)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun allRemoteCommandActionsSerializeAndDeserialize() {
        RemoteCommandAction.entries.forEach { action ->
            val msg = LanLinkMessage.RemoteCommand(action = action)
            val encoded = LanLinkJson.encodeToString<LanLinkMessage>(msg)
            val decoded = LanLinkJson.decodeFromString<LanLinkMessage>(encoded) as LanLinkMessage.RemoteCommand
            assertEquals(action, decoded.action)
        }
    }
}
