package com.lin0721.linmusic.core.download.recognition

import com.lin0721.linmusic.core.model.qualityRank
import kotlin.math.abs

private const val DURATION_TOLERANCE_MS = 3000L

// 预处理后的本地文件：带 id 的按 id 分组，其余按归一化歌名分组，查找不必逐条比较
class PreparedEntries(entries: List<LocalAudioEntry>) {

    class Untagged(val entry: LocalAudioEntry, val artists: Set<String>)

    val byTag: Map<Long, List<LocalAudioEntry>>
    val byTitle: Map<String, List<Untagged>>

    init {
        val distinct = entries.distinctBy { it.uri }
        byTag = distinct.filter { it.songIdTag != null }.groupBy { it.songIdTag!! }
        // 带了 id 的文件不参与模糊匹配
        byTitle = distinct.asSequence()
            .filter { it.songIdTag == null }
            .mapNotNull { entry ->
                val title = TrackNameNormalizer.normalize(entry.title)
                val artists = TrackNameNormalizer.splitArtists(entry.artist)
                if (title.isEmpty() || artists.isEmpty()) null else title to Untagged(entry, artists)
            }
            .groupBy({ it.first }, { it.second })
    }

    companion object {
        val EMPTY = PreparedEntries(emptyList())
    }
}

// 本地文件与网易歌曲的对应：标签带 id 的精确命中，其余要求歌名一致、歌手相交且时长相近
object DownloadedFileMatcher {

    fun match(
        targets: List<RecognitionTarget>,
        entries: List<LocalAudioEntry>,
        claimedUris: Set<String> = emptySet()
    ): Map<Long, LocalAudioEntry> = matchPrepared(targets, listOf(PreparedEntries(entries)), claimedUris)

    // 返回 songId → 文件；claimedUris 为已被下载记录占用的文件，不再分配
    fun matchPrepared(
        targets: List<RecognitionTarget>,
        sources: List<PreparedEntries>,
        claimedUris: Set<String> = emptySet()
    ): Map<Long, LocalAudioEntry> {
        val distinctTargets = targets.filter { it.songId > 0 }.distinctBy { it.songId }
        if (distinctTargets.isEmpty() || sources.isEmpty()) return emptyMap()

        val result = HashMap<Long, LocalAudioEntry>()
        val usedUris = HashSet<String>(claimedUris)

        // 标签 id 精确命中，同一首有多个文件时取音质最高
        for (target in distinctTargets) {
            val best = sources.asSequence()
                .flatMap { it.byTag[target.songId].orEmpty() }
                .filter { it.uri !in usedUris }
                .maxWithOrNull(compareBy { qualityRank(it.quality) })
                ?: continue
            result[target.songId] = best
            usedUris += best.uri
        }

        val pending = distinctTargets.filter { it.songId !in result }
        if (pending.isEmpty()) return result

        // 每个文件只归属时长最接近的一首
        class Owner(val entry: LocalAudioEntry, var target: RecognitionTarget, var diff: Long)
        val owners = LinkedHashMap<String, Owner>()
        for (target in pending) {
            val title = TrackNameNormalizer.normalize(target.title)
            if (title.isEmpty()) continue
            val artists = target.artists.flatMapTo(HashSet()) { TrackNameNormalizer.splitArtists(it) }
            if (artists.isEmpty()) continue
            for (source in sources) {
                for (candidate in source.byTitle[title].orEmpty()) {
                    val entry = candidate.entry
                    if (entry.uri in usedUris) continue
                    if (candidate.artists.none(artists::contains)) continue
                    if (!durationClose(entry.durationMs, target.durationMs)) continue
                    val diff = durationDiff(entry.durationMs, target.durationMs)
                    val owner = owners[entry.uri]
                    if (owner == null) {
                        owners[entry.uri] = Owner(entry, target, diff)
                    } else if (diff < owner.diff) {
                        owner.target = target
                        owner.diff = diff
                    }
                }
            }
        }
        // 一首有多个文件时取音质最高，同音质取时长最接近
        owners.values.groupBy { it.target.songId }.forEach { (songId, candidates) ->
            val best = candidates.sortedWith(
                compareByDescending<Owner> { qualityRank(it.entry.quality) }.thenBy { it.diff }
            ).first()
            result[songId] = best.entry
        }
        return result
    }

    // 任一方读不到时长时不比较
    private fun durationClose(a: Long, b: Long): Boolean = a <= 0 || b <= 0 || abs(a - b) <= DURATION_TOLERANCE_MS

    private fun durationDiff(a: Long, b: Long): Long = if (a <= 0 || b <= 0) DURATION_TOLERANCE_MS else abs(a - b)
}
