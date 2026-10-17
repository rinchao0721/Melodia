package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchStatus
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Version-tolerant report data. Playback references remain in their own authoritative store. */
internal object BatchLocalMatchRecordCodec {
    val json = Json { ignoreUnknownKeys = true }

    data class Record(val local: Local, val result: Result)

    /** Reject only this row if it is damaged or belongs to another URI, never the whole report. */
    fun decode(uri: String, localJson: String, resultJson: String): Record? = runCatching {
        val local = json.decodeFromString<Local>(localJson)
        require(local.uri == uri && uri.startsWith("content://"))
        Record(local, json.decodeFromString<Result>(resultJson))
    }.getOrNull()

    @Serializable
    data class Result(
        val query: String,
        val status: BatchLocalMatchStatus,
        val matchedTrack: Track? = null,
        val candidates: List<Track> = emptyList(),
        val reason: String = "",
        val updatedAtMs: Long = 0L,
        val usesExistingSource: Boolean = false,
    )

    @Serializable
    data class Local(
        val mediaStoreId: Long,
        val songId: Long? = null,
        val title: String,
        val artist: String,
        val album: String? = null,
        val durationMs: Long,
        val sizeBytes: Long,
        val uri: String,
        val path: String? = null,
        val dateAddedMs: Long,
        val source: LocalTrackSource,
        val albumArtist: String? = null,
        val year: Int? = null,
        val trackNumber: Int? = null,
    )
}
