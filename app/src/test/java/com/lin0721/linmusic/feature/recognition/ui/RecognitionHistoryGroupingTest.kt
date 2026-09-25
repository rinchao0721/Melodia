package com.lin0721.linmusic.feature.recognition.ui

import com.lin0721.linmusic.feature.recognition.domain.RecognitionHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecognitionHistoryGroupingTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 9, 25, 22, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) = RecognitionHistoryEntry(
        id = "$year-$month-$day-$hour-$minute",
        songId = 1,
        title = "t",
        artists = "a",
        coverUrl = "",
        startTimeMs = 0,
        recognizedAt = LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    )

    @Test
    fun `按今天昨天更早分组并保持顺序`() {
        val entries = listOf(
            at(2026, 9, 25, 21, 30),
            at(2026, 9, 25, 9, 5),
            at(2026, 9, 24, 23, 5),
            at(2026, 9, 20, 9, 15),
            at(2025, 12, 31, 8, 0)
        )

        val sections = groupRecognitionHistory(entries, now, zone)

        assertEquals(listOf("今天", "昨天", "更早"), sections.map { it.label })
        assertEquals(listOf("21:30", "09:05"), sections[0].rows.map { it.timeLabel })
        assertEquals(listOf("23:05"), sections[1].rows.map { it.timeLabel })
        assertEquals(listOf("9月20日", "2025年12月31日"), sections[2].rows.map { it.timeLabel })
    }

    @Test
    fun `空分组不输出`() {
        val sections = groupRecognitionHistory(listOf(at(2026, 9, 1, 8, 0)), now, zone)
        assertEquals(listOf("更早"), sections.map { it.label })
        assertTrue(groupRecognitionHistory(emptyList(), now, zone).isEmpty())
    }

    @Test
    fun `未来时间归入今天`() {
        val sections = groupRecognitionHistory(listOf(at(2026, 9, 26, 1, 0)), now, zone)
        assertEquals("今天", sections.single().label)
    }
}
