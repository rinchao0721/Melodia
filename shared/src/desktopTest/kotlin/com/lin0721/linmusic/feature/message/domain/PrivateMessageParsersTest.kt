package com.lin0721.linmusic.feature.message.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateMessageParsersTest {

    private fun parseJsonObject(raw: String): JsonObject =
        Json.parseToJsonElement(raw) as JsonObject

    @Test
    fun `会话解析根据selfUid从fromUserId与toUserId挑出对方`() {
        val selfUid = 100L
        val peerUid = 200L
        val sentByMe = parseJsonObject(
            """
            {
              "fromUser": {"userId": 100, "nickname": "我自己", "avatarUrl": "http://self"},
              "toUser": {"userId": 200, "nickname": "好友A", "avatarUrl": "http://peer"},
              "user": {"id": 999, "fromUserId": 100, "toUserId": 200, "lastMsgTime": 17000, "newMsgCount": 2},
              "lastMsg": "{\"msg\":\"你好呀\",\"type\":6}"
            }
            """.trimIndent()
        )
        val sentByPeer = parseJsonObject(
            """
            {
              "fromUser": {"userId": 200, "nickname": "好友A", "avatarUrl": "http://peer"},
              "toUser": {"userId": 100, "nickname": "我自己", "avatarUrl": "http://self"},
              "user": {"id": 999, "fromUserId": 200, "toUserId": 100},
              "lastMsgTime": 18000,
              "newMsgCount": 5,
              "lastMsg": "{\"msg\":\"收到了\",\"type\":6}"
            }
            """.trimIndent()
        )

        val conv1 = parsePrivateConversation(sentByMe, selfUid)
        val conv2 = parsePrivateConversation(sentByPeer, selfUid)

        assertNotNull(conv1)
        assertEquals(peerUid, conv1!!.user.uid)
        assertEquals("好友A", conv1.user.nickname)
        assertEquals("你好呀", conv1.lastMessage)
        assertEquals(17000L, conv1.lastTime)
        assertEquals(2, conv1.unreadCount)

        assertNotNull(conv2)
        assertEquals(peerUid, conv2!!.user.uid)
        assertEquals("好友A", conv2.user.nickname)
        assertEquals("收到了", conv2.lastMessage)
        assertEquals(18000L, conv2.lastTime)
        assertEquals(5, conv2.unreadCount)
    }

    @Test
    fun `会话lastMsg支持双层msgId嵌套与歌曲摘要`() {
        val wrapped = parseJsonObject(
            """
            {
              "fromUser": {"userId": 200, "nickname": "好友", "avatarUrl": ""},
              "toUser": {"userId": 100, "nickname": "我", "avatarUrl": ""},
              "user": {"fromUserId": 200, "toUserId": 100},
              "lastMsgTime": 5000,
              "lastMsg": "{\"msgId\":123,\"msg\":\"{\\\"type\\\":1,\\\"song\\\":{\\\"id\\\":888,\\\"name\\\":\\\"夜曲\\\"}}\"}"
            }
            """.trimIndent()
        )

        val conv = parsePrivateConversation(wrapped, selfUid = 100L)

        assertNotNull(conv)
        assertEquals("分享单曲：夜曲", conv!!.lastMessage)
    }

    @Test
    fun `会话缺失关键字段或类型不符时安全跳过返回null`() {
        val missingUser = parseJsonObject("""{"fromUser":{"userId":1}}""")
        val invalidPeer = parseJsonObject("""{"user":{"fromUserId":100,"toUserId":0}}""")
        val missingProfile = parseJsonObject(
            """
            {
              "fromUser": {"userId": 100, "nickname": "我"},
              "user": {"fromUserId": 100, "toUserId": 200},
              "lastMsg": "不是json"
            }
            """.trimIndent()
        )
        val brokenLastMsg = parseJsonObject(
            """
            {
              "fromUser": {"userId": 200, "nickname": "对方"},
              "toUser": {"userId": 100, "nickname": "我"},
              "user": {"fromUserId": 200, "toUserId": 100},
              "lastMsg": 12345
            }
            """.trimIndent()
        )

        assertNull(parsePrivateConversation(missingUser, 100L))
        assertNull(parsePrivateConversation(invalidPeer, 100L))
        assertNull(parsePrivateConversation(missingProfile, 100L))

        val degraded = parsePrivateConversation(brokenLastMsg, 100L)
        assertNotNull(degraded)
        assertEquals(200L, degraded!!.user.uid)
        assertNull(degraded.lastMessage)
    }

    @Test
    fun `歌曲卡片封面优先取song_album_picUrl，依次兜底blurPicUrl与song_picUrl`() {
        val preferAlbumPic = parsePrivateMessage(
            parseJsonObject(
                """
                {
                  "id": 1,
                  "time": 100,
                  "fromUser": {"userId": 10, "nickname": "A"},
                  "toUser": {"userId": 20, "nickname": "B"},
                  "msg": "{\"type\":1,\"msg\":\"分享单曲\",\"song\":{\"id\":101,\"name\":\"歌1\",\"picUrl\":\"http://song\",\"artists\":[{\"name\":\"歌手1\"}],\"album\":{\"picUrl\":\"http://album\",\"blurPicUrl\":\"http://blur\"}}}"
                }
                """.trimIndent()
            )
        )
        val fallbackBlur = parsePrivateMessage(
            parseJsonObject(
                """
                {
                  "id": 2,
                  "time": 200,
                  "msg": "{\"type\":1,\"song\":{\"id\":102,\"name\":\"歌2\",\"picUrl\":\"http://song\",\"ar\":[{\"name\":\"甲\"},{\"name\":\"乙\"}],\"album\":{\"picUrl\":\"\",\"blurPicUrl\":\"http://blur\"}}}"
                }
                """.trimIndent()
            )
        )
        val fallbackSongPic = parsePrivateMessage(
            parseJsonObject(
                """
                {
                  "id": 3,
                  "time": 300,
                  "msg": "{\"type\":1,\"song\":{\"id\":103,\"name\":\"歌3\",\"picUrl\":\"http://song\",\"album\":{}}}"
                }
                """.trimIndent()
            )
        )

        assertEquals("http://album", preferAlbumPic?.card?.cover)
        assertEquals(PrivateCardKind.Song, preferAlbumPic?.card?.kind)
        assertEquals("歌手1", preferAlbumPic?.card?.subtitle)
        assertEquals(101L, preferAlbumPic?.card?.resourceId)

        assertEquals("http://blur", fallbackBlur?.card?.cover)
        assertEquals("甲/乙", fallbackBlur?.card?.subtitle)

        assertEquals("http://song", fallbackSongPic?.card?.cover)
    }

    @Test
    fun `歌单专辑MV与通用卡片均可安全解析`() {
        val playlist = parsePrivateMessage(
            parseJsonObject(
                """{"id":10,"time":1,"msg":"{\"type\":1,\"playlist\":{\"id\":50,\"name\":\"歌单X\",\"coverImgUrl\":\"http://pl\"}}"}"""
            )
        )
        val album = parsePrivateMessage(
            parseJsonObject(
                """{"id":11,"time":2,"msg":"{\"type\":1,\"album\":{\"id\":60,\"name\":\"专辑Y\",\"blurPicUrl\":\"http://al\",\"artist\":{\"name\":\"周杰伦\"}}}"}"""
            )
        )
        val mv = parsePrivateMessage(
            parseJsonObject(
                """{"id":12,"time":3,"msg":"{\"type\":1,\"mv\":{\"id\":70,\"name\":\"MVZ\",\"artistName\":\"陈奕迅\",\"imgurl16v9\":\"http://mv\"}}"}"""
            )
        )
        val general = parsePrivateMessage(
            parseJsonObject(
                """{"id":13,"time":4,"msg":"{\"type\":1,\"generalMsg\":{\"title\":\"活动卡片\",\"cover\":\"http://g\",\"inboxBriefContent\":\"活动简介\"}}"}"""
            )
        )

        assertEquals(PrivateCardKind.Playlist, playlist?.card?.kind)
        assertEquals("http://pl", playlist?.card?.cover)
        assertEquals(PrivateCardKind.Album, album?.card?.kind)
        assertEquals("周杰伦", album?.card?.subtitle)
        assertEquals("http://al", album?.card?.cover)
        assertEquals(PrivateCardKind.Mv, mv?.card?.kind)
        assertEquals("陈奕迅", mv?.card?.subtitle)
        assertEquals(PrivateCardKind.General, general?.card?.kind)
        assertEquals("活动简介", general?.text)
    }

    @Test
    fun `未知消息类型或双层JSON解析失败时降级为isUnsupported，不崩溃`() {
        val invalidId = parsePrivateMessage(parseJsonObject("""{"id":0,"msg":"{\"msg\":\"hi\"}"}"""))
        val badJson = parsePrivateMessage(parseJsonObject("""{"id":20,"time":5,"msg":"{bad json"}"""))
        val wrongTypes = parsePrivateMessage(
            parseJsonObject("""{"id":21,"time":6,"msg":"{\"type\":\"bad\",\"msg\":123,\"song\":\"not_obj\"}"}""")
        )
        val unknownTypeEmpty = parsePrivateMessage(
            parseJsonObject("""{"id":22,"time":7,"msg":"{\"type\":999}"}""")
        )

        assertNull(invalidId)
        assertNotNull(badJson)
        assertTrue(badJson!!.isUnsupported)
        assertEquals("{bad json", badJson.raw)

        assertNotNull(wrongTypes)
        assertTrue(wrongTypes!!.isUnsupported)
        assertFalse(wrongTypes.id <= 0L)

        assertNotNull(unknownTypeEmpty)
        assertEquals(999, unknownTypeEmpty!!.type)
        assertTrue(unknownTypeEmpty.isUnsupported)
    }
}
