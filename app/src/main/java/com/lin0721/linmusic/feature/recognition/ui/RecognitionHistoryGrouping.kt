package com.lin0721.linmusic.feature.recognition.ui

import com.lin0721.linmusic.feature.recognition.domain.RecognitionHistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RecognitionHistoryRow(val entry: RecognitionHistoryEntry, val timeLabel: String)

data class RecognitionHistorySection(val label: String, val rows: List<RecognitionHistoryRow>)

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val dayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val yearDayFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

// 按"今天 / 昨天 / 更早"分组，保持输入顺序（最近优先）；今天昨天显示时刻，更早显示日期
internal fun groupRecognitionHistory(
    entries: List<RecognitionHistoryEntry>,
    nowMillis: Long,
    zone: ZoneId
): List<RecognitionHistorySection> {
    if (entries.isEmpty()) return emptyList()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val yesterday = today.minusDays(1)

    val todayRows = ArrayList<RecognitionHistoryRow>()
    val yesterdayRows = ArrayList<RecognitionHistoryRow>()
    val earlierRows = ArrayList<RecognitionHistoryRow>()
    for (entry in entries) {
        val dateTime = Instant.ofEpochMilli(entry.recognizedAt).atZone(zone)
        val date: LocalDate = dateTime.toLocalDate()
        when {
            // 系统时间被回拨时未来时间也归到今天
            !date.isBefore(today) -> todayRows += RecognitionHistoryRow(entry, dateTime.format(timeFormatter))
            date == yesterday -> yesterdayRows += RecognitionHistoryRow(entry, dateTime.format(timeFormatter))
            else -> {
                val formatter = if (date.year == today.year) dayFormatter else yearDayFormatter
                earlierRows += RecognitionHistoryRow(entry, dateTime.format(formatter))
            }
        }
    }
    return listOf(
        RecognitionHistorySection("今天", todayRows),
        RecognitionHistorySection("昨天", yesterdayRows),
        RecognitionHistorySection("更早", earlierRows)
    ).filter { it.rows.isNotEmpty() }
}
