package com.lin0721.linmusic.feature.podcast.domain

import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem

// 队列项的副标题：电台名 · 主播名
fun podcastSubtitle(radioName: String, djName: String): String = listOfNotNull(
    radioName.takeIf { it.isNotBlank() },
    djName.takeIf { it.isNotBlank() }
).joinToString(" · ")

// 节目的播放 id 是 mainSong，不是节目自身 id
fun PodcastProgram.toQueueItem(): QueueItem = QueueItem(
    songId = songId,
    title = name,
    artist = podcastSubtitle(radioName, djName),
    coverUrl = coverUrl,
    radioId = radioId
)

fun PodcastProgressEntry.toQueueItem(): QueueItem = QueueItem(
    songId = songId,
    title = title,
    artist = subtitle,
    coverUrl = coverUrl,
    radioId = radioId
)

// 听完或没有记录从头播，否则从上次进度续播
fun resumePositionOf(songId: Long, progress: Map<Long, PodcastProgressEntry>): Long {
    val entry = progress[songId] ?: return 0L
    return if (entry.isFinished) 0L else entry.positionMs
}

// 从指定一期起播，整段节目列表作为队列；起播那期有未听完的进度则续播
fun PlaybackController.playPodcastPrograms(
    programs: List<PodcastProgram>,
    index: Int,
    progress: Map<Long, PodcastProgressEntry>
) {
    if (programs.isEmpty()) return
    val start = index.coerceIn(programs.indices)
    val startProgram = programs[start]
    val source = startProgram.radioId.takeIf { it > 0L }?.let { rid ->
        PlaySource(
            kind = PlaySource.Kind.RADIO,
            id = rid,
            name = startProgram.radioName.ifBlank { "播客" },
            coverUrl = startProgram.coverUrl
        )
    }
    playQueue(
        items = programs.map { it.toQueueItem() },
        startIndex = start,
        playContext = PlaybackController.CONTEXT_PODCAST,
        source = source,
        startPositionMs = resumePositionOf(startProgram.songId, progress)
    )
}

// 继续收听：单曲队列，从上次进度起播
fun PlaybackController.resumePodcast(entry: PodcastProgressEntry) {
    val source = entry.radioId.takeIf { it > 0L }?.let { rid ->
        PlaySource(
            kind = PlaySource.Kind.RADIO,
            id = rid,
            name = entry.subtitle.substringBefore(" · ").ifBlank { "播客" },
            coverUrl = entry.coverUrl
        )
    }
    playQueue(
        items = listOf(entry.toQueueItem()),
        startIndex = 0,
        playContext = PlaybackController.CONTEXT_PODCAST,
        source = source,
        startPositionMs = if (entry.isFinished) 0L else entry.positionMs
    )
}
