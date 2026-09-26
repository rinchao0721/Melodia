package com.lin0721.linmusic.feature.listendata.domain

import java.util.Calendar
import java.util.Locale

// 服务端返回的时段是乱序的，按一天的自然顺序重排
private val PERIOD_ORDER = listOf(
    "early_morning", "morning", "noon", "afternoon", "night", "deep_night"
)

private val PERIOD_LABELS = mapOf(
    "early_morning" to "凌晨",
    "morning" to "早上",
    "noon" to "中午",
    "afternoon" to "下午",
    "night" to "晚上",
    "deep_night" to "深夜"
)

private val WEEKDAY_LABELS = arrayOf("日", "一", "二", "三", "四", "五", "六")

// 取百分比重新组装
private val PERCENT_REGEX = Regex("""(\d{1,3})\s*%""")

fun sortedPeriodKeys(): List<String> = PERIOD_ORDER

fun periodLabel(period: String): String = PERIOD_LABELS[period] ?: period

fun periodSortIndex(period: String): Int =
    PERIOD_ORDER.indexOf(period).let { if (it < 0) PERIOD_ORDER.size else it }

// 秒 → 小时，用于累计时长这类大数字
fun toHours(seconds: Long): Long = if (seconds <= 0) 0 else seconds / 3600

// 秒 → 保留一位小数的小时，用于历年对比
fun toHoursText(seconds: Long): String =
    if (seconds <= 0) "0" else String.format(Locale.US, "%.1f", seconds / 3600.0)

fun cleanBeyondPercentText(raw: String): String {
    val percent = PERCENT_REGEX.find(raw)?.groupValues?.getOrNull(1) ?: return ""
    return "超过了 $percent% 的用户"
}

// 「2000」→「2000s」，服务端偶尔给空值
fun ageLabel(age: String): String = if (age.isBlank()) "" else "${age}s"

// 「2026-08-23」→ 星期几；解析失败退回月日
fun weekdayLabel(period: String): String {
    val parts = period.split("-")
    if (parts.size != 3) return period
    val year = parts[0].toIntOrNull() ?: return period
    val month = parts[1].toIntOrNull() ?: return period
    val day = parts[2].toIntOrNull() ?: return period
    return runCatching {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
        }
        WEEKDAY_LABELS[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }.getOrElse { monthDayLabel(period) }
}

// 「2026-08-23」→「23」。月视图一行三十根柱子，每格只有几 dp，带月份的标签会被截断
fun monthDayLabel(period: String): String {
    val day = period.split("-").getOrNull(2)?.trimStart('0').orEmpty()
    return day.ifBlank { period }
}

// 选中某根柱子时展示的完整日期。周视图给星期，月视图给月日
fun fullWeekdayLabel(period: String): String {
    val label = weekdayLabel(period)
    return if (label.length == 1) "周$label" else label
}

fun fullMonthDayLabel(period: String): String {
    val parts = period.split("-")
    val month = parts.getOrNull(1)?.trimStart('0')
    val day = parts.getOrNull(2)?.trimStart('0')
    return if (month.isNullOrBlank() || day.isNullOrBlank()) period else "${month}月${day}日"
}
