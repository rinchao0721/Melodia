package com.lin0721.linmusic.feature.localmusic.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.localmusic.LocalSongMetadata
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.feature.localmusic.domain.ManualLocalAudioAssociations
import com.lin0721.linmusic.feature.localmusic.domain.PreferredLocalAudioMatcher
import com.lin0721.linmusic.feature.localmusic.domain.LocalAudioCandidate
import com.lin0721.linmusic.feature.localmusic.domain.BatchAssociationSaveResult
import com.lin0721.linmusic.feature.localmusic.domain.BatchAssociationSaveStatus
import com.lin0721.linmusic.feature.localmusic.domain.BatchOnlineMatchPolicy
import com.lin0721.linmusic.feature.localmusic.domain.BatchOnlineMatchStatus
import com.lin0721.linmusic.feature.localmusic.domain.AlbumScopedMatchPolicy
import com.lin0721.linmusic.feature.localmusic.domain.AlbumScopedMatchStatus
import com.lin0721.linmusic.feature.localmusic.domain.resolvePreferredLocalAudio
import com.lin0721.linmusic.feature.localmusic.domain.resolveCurrentPreferredLocalAudio
import com.lin0721.linmusic.feature.localmusic.domain.LocalAudioSelectionSettings
import com.lin0721.linmusic.feature.localmusic.data.transfer.*
import com.lin0721.linmusic.feature.localmusic.data.batch.batchAssociationGuard
import java.io.ByteArrayOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private val Context.preferredLocalAudioDataStore by preferencesDataStore(name = "preferred_local_audio")

