package com.lin0721.linmusic.core.localmusic.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_track",
    indices = [
        Index("artist"),
        Index("album"),
        Index("folder")
    ]
)
data class LocalTrackEntity(
    @PrimaryKey val location: String,
    val mediaStoreId: Long?,
    val songId: Long?,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val path: String?,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    val source: String,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val qualityLevel: String? = null,
    val matchState: String = "PENDING",
    val matchedSongId: Long? = null,
    val hasEmbeddedCover: Boolean = false,
    val folder: String? = null
)
