package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
import kotlinx.serialization.Serializable

// 单档位酷狗音质权益信息（对照 module/privilege_lite.js）
@Serializable
data class KugouQualityItem(
    val quality: String,
    val unifiedQuality: UnifiedAudioQuality? = null,
    val exists: Boolean = false,
    val level: Int = 0,
    val bitrate: Int = 0,
    val extname: String = "",
    val hash: String = "",
    val fileSize: Long = 0L,
    val privilege: Int = -1,
    val status: Int = 0,
    val payType: Int = -1,
    val price: Int = 0,
    val failProcess: Int = 0,
    val needVip: Boolean = false,
    val obtainable: Boolean = false,
    val freeListen: Boolean = false,
    val requireVipType: String = "",
    val message: String = ""
)

// 单首歌曲的酷狗音质权益聚合结果
@Serializable
data class KugouPrivilegeResult(
    val hash: String,
    val albumId: String = "",
    val albumAudioId: Long = 0L,
    val mixSongId: Long = 0L,
    val name: String = "",
    val singerName: String = "",
    val albumName: String = "",
    val durationMs: Long = 0L,
    val coverUrl: String = "",
    val freeSong: Boolean = false,
    val freeListen: Boolean = false,
    val qualities: List<KugouQualityItem> = emptyList(),
    val freeQualities: List<String> = emptyList(),
    val vipQualities: List<String> = emptyList(),
    val superVipQualities: List<String> = emptyList(),
    val blockedQualities: List<String> = emptyList(),
    val missingQualities: List<String> = emptyList(),
    val bestFreeQuality: String = "",
    // 该歌曲在酷狗库中存在的最高音质映射（用于音质角标与选质）
    val highestQuality: UnifiedAudioQuality? = null,
    // relate_goods 中各音质档位（如 "128"/"320"/"flac"/"high"/"viper_tape"）对应的 hash
    val qualityHashes: Map<String, String> = emptyMap(),
    val ogg128Hash: String? = null,
    val ogg320Hash: String? = null
) {
    fun hashForQuality(quality: UnifiedAudioQuality): String? =
        qualityHashes[quality.kugouCode]
            ?: qualities.firstOrNull { it.unifiedQuality == quality && it.exists && it.hash.isNotBlank() }?.hash
}

// 酷狗播放直链解析结果（覆盖 /v6/priv_url 与 /v5/url 回退链路）
@Serializable
data class KugouSongUrlResult(
    val hash: String,
    val url: String = "",
    val backupUrls: List<String> = emptyList(),
    val quality: String = "",
    val unifiedQuality: UnifiedAudioQuality? = null,
    val extName: String = "",
    val fileSize: Long = 0L,
    val bitRate: Int = 0,
    val durationMs: Long = 0L,
    val isFreeTrial: Boolean = false,
    val volume: Double? = null,
    val volumePeak: Double? = null,
    val fromLegacyFallback: Boolean = false
)

// 酷狗音频元信息（对照 module/audio.js /v1/audio/audio）
@Serializable
data class KugouAudioMetadataDto(
    val hash: String,
    val audioId: Long = 0L,
    val albumId: String = "",
    val albumAudioId: Long = 0L,
    val songName: String = "",
    val authorName: String = "",
    val albumName: String = "",
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val bitRate: Int = 0,
    val extName: String = "",
    val coverUrl: String = ""
)

// 酷狗歌词搜索候选（对照 module/search_lyric.js）
@Serializable
data class KugouLyricCandidateDto(
    val id: String,
    val accessKey: String,
    val score: Int = 0,
    val song: String = "",
    val singer: String = "",
    val durationMs: Long = 0L,
    val language: String = "",
    val fmt: String = "krc"
)

// 酷狗歌词下载与解码结果（对照 module/lyric.js）
@Serializable
data class KugouLyricDownloadResult(
    val id: String,
    val fmt: String,
    val contentType: Int? = null,
    val rawContentBase64: String = "",
    val decodedContent: String = ""
)

// 酷狗云端听歌历史单条记录（对照 module/user_history.js）
@Serializable
data class KugouPlayHistoryItem(
    val mixSongId: Long = 0L,
    val hash: String = "",
    val songName: String = "",
    val singerName: String = "",
    val albumId: String = "",
    val albumName: String = "",
    val albumAudioId: Long = 0L,
    val playedAtSec: Long = 0L,
    val playCount: Int = 1,
    val durationMs: Long = 0L,
    val coverUrl: String = ""
)

// 酷狗云端听歌历史分页结果
@Serializable
data class KugouPlayHistoryPage(
    val bp: String? = null,
    val hasMore: Boolean = false,
    val items: List<KugouPlayHistoryItem> = emptyList()
)
