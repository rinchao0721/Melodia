package com.lin0721.linmusic.feature.podcast.domain

import com.lin0721.linmusic.feature.podcast.data.PodcastCategoryDto
import com.lin0721.linmusic.feature.podcast.data.PodcastDjDto
import com.lin0721.linmusic.feature.podcast.data.PodcastMainSongDto
import com.lin0721.linmusic.feature.podcast.data.PodcastProgramDto
import com.lin0721.linmusic.feature.podcast.data.PodcastRadioDetailDto
import com.lin0721.linmusic.feature.podcast.data.PodcastRadioDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PodcastMapperTest {

    private val radio = PodcastRadioDto(
        id = 1231298498,
        name = "多来听听吧",
        picUrl = "http://p2.music.126.net/x/1.jpg",
        dj = PodcastDjDto(nickname = "赞多_SANTA")
    )

    private fun program(
        id: Long = 3726744457,
        songId: Long? = 3422937414,
        name: String = "你比想象中的自己更厉害",
        cover: String? = "http://p1.music.126.net/y/2.jpg",
        dj: PodcastDjDto? = null,
        radioDto: PodcastRadioDto? = radio
    ) = PodcastProgramDto(
        id = id,
        name = name,
        coverUrl = cover,
        duration = 1530384,
        createTime = 1787284800000,
        listenerCount = 5887,
        serialNum = 26,
        mainSong = songId?.let { PodcastMainSongDto(it) },
        radio = radioDto,
        dj = dj
    )

    @Test
    fun `节目映射取mainSong作为播放id`() {
        val item = listOf(program()).toPodcastPrograms().single()

        // 节目自身 id 播不了，必须是 mainSong 的 id
        assertEquals(3726744457L, item.id)
        assertEquals(3422937414L, item.songId)
        assertEquals("多来听听吧", item.radioName)
        assertEquals("赞多_SANTA", item.djName)
        assertEquals(26, item.serialNum)
    }

    @Test
    fun `没有mainSong的节目被丢弃`() {
        val items = listOf(
            program(id = 1, songId = null),
            program(id = 2, songId = 0),
            program(id = 3, songId = 999)
        ).toPodcastPrograms()

        assertEquals(listOf(3L), items.map { it.id })
    }

    @Test
    fun `节目缺封面时退回电台封面`() {
        val item = listOf(program(cover = null)).toPodcastPrograms().single()
        assertEquals("http://p2.music.126.net/x/1.jpg", item.coverUrl)
    }

    @Test
    fun `节目与电台都无封面则丢弃`() {
        val items = listOf(
            program(cover = null, radioDto = radio.copy(picUrl = null))
        ).toPodcastPrograms()

        assertTrue(items.isEmpty())
    }

    @Test
    fun `主播缺省时退回电台主播`() {
        val withOwnDj = listOf(program(dj = PodcastDjDto(nickname = "客座主播"))).toPodcastPrograms().single()
        assertEquals("客座主播", withOwnDj.djName)

        val fallback = listOf(program(dj = PodcastDjDto(nickname = ""))).toPodcastPrograms().single()
        assertEquals("赞多_SANTA", fallback.djName)
    }

    @Test
    fun `缺封面的电台被丢弃`() {
        val radios = listOf(
            radio.copy(id = 1, name = "有封面"),
            radio.copy(id = 2, name = "无封面", picUrl = null),
            radio.copy(id = 0, name = "无id")
        ).toPodcastRadios()

        assertEquals(listOf("有封面"), radios.map { it.name })
    }

    @Test
    fun `分类过滤空名`() {
        val categories = listOf(
            PodcastCategoryDto(3, "情感"),
            PodcastCategoryDto(0, "无id"),
            PodcastCategoryDto(5, " ")
        ).toPodcastCategories()

        assertEquals(listOf("情感"), categories.map { it.name })
    }

    @Test
    fun `电台简介压平换行`() {
        val detail = PodcastRadioDetailDto(
            id = 1,
            name = "多来听听吧",
            desc = "大家好！我是赞多。\n未来一段时间，\n我会分享音乐。 ",
            dj = PodcastDjDto("赞多_SANTA", "http://a/1.jpg")
        ).toPodcastRadioDetail()

        assertEquals("大家好！我是赞多。 未来一段时间， 我会分享音乐。", detail.desc)
        assertEquals("赞多_SANTA", detail.djName)
    }

    @Test
    fun `时长格式化超过一小时才补时位`() {
        assertEquals("25:30", formatProgramDuration(1530384))
        assertEquals("0:05", formatProgramDuration(5000))
        assertEquals("1:00:00", formatProgramDuration(3600000))
        assertEquals("", formatProgramDuration(0))
    }

    @Test
    fun `收听数与订阅数过万折算`() {
        assertEquals("5887 人听过", formatListenerCount(5887))
        assertEquals("1.2 万人听过", formatListenerCount(12345))
        assertEquals("", formatListenerCount(0))
        assertEquals("4.3 万订阅", formatSubCount(43284))
        assertEquals("213 订阅", formatSubCount(213))
    }

    @Test
    fun `日期跨年才带年份`() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.AUGUST, 24) }.timeInMillis
        val sameYear = Calendar.getInstance().apply { set(2026, Calendar.AUGUST, 19) }.timeInMillis
        val lastYear = Calendar.getInstance().apply { set(2025, Calendar.DECEMBER, 3) }.timeInMillis

        assertEquals("08-19", formatProgramDate(sameYear, now))
        assertEquals("2025-12-03", formatProgramDate(lastYear, now))
        assertEquals("", formatProgramDate(0, now))
    }
}
