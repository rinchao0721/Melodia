package com.lin0721.linmusic.feature.localmusic.ui

/** The selected filter stays unchanged; after a saved row, show the next row in that filter. */
internal fun nextBatchReportIndex(previousIndex: Int, completedStillVisible: Boolean, visibleCount: Int): Int? {
    if (visibleCount == 0) return null
    return (previousIndex.coerceAtLeast(0) + if (completedStillVisible) 1 else 0).coerceAtMost(visibleCount - 1)
}

internal fun nextBatchReportIndex(
    previousIndex: Int,
    completedStillVisible: Boolean,
    visibleUris: List<String>,
    nextLocalUri: String?
): Int? = nextLocalUri?.let { visibleUris.indexOf(it).takeIf { index -> index >= 0 } }
    ?: nextBatchReportIndex(previousIndex, completedStillVisible, visibleUris.size)

internal data class BatchReportCompletion(val localUri: String, val previousIndex: Int, val nextLocalUri: String? = null)
