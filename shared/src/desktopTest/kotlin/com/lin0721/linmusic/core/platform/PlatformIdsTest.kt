package com.lin0721.linmusic.core.platform

import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlatformIdsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class LegacyIdHolder(
        val trackId: TrackId,
        val playlistId: PlaylistId,
        val albumId: AlbumId,
        val artistId: ArtistId,
        val accountId: AccountId
    )

    @Test
    fun `规范编解码输出与解析保持一致`() {
        val track = TrackId.netease(186016L)
        val playlist = PlaylistId.kugou("collection_3_1312812254_37_0")
        val album = AlbumId.of(MusicPlatform.QQ, "003RMaRI1iFoYd")
        val artist = ArtistId.netease(6452L)
        val account = AccountId.kugou(987654321L)

        assertEquals("netease:186016", track.canonical)
        assertEquals("kugou:collection_3_1312812254_37_0", playlist.canonical)
        assertEquals("qq:003RMaRI1iFoYd", album.canonical)
        assertEquals("netease:6452", artist.canonical)
        assertEquals("kugou:987654321", account.canonical)

        val encoded = json.encodeToString(
            LegacyIdHolder(track, playlist, album, artist, account)
        )
        val decoded = json.decodeFromString<LegacyIdHolder>(encoded)
        assertEquals(track, decoded.trackId)
        assertEquals(playlist, decoded.playlistId)
        assertEquals(album, decoded.albumId)
        assertEquals(artist, decoded.artistId)
        assertEquals(account, decoded.accountId)
    }

    @Test
    fun `反序列化向后兼容纯数字 Long 与纯数字字符串`() {
        val rawNumericJson = """
            {
              "trackId": 186016,
              "playlistId": "24381616",
              "albumId": 18903,
              "artistId": "6452",
              "accountId": 10086
            }
        """.trimIndent()

        val decoded = json.decodeFromString<LegacyIdHolder>(rawNumericJson)
        assertEquals(TrackId.netease(186016L), decoded.trackId)
        assertEquals(186016L, decoded.trackId.neteaseIdOrNull())
        assertEquals(PlaylistId.netease(24381616L), decoded.playlistId)
        assertEquals(AlbumId.netease(18903L), decoded.albumId)
        assertEquals(ArtistId.netease(6452L), decoded.artistId)
        assertEquals(AccountId.netease(10086L), decoded.accountId)

        // 再序列化时应升级为规范前缀格式
        val reEncoded = json.encodeToString(decoded)
        assertEquals(true, reEncoded.contains(""""trackId":"netease:186016""""))
        assertEquals(true, reEncoded.contains(""""playlistId":"netease:24381616""""))
    }

    @Test
    fun `酷狗 hash 规范化为小写去空白`() {
        val fromFactory = TrackId.kugou("  B5E3CA48FC0D01F01DB0B6BBA570480F  ")
        val fromCtor = TrackId(MusicPlatform.KUGOU, "B5E3CA48FC0D01F01DB0B6BBA570480F")
        val fromParse = TrackId.parse("  KUGOU: b5e3ca48fc0d01f01db0b6bba570480f ")

        assertEquals("b5e3ca48fc0d01f01db0b6bba570480f", fromFactory.rawId)
        assertEquals("kugou:b5e3ca48fc0d01f01db0b6bba570480f", fromFactory.canonical)
        assertEquals(fromFactory, fromCtor)
        assertEquals(fromFactory, fromParse)
        assertNull(fromFactory.neteaseIdOrNull())
    }

    @Test
    fun `本地路径与 URI 中包含冒号时完整保留 rawId`() {
        val contentUri = TrackId.parse("local:content://media/external/audio/media/42")
        assertEquals(MusicPlatform.LOCAL, contentUri.platform)
        assertEquals("content://media/external/audio/media/42", contentUri.rawId)
        assertEquals("local:content://media/external/audio/media/42", contentUri.canonical)
        assertNull(contentUri.neteaseIdOrNull())

        val winPath = TrackId.local("D:\\Music\\test.flac")
        assertEquals(winPath, TrackId.parse(winPath.canonical))
    }

    @Test
    fun `异常输入返回 null 或抛出预期异常`() {
        listOf("", "   ", "-123", "not_a_number", "unknown:123", "netease:", ":123", "kugou:   ").forEach { bad ->
            assertNull("期望 parseOrNull 返回 null: $bad", TrackId.parseOrNull(bad))
            assertNull("期望 PlaylistId.parseOrNull 返回 null: $bad", PlaylistId.parseOrNull(bad))
            assertThrows(IllegalArgumentException::class.java) { TrackId.parse(bad) }
        }

        assertThrows(IllegalArgumentException::class.java) { TrackId.netease(-1L) }
        assertThrows(IllegalArgumentException::class.java) { TrackId.kugou("   ") }
        assertThrows(SerializationException::class.java) { json.decodeFromString<TrackId>("-100") }
        assertThrows(SerializationException::class.java) { json.decodeFromString<TrackId>("""{"id":1}""") }
        assertThrows(SerializationException::class.java) { json.decodeFromString<TrackId>(""""unknown:123"""") }
    }
}
