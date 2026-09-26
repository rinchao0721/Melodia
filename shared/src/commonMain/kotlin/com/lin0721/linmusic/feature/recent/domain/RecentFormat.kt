package com.lin0721.linmusic.feature.recent.domain

import java.util.Calendar

// Calendar.DAY_OF_WEEK 从周日(1)到周六(7)，数组下标与之对齐
private val WEEKDAY_NAMES = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

// 区块内的行内时间，仅到分钟——所在日期已由分区头承担
fun formatClockTime(timeMs: Long): String {
    if (timeMs <= 0) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

// 按天分区块的头部文案，中文日期+星期，跨年补年份
fun formatDayHeaderLabel(timeMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (timeMs <= 0) return ""
    val target = Calendar.getInstance().apply { timeInMillis = timeMs }
    val now = Calendar.getInstance().apply { timeInMillis = nowMs }
    val month = target.get(Calendar.MONTH) + 1
    val day = target.get(Calendar.DAY_OF_MONTH)
    val weekday = WEEKDAY_NAMES[target.get(Calendar.DAY_OF_WEEK) - 1]
    val datePart = if (target.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
        "${month}月${day}日"
    } else {
        "${target.get(Calendar.YEAR)}年${month}月${day}日"
    }
    return "$datePart $weekday"
}
