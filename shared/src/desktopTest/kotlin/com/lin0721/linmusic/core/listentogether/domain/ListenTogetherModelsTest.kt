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
        assertEquals("邀请你一起听【Melodia 一起听】roomId=abc_1;inviterId=42", text)
        assertEquals(InviteTarget("abc_1", 42L), ListenTogetherInvite.parse("前缀 $text 后缀"))
        assertEquals(InviteTarget("abc_1", 42L), ListenTogetherInvite.parse("【Melodia 一起听】roomId=abc_1;inviterId=42"))
        assertEquals(InviteTarget("abc_1", 42L), ListenTogetherInvite.parse("roomId=abc_1;inviterId=42"))
        assertNull(ListenTogetherInvite.parse(""))
        assertNull(ListenTogetherInvite.parse("   "))
        assertNull(ListenTogetherInvite.parse("roomId=abc_1"))
        assertNull(ListenTogetherInvite.parse("随便一段文字"))
        assertNull(ListenTogetherInvite.parse("【Melodia 一起听】roomId=abc/def;inviterId=42"))
        assertNull(ListenTogetherInvite.parse("【Melodia 一起听】roomId=;inviterId=42"))
        assertNull(ListenTogetherInvite.parse("【Melodia 一起听】roomId=${"a".repeat(129)};inviterId=42"))
        assertNull(ListenTogetherInvite.parse("【Melodia 一起听】roomId=abc_1;inviterId=42abc"))
        assertNull(ListenTogetherInvite.parse("a".repeat(5000)))
    }

    @Test
    fun `带歌名的邀请文案格式化、截断、展示文本与歌名提取`() {
        val withSong = ListenTogetherInvite.format("r1", 7L, "晴天")
        assertEquals("邀请你一起听「晴天」【Melodia 一起听】roomId=r1;inviterId=7", withSong)
        assertEquals(InviteTarget("r1", 7L), ListenTogetherInvite.parse(withSong))
        assertEquals("邀请你一起听「晴天」", ListenTogetherInvite.displayText(withSong))
        assertEquals("晴天", ListenTogetherInvite.extractSongName(withSong))

        val longName = "一二三四五六七八九十".repeat(5)
        val formattedLong = ListenTogetherInvite.format("r2", 8L, longName)
        val extractedLong = ListenTogetherInvite.extractSongName(formattedLong)
        assertEquals(40, extractedLong?.length)
        assertEquals(longName.take(40), extractedLong)

        val nested = ListenTogetherInvite.format("r3", 9L, "晴天「Live」")
        assertEquals("晴天「Live」", ListenTogetherInvite.extractSongName(nested))

        val blankSong = ListenTogetherInvite.format("r4", 10L, "   ")
        assertEquals("邀请你一起听【Melodia 一起听】roomId=r4;inviterId=10", blankSong)
        assertEquals("邀请你一起听", ListenTogetherInvite.displayText(blankSong))
        assertNull(ListenTogetherInvite.extractSongName(blankSong))

        val legacy = "【Melodia 一起听】roomId=r5;inviterId=11"
        assertEquals("邀请你一起听", ListenTogetherInvite.displayText(legacy))
        assertNull(ListenTogetherInvite.extractSongName(legacy))

        assertEquals("", ListenTogetherInvite.displayText(""))
        assertEquals("普通文本", ListenTogetherInvite.displayText("普通文本"))
        assertNull(ListenTogetherInvite.extractSongName("邀请你一起听「」【Melodia 一起听】roomId=r6;inviterId=12"))
        assertNull(ListenTogetherInvite.extractSongName("普通文本"))
    }
}
