package com.lin0721.linmusic.core.localmusic.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "local_playlist")
data class LocalPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val lastPlayedAt: Long = 0,
    val systemKind: String? = null
)

@Entity(
    tableName = "local_playlist_track",
    primaryKeys = ["playlistId", "trackLocation"],
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalTrackEntity::class,
            parentColumns = ["location"],
            childColumns = ["trackLocation"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackLocation")]
)
data class LocalPlaylistTrackEntity(
    val playlistId: Long,
    val trackLocation: String,
    val position: Int
)

@Entity(
    tableName = "local_playlist_source",
    primaryKeys = ["playlistId", "folderPath"],
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class LocalPlaylistSourceEntity(
    val playlistId: Long,
    val folderPath: String
)

@Entity(
    tableName = "local_playlist_removed",
    primaryKeys = ["playlistId", "trackLocation"],
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class LocalPlaylistRemovedEntity(
    val playlistId: Long,
    val trackLocation: String
)
