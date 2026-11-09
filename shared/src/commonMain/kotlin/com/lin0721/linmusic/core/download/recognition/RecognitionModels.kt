package com.lin0721.linmusic.core.download.recognition

import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.model.Track
import kotlinx.serialization.Serializable

// 下载文件内写入网易歌曲 id 的自定义标签名
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
    val root: String = ""
)

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

// 解析标签里的歌曲 id，非正整数视为没有
fun parseSongIdTag(raw: String?): Long? = raw?.trim()?.toLongOrNull()?.takeIf { it > 0 }
