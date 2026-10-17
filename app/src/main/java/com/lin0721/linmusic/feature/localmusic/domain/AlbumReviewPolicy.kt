package com.lin0721.linmusic.feature.localmusic.domain

/** Album review is independent of how many individual playback references have been saved. */
enum class AlbumCompletion {
    NONE,
    ALL_SONGS_LINKED,
    MANUALLY_REVIEWED,
}

object AlbumReviewPolicy {
    fun completion(
        currentFingerprint: String,
        reviewedFingerprint: String?,
        linkedSongs: Int,
        totalSongs: Int,
    ): AlbumCompletion = when {
        currentFingerprint.isNotBlank() && reviewedFingerprint == currentFingerprint ->
            AlbumCompletion.MANUALLY_REVIEWED
        totalSongs > 0 && linkedSongs == totalSongs -> AlbumCompletion.ALL_SONGS_LINKED
        else -> AlbumCompletion.NONE
    }

    /** A cancelled/failed operation, or an album edited during review, cannot create a confirmation. */
    fun canConfirm(
        reviewedFingerprint: String,
        currentFingerprint: String?,
        stopped: Boolean,
        saveFailed: Boolean,
    ): Boolean = reviewedFingerprint.isNotBlank() && currentFingerprint == reviewedFingerprint &&
        !stopped && !saveFailed
}
