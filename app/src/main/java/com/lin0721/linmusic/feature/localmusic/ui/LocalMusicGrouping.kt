package com.lin0721.linmusic.feature.localmusic.ui

import com.lin0721.linmusic.core.localmusic.LocalTrack

enum class LocalMusicGroupMode(val label: String) {
    SONGS("单曲"), ARTIST("歌手"), ALBUM("专辑"), FOLDER("文件夹")
}

enum class LocalMusicSortOrder(val label: String) {
    DATE_DESC("最近添加"), NAME_ASC("按名称"), SIZE_DESC("按大小")
}

fun sortTracks(tracks: List<LocalTrack>, order: LocalMusicSortOrder): List<LocalTrack> = when (order) {
    LocalMusicSortOrder.DATE_DESC -> tracks.sortedByDescending { it.dateAddedMs }
    LocalMusicSortOrder.NAME_ASC -> tracks.sortedBy { it.title }
    LocalMusicSortOrder.SIZE_DESC -> tracks.sortedByDescending { it.sizeBytes }
}

data class LocalMusicGroup(val key: String, val tracks: List<LocalTrack>)

private const val UNKNOWN_ARTIST = "未知艺术家"
private const val UNKNOWN_ALBUM = "未知专辑"
private const val MELODIA_FOLDER_LABEL = "Melodia 下载"

fun groupTracks(tracks: List<LocalTrack>, mode: LocalMusicGroupMode): List<LocalMusicGroup> {
    if (mode == LocalMusicGroupMode.SONGS) return emptyList()
    val grouped = when (mode) {
        LocalMusicGroupMode.ARTIST -> tracks.groupBy { it.artist.ifBlank { UNKNOWN_ARTIST } }
        LocalMusicGroupMode.ALBUM -> tracks.groupBy { it.album?.takeIf { a -> a.isNotBlank() } ?: UNKNOWN_ALBUM }
        LocalMusicGroupMode.FOLDER -> tracks.groupBy(::folderLabel)
        LocalMusicGroupMode.SONGS -> emptyMap()
    }
    return grouped.map { (key, list) -> LocalMusicGroup(key, list) }.sortedBy { it.key }
}

// 提取文件夹展示标签
private fun folderLabel(track: LocalTrack): String {
    val path = track.path ?: return MELODIA_FOLDER_LABEL
    val parent = path.substringBeforeLast('/', "")
    return parent.substringAfterLast('/').ifBlank { "根目录" }
}
