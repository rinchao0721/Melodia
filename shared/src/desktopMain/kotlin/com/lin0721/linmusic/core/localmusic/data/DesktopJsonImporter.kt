package com.lin0721.linmusic.core.localmusic.data

import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistDao
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistEntity
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistRemovedEntity
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistSourceEntity
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistTrackEntity
import com.lin0721.linmusic.core.localmusic.db.LocalTrackDao
import com.lin0721.linmusic.core.localmusic.db.LocalTrackEntity
import com.lin0721.linmusic.core.localmusic.domain.LocalScanFilter
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.folderPathOf
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TAG = "DesktopJsonImporter"

@Serializable
internal data class LegacyDesktopLibraryData(
    val playlists: List<LegacyDesktopPlaylist> = emptyList(),
    val tracks: List<LegacyDesktopTrack> = emptyList(),
    val filter: LegacyDesktopFilter = LegacyDesktopFilter(),
    val builtins: List<LegacyDesktopBuiltinState> = emptyList(),
    val qualityVersion: Int = 0
)

@Serializable
internal data class LegacyDesktopTrack(
    val path: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val durationMs: Long = 0,
    val size: Long = 0,
    val lastModified: Long = 0,
    val hasCover: Boolean = false,
    val songId: Long? = null,
    val matchState: String = "PENDING",
    val quality: String? = null
)

@Serializable
internal data class LegacyDesktopPlaylist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val isPinned: Boolean = false,
    val lastPlayedAt: Long = 0,
    val folders: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val removed: List<String> = emptyList()
)

@Serializable
internal data class LegacyDesktopFilter(
    val minDurationSec: Int = 60,
    val excludedFolders: List<String> = emptyList()
)

@Serializable
internal data class LegacyDesktopBuiltinState(
    val id: Long,
    val isPinned: Boolean = false,
    val lastPlayedAt: Long = 0
)

data class DesktopImportResult(
    val imported: Boolean,
    val filter: LocalScanFilter? = null
)

class DesktopJsonImporter(
    private val trackDao: LocalTrackDao,
    private val playlistDao: LocalPlaylistDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    suspend fun importIfNeeded(jsonFile: File): DesktopImportResult {
        if (!jsonFile.isFile) return DesktopImportResult(imported = false)
        if (trackDao.count() > 0 || playlistDao.getPlaylists().isNotEmpty()) {
            return DesktopImportResult(imported = false)
        }

        return try {
            val data = json.decodeFromString(LegacyDesktopLibraryData.serializer(), jsonFile.readText(Charsets.UTF_8))

            val trackEntities = data.tracks.map { t ->
                LocalTrackEntity(
                    location = t.path,
                    mediaStoreId = null,
                    songId = t.songId,
                    title = t.title,
                    artist = t.artist,
                    album = t.album.takeIf { it.isNotBlank() },
                    durationMs = t.durationMs,
                    sizeBytes = t.size,
                    path = t.path,
                    dateAddedMs = t.lastModified,
                    dateModifiedMs = t.lastModified,
                    source = LocalTrackSource.EXTERNAL.name,
                    albumArtist = null,
                    year = t.year.takeIf { it > 0 },
                    trackNumber = t.trackNumber.takeIf { it > 0 },
                    qualityLevel = t.quality?.takeIf { it.isNotBlank() },
                    matchState = t.matchState,
                    matchedSongId = t.songId,
                    hasEmbeddedCover = t.hasCover,
                    folder = folderPathOf(t.path)
                )
            }
            if (trackEntities.isNotEmpty()) {
                trackDao.upsert(trackEntities)
            }

            val knownLocations = trackEntities.mapTo(HashSet()) { it.location }

            for (pl in data.playlists) {
                val newId = playlistDao.insertPlaylist(
                    LocalPlaylistEntity(
                        name = pl.name,
                        createdAt = pl.createdAt,
                        updatedAt = pl.createdAt,
                        isPinned = pl.isPinned,
                        lastPlayedAt = pl.lastPlayedAt
                    )
                )
                if (pl.folders.isNotEmpty()) {
                    playlistDao.insertSources(
                        pl.folders.distinct().map { LocalPlaylistSourceEntity(newId, it) }
                    )
                }
                val validFiles = pl.files.distinct().filter { it in knownLocations }
                if (validFiles.isNotEmpty()) {
                    playlistDao.insertEntries(
                        validFiles.mapIndexed { idx, loc -> LocalPlaylistTrackEntity(newId, loc, idx) }
                    )
                }
                if (pl.removed.isNotEmpty()) {
                    playlistDao.insertRemoved(
                        pl.removed.distinct().map { LocalPlaylistRemovedEntity(newId, it) }
                    )
                }
            }

            val bakFile = File(jsonFile.parentFile, "${jsonFile.name}.bak")
            Files.move(jsonFile.toPath(), bakFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

            DesktopImportResult(
                imported = true,
                filter = LocalScanFilter(
                    minDurationSec = data.filter.minDurationSec,
                    excludedFolders = data.filter.excludedFolders.toSet()
                )
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "导入 local_library.json 失败，保留原文件等待下次重试", e)
            DesktopImportResult(imported = false)
        }
    }
}
