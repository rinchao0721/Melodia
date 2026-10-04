package com.lin0721.linmusic.core.player.domain

enum class LyricsKind { LYRICS, PURE_MUSIC, UNAVAILABLE }

internal fun String.isPureMusicMarker(): Boolean {
    val normalized = filterNot {
        it.isWhitespace() || it in ",，。.!！:：()（）[]【】"
    }
    return normalized.equals("Instrumental", ignoreCase = true) ||
        normalized == "纯音乐" || normalized == "纯音乐请欣赏"
}

fun List<LyricLine>.lyricsKind(): LyricsKind = when {
    isEmpty() -> LyricsKind.UNAVAILABLE
    all { it.text.trim().isPureMusicMarker() } -> LyricsKind.PURE_MUSIC
    else -> LyricsKind.LYRICS
}

fun List<LyricLine>.isPureMusicLyrics(): Boolean = lyricsKind() == LyricsKind.PURE_MUSIC

fun shouldShowFloatingLyrics(
    isPlaying: Boolean,
    hasTrack: Boolean,
    lyricsKind: LyricsKind?,
    showPureMusicPreview: Boolean = false
): Boolean = isPlaying && hasTrack && when (lyricsKind) {
    LyricsKind.LYRICS -> true
    LyricsKind.PURE_MUSIC -> showPureMusicPreview
    LyricsKind.UNAVAILABLE, null -> false
}
