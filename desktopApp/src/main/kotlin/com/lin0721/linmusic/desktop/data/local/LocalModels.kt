package com.lin0721.linmusic.desktop.data.local

import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest

const val DEFAULT_MIN_DURATION_SEC = 60

// 内置歌单 id；用户歌单用创建时间戳，恒为正
const val LOCAL_DOWNLOADS_ID = -1L
const val LOCAL_ALL_TRACKS_ID = -2L

// 只有 PENDING 会被自动匹配；用户手动匹配或解除后不再自动改动
@Serializable
enum class LocalMatchState { PENDING, AUTO, MANUAL, NOT_FOUND, DETACHED }

@Serializable
data class LocalTrack(
    val path: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val year: Int = 0,
    val trackNumber: Int = 0,
    // 0 表示读不出时长
    val durationMs: Long = 0,
    val size: Long = 0,
    val lastModified: Long = 0,
    val hasCover: Boolean = false,
    val songId: Long? = null,
    val matchState: LocalMatchState = LocalMatchState.PENDING
) {
    // 未匹配时的播放队列占位 id
    val placeholderId: Long by lazy { localPlaceholderId(path) }

    val queueSongId: Long get() = songId ?: placeholderId
}

@Serializable
data class LocalPlaylist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val isPinned: Boolean = false,
    val lastPlayedAt: Long = 0,
    val folders: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    // 从歌单移除的文件夹内歌曲，重扫后不再纳入
    val removed: List<String> = emptyList()
)

@Serializable
data class LocalScanFilter(
    val minDurationSec: Int = DEFAULT_MIN_DURATION_SEC,
    val excludedFolders: List<String> = emptyList()
)

@Serializable
data class LocalBuiltinState(
    val id: Long,
    val isPinned: Boolean = false,
    val lastPlayedAt: Long = 0
)

@Serializable
data class LocalLibraryData(
    val playlists: List<LocalPlaylist> = emptyList(),
    val tracks: List<LocalTrack> = emptyList(),
    val filter: LocalScanFilter = LocalScanFilter(),
    val builtins: List<LocalBuiltinState> = emptyList()
)

// 音乐库列表与侧栏展示用的歌单摘要
data class LocalPlaylistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val coverUri: String?,
    val isPinned: Boolean,
    val lastPlayedAt: Long,
    val createdAt: Long
) {
    val isBuiltin: Boolean get() = id <= 0L
}

sealed interface LocalScanState {
    data object Idle : LocalScanState
    data class Scanning(val done: Int, val total: Int) : LocalScanState
}

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "aif", "aiff", "aifc", "wma", "ape", "dsf", "dff", "wv"
)

internal fun isAudioFile(file: File): Boolean = file.extension.lowercase() in AUDIO_EXTENSIONS

internal fun normalizePath(path: String): String = File(path).absoluteFile.normalize().path

private val caseInsensitivePaths = File.separatorChar == '\\'

internal fun samePath(a: String, b: String): Boolean = a.equals(b, ignoreCase = caseInsensitivePaths)

internal fun isUnder(path: String, folder: String): Boolean {
    val prefix = if (folder.endsWith(File.separatorChar)) folder else folder + File.separatorChar
    return path.startsWith(prefix, ignoreCase = caseInsensitivePaths)
}

// 取哈希高位并保证量级远离 -1、-2 这类约定的特殊 id
internal fun localPlaceholderId(path: String): Long {
    val digest = MessageDigest.getInstance("SHA-256").digest(path.lowercase().toByteArray(Charsets.UTF_8))
    var value = 0L
    for (i in 0 until 8) value = (value shl 8) or (digest[i].toLong() and 0xFF)
    return -((value and Long.MAX_VALUE) or (1L shl 40))
}
