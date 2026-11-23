package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalEntry
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalSnapshot
import com.lin0721.linmusic.core.playlistexternal.PlaylistUnifiedEntry
import com.lin0721.linmusic.core.playlistexternal.PlaylistUnifiedTombstone
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanLinkProtocolV2InteropTest {

    @Test
    fun legacyV1HandshakeFramesDecodeWithProtocolVersion1Default() {
        val v1AuthHelloJson = """
            {"type":"auth_hello","clientDeviceId":"dev-1","clientDeviceName":"Pixel","clientNonceBase64":"abc"}
        """.trimIndent()
        val decoded = LanLinkJson.decodeFromString<LanLinkMessage>(v1AuthHelloJson) as LanLinkMessage.AuthHello
        assertEquals(LanLinkConstants.PROTOCOL_VERSION_V1, decoded.protocolVersion)

        val v1AccountJson = """{"type":"account_exchange","uid":10001}"""
        val decodedAccount = LanLinkJson.decodeFromString<LanLinkMessage>(v1AccountJson) as LanLinkMessage.AccountExchange
        assertEquals(10001L, decodedAccount.uid)
        assertTrue(decodedAccount.activeAccounts.isEmpty())
    }

    @Test
    fun v2PreservesKugouQueueAndUnifiedSnapshotAndPlatformFm() {
        val kugouItem = QueueItem(
            songId = -12345L,
            title = "以我",
            artist = "张赫煊",
            coverUrl = "https://imge.kugou.com/stdmusic/400/test.jpg",
            trackId = TrackId.kugou("b5e3ca48fc0d01f01db0b6bba570480f"),
            kugouMeta = KugouTrackMeta(
                hash = "b5e3ca48fc0d01f01db0b6bba570480f",
                mixSongId = 1109118629L,
                albumAudioId = 988834222L,
                albumId = "215644565"
            )
        )
        val msg = LanLinkMessage.RemoteStartPersonalFm(
            items = listOf(kugouItem),
            platform = MusicPlatform.KUGOU
        )
        val encoded = LanLinkJson.encodeToString<LanLinkMessage>(msg)
        val decoded = LanLinkJson.decodeFromString<LanLinkMessage>(encoded) as LanLinkMessage.RemoteStartPersonalFm
        assertEquals(MusicPlatform.KUGOU, decoded.platform)
        assertEquals(TrackId.kugou("b5e3ca48fc0d01f01db0b6bba570480f"), decoded.items.single().trackId)
        assertEquals(1109118629L, decoded.items.single().kugouMeta?.mixSongId)

        val snapshot = PlaylistExternalSnapshot(
            entries = mapOf(
                100L to listOf(
                    PlaylistExternalEntry(
                        track = ExternalTrack(platform = MusicPlatform.KUGOU, id = "hash1", name = "A", artists = "B"),
                        addedAt = 1000L
                    )
                )
            ),
            unifiedEntries = mapOf(
                PlaylistId.kugou("pl1").canonical to listOf(
                    PlaylistUnifiedEntry(
                        track = UnifiedTrack(
                            id = TrackId.kugou("hash1"),
                            name = "A",
                            artists = listOf(UnifiedArtist(name = "B"))
                        ),
                        addedAt = 1000L
                    )
                )
            ),
            unifiedTombstones = listOf(
                PlaylistUnifiedTombstone(PlaylistId.kugou("pl1"), "kugou:hash2", 2000L)
            )
        )
        val v2Snapshot = LanLinkMessage.SyncSnapshot(snapshot, 3000L).projectForProtocol(2) as LanLinkMessage.SyncSnapshot
        assertEquals(1, v2Snapshot.snapshot.unifiedEntries.size)
        assertEquals(1, v2Snapshot.snapshot.unifiedTombstones.size)
    }

    @Test
    fun v1ProjectionStripsV2FieldsAndKeepsExternalFallback() {
        val kugouItem = QueueItem(
            songId = -12345L,
            title = "以我",
            artist = "张赫煊",
            coverUrl = "https://imge.kugou.com/stdmusic/400/test.jpg",
            trackId = TrackId.kugou("b5e3ca48fc0d01f01db0b6bba570480f"),
            kugouMeta = KugouTrackMeta(hash = "b5e3ca48fc0d01f01db0b6bba570480f")
        )
        val projected = LanLinkMessage.RemotePlayQueue(
            items = listOf(kugouItem),
            startIndex = 0
        ).projectForProtocol(LanLinkConstants.PROTOCOL_VERSION_V1) as LanLinkMessage.RemotePlayQueue

        val item = projected.items.single()
        assertNull(item.trackId)
        assertNull(item.kugouMeta)
        assertNotNull(item.external)
        assertEquals(MusicPlatform.KUGOU, item.external?.platform)

        assertNull(
            LanLinkMessage.RemoteCommand(
                action = RemoteCommandAction.SWITCH_FM_PLATFORM,
                platform = MusicPlatform.KUGOU
            ).projectForProtocol(LanLinkConstants.PROTOCOL_VERSION_V1)
        )
    }
}
