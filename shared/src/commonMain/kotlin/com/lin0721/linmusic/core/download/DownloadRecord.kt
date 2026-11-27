package com.lin0721.linmusic.core.download

import com.lin0721.linmusic.core.model.qualityRank
import com.lin0721.linmusic.core.platform.TrackId
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
    val recognized: Boolean = false,
    // 带旧 id 的文件经模糊规则识别，列表以问号图标提示可能不是同一版本
    val fuzzyMatched: Boolean = false,
    // P2 跨平台歌曲标识；老数据反序列化缺失时回退 songId 推导网易云 TrackId
    val trackId: TrackId? = null,
    // 播客节目标记与所属电台 ID
    val isPodcast: Boolean = false,
    val radioId: Long = 0L
) {
    val effectiveTrackId: TrackId
        get() = trackId ?: TrackId.netease(songId.coerceAtLeast(0L))

    // 持久化去重与删除键：优先使用规范 TrackId，兼容无 trackId 且 songId < 0 的历史记录不互覆
    val storageKey: String
        get() = trackId?.canonical
            ?: if (songId >= 0L) TrackId.netease(songId).canonical else "legacy:$songId"
}

// 实际下发或当初请求的档位不低于目标即满足
fun DownloadRecord.satisfies(level: String): Boolean {
    val target = qualityRank(level)
    if (target < 0) return quality == level || requestedLevel == level
    return maxOf(qualityRank(quality), qualityRank(requestedLevel)) >= target
}

// 下载前是否可以跳过：识别出的已有文件一律跳过，避免无权覆盖时生成副本
fun DownloadRecord.skipsDownloadAt(level: String): Boolean = recognized || satisfies(level)
