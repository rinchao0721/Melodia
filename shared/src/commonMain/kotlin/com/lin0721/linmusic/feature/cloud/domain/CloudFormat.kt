package com.lin0721.linmusic.feature.cloud.domain

import java.util.Locale

private const val KB = 1024.0
private const val MB = KB * 1024
private const val GB = MB * 1024

// 单曲文件大小，自适应 KB/MB/GB
fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "0 KB"
    bytes < MB -> String.format(Locale.US, "%.0f KB", bytes / KB)
    bytes < GB -> String.format(Locale.US, "%.1f MB", bytes / MB)
    else -> String.format(Locale.US, "%.1f GB", bytes / GB)
}

// 容量条巨型数字用，取整 GB，不带单位（单位由外层文案单独给出）
fun formatWholeGigabytes(bytes: Long): Int = (bytes / GB).toInt()

// 上传时间转相对文案，超过一周退化成日期。云盘列表不做按天分组，独立实现，不复用
// feature/recent 那套按天分组+时钟时间的方案（两边语义已经不同）
fun formatUploadedAt(timeMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (timeMs <= 0) return ""
    val diff = nowMs - timeMs
    if (diff < 0) return "刚刚"

    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        hours < 24 -> "$hours 小时前"
        days < 7 -> "$days 天前"
        else -> formatUploadedDate(timeMs, nowMs)
    }
}

private fun formatUploadedDate(timeMs: Long, nowMs: Long): String {
    val target = java.util.Calendar.getInstance().apply { timeInMillis = timeMs }
    val now = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    val month = target.get(java.util.Calendar.MONTH) + 1
    val day = target.get(java.util.Calendar.DAY_OF_MONTH)
    return if (target.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)) {
        String.format(Locale.US, "%02d-%02d", month, day)
    } else {
        String.format(Locale.US, "%d-%02d-%02d", target.get(java.util.Calendar.YEAR), month, day)
    }
}
