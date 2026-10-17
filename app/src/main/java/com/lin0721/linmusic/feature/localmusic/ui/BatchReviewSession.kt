package com.lin0721.linmusic.feature.localmusic.ui

import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchRow
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchStatus

/** A fixed pass prevents skipped rows or live report updates from repeatedly opening the same song. */
internal data class BatchReviewSession(val uris: List<String>, val index: Int = 0) {
    val currentUri: String get() = uris[index]
    val positionLabel: String get() = "${index + 1} / ${uris.size}"

    fun advance(unresolvedUris: Set<String>): BatchReviewSession? =
        ((index + 1)..uris.lastIndex).firstOrNull { uris[it] in unresolvedUris }?.let { copy(index = it) }

    companion object {
        fun start(rows: List<BatchLocalMatchRow>, filter: BatchMatchFilter, firstUri: String? = null): BatchReviewSession? {
            val unresolved = rows.filter { it.status.isUnresolved() }
            val selected = unresolved.filter { filter.accepts(it.status) }.ifEmpty { unresolved }
                .map { it.local.uri.toString() }.distinct()
            // Replacing an existing association is an explicit, single-song operation.
            if (firstUri != null && rows.any { it.local.uri.toString() == firstUri && !it.status.isUnresolved() }) {
                return BatchReviewSession(listOf(firstUri))
            }
            return fromUris(selected, firstUri)
        }

        internal fun fromUris(uris: List<String>, firstUri: String? = null): BatchReviewSession? {
            val selected = uris.distinct()
            if (selected.isEmpty()) return null
            val firstIndex = firstUri?.let(selected::indexOf)?.takeIf { it >= 0 } ?: 0
            return BatchReviewSession(selected.drop(firstIndex) + selected.take(firstIndex))
        }
    }
}

internal fun batchUnresolvedUris(rows: List<BatchLocalMatchRow>): Set<String> =
    rows.filter { it.status.isUnresolved() }.mapTo(linkedSetOf()) { it.local.uri.toString() }

internal fun BatchLocalMatchStatus.isUnresolved(): Boolean =
    this != BatchLocalMatchStatus.MATCHED && this != BatchLocalMatchStatus.ALREADY_LINKED
