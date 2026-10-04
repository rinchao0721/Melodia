package com.lin0721.linmusic.core.player.domain

fun List<LyricLine>.isPureMusicLyrics(): Boolean {
    if (size != 1) return false
    val marker = first().text.trim()
    return marker.equals("Instrumental", ignoreCase = true) ||
        marker == "纯音乐" ||
        marker == "纯音乐，请欣赏" ||
        marker == "纯音乐,请欣赏"
}

fun shouldShowFloatingLyrics(
    isPlaying: Boolean,
    hasTrack: Boolean,
    lyricsResolved: Boolean,
    isPureMusic: Boolean,
    showPureMusicPreview: Boolean = false
): Boolean = isPlaying && hasTrack && lyricsResolved && (!isPureMusic || showPureMusicPreview)
