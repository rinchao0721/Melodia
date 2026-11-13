package com.lin0721.linmusic.core.listentogether.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class ListenTogetherModelsTest {

    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `解析房间并用 creatorId 判定房主`() {
        val room = parseRoom(
            obj("""{"roomId":"abc_1","creatorId":7,"roomUsers":[{"userId":7,"nickname":"甲","avatarUrl":"u"},{"userId":8}]}""")
        )
        assertEquals("abc_1", room?.id)
        assertEquals(2, room?.members?.size)
        assertTrue(room!!.isHost(7L))
        assertFalse(room.isHost(8L))
        assertFalse(room.isHost(0L))
    }

    @Test
    fun `缺少 roomId 时解析为空`() {
        assertNull(parseRoom(obj("""{"creatorId":7}""")))
        assertNull(parseRoom(null))
    }

    @Test
    fun `解析房间队列与播放指令`() {
        val playback = parseRoomPlayback(
            obj(
                """{"playlist":{"playMode":"ORDER","displayList":{"result":["1","2",3,"2"]},"randomList":{"result":["9"]}},
                "playCommand":{"commandType":"play","targetSongId":"2","progress":4000,"playStatus":"PAUSE","serverSeq":99}}"""
            )
        )
        assertEquals(listOf(1L, 2L, 3L), playback?.songIds)
        val command = playback?.command
        assertEquals("PLAY", command?.commandType)
        assertEquals(2L, command?.targetSongId)
        assertEquals(4_000L, command?.progressMs)
        assertEquals(false, command?.playing)
        assertEquals(99L, command?.serverSeq)
    }

    @Test
    fun `随机模式读取 randomList`() {
        val playback = parseRoomPlayback(
            obj("""{"playlist":{"playMode":"RANDOM","displayList":{"result":["1"]},"randomList":{"result":["9"]}}}""")
        )
        assertEquals(listOf(9L), playback?.songIds)
    }

    @Test
    fun `既无队列也无指令时为空`() {
        assertNull(parseRoomPlayback(obj("{}")))
    }

    @Test
    fun `房间没有队列时仍解析出播放指令`() {
        val playback = parseRoomPlayback(
            obj("""{"playCommand":{"commandType":"GOTO","targetSongId":"5","progress":1000,"playStatus":"PLAY","serverSeq":7,"userId":1}}""")
        )
        assertTrue(playback!!.songIds.isEmpty())
        assertEquals(5L, playback.command?.targetSongId)
        assertEquals(1L, playback.command?.userId)
    }

    @Test
    fun `邀请文案往返与非法输入`() {
        val text = ListenTogetherInvite.format("abc_1", 42L)
        assertEquals(InviteTarget("abc_1", 42L), ListenTogetherInvite.parse("前缀 $text 后缀"))
        assertNull(ListenTogetherInvite.parse("roomId=abc_1"))
        assertNull(ListenTogetherInvite.parse("随便一段文字"))
    }
}
