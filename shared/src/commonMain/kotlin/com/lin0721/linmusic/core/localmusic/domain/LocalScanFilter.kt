package com.lin0721.linmusic.core.localmusic.domain

data class LocalScanFilter(
    val minDurationSec: Int = 60,
    val excludedFolders: Set<String> = emptySet()
) {
    fun accepts(track: LocalTrack): Boolean = accepts(track.durationMs, track.folder ?: folderPathOf(track.path))

    fun accepts(durationMs: Long, folderPath: String?): Boolean {
        if (durationMs in 1 until minDurationSec * 1000L) return false
        return folderPath == null || folderPath !in excludedFolders
    }
}

data class LocalFolder(
    val path: String,
    val trackCount: Int,
    val excluded: Boolean
) {
    val name: String get() = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { path }
}

fun folderPathOf(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val normalized = path.replace('\\', '/')
    return normalized.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
}