/** Only stores references. These files never become Melodia download records. */
class PreferredLocalAudioRepository(
    private val context: Context,
    private val library: LocalLibraryRepository
) {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val automaticKey = booleanPreferencesKey("automatic")
    private val excludedKey = stringSetPreferencesKey("automatic_excluded_song_ids")
    private fun linkKey(songId: Long) = stringPreferencesKey("song_uri_$songId")
    private fun durationKey(songId: Long) = longPreferencesKey("song_duration_$songId")
    private fun titleKey(songId: Long) = stringPreferencesKey("song_title_$songId")
    private fun artistKey(songId: Long) = stringPreferencesKey("song_artist_$songId")
    private fun albumKey(songId: Long) = stringPreferencesKey("song_album_$songId")
    private fun albumCoverKey(songId: Long) = stringPreferencesKey("song_album_cover_$songId")
    private fun albumIdKey(songId: Long) = longPreferencesKey("song_album_id_$songId")
    private fun localTitleKey(songId: Long) = stringPreferencesKey("linked_file_title_$songId")
    private fun localSnapshotKey(songId: Long) = stringPreferencesKey("linked_file_metadata_$songId")
    private val snapshotJson = Json { ignoreUnknownKeys = true }

    val enabled: Flow<Boolean> = context.preferredLocalAudioDataStore.data.map { it[enabledKey] ?: true }
    val automaticEnabled: Flow<Boolean> = context.preferredLocalAudioDataStore.data.map { it[automaticKey] ?: true }
    val changes: Flow<Unit> = combine(context.preferredLocalAudioDataStore.data, library.tracks) { _, _ -> Unit }
    private data class LibraryAssociationProjection(val links: Map<Long, String>, val titles: Map<Long, String>)
    private val libraryAssociationProjection = context.preferredLocalAudioDataStore.data.map { prefs ->
        val links = storedLinks(prefs)
        LibraryAssociationProjection(links, links.keys.mapNotNull { songId ->
            prefs[localTitleKey(songId)]?.let { songId to it }
        }.toMap())
    }.distinctUntilChanged()
    // A read projection only: Room's source/songId and the app's download records stay untouched.
    val associatedTracks: Flow<List<LocalTrack>> = combine(libraryAssociationProjection, library.tracks) { projection, tracks ->
        val links = projection.links
        if (links.isEmpty()) tracks else {
            val identities = HashMap<String, String?>()
            fun identity(uri: String): String? = identities.getOrPut(uri) { localAudioIdentity(context, uri) }
            val byIdentity = ManualLocalAudioAssociations.uniqueByIdentity(links, ::identity)
            val tracksByUri = tracks.associateBy { it.uri.toString() }
            val linkUris = links.values.toHashSet()
            val localTitles = links.mapNotNull { (songId, uri) ->
                projection.titles[songId] ?: tracksByUri[uri]?.title
            }
            val titleKeys = localTitles.map(ManualLocalAudioAssociations::titleKey).filter(String::isNotBlank).toHashSet()
            tracks.map { track ->
                if (track.source == LocalTrackSource.MELODIA_DOWNLOAD ||
                    (track.uri.toString() !in linkUris && localTitles.isNotEmpty() &&
                        ManualLocalAudioAssociations.titleKey(track.title) !in titleKeys)) track
                else track.copy(songId = identity(track.uri.toString())?.let { byIdentity[it] } ?: track.songId)
            }
        }
    }.flowOn(Dispatchers.IO)
    fun manualUri(songId: Long): Flow<String?> = context.preferredLocalAudioDataStore.data.map { it[linkKey(songId)] }

    suspend fun setEnabled(value: Boolean) { context.preferredLocalAudioDataStore.edit { it[enabledKey] = value } }
    suspend fun setAutomaticEnabled(value: Boolean) { context.preferredLocalAudioDataStore.edit { it[automaticKey] = value } }

    suspend fun link(songId: Long, uri: Uri, onlineTrack: Track? = null): Boolean = withContext(Dispatchers.IO) {
        if (songId <= 0L || uri.scheme != "content") return@withContext false
        try {
            // OpenDocument grants read access; retain it across restarts, without requesting write access.
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val inspected = inspectAssociationFile(context, uri.toString()) ?: return@withContext false
            val metadata = onlineTrack?.takeIf { it.id == songId &&
                (it.ar.any { artist -> artist.id > 0L } || it.al.id > 0L) }
            saveManualReference(songId, inspected, metadata)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    /** Reuse MediaStore read permission or an already persisted file/folder import grant. */
    suspend fun linkLocalTrack(track: LocalTrack, onlineTrack: Track): Boolean = withContext(Dispatchers.IO) {
        if (onlineTrack.id <= 0L || track.source == LocalTrackSource.MELODIA_DOWNLOAD || track.uri.scheme != "content") {
            return@withContext false
        }
        try {
            val inspected = inspectAssociationFile(context, track.uri.toString(), track) ?: return@withContext false
            saveManualReference(onlineTrack.id, inspected, onlineTrack)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    /**
     * Both manual entry points use one replacement transaction. A file selected before library
     * import must not keep multiple online IDs and later become ambiguous when it is scanned.
     * Descriptor inspection occurs before the transaction; only concurrent changes are rechecked.
     */
    private suspend fun saveManualReference(songId: Long, inspected: AssociationLocalCandidate, onlineTrack: Track?): Boolean {
        val linksBefore = storedLinks(context.preferredLocalAudioDataStore.data.first())
        val plannedIds = ManualLocalAudioAssociations.idsForReadableFile(linksBefore, inspected.uri, inspected.identity) {
            localAudioIdentity(context, it)
        }
        var readable = true
        context.preferredLocalAudioDataStore.edit { prefs ->
            val oldIds = ManualLocalAudioAssociations.completeReplacementIds(linksBefore, plannedIds, storedLinks(prefs),
                inspected.uri, inspected.identity) { localAudioIdentity(context, it) }
            if (localAudioIdentity(context, inspected.uri) != inspected.identity) { readable = false; return@edit }
            oldIds.forEach { prefs.remove(linkKey(it)); prefs.remove(localTitleKey(it)); prefs.remove(localSnapshotKey(it)) }
            // Explicit replacement suppresses automatic resurrection of the displaced online IDs.
            prefs[excludedKey] = (prefs[excludedKey].orEmpty() + oldIds.map(Long::toString)) - songId.toString()
            prefs[linkKey(songId)] = inspected.uri
            prefs[localTitleKey(songId)] = inspected.title
            prefs[localSnapshotKey(songId)] = snapshotJson.encodeToString(inspected.toBackupFile())
            onlineTrack?.let {
                prefs[titleKey(songId)] = it.name
                prefs[artistKey(songId)] = it.ar.joinToString(" • ") { artist -> artist.name }
                prefs[albumKey(songId)] = it.al.name
                it.al.picUrl.takeIf(String::isNotBlank)?.let { cover -> prefs[albumCoverKey(songId)] = cover }
                if (it.al.id > 0L) prefs[albumIdKey(songId)] = it.al.id
                if (it.dt > 0L) prefs[durationKey(songId)] = it.dt
            }
        }
        return readable
    }

    suspend fun unlinkLocalTrack(track: LocalTrack) = withContext(Dispatchers.IO) {
        context.preferredLocalAudioDataStore.edit { prefs ->
            val identity = localAudioIdentity(context, track.uri.toString())
            val ids = storedLinks(prefs).filter { (_, uri) ->
                uri == track.uri.toString() || (identity != null && localAudioIdentity(context, uri) == identity)
            }.keys
            ids.forEach { prefs.remove(linkKey(it)); prefs.remove(localTitleKey(it)); prefs.remove(localSnapshotKey(it)) }
            prefs[excludedKey] = prefs[excludedKey].orEmpty() + ids.map(Long::toString)
        }
    }

    /** Re-read the audio, then atomically add a unique batch result without replacing any reference. */
    suspend fun linkBatchMatch(
        track: LocalTrack,
        inspected: LocalAudioCandidate,
        onlineTrack: Track
    ): BatchAssociationSaveResult = withContext(Dispatchers.IO) {
        if (track.source == LocalTrackSource.MELODIA_DOWNLOAD || onlineTrack.id <= 0L) {
            return@withContext BatchAssociationSaveResult(BatchAssociationSaveStatus.CONFLICT, "下载记录无需批量关联")
        }
        val live = inspectAssociationFile(context, track.uri.toString(), track)
            ?: return@withContext BatchAssociationSaveResult(BatchAssociationSaveStatus.UNREADABLE, "文件不可读取，请检查授权或重新扫描")
        if (live.identity != inspected.identity || live.title != inspected.title || live.artist != inspected.artist ||
            live.album != inspected.album || live.durationMs != inspected.durationMs || live.fileName != inspected.fileName) {
            return@withContext BatchAssociationSaveResult(BatchAssociationSaveStatus.CHANGED, "搜索后文件信息发生变化，需重新核对")
        }
        val candidate = LocalAudioCandidate(live.uri, live.title, live.artist, live.durationMs, live.identity, live.album, live.fileName)
        if (BatchOnlineMatchPolicy.evaluate(candidate, listOf(onlineTrack)).status != BatchOnlineMatchStatus.MATCHED) {
            return@withContext BatchAssociationSaveResult(BatchAssociationSaveStatus.CHANGED, "重新读取的音频信息无法严格匹配，需手动核对")
        }
        var result = BatchAssociationSaveResult(BatchAssociationSaveStatus.CONFLICT, "关联冲突，原关联已保留")
        context.preferredLocalAudioDataStore.edit { prefs ->
            val guard = batchAssociationGuard(onlineTrack.id, live.uri, live.identity, prefs[excludedKey].orEmpty(), storedLinks(prefs)) {
                localAudioIdentity(context, it)
            }
            if (guard != null) {
                result = guard
                return@edit
            }
            // Check again inside the edit transaction; user actions and other import jobs cannot race this write.
            if (localAudioIdentity(context, live.uri) != live.identity) {
                result = BatchAssociationSaveResult(BatchAssociationSaveStatus.CHANGED, "写入前文件状态变化，未建立关联")
                return@edit
            }
            prefs[linkKey(onlineTrack.id)] = live.uri
            prefs[localTitleKey(onlineTrack.id)] = live.title
            prefs[localSnapshotKey(onlineTrack.id)] = snapshotJson.encodeToString(live.toBackupFile())
            prefs[titleKey(onlineTrack.id)] = onlineTrack.name
            prefs[artistKey(onlineTrack.id)] = onlineTrack.ar.joinToString(" • ") { it.name }
            prefs[albumKey(onlineTrack.id)] = onlineTrack.al.name
            onlineTrack.al.picUrl.takeIf(String::isNotBlank)?.let { prefs[albumCoverKey(onlineTrack.id)] = it }
            if (onlineTrack.al.id > 0L) prefs[albumIdKey(onlineTrack.id)] = onlineTrack.al.id
            prefs[durationKey(onlineTrack.id)] = onlineTrack.dt
            result = BatchAssociationSaveResult(BatchAssociationSaveStatus.SAVED, "唯一严格匹配，已建立关联")
        }
        result
    }

    suspend fun confirmedTrack(songId: Long): Track? = withContext(Dispatchers.IO) {
        val prefs = context.preferredLocalAudioDataStore.data.first()
        if (prefs[linkKey(songId)] == null) null else cachedTrack(prefs, songId)
    }

    /** One preference snapshot for album previews; physical aliases remain a write-time safety check. */
    val manualLinkReferences: Flow<Map<Long, String>> = context.preferredLocalAudioDataStore.data
        .map(::storedLinks).distinctUntilChanged()

    suspend fun manualLinksSnapshot(): Map<Long, String> = withContext(Dispatchers.IO) {
        storedLinks(context.preferredLocalAudioDataStore.data.first())
    }

    /** Save a uniquely matched song inside a user-confirmed album without replacing existing links. */
    suspend fun linkAlbumMatch(
        track: LocalTrack,
        onlineTracks: List<Track>,
        selected: Track,
    ): BatchAssociationSaveResult = withContext(Dispatchers.IO) {
        if (track.source == LocalTrackSource.MELODIA_DOWNLOAD || selected.id <= 0L ||
            onlineTracks.none { it.id == selected.id }) {
            return@withContext BatchAssociationSaveResult(BatchAssociationSaveStatus.CONFLICT, "此歌曲不在已确认的专辑范围内")
        }
        val live = inspectAssociationFile(context, track.uri.toString(), track)
            ?: return@withContext BatchAssociationSaveResult(BatchAssociationSaveStatus.UNREADABLE, "文件不可读取，请检查授权或重新扫描")
        val candidate = LocalAudioCandidate(live.uri, live.title, live.artist, live.durationMs, live.identity, live.album, live.fileName)
        val decision = AlbumScopedMatchPolicy.evaluate(candidate, onlineTracks)
        val onlineTrack = decision.match
        if (decision.status != AlbumScopedMatchStatus.MATCH || onlineTrack == null || onlineTrack.id != selected.id) {
            return@withContext BatchAssociationSaveResult(BatchAssociationSaveStatus.CHANGED, "重新读取后未能在专辑内唯一匹配，请手动核对")
        }
        var result = BatchAssociationSaveResult(BatchAssociationSaveStatus.CONFLICT, "关联冲突，原关联已保留")
        context.preferredLocalAudioDataStore.edit { prefs ->
            val guard = batchAssociationGuard(onlineTrack.id, live.uri, live.identity, prefs[excludedKey].orEmpty(), storedLinks(prefs)) {
                localAudioIdentity(context, it)
            }
            if (guard != null) {
                result = guard
                return@edit
            }
            if (localAudioIdentity(context, live.uri) != live.identity) {
                result = BatchAssociationSaveResult(BatchAssociationSaveStatus.CHANGED, "写入前文件状态变化，未建立关联")
                return@edit
            }
            prefs[linkKey(onlineTrack.id)] = live.uri
            prefs[localTitleKey(onlineTrack.id)] = live.title
            prefs[localSnapshotKey(onlineTrack.id)] = snapshotJson.encodeToString(live.toBackupFile())
            prefs[titleKey(onlineTrack.id)] = onlineTrack.name
            prefs[artistKey(onlineTrack.id)] = onlineTrack.ar.joinToString(" • ") { it.name }
            prefs[albumKey(onlineTrack.id)] = onlineTrack.al.name
            onlineTrack.al.picUrl.takeIf(String::isNotBlank)?.let { prefs[albumCoverKey(onlineTrack.id)] = it }
            if (onlineTrack.al.id > 0L) prefs[albumIdKey(onlineTrack.id)] = onlineTrack.al.id
            prefs[durationKey(onlineTrack.id)] = onlineTrack.dt
            result = BatchAssociationSaveResult(BatchAssociationSaveStatus.SAVED, "专辑内唯一匹配，已建立关联")
        }
        result
    }

    suspend fun confirmedPlayableTrack(songId: Long): Track? = withContext(Dispatchers.IO) {
        val prefs = context.preferredLocalAudioDataStore.data.first()
        val uri = prefs[linkKey(songId)] ?: return@withContext null
        if (isReadable(uri)) cachedTrack(prefs, songId) else null
    }

    suspend fun confirmedTracks(): Map<Long, Track> = withContext(Dispatchers.IO) {
        val prefs = context.preferredLocalAudioDataStore.data.first()
        storedLinks(prefs).keys.associateWith { songId -> cachedTrack(prefs, songId) }
    }

    /** Inspect only this file and possible same-title aliases; avoid projecting the entire scanned library per row. */
    suspend fun confirmedForLocalTrack(track: LocalTrack): Track? = confirmedForLocalTracks(listOf(track))[track.uri.toString()]

    /** A manual report update shares one preference snapshot and one identity read per distinct alias. */
    internal suspend fun confirmedForLocalTracks(tracks: List<LocalTrack>): Map<String, Track?> = withContext(Dispatchers.IO) {
        val prefs = context.preferredLocalAudioDataStore.data.first()
        val links = storedLinks(prefs)
        val titles = links.keys.mapNotNull { id -> prefs[localTitleKey(id)]?.let { id to it } }.toMap()
        ManualLocalAudioAssociations.confirmedIdsForFiles(links, titles,
            tracks.map { it.uri.toString() to it.title }) { localAudioIdentity(context, it) }
            .mapValues { (_, id) -> id?.let { cachedTrack(prefs, it) } }
    }

    /** Confirmed online metadata is reusable evidence for alternate encodings; no reference is changed. */
    suspend fun confirmedTracksForTitle(localTitle: String): List<Track> = withContext(Dispatchers.IO) {
        val prefs = context.preferredLocalAudioDataStore.data.first()
        storedLinks(prefs).keys.filter { songId ->
            PreferredLocalAudioMatcher.sameLocalTitle(prefs[titleKey(songId)].orEmpty(), localTitle)
        }.map { cachedTrack(prefs, it) }
    }

    suspend fun confirmedTracksForCandidate(local: LocalAudioCandidate): List<Track> = withContext(Dispatchers.IO) {
        val prefs = context.preferredLocalAudioDataStore.data.first()
        val resolvedTitle = com.lin0721.linmusic.feature.localmusic.domain.LocalMatchMetadata.resolve(local).title
        storedLinks(prefs).keys.filter { songId ->
            val title = prefs[titleKey(songId)].orEmpty()
            PreferredLocalAudioMatcher.sameLocalTitle(title, local.title) || PreferredLocalAudioMatcher.sameLocalTitle(title, resolvedTitle)
        }.map { cachedTrack(prefs, it) }
    }

    private fun cachedTrack(prefs: Preferences, songId: Long) = Track(
            id = songId,
            name = prefs[titleKey(songId)].orEmpty(),
            ar = prefs[artistKey(songId)]?.split(" • ")?.map { com.lin0721.linmusic.core.model.Artist(name = it) }.orEmpty(),
            al = com.lin0721.linmusic.core.model.Album(id = prefs[albumIdKey(songId)] ?: 0L,
                name = prefs[albumKey(songId)].orEmpty(), picUrl = prefs[albumCoverKey(songId)].orEmpty()),
            dt = prefs[durationKey(songId)] ?: 0L
    )

    /** Artwork may be filled later without changing a confirmed recording or any playback preference. */
    suspend fun cacheConfirmedCovers(tracks: List<Track>) {
        if (tracks.none { it.al.picUrl.isNotBlank() }) return
        context.preferredLocalAudioDataStore.edit { prefs ->
            tracks.filter { prefs[linkKey(it.id)] != null && it.al.picUrl.isNotBlank() }.forEach {
                if (prefs[albumCoverKey(it.id)] != it.al.picUrl) prefs[albumCoverKey(it.id)] = it.al.picUrl
                if (it.al.id > 0L) prefs[albumIdKey(it.id)] = it.al.id
            }
        }
    }

    suspend fun unlink(songId: Long) {
        context.preferredLocalAudioDataStore.edit {
            it.remove(linkKey(songId))
            it.remove(localTitleKey(songId))
            it.remove(localSnapshotKey(songId))
            // Explicitly unlinking must not immediately reselect the same file by metadata.
            it[excludedKey] = it[excludedKey].orEmpty() + songId.toString()
        }
    }

    suspend fun allowAutomaticMatch(songId: Long) {
        context.preferredLocalAudioDataStore.edit { it[excludedKey] = it[excludedKey].orEmpty() - songId.toString() }
    }

    fun automaticExcluded(songId: Long): Flow<Boolean> = context.preferredLocalAudioDataStore.data
        .map { songId.toString() in it[excludedKey].orEmpty() }

    suspend fun preferredUri(
        songId: Long,
        title: String,
        artist: String,
        loadMetadata: suspend () -> LocalSongMetadata? = { null }
    ): String? = withContext(Dispatchers.IO) {
        if (songId <= 0L) return@withContext null
        val prefs = context.preferredLocalAudioDataStore.data.first()
        fun selection(settings: Preferences) = LocalAudioSelectionSettings(settings[enabledKey] ?: true,
            settings[linkKey(songId)], (settings[automaticKey] ?: true) && songId.toString() !in settings[excludedKey].orEmpty())
        resolveCurrentPreferredLocalAudio(
            initial = selection(prefs),
            latestSettings = { selection(context.preferredLocalAudioDataStore.data.first()) },
            automaticUri = {
                val candidates = readableLocalAudioCandidates(context, library.tracks.first().filter {
                    it.source != LocalTrackSource.MELODIA_DOWNLOAD && PreferredLocalAudioMatcher.sameCandidateTitle(title,
                        LocalAudioCandidate(it.uri.toString(), it.title, it.artist, it.durationMs, album = it.album,
                            fileName = it.path?.substringAfterLast('/')))
                })
                if (candidates.isEmpty()) null else {
                    val cachedDuration = prefs[durationKey(songId)]
                    val cached = if (cachedDuration != null) LocalSongMetadata(
                        prefs[titleKey(songId)] ?: title, prefs[artistKey(songId)] ?: artist, cachedDuration, prefs[albumKey(songId)]
                    ) else null
                    // Earlier personal builds cached duration without album. Refresh only when the
                    // old cache cannot already match, and keep that cache usable if the request fails.
                    val needsAlbum = cached != null && cached.album.isNullOrBlank() &&
                        PreferredLocalAudioMatcher.uniqueMatch(cached.title, cached.artist, cached.durationMs, candidates, cached.album) == null
                    val metadata = if (cached == null || needsAlbum) loadMetadata()?.takeIf { it.durationMs > 0L }?.also { value ->
                        context.preferredLocalAudioDataStore.edit {
                            it[durationKey(songId)] = value.durationMs
                            it[titleKey(songId)] = value.title
                            it[artistKey(songId)] = value.artist
                            it.remove(albumKey(songId))
                            value.album?.let { album -> it[albumKey(songId)] = album }
                        }
                    } ?: cached else cached
                    metadata?.let { PreferredLocalAudioMatcher.uniqueMatch(it.title, it.artist, it.durationMs, candidates, it.album) }
                }
            },
            isReadable = ::isReadable
        )
    }

    // Used by song-list offline gates before a queue exists. Automatic matches require previously verified online metadata.
    suspend fun knownPlayableIds(ids: Collection<Long>): Set<Long> = withContext(Dispatchers.IO) {
        val prefs = context.preferredLocalAudioDataStore.data.first()
        if (prefs[enabledKey] == false) return@withContext emptySet()
        val cachedTitles = ids.mapNotNull { songId -> prefs[titleKey(songId)]?.takeIf { prefs[durationKey(songId)] != null } }
        val candidates = if (prefs[automaticKey] != false && cachedTitles.isNotEmpty()) {
            readableLocalAudioCandidates(context, library.tracks.first().filter { track ->
                track.source != LocalTrackSource.MELODIA_DOWNLOAD && cachedTitles.any {
                    PreferredLocalAudioMatcher.sameCandidateTitle(it, LocalAudioCandidate(track.uri.toString(),
                        track.title, track.artist, track.durationMs, album = track.album, fileName = track.path?.substringAfterLast('/')))
                }
            })
        } else emptyList()
        ids.filterTo(HashSet()) { songId ->
            resolvePreferredLocalAudio(
                enabled = songId > 0L,
                manualUri = prefs[linkKey(songId)],
                automaticEnabled = prefs[automaticKey] != false && songId.toString() !in prefs[excludedKey].orEmpty(),
                automaticUri = {
                    PreferredLocalAudioMatcher.uniqueMatch(
                        prefs[titleKey(songId)].orEmpty(), prefs[artistKey(songId)].orEmpty(),
                        prefs[durationKey(songId)] ?: 0L, candidates, prefs[albumKey(songId)]
                    )
                },
                isReadable = ::isReadable
            ) != null
        }
    }

    /** Only confirmed references are exported; automatic playback matches are not saved associations. */
    suspend fun exportAssociations(destination: Uri): AssociationExportResult = withContext(Dispatchers.IO) {
        require(destination.scheme == "content") { "请选择文档保存位置" }
        val prefs = context.preferredLocalAudioDataStore.data.first()
        val links = storedLinks(prefs)
        require(links.size <= AssociationBackupCodec.MAX_RECORDS) { "关联记录超过 10000 条" }
        val tracks = library.tracks.first().associateBy { it.uri.toString() }
        val issues = ArrayList<AssociationTransferIssue>()
        val records = links.toSortedMap().map { (songId, uri) ->
            currentCoroutineContext().ensureActive()
            val liveCandidate = inspectAssociationFile(context, uri, tracks[uri])
            val live = liveCandidate?.toBackupFile()
            val saved = prefs[localSnapshotKey(songId)]?.let { value ->
                runCatching { snapshotJson.decodeFromString<AssociationLocalFile>(value) }.getOrNull()
            }?.takeIf { it.uri == uri }
            val tracked = tracks[uri]
            val unavailableReason = "当前文件不可读取或无法取得完整音频信息，保留原引用供重新选择"
            val changedFile = saved != null && liveCandidate != null && AssociationImportPlanner.hasPortableEvidence(saved) &&
                !AssociationImportPlanner.sameFileEvidence(saved, liveCandidate)
            val local = if (changedFile) saved!!.copy(readableAtExport = false,
                exportIssue = "文件信息在建立关联后已变化，保留确认时记录供重新选择") else live ?: (saved ?: AssociationLocalFile(
                uri = uri,
                fileName = tracked?.path?.substringAfterLast('/'),
                title = tracked?.title ?: prefs[localTitleKey(songId)],
                artist = tracked?.artist,
                album = tracked?.album,
                durationMs = tracked?.durationMs,
                sizeBytes = tracked?.sizeBytes
            )).copy(readableAtExport = false, exportIssue = unavailableReason)
            val issue = local.exportIssue ?: if (!AssociationImportPlanner.hasPortableEvidence(local)) {
                "缺少完整文件名、标签、大小或时长，恢复时需重新选择音频"
            } else null
            issue?.let { issues += AssociationTransferIssue(songId, prefs[titleKey(songId)], it) }
            AssociationBackupRecord(AssociationOnlineSong(songId, prefs[titleKey(songId)], prefs[artistKey(songId)],
                prefs[albumKey(songId)], prefs[durationKey(songId)]), local)
        }
        val bytes = AssociationBackupCodec.encode(AssociationBackup(exportedAtMs = System.currentTimeMillis(), records = records))
            .toByteArray(Charsets.UTF_8)
        val output = context.contentResolver.openOutputStream(destination, "wt") ?: error("无法打开保存文档")
        output.use { it.write(bytes) }
        AssociationExportResult(records.size, issues)
    }

    suspend fun previewAssociationImport(source: Uri): AssociationImportPreview = withContext(Dispatchers.IO) {
        require(source.scheme == "content") { "请选择关联备份文档" }
        val input = context.contentResolver.openInputStream(source) ?: error("无法打开关联备份")
        val bytes = input.use { stream ->
            val buffer = ByteArray(8_192)
            val result = ByteArrayOutputStream()
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                require(result.size() + count <= AssociationBackupCodec.MAX_BYTES) { "关联备份超过 20 MB" }
                result.write(buffer, 0, count)
            }
            result.toByteArray()
        }
        val backup = AssociationBackupCodec.decode(bytes.toString(Charsets.UTF_8))
        val candidates = importCandidates(backup.records)
        val prefs = context.preferredLocalAudioDataStore.data.first()
        val identityCache = candidates.associate { it.uri to it.identity }.toMutableMap<String, String?>()
        AssociationImportPlanner.preview(backup.records, candidates, storedLinks(prefs)) { uri ->
            identityCache.getOrPut(uri) { localAudioIdentity(context, uri) }
        }
    }

    /** Rechecks the approved files and saves additions in one transaction, preserving conflicts and settings. */
    suspend fun applyAssociationImport(preview: AssociationImportPreview): AssociationImportResult = withContext(Dispatchers.IO) {
        val records = preview.rows.map { it.record }
        AssociationBackupCodec.validate(AssociationBackup(records = records))
        val candidates = importCandidates(records)
        val byUri = candidates.associateBy { it.uri }
        val identityCache = candidates.associate { it.uri to it.identity }.toMutableMap<String, String?>()
        val prefs = context.preferredLocalAudioDataStore.data.first()
        val currentLinks = storedLinks(prefs)
        val checked = AssociationImportPlanner.preview(records, candidates, currentLinks) { uri ->
            identityCache.getOrPut(uri) { localAudioIdentity(context, uri) }
        }.rows.associateBy { it.record.online.songId }
        val additions = ArrayList<Pair<AssociationBackupRecord, AssociationLocalCandidate>>()
        val issues = ArrayList<AssociationTransferIssue>()
        var sameCount = 0
        preview.rows.forEach { row ->
            val now = checked[row.record.online.songId]!!
            when {
                row.status == AssociationImportStatus.SAME && now.status == AssociationImportStatus.SAME -> sameCount++
                row.status == AssociationImportStatus.NEW && now.status == AssociationImportStatus.NEW &&
                    now.resolvedIdentity == row.resolvedIdentity -> {
                    val file = now.resolvedUri?.let(byUri::get)
                    if (file != null) additions += row.record to file
                    else issues += AssociationTransferIssue(row.record.online.songId, row.record.online.title, "预览后的文件状态已变化，请重新导入预览")
                }
                else -> issues += AssociationTransferIssue(row.record.online.songId, row.record.online.title,
                    if (row.status == AssociationImportStatus.NEW) now.reason.ifBlank { "文件状态已变化，请重新导入预览" } else row.reason)
            }
        }
        var changedSincePreview = currentLinks != preview.existingLinks
        if (!changedSincePreview && additions.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            context.preferredLocalAudioDataStore.edit { current ->
                if (storedLinks(current) != preview.existingLinks) {
                    changedSincePreview = true
                } else additions.forEach { (record, file) ->
                    val id = record.online.songId
                    current[linkKey(id)] = file.uri
                    current[localTitleKey(id)] = file.title
                    current[localSnapshotKey(id)] = snapshotJson.encodeToString(file.toBackupFile())
                    record.online.title?.let { current[titleKey(id)] = it }
                    record.online.artist?.let { current[artistKey(id)] = it }
                    record.online.album?.let { current[albumKey(id)] = it }
                    record.online.durationMs?.takeIf { it > 0L }?.let { current[durationKey(id)] = it }
                    // Exclusion flags and global playback switches are intentionally preserved.
                }
            }
        }
        if (changedSincePreview) {
            AssociationImportResult(0, 0, records.size, records.map { record ->
                AssociationTransferIssue(record.online.songId, record.online.title, "当前关联在预览后发生变化，未写入；请重新导入预览")
            })
        } else {
            AssociationImportResult(additions.size, sameCount, records.size - additions.size - sameCount, issues)
        }
    }

    private suspend fun importCandidates(records: List<AssociationBackupRecord>): List<AssociationLocalCandidate> {
        val accepts = AssociationImportPlanner.candidateFilter(records)
        return library.tracks.first().filter { track ->
            accepts(track.uri.toString(), track.title, track.path?.substringAfterLast('/'))
        }.distinctBy { it.uri.toString() }.mapNotNull { track ->
            currentCoroutineContext().ensureActive()
            inspectAssociationFile(context, track.uri.toString(), track)
        }
    }

    private fun storedLinks(prefs: Preferences): Map<Long, String> = prefs.asMap().mapNotNull { (key, value) ->
        if (!key.name.startsWith("song_uri_")) null else {
            val songId = key.name.removePrefix("song_uri_").toLongOrNull()
            val uri = value as? String
            if (songId != null && songId > 0L && uri != null) songId to uri else null
        }
    }.toMap()

    private suspend fun isReadable(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { it.length != 0L } ?: false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
}
