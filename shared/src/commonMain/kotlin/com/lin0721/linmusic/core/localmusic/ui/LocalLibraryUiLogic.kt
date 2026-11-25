package com.lin0721.linmusic.core.localmusic.ui

import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.folderPathOf
import com.lin0721.linmusic.core.player.QueueState

enum class LocalLibraryTab(val label: String) {
    SONGS("歌曲"),
    ALBUMS("专辑"),
    ARTISTS("歌手"),
    FOLDERS("文件夹")
}

enum class LocalTrackSortOrder(val label: String) {
    DATE_DESC("最近添加"),
    NAME_ASC("按名称"),
    SIZE_DESC("按大小")
}

fun sortLocalTracks(tracks: List<LocalTrack>, order: LocalTrackSortOrder): List<LocalTrack> = when (order) {
    LocalTrackSortOrder.DATE_DESC -> tracks.sortedByDescending { it.dateAddedMs }
    LocalTrackSortOrder.NAME_ASC -> tracks.sortedBy { it.title.lowercase() }
    LocalTrackSortOrder.SIZE_DESC -> tracks.sortedByDescending { it.sizeBytes }
}

fun filterDownloadTracks(tracks: List<LocalTrack>): List<LocalTrack> =
    tracks.filter { it.source == LocalTrackSource.DOWNLOAD || it.source == LocalTrackSource.MELODIA_DOWNLOAD }

data class ResumePlaybackInfo(
    val trackTitle: String,
    val artist: String,
    val itemCount: Int,
    val currentIndex: Int
)

fun resolveResumePlayback(queueState: QueueState): ResumePlaybackInfo? {
    if (queueState.queue.isEmpty()) return null
    val safeIndex = queueState.currentIndex.coerceIn(0, queueState.queue.lastIndex)
    val current = queueState.queue[safeIndex]
    return ResumePlaybackInfo(
        trackTitle = current.title.ifBlank { "未知歌曲" },
        artist = current.artist,
        itemCount = queueState.queue.size,
        currentIndex = safeIndex
    )
}

data class LocalFolderNode(
    val path: String,
    val name: String,
    val childFolders: List<LocalFolderNode>,
    val tracks: List<LocalTrack>,
    val totalTrackCount: Int
)

object LocalFolderTree {
    fun buildTree(tracks: List<LocalTrack>): LocalFolderNode {
        val grouped = LinkedHashMap<String, MutableList<LocalTrack>>()
        for (track in tracks) {
            val rawFolder = track.folder ?: folderPathOf(track.path) ?: ""
            val normalized = normalizeFolderPath(rawFolder)
            grouped.getOrPut(normalized) { mutableListOf() }.add(track)
        }

        val allPaths = grouped.keys.filter { it.isNotEmpty() }.toSet()
        // 找出没有更高层父目录在 allPaths 中的顶层目录
        val topPaths = allPaths.filter { path ->
            allPaths.none { other -> other != path && isAncestor(other, path) }
        }.sortedBy { it.lowercase() }

        fun buildNode(path: String): LocalFolderNode {
            val directTracks = grouped[path].orEmpty().sortedBy { it.title.lowercase() }
            val directChildren = allPaths
                .filter { candidate ->
                    candidate != path &&
                        isAncestor(path, candidate) &&
                        allPaths.none { mid -> mid != path && mid != candidate && isAncestor(path, mid) && isAncestor(mid, candidate) }
                }
                .sortedBy { it.lowercase() }
                .map { buildNode(it) }
            val totalCount = directTracks.size + directChildren.sumOf { it.totalTrackCount }
            return LocalFolderNode(
                path = path,
                name = folderDisplayName(path),
                childFolders = directChildren,
                tracks = directTracks,
                totalTrackCount = totalCount
            )
        }

        val rootChildren = topPaths.map { buildNode(it) }
        val rootTracks = grouped[""].orEmpty().sortedBy { it.title.lowercase() }
        return LocalFolderNode(
            path = "",
            name = "全部文件夹",
            childFolders = rootChildren,
            tracks = rootTracks,
            totalTrackCount = rootTracks.size + rootChildren.sumOf { it.totalTrackCount }
        )
    }

    fun findNode(root: LocalFolderNode, path: String): LocalFolderNode? {
        val target = normalizeFolderPath(path)
        if (root.path == target) return root
        for (child in root.childFolders) {
            val found = findNode(child, target)
            if (found != null) return found
        }
        return null
    }

    fun parentPathOf(root: LocalFolderNode, path: String): String {
        val target = normalizeFolderPath(path)
        if (target.isEmpty() || root.path == target) return ""
        fun search(node: LocalFolderNode): String? {
            for (child in node.childFolders) {
                if (child.path == target) return node.path
                val found = search(child)
                if (found != null) return found
            }
            return null
        }
        return search(root) ?: ""
    }

    private fun normalizeFolderPath(raw: String): String {
        val unified = raw.trim().replace('\\', '/').trimEnd('/')
        return unified
    }

    private fun isAncestor(parent: String, child: String): Boolean {
        if (parent.isEmpty()) return child.isNotEmpty()
        return child.startsWith("$parent/")
    }

    private fun folderDisplayName(path: String): String {
        if (path.isEmpty()) return "全部文件夹"
        return path.substringAfterLast('/').ifBlank { path }
    }
}
