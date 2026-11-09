package com.lin0721.linmusic.core.download.recognition

import com.lin0721.linmusic.core.model.qualityRank
import kotlin.math.abs

private const val DURATION_TOLERANCE_MS = 3000L

// 本地文件与网易歌曲的对应：标签带 id 的精确命中，其余要求歌名一致、歌手相交且时长相近
object DownloadedFileMatcher {

    // 返回 songId → 文件；claimedUris 为已被下载记录占用的文件，不再分配
    fun match(
        targets: List<RecognitionTarget>,
        entries: List<LocalAudioEntry>,
        claimedUris: Set<String> = emptySet()
    ): Map<Long, LocalAudioEntry> {
        val distinctTargets = targets.filter { it.songId > 0 }.distinctBy { it.songId }
        val available = entries.filter { it.uri !in claimedUris }.distinctBy { it.uri }
        if (distinctTargets.isEmpty() || available.isEmpty()) return emptyMap()

        val result = HashMap<Long, LocalAudioEntry>()
        val usedUris = HashSet<String>()

        // 标签 id 精确命中，同一首有多个文件时取音质最高
        val byTag = available.filter { it.songIdTag != null }.groupBy { it.songIdTag }
        for (target in distinctTargets) {
            val best = byTag[target.songId]?.maxWithOrNull(compareBy { qualityRank(it.quality) }) ?: continue
            result[target.songId] = best
            usedUris += best.uri
        }

        // 带了其他歌曲 id 的文件不参与模糊匹配
        val untagged = available.filter { it.songIdTag == null && it.uri !in usedUris }
        val pending = distinctTargets.filter { it.songId !in result }
        if (untagged.isEmpty() || pending.isEmpty()) return result

        val normalizedTargets = pending.map { NormalizedTarget(it) }
        // 每个文件只归属时长最接近的一首
        val entryOwner = untagged.mapNotNull { entry ->
            val title = TrackNameNormalizer.normalize(entry.title)
            if (title.isEmpty()) return@mapNotNull null
            val artists = TrackNameNormalizer.splitArtists(entry.artist)
            if (artists.isEmpty()) return@mapNotNull null
            val owner = normalizedTargets
                .filter { it.title == title && it.artists.any(artists::contains) && durationClose(entry.durationMs, it.durationMs) }
                .minByOrNull { durationDiff(entry.durationMs, it.durationMs) }
                ?: return@mapNotNull null
            entry to owner.songId
        }
        // 一首有多个文件时取音质最高，同音质取时长最接近
        entryOwner.groupBy({ it.second }, { it.first }).forEach { (songId, candidates) ->
            val targetDuration = pending.first { it.songId == songId }.durationMs
            val best = candidates.sortedWith(
                compareByDescending<LocalAudioEntry> { qualityRank(it.quality) }
                    .thenBy { durationDiff(it.durationMs, targetDuration) }
            ).first()
            result[songId] = best
        }
        return result
    }

    private class NormalizedTarget(target: RecognitionTarget) {
        val songId = target.songId
        val title = TrackNameNormalizer.normalize(target.title)
        val artists = target.artists.flatMapTo(HashSet()) { TrackNameNormalizer.splitArtists(it) }
        val durationMs = target.durationMs
    }

    // 任一方读不到时长时不比较
    private fun durationClose(a: Long, b: Long): Boolean = a <= 0 || b <= 0 || abs(a - b) <= DURATION_TOLERANCE_MS

    private fun durationDiff(a: Long, b: Long): Long = if (a <= 0 || b <= 0) DURATION_TOLERANCE_MS else abs(a - b)
}
