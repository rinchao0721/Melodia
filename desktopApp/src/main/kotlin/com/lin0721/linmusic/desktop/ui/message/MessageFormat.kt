package com.lin0721.linmusic.desktop.ui.message

import com.lin0721.linmusic.feature.message.domain.PrivateCard
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

private val SHARE_PREFIXES = listOf(
    "分享单曲" to "单曲",
    "分享歌单" to "歌单",
    "分享专辑" to "专辑",
    "分享MV" to "MV",
    "分享分享" to "分享"
)

internal data class ConversationSummaryDisplay(
    val musicTag: String?,
    val body: String
)

internal fun formatConversationSummary(raw: String?): ConversationSummaryDisplay {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return ConversationSummaryDisplay(musicTag = null, body = "")
    for ((prefix, label) in SHARE_PREFIXES) {
        if (trimmed == prefix) {
            return ConversationSummaryDisplay(musicTag = "♪ [$label]", body = "")
        }
        val withFullColon = "$prefix："
        if (trimmed.startsWith(withFullColon)) {
            return ConversationSummaryDisplay(
                musicTag = "♪ [$label]",
                body = trimmed.removePrefix(withFullColon).trim()
            )
        }
        val withHalfColon = "$prefix:"
        if (trimmed.startsWith(withHalfColon)) {
            return ConversationSummaryDisplay(
                musicTag = "♪ [$label]",
                body = trimmed.removePrefix(withHalfColon).trim()
            )
        }
    }
    return ConversationSummaryDisplay(musicTag = null, body = trimmed)
}

internal fun isRedundantShareText(text: String, card: PrivateCard): Boolean {
    val trimmed = text.trim()
    val kindLabel = card.kind.label
    return trimmed == "分享$kindLabel" ||
        trimmed == "分享$kindLabel：${card.title}" ||
        trimmed == "分享$kindLabel:${card.title}"
}

internal fun formatConversationTime(
    timeMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    if (timeMs <= 0L) return ""
    val target = Instant.ofEpochMilli(timeMs).atZone(zone)
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val targetDate = target.toLocalDate()
    val nowDate = now.toLocalDate()
    val days = ChronoUnit.DAYS.between(targetDate, nowDate)
    return when {
        targetDate == nowDate -> target.format(TIME_FORMAT)
        days == 1L -> "昨天"
        days in 2L..6L -> weekdayLabel(targetDate.dayOfWeek)
        targetDate.year == nowDate.year -> "${targetDate.monthValue}/${targetDate.dayOfMonth}"
        else -> "${targetDate.year}/${targetDate.monthValue}/${targetDate.dayOfMonth}"
    }
}

internal fun formatChatTimeDivider(
    timeMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    if (timeMs <= 0L) return ""
    val target = Instant.ofEpochMilli(timeMs).atZone(zone)
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val targetDate = target.toLocalDate()
    val nowDate = now.toLocalDate()
    val hm = target.format(TIME_FORMAT)
    return when {
        targetDate == nowDate -> hm
        ChronoUnit.DAYS.between(targetDate, nowDate) == 1L -> "昨天 $hm"
        targetDate.year == nowDate.year -> "${targetDate.monthValue}月${targetDate.dayOfMonth}日 $hm"
        else -> "${targetDate.year}年${targetDate.monthValue}月${targetDate.dayOfMonth}日 $hm"
    }
}

private fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}
