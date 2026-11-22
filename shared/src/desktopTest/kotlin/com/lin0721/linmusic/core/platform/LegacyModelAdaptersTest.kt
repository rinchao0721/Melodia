package com.lin0721.linmusic.core.platform

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.BeRepliedComment
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.model.CommentUser
import com.lin0721.linmusic.core.model.PlaylistCreator
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.model.SongFileInfo
import com.lin0721.linmusic.core.model.SongPrivilege
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyModelAdaptersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `网易云 Track 与 UnifiedTrack 双向转换无损`() {
        val legacyTrack = Track(
            id = 186016L,
            name = "晴天",
            ar = listOf(Artist(id = 6452L, name = "周杰伦", picUrl = "https://img/ar.jpg", img1v1Url = "https://img/1v1.jpg")),
            al = Album(id = 18903L, name = "叶惠美", picUrl = "https://img/al.jpg", publishTime = 1059667200000L),
            fee = 8,
            publishTime = 1059667200000L,
            dt = 269000L,
            privilege = SongPrivilege(id = 186016L, maxBrLevel = "hires", st = 0),
            hr = SongFileInfo(br = 1750000, sr = 96000)
        )

        val unified = legacyTrack.toUnified()
        assertEquals(TrackId.netease(186016L), unified.id)
        assertEquals(MusicPlatform.NETEASE, unified.platform)
        assertEquals("晴天", unified.name)
        assertEquals("周杰伦", unified.artistNames)
        assertEquals(AlbumId.netease(18903L), unified.album?.id)
        assertEquals("叶惠美", unified.album?.name)
        assertEquals(269000L, unified.durationMs)
        assertEquals(UnifiedAudioQuality.HIRES, unified.maxQuality)
        assertTrue(unified.playable)

        // JSON 往返也应保持保真
        val jsonRoundTrip = json.decodeFromString<UnifiedTrack>(json.encodeToString(unified))
        assertEquals(unified, jsonRoundTrip)

        val restored = jsonRoundTrip.toLegacyNeteaseTrackOrNull()
        assertNotNull(restored)
        assertEquals(legacyTrack.id, restored!!.id)
        assertEquals(legacyTrack.name, restored.name)
        assertEquals(legacyTrack.ar, restored.ar)
        assertEquals(legacyTrack.al.id, restored.al.id)
        assertEquals(legacyTrack.al.name, restored.al.name)
        assertEquals(legacyTrack.al.picUrl, restored.al.picUrl)
        assertEquals(legacyTrack.dt, restored.dt)
        assertEquals(legacyTrack.fee, restored.fee)
        assertEquals("hires", restored.privilege?.maxBrLevel)
        assertEquals(0, restored.privilege?.st)
    }

    @Test
    fun `无版权 Track 的 playable 与 st 转换准确`() {
        val blocked = Track(
            id = 999L,
            name = "灰歌",
            privilege = SongPrivilege(id = 999L, st = -200),
            sq = SongFileInfo(br = 900000, sr = 44100)
        )
        val unified = blocked.toUnified()
        assertFalse(unified.playable)
        assertEquals(UnifiedAudioQuality.LOSSLESS, unified.maxQuality)

        val restored = unified.toLegacyNeteaseTrackOrNull()!!
        assertTrue((restored.privilege?.st ?: 0) < 0)
        assertEquals("lossless", restored.privilege?.maxBrLevel)
    }

    @Test
    fun `酷狗 ExternalTrack 双向转换与 hash 归一化保真`() {
        val kugouExternal = ExternalTrack(
            id = "B5E3CA48FC0D01F01DB0B6BBA570480F",
            name = "以我",
            artists = "张赫煊",
            artistList = listOf("张赫煊"),
            albumName = "以我",
            albumId = "215644565",
            durationMs = 229000L,
            coverUrl = "https://imge.kugou.com/stdmusic/400/test.jpg",
            platform = MusicPlatform.KUGOU,
            hash = "B5E3CA48FC0D01F01DB0B6BBA570480F"
        )

        val unified = kugouExternal.toUnified()
        assertEquals(TrackId.kugou("b5e3ca48fc0d01f01db0b6bba570480f"), unified.id)
        assertEquals(MusicPlatform.KUGOU, unified.platform)
        assertEquals("b5e3ca48fc0d01f01db0b6bba570480f", unified.kugouMeta?.hash)
        assertEquals("215644565", unified.kugouMeta?.albumId)
        assertNull(unified.toLegacyNeteaseTrackOrNull())

        val restored = unified.toLegacyExternalTrackOrNull()
        assertNotNull(restored)
        assertEquals("b5e3ca48fc0d01f01db0b6bba570480f", restored!!.id)
        assertEquals("b5e3ca48fc0d01f01db0b6bba570480f", restored.hash)
        assertEquals(kugouExternal.name, restored.name)
        assertEquals(kugouExternal.artists, restored.artists)
        assertEquals(kugouExternal.artistList, restored.artistList)
        assertEquals(kugouExternal.albumName, restored.albumName)
        assertEquals(kugouExternal.albumId, restored.albumId)
        assertEquals(kugouExternal.durationMs, restored.durationMs)
        assertEquals(kugouExternal.coverUrl, restored.coverUrl)
        assertEquals(MusicPlatform.KUGOU, restored.platform)
    }

    @Test
    fun `QQ ExternalTrack 双向转换保留 qqSongId`() {
        val qqExternal = ExternalTrack(
            id = "0039MnYb0qxYhV",
            name = "青花瓷",
            artists = "周杰伦",
            artistList = listOf("周杰伦"),
            albumName = "我很忙",
            albumId = "002eFUFm2XYZ7z",
            durationMs = 239000L,
            coverUrl = "https://y.gtimg.cn/test.jpg",
            platform = MusicPlatform.QQ,
            qqSongId = 449205L
        )

        val unified = qqExternal.toUnified()
        assertEquals(TrackId.qq("0039MnYb0qxYhV"), unified.id)
        assertEquals(449205L, unified.qqSongId)
        assertNull(unified.kugouMeta)

        val restored = unified.toLegacyExternalTrackOrNull()
        assertEquals(qqExternal, restored)
    }

    @Test
    fun `本地与网易曲目转 ExternalTrack 返回 null`() {
        val localTrack = UnifiedTrack(
            id = TrackId.local("content://media/external/audio/media/7"),
            name = "本地测试",
            localUri = "content://media/external/audio/media/7"
        )
        assertNull(localTrack.toLegacyExternalTrackOrNull())
        assertNull(localTrack.toLegacyNeteaseTrackOrNull())

        val neteaseTrack = Track(id = 123L, name = "网易歌").toUnified()
        assertNull(neteaseTrack.toLegacyExternalTrackOrNull())
    }

    @Test
    fun `PlaylistDetail 与 UnifiedPlaylist 双向转换无损`() {
        val detail = PlaylistDetail(
            id = 24381616L,
            name = "私藏华语精选",
            coverImgUrl = "https://img/pl.jpg",
            description = "好歌合集",
            playCount = 123456L,
            subscribed = true,
            creator = PlaylistCreator(userId = 888L, nickname = "小明", avatarUrl = "https://img/u.jpg"),
            tracks = listOf(Track(id = 101L, name = "曲1", dt = 200000L)),
            trackCount = 0,
            trackIds = listOf(PlaylistTrackId(id = 101L), PlaylistTrackId(id = 102L)),
            privacy = 10
        )

        val unified = detail.toUnified()
        assertEquals(PlaylistId.netease(24381616L), unified.id)
        assertEquals(AccountId.netease(888L), unified.creator?.id)
        assertEquals(2, unified.trackCount)
        assertEquals(listOf(TrackId.netease(101L), TrackId.netease(102L)), unified.trackIds)
        assertFalse(unified.isPublic)
        assertFalse(unified.isAlbum)

        val restored = unified.toLegacyNeteasePlaylistOrNull()!!
        assertEquals(detail.id, restored.id)
        assertEquals(detail.name, restored.name)
        assertEquals(detail.coverImgUrl, restored.coverImgUrl)
        assertEquals(detail.description, restored.description)
        assertEquals(detail.playCount, restored.playCount)
        assertEquals(detail.subscribed, restored.subscribed)
        assertEquals(detail.creator, restored.creator)
        assertEquals(1, restored.tracks.size)
        assertEquals(2, restored.trackIds.size)
        assertEquals(10, restored.privacy)

        // 非网易歌单不可转回网易 PlaylistDetail
        val kugouPlaylist = unified.copy(id = PlaylistId.kugou("collection_3_1_2_0"))
        assertNull(kugouPlaylist.toLegacyNeteasePlaylistOrNull())
    }

    @Test
    fun `CommentItem 与 PlaySource 转换正确`() {
        val comment = CommentItem(
            commentId = 555L,
            user = CommentUser(userId = 777L, nickname = "听众", avatarUrl = "https://img/c.jpg"),
            content = "好听",
            time = 1700000000000L,
            timeStr = "2023-11-14",
            likedCount = 42,
            liked = true,
            replyCount = 1,
            parentCommentId = 0L,
            beReplied = listOf(
                BeRepliedComment(
                    user = CommentUser(userId = 666L, nickname = "原评"),
                    content = "确实",
                    beRepliedCommentId = 554L
                )
            )
        )
        val unifiedComment = comment.toUnified()
        assertEquals("555", unifiedComment.id)
        assertEquals(AccountId.netease(777L), unifiedComment.author.id)
        assertNull(unifiedComment.parentCommentId)
        assertEquals("554", unifiedComment.repliedTo.single().commentId)

        val playSource = PlaySource(PlaySource.Kind.ALBUM, 18903L, "叶惠美", "https://img/al.jpg")
        val unifiedSource = playSource.toUnified()
        assertEquals(UnifiedPlaySource.Kind.ALBUM, unifiedSource.kind)
        assertEquals("18903", unifiedSource.rawId)
        assertEquals(playSource, unifiedSource.toLegacyNeteasePlaySourceOrNull())
    }
}
