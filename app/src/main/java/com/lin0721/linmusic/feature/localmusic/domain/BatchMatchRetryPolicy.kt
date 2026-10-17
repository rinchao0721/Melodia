package com.lin0721.linmusic.feature.localmusic.domain

/** Requeue unsuccessful results only at an explicit user-start boundary, never within the runner. */
object BatchMatchRetryPolicy {
    fun shouldRetryOnStart(status: BatchLocalMatchStatus): Boolean = when (status) {
        BatchLocalMatchStatus.PENDING, BatchLocalMatchStatus.NO_MATCH,
        BatchLocalMatchStatus.NEEDS_CONFIRMATION, BatchLocalMatchStatus.ERROR -> true
        BatchLocalMatchStatus.MATCHED, BatchLocalMatchStatus.ALREADY_LINKED -> false
    }

    fun statusForExplicitStart(status: BatchLocalMatchStatus): BatchLocalMatchStatus =
        if (shouldRetryOnStart(status)) BatchLocalMatchStatus.PENDING else status
}
