package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntelligencePlaylistPickerTest {

    private fun playlist(id: Long, name: String, specialType: Int = 0) =
        UserPlaylist(id = id, name = name, specialType = specialType)

    // 歌单列表的第一项通常是用户最近自建的歌单,不能用来当心动模式的上下文
    @Test
    fun `挑红心歌单而不是列表首位`() {
        val picked = pickIntelligencePlaylist(
            listOf(
                playlist(1, "想学的指弹曲"),
                playlist(2, "下去玩喜欢的音乐", LIKED_PLAYLIST_SPECIAL_TYPE),
                playlist(3, "收藏的歌单", 300)
            )
        )
        assertEquals(2L, picked?.id)
    }

    @Test
    fun `没有红心歌单时返回 null`() {
        assertNull(pickIntelligencePlaylist(listOf(playlist(1, "自建"), playlist(2, "收藏", 300))))
    }

    @Test
    fun `空歌单列表返回 null`() {
        assertNull(pickIntelligencePlaylist(emptyList()))
    }
}
