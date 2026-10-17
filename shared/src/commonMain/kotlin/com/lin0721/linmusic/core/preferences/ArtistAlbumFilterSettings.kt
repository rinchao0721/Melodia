package com.lin0721.linmusic.core.preferences

/** Shared defaults for the artist album count filter and its persisted setting. */
object ArtistAlbumFilterSettings {
    const val DEFAULT_MIN_TRACKS = 0
    const val MIN_TRACKS = 0
    const val MAX_TRACKS = 100

    fun normalizeMinTracks(value: Int): Int = value.coerceIn(MIN_TRACKS, MAX_TRACKS)
}
