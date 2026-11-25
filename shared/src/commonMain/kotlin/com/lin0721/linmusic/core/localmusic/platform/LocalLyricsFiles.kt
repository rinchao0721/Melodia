package com.lin0721.linmusic.core.localmusic.platform

interface LocalLyricsFiles {
    suspend fun readExternalLyrics(location: String, path: String?): String?
}
