package com.lin0721.linmusic.core.download

import com.lin0721.linmusic.core.model.qualityRank
import kotlinx.serialization.Serializable

// 下载记录实体；mediaStoreUri 在 Android 为 content Uri，桌面端为文件绝对路径
@Serializable
data class DownloadRecord(
    val songId: Long,
    val mediaStoreUri: String,
    val quality: String,
    val downloadedAt: Long,
    val fileSize: Long,
    val songName: String = "",
    val artistName: String = "",
    // 发起下载时请求的音质，服务端降级下发时据此判断无需重下
    val requestedLevel: String = "",
    // 扫描识别补登记的已有文件，不再按音质重下
    val recognized: Boolean = false
)

// 实际下发或当初请求的档位不低于目标即满足
fun DownloadRecord.satisfies(level: String): Boolean {
    val target = qualityRank(level)
    if (target < 0) return quality == level || requestedLevel == level
    return maxOf(qualityRank(quality), qualityRank(requestedLevel)) >= target
}

// 下载前是否可以跳过：识别出的已有文件一律跳过，避免无权覆盖时生成副本
fun DownloadRecord.skipsDownloadAt(level: String): Boolean = recognized || satisfies(level)
