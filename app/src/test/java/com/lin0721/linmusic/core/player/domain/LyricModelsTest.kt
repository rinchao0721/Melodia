package com.lin0721.linmusic.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LyricModelsTest {

    private fun line(timeMs: Long, text: String) = LyricLine(timeMs = timeMs, text = text)

    @Test
    fun `相同时间戳的歌词行生成的key必须不同`() {
        // 逐字歌词里作词、作曲等信息行经常共享同一个时间戳（例如都标在0ms）
        val lines = listOf(line(0, "作词：某某"), line(0, "作曲：某某"), line(0, "编曲：某某"))
        val keys = lines.mapIndexed { index, l -> lyricLineKey(index, l) }
        assertEquals(3, keys.toSet().size)
    }

    @Test
    fun `不同时间戳的歌词行key也各不相同`() {
        val a = lyricLineKey(0, line(1000, "First"))
        val b = lyricLineKey(1, line(2000, "Second"))
        assertNotEquals(a, b)
    }
}
