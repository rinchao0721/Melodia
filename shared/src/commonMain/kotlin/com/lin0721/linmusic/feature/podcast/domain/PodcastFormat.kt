package com.lin0721.linmusic.feature.podcast.domain

import java.util.Calendar
import java.util.Locale

// 节目时长：服务端给毫秒，超过一小时才补时位
fun formatProgramDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

// 收听人数，过万折算成「万」
fun formatListenerCount(count: Long): String = when {
    count <= 0 -> ""
    count >= 100_000_000 -> "${count / 100_000_000} 亿人听过"
    count >= 10_000 -> String.format(Locale.US, "%.1f 万人听过", count / 10_000.0)
    else -> "$count 人听过"
}

// 订阅数，用于电台卡副标题
fun formatSubCount(count: Long): String = when {
    count <= 0 -> ""
    count >= 100_000_000 -> "${count / 100_000_000} 亿订阅"
    count >= 10_000 -> String.format(Locale.US, "%.1f 万订阅", count / 10_000.0)
    else -> "$count 订阅"
}

// 发布日期。跨年才带年份，当年只显示月日
fun formatProgramDate(timeMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (timeMs <= 0) return ""
    val target = Calendar.getInstance().apply { timeInMillis = timeMs }
    val now = Calendar.getInstance().apply { timeInMillis = nowMs }
    val month = target.get(Calendar.MONTH) + 1
    val day = target.get(Calendar.DAY_OF_MONTH)
    return if (target.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
        String.format(Locale.US, "%02d-%02d", month, day)
    } else {
        String.format(Locale.US, "%d-%02d-%02d", target.get(Calendar.YEAR), month, day)
    }
}
