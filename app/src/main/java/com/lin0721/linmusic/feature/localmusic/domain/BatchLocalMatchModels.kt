package com.lin0721.linmusic.feature.localmusic.domain

import com.lin0721.linmusic.core.model.Track

enum class BatchLocalMatchStatus { PENDING, MATCHED, ALREADY_LINKED, NO_MATCH, NEEDS_CONFIRMATION, ERROR }

data class BatchLocalMatchRow(
    val local: LocalTrack,
    val query: String,
    val status: BatchLocalMatchStatus = BatchLocalMatchStatus.PENDING,
    val matchedTrack: Track? = null,
    val candidates: List<Track> = emptyList(),
    val reason: String = "",
    val updatedAtMs: Long = 0L,
    val usesExistingSource: Boolean = false
)

data class BatchLocalMatchState(
    val rows: List<BatchLocalMatchRow> = emptyList(),
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null
) {
    val totalCount: Int get() = rows.size
    val processedCount: Int get() = rows.count { it.status != BatchLocalMatchStatus.PENDING }
    fun count(status: BatchLocalMatchStatus): Int = rows.count { it.status == status }
}

/** A batch search never replaces a user's confirmed reference or an explicitly excluded song. */
enum class BatchAssociationSaveStatus { SAVED, ALREADY_LINKED, CONFLICT, UNREADABLE, CHANGED }
data class BatchAssociationSaveResult(val status: BatchAssociationSaveStatus, val reason: String)
