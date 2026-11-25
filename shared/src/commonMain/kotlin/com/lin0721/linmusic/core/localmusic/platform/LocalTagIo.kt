package com.lin0721.linmusic.core.localmusic.platform

data class LocalAudioTags(
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val durationMs: Long = 0L,
    val qualityLevel: String? = null,
    val artwork: ByteArray? = null,
    val embeddedLyrics: String? = null
)

interface LocalTagIo {
    suspend fun readTags(location: String): LocalAudioTags?
    suspend fun readArtwork(location: String): ByteArray?
    suspend fun readEmbeddedLyrics(location: String): String?
    suspend fun writeTags(location: String, tags: LocalAudioTags): Boolean
}
