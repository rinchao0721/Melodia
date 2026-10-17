package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Track
import java.util.Locale
import kotlin.math.abs

data class BatchAlbumWritableLocal(val scoped: AlbumScopedLocal, val sizeBytes: Long)

/** Batch-only encoding preference; manual album review keeps its original collision policy. */
object BatchAlbumWritePolicy {
    fun plan(locals: List<BatchAlbumWritableLocal>, tracks: List<Track>): List<AlbumScopedMatchDecision> {
        val unique = locals.distinctBy { it.scoped.candidate.uri }
        val base = AlbumScopedMatchPolicy.plan(unique.map { it.scoped }, tracks)
        val individual = unique.associate { row -> row.scoped.candidate.uri to
            AlbumScopedMatchPolicy.evaluate(row.scoped.candidate, tracks) }
        val trustedGroups = unique.filter { individual.getValue(it.scoped.candidate.uri).status == AlbumScopedMatchStatus.MATCH }
            .groupBy { individual.getValue(it.scoped.candidate.uri).match!!.id }
        val replacements = mutableMapOf<String, AlbumScopedMatchDecision>()
        trustedGroups.values.filter { it.size > 1 }.forEach { group ->
            val durations = group.map { it.scoped.candidate.durationMs }
            if (durations.any { it <= 0L } || durations.max() - durations.min() > 2_000L) return@forEach
            val selected = group.sortedWith(compareByDescending<BatchAlbumWritableLocal> { lossless(it) }
                // Container suffixes cannot prove ALAC. Ordinary formats are compared only by
                // available size/duration, an encoding preference rather than a codec assertion.
                .thenByDescending { if (lossless(it)) 0.0 else estimatedRate(it) }
                .thenBy { abs(it.scoped.candidate.durationMs - individual.getValue(it.scoped.candidate.uri).match!!.dt) }
                .thenBy { it.scoped.candidate.uri }).first()
            val selectedUri = selected.scoped.candidate.uri
            val label = selected.scoped.candidate.fileName?.takeIf(String::isNotBlank) ?: "时长最接近的本地文件"
            group.forEach { row ->
                val uri = row.scoped.candidate.uri
                val decision = individual.getValue(uri)
                replacements[uri] = if (uri == selectedUri) decision.copy(
                    reason = "同曲多个编码的时长相差不超过 2 秒，已优先选择可确认的音源"
                ) else decision.copy(status = AlbumScopedMatchStatus.REVIEW, match = null,
                    reason = "同曲多种编码，优先文件为 $label；保存成功后可沿用已有音源")
            }
        }
        // A recommendation never claims a write has succeeded. The caller verifies the selected
        // file afresh, preserves an existing canonical source, and then reconciles duplicates.
        return base.map { replacements[it.localUri] ?: it }
    }

    private fun lossless(row: BatchAlbumWritableLocal): Boolean = row.scoped.candidate.fileName
        ?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in setOf("flac", "wav", "wave", "ape", "alac", "aif", "aiff")

    private fun estimatedRate(row: BatchAlbumWritableLocal): Double =
        if (row.sizeBytes > 0L && row.scoped.candidate.durationMs > 0L) row.sizeBytes.toDouble() / row.scoped.candidate.durationMs else 0.0
}
