package com.lin0721.linmusic.core.download.recognition

import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.Serializable

// 下载文件内写入歌曲标识的自定义标签名（网易云保持正整数，其余平台写入 platform:rawId）
const val SONG_ID_TAG = "MELODIA_SONG_ID"

// 扫描得到的本地音频文件；uri 在 Android 为 content Uri，桌面端为绝对路径
@Serializable
data class LocalAudioEntry(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val quality: String = "standard",
    val songIdTag: Long? = null,
    // 所属扫描目录，重扫同一目录时整体替换
    val root: String = "",
    // P2 跨平台歌曲标签；老数据缺失时按 songIdTag 推导网易云 TrackId
    val trackIdTag: TrackId? = null
) {
    val effectiveTrackIdTag: TrackId?
        get() = trackIdTag ?: songIdTag?.takeIf { it > 0L }?.let(TrackId::netease)
}

// 待识别的网易歌曲
data class RecognitionTarget(
    val songId: Long,
    val title: String,
    val artists: List<String>,
    val durationMs: Long = 0
)

fun Track.toRecognitionTarget() = RecognitionTarget(
    songId = id,
    title = name,
    artists = ar.map { it.name },
    durationMs = dt
)

fun DownloadTrackInfo.toRecognitionTarget() = RecognitionTarget(
    songId = songId,
    title = songName,
    artists = listOf(artistName),
    durationMs = durationMs
)

// 读不到标签时按 “歌手 - 歌名” 文件名拆出歌名与歌手
fun parseDownloadFileName(fileName: String): Pair<String, String>? {
    val base = fileName.substringBeforeLast('.', fileName)
    val index = base.indexOf(" - ")
    if (index <= 0 || index + 3 >= base.length) return null
    val artist = base.substring(0, index).trim()
    val title = base.substring(index + 3).trim()
    if (artist.isEmpty() || title.isEmpty()) return null
    return title to artist
}

// 解析标签里的跨平台歌曲标识，兼容纯正整数（视为网易云）与 "${platform}:${rawId}" 格式
fun parseTrackIdTag(raw: String?): TrackId? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parsed = TrackId.parseOrNull(trimmed) ?: return null
    if (parsed.platform == MusicPlatform.NETEASE) {
        val id = parsed.neteaseIdOrNull() ?: return null
        if (id <= 0L) return null
    }
    return parsed
}

// 解析标签里的网易云歌曲 id，非正整数或非网易云平台视为没有
fun parseSongIdTag(raw: String?): Long? =
    parseTrackIdTag(raw)?.neteaseIdOrNull()?.takeIf { it > 0L }
