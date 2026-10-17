package com.lin0721.linmusic.feature.localmusic.domain

import kotlinx.coroutines.CancellationException

internal sealed interface ManualAssociationCommitResult {
    data object Unreadable : ManualAssociationCommitResult
    data class Saved(val reportError: Exception? = null) : ManualAssociationCommitResult
}

/** A saved playback reference stays saved even when its separate matching report cannot be updated. */
internal suspend fun commitManualAssociation(
    link: suspend () -> Boolean,
    updateReport: suspend () -> Unit
): ManualAssociationCommitResult {
    if (!link()) return ManualAssociationCommitResult.Unreadable
    return try {
        updateReport()
        ManualAssociationCommitResult.Saved()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        ManualAssociationCommitResult.Saved(reportError = error)
    }
}
