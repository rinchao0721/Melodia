package com.lin0721.linmusic.desktop.data.local

import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.download.recognition.LocalAudioEntry
import com.lin0721.linmusic.core.download.recognition.estimateBitrateKbps
import com.lin0721.linmusic.core.download.recognition.guessAudioQuality
import com.lin0721.linmusic.core.localmusic.data.DesktopJsonImporter
import com.lin0721.linmusic.core.localmusic.data.LocalMatchService
import com.lin0721.linmusic.core.localmusic.data.LocalPlaylistRepository
import com.lin0721.linmusic.core.localmusic.data.LocalLibraryRepository as SharedLocalLibraryRepository
import com.lin0721.linmusic.core.localmusic.db.LocalPlaylistDao
import com.lin0721.linmusic.core.localmusic.db.LocalTrackDao
import com.lin0721.linmusic.core.localmusic.db.LocalTrackEntity
import com.lin0721.linmusic.core.localmusic.db.toDomain
import com.lin0721.linmusic.core.localmusic.domain.LocalMatchState as SharedMatchState
import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist as SharedLocalPlaylist
import com.lin0721.linmusic.core.localmusic.domain.LocalScanFilter as SharedScanFilter
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack as SharedLocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.folderPathOf
import com.lin0721.linmusic.core.localmusic.platform.LocalMediaEntry
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.desktop.data.download.DesktopDownloadPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale

private const val TAG = "LocalLibrary"
private const val MATCH_INTERVAL_MS = 400L
private const val SCAN_PROGRESS_STEP = 20
private const val NAME_MAX_LENGTH = 40
private val MATCHED_STATES = setOf(LocalMatchState.AUTO, LocalMatchState.MANUAL)

// 桌面本地音乐协调器：底层读写全部委托给 shared Room 仓库
class LocalLibraryRepository(
    val sharedLibrary: SharedLocalLibraryRepository,
    val sharedPlaylists: LocalPlaylistRepository,
    private val trackDao: LocalTrackDao,
    private val playlistDao: LocalPlaylistDao,
    private val jsonImporter: DesktopJsonImporter,
    private val matchService: LocalMatchService,
    private val scanner: LocalScanner,
    downloadPreferences: DesktopDownloadPreferences,
    private val onlineState: OnlineStateProvider,
    private val scope: CoroutineScope,
    private val legacyJsonFile: File? = null,
    private val loadFilter: suspend () -> LocalScanFilter = { LocalScanFilter() },
    private val saveFilter: suspend (LocalScanFilter) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val ready = CompletableDeferred<Unit>()
    private val scanMutex = Mutex()
    private var matchJob: Job? = null

    private val records: StateFlow<List<DownloadRecord>> =
        downloadPreferences.records.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _scanState = MutableStateFlow<LocalScanState>(LocalScanState.Idle)
    val scanState: StateFlow<LocalScanState> = _scanState.asStateFlow()

    private val _builtinStates = MutableStateFlow<Map<Long, LocalBuiltinState>>(emptyMap())

    private val sharedTracksFlow: StateFlow<List<SharedLocalTrack>> =
        sharedLibrary.observeFilteredTracks().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val sharedPlaylistsFlow: StateFlow<List<SharedLocalPlaylist>> =
        sharedPlaylists.observePlaylists().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val data: StateFlow<LocalLibraryData> = combine(
        sharedTracksFlow,
        sharedPlaylistsFlow,
        sharedLibrary.scanFilter,
        _builtinStates
    ) { tracks, playlists, filter, builtins ->
        LocalLibraryData(
            playlists = playlists.map { it.toDesktopPlaylist() },
            tracks = tracks.map { it.toDesktopTrack() },
            filter = LocalScanFilter(filter.minDurationSec, filter.excludedFolders.toList()),
            builtins = builtins.values.toList()
        )
    }.stateIn(scope, SharingStarted.Eagerly, LocalLibraryData())

    private val view: StateFlow<LibraryView> = combine(
        sharedTracksFlow,
        sharedPlaylistsFlow,
        records,
        _builtinStates
    ) { tracks, playlists, downloadRecords, builtins ->
        LibraryView(
            tracks = tracks.map { it.toDesktopTrack() },
            playlists = playlists,
            records = downloadRecords,
            builtins = builtins,
            scanner = scanner
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, LibraryView(emptyList(), emptyList(), emptyList(), emptyMap(), scanner))

    val summaries: StateFlow<List<LocalPlaylistSummary>> = view.map { it.summaries }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val likedLocations: StateFlow<Set<String>> = sharedPlaylists.likedLocations
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    @Volatile
    private var pathIndex: Map<Long, String> = emptyMap()

    init {
        scope.launch {
            val imported = legacyJsonFile?.let { file ->
                withContext(ioDispatcher) { jsonImporter.importIfNeeded(file) }
            }
            val initialFilter = if (imported?.imported == true && imported.filter != null) {
                val f = imported.filter!!
                LocalScanFilter(f.minDurationSec, f.excludedFolders.toList()).also { saveFilter(it) }
            } else {
                loadFilter()
            }
            sharedLibrary.updateScanFilter {
                SharedScanFilter(initialFilter.minDurationSec, initialFilter.excludedFolders.toSet())
            }
            ready.complete(Unit)
            rescan()
        }
        scope.launch { view.collect { pathIndex = it.pathIndex } }
        scope.launch {
            records.drop(1).collect { list ->
                val known = trackDao.getAllLocations().mapTo(HashSet()) { it.lowercase() }
                val fresh = list.map { normalizePath(it.mediaStoreUri) }.filter { it.lowercase() !in known }
                if (fresh.isNotEmpty()) refreshPaths(fresh)
            }
        }
        scope.launch {
            onlineState.online.drop(1).distinctUntilChanged().filter { it }.collect { startAutoMatch() }
        }
    }

    fun tracksOf(playlistId: Long): Flow<List<LocalTrack>> = view.map { it.tracksOf(playlistId) }.distinctUntilChanged()

    fun currentTracks(playlistId: Long): List<LocalTrack> = view.value.tracksOf(playlistId)

    fun playlist(playlistId: Long): Flow<LocalPlaylistSummary?> =
        view.map { v -> v.summaries.firstOrNull { it.id == playlistId } }.distinctUntilChanged()

    fun coverUri(track: LocalTrack): String? = scanner.coverUri(track)

    fun pathForSongId(songId: Long): String? = pathIndex[songId]

    fun matchedEntries(): List<LocalAudioEntry> = sharedTracksFlow.value
        .filter { it.songId != null && it.matchState in setOf(SharedMatchState.AUTO, SharedMatchState.MANUAL) }
        .map { track ->
            val path = track.path.orEmpty()
            LocalAudioEntry(
                uri = path,
                title = track.title,
                artist = track.artist,
                durationMs = track.durationMs,
                sizeBytes = track.sizeBytes,
                quality = track.qualityLevel
                    ?: guessAudioQuality(File(path).extension, estimateBitrateKbps(track.sizeBytes, track.durationMs)),
                songIdTag = track.songId
            )
        }

    fun readLyrics(path: String): String? = LocalLyricsReader.read(path)

    fun toQueueItem(track: LocalTrack): QueueItem =
        QueueItem(track.queueSongId, track.title, track.artist, coverUri(track).orEmpty(), localUri = track.path)

    // ─── 歌单 ───

    suspend fun createPlaylist(name: String, folder: File? = null): Long {
        ready.await()
        val folderPath = folder?.invariantSeparatorsPath?.trimEnd('/')
        val finalName = name.trim().ifBlank { folder?.name.orEmpty() }.ifBlank { "本地歌单" }.take(NAME_MAX_LENGTH)
        val createdId = sharedPlaylists.createPlaylist(finalName)
        if (folderPath != null) {
            sharedPlaylists.addFolderSource(createdId, folderPath)
            rescan()
        }
        return createdId
    }

    fun addFolder(playlistId: Long, folder: File) {
        scope.launch {
            ready.await()
            sharedPlaylists.addFolderSource(playlistId, folder.invariantSeparatorsPath.trimEnd('/'))
            rescan()
        }
    }

    fun addFiles(playlistId: Long, files: List<File>) {
        val paths = files.filter { it.isFile && isAudioFile(it) }.map { normalizePath(it.path) }
        if (paths.isEmpty()) return
        scope.launch {
            ready.await()
            refreshPaths(paths)
            sharedPlaylists.addTracks(playlistId, paths)
        }
    }

    fun removeFolder(playlistId: Long, folder: String) {
        scope.launch {
            ready.await()
            sharedPlaylists.removeFolderSource(playlistId, normalizePath(folder))
        }
    }

    fun rename(playlistId: Long, name: String) {
        val trimmed = name.trim().take(NAME_MAX_LENGTH)
        if (trimmed.isEmpty()) return
        scope.launch {
            ready.await()
            sharedPlaylists.renamePlaylist(playlistId, trimmed)
        }
    }

    fun setPinned(playlistId: Long, pinned: Boolean) {
        scope.launch {
            ready.await()
            when {
                playlistId == LOCAL_LIKED_ID -> {
                    val realId = sharedPlaylists.ensureLikedPlaylist()
                    sharedPlaylists.setPinned(realId, pinned)
                }
                playlistId <= 0L -> {
                    val prev = _builtinStates.value[playlistId] ?: LocalBuiltinState(playlistId)
                    _builtinStates.value = _builtinStates.value + (playlistId to prev.copy(isPinned = pinned))
                }
                else -> sharedPlaylists.setPinned(playlistId, pinned)
            }
        }
    }

    fun markPlayed(playlistId: Long) {
        scope.launch {
            ready.await()
            if (playlistId <= 0L) {
                val prev = _builtinStates.value[playlistId] ?: LocalBuiltinState(playlistId)
                _builtinStates.value = _builtinStates.value + (playlistId to prev.copy(lastPlayedAt = clock()))
            } else {
                sharedPlaylists.markPlayed(playlistId)
            }
        }
    }

    fun removePlaylist(playlistId: Long) {
        scope.launch {
            ready.await()
            sharedPlaylists.deletePlaylist(playlistId)
        }
    }

    fun removeTracks(playlistId: Long, paths: Collection<String>) {
        scope.launch {
            ready.await()
            paths.forEach { path ->
                sharedPlaylists.removeTrack(playlistId, normalizePath(path))
            }
        }
    }

    fun isLiked(path: String, liked: Set<String> = likedLocations.value): Boolean = normalizePath(path) in liked

    suspend fun ensureLikedPlaylist(): Long {
        ready.await()
        return sharedPlaylists.ensureLikedPlaylist()
    }

    fun toggleLiked(path: String) {
        scope.launch {
            ready.await()
            sharedPlaylists.toggleLiked(normalizePath(path))
        }
    }


    fun isSystemPlaylist(playlistId: Long): Boolean =
        summaries.value.firstOrNull { it.id == playlistId }?.isSystem == true

    // ─── 扫描与过滤 ───

    fun updateFilter(filter: LocalScanFilter) {
        val normalized = filter.copy(
            minDurationSec = filter.minDurationSec.coerceAtLeast(0),
            excludedFolders = filter.excludedFolders.map(::normalizePath).distinctBy { it.lowercase() }
        )
        scope.launch {
            ready.await()
            sharedLibrary.updateScanFilter {
                SharedScanFilter(normalized.minDurationSec, normalized.excludedFolders.toSet())
            }
            saveFilter(normalized)
            rescan()
        }
    }

    fun rescan() {
        scope.launch { scanMutex.withLock { scanAll() } }
    }

    fun refreshPaths(paths: List<String>) {
        if (paths.isEmpty()) return
        scope.launch {
            ready.await()
            val normalized = paths.map(::normalizePath).distinctBy { it.lowercase() }
            val existingByLoc = trackDao.getAll().associateBy { it.location.lowercase() }
            val downloads = records.value.associateBy { normalizePath(it.mediaStoreUri).lowercase() }
            val upserts = ArrayList<LocalTrackEntity>(normalized.size)
            withContext(ioDispatcher) {
                for (path in normalized) {
                    val file = File(path)
                    if (!file.isFile) continue
                    val prev = existingByLoc[path.lowercase()]?.toDesktopTrack()
                    val scanned = scanner.refresh(path, prev) ?: continue
                    val dl = downloads[path.lowercase()]
                    upserts += scanned.toEntity(
                        source = if (dl != null) LocalTrackSource.DOWNLOAD else LocalTrackSource.EXTERNAL,
                        overrideSongId = dl?.songId ?: scanned.songId,
                        overrideMatchState = if (dl != null) SharedMatchState.MANUAL else scanned.matchState.toShared()
                    )
                }
            }
            if (upserts.isNotEmpty()) {
                trackDao.upsert(upserts)
            }
            if (onlineState.isOnline()) startAutoMatch()
        }
    }

    private suspend fun scanAll() {
        ready.await()
        val playlists = playlistDao.getPlaylists()
        val currentPlaylists = sharedPlaylists.observePlaylists().first()
        val folderPaths = currentPlaylists.flatMap { it.folderSources }.distinct()
        val manualLocations = currentPlaylists.flatMap { p -> p.tracks.mapNotNull { it.path } }
        val downloadList = records.value
        val downloadMap = downloadList.associateBy { normalizePath(it.mediaStoreUri).lowercase() }
        val excluded = sharedLibrary.scanFilter.value.excludedFolders

        val existingByPath = trackDao.getAll().associateBy { it.location.lowercase() }

        val candidateFiles = withContext(ioDispatcher) {
            val collected = LinkedHashMap<String, File>()
            for (folder in folderPaths) {
                val root = File(folder)
                if (!root.isDirectory) continue
                root.walkTopDown()
                    .onEnter { dir -> excluded.none { isUnder(dir.path, it) } }
                    .filter { it.isFile && isAudioFile(it) }
                    .forEach { file ->
                        val norm = normalizePath(file.path)
                        collected.putIfAbsent(norm.lowercase(), file)
                    }
            }
            for (loc in manualLocations) {
                val file = File(loc)
                if (file.isFile) collected.putIfAbsent(normalizePath(loc).lowercase(), file)
            }
            for (dl in downloadList) {
                val file = File(normalizePath(dl.mediaStoreUri))
                if (file.isFile) collected.putIfAbsent(normalizePath(file.path).lowercase(), file)
            }
            collected.values.toList()
        }

        _scanState.value = LocalScanState.Scanning(0, candidateFiles.size)
        val entries = ArrayList<LocalMediaEntry>(candidateFiles.size)
        withContext(ioDispatcher) {
            candidateFiles.forEachIndexed { index, file ->
                val norm = normalizePath(file.path)
                val prev = existingByPath[norm.lowercase()]?.toDesktopTrack()
                val scanned = scanner.refresh(norm, prev)
                if (scanned != null) {
                    val dl = downloadMap[norm.lowercase()]
                    entries += LocalMediaEntry(
                        location = scanned.path,
                        mediaStoreId = null,
                        title = scanned.title,
                        artist = scanned.artist,
                        album = scanned.album,
                        albumArtist = null,
                        year = scanned.year,
                        trackNumber = scanned.trackNumber,
                        durationMs = scanned.durationMs,
                        sizeBytes = scanned.size,
                        path = scanned.path,
                        dateAddedMs = scanned.lastModified,
                        dateModifiedMs = scanned.lastModified,
                        qualityLevel = scanned.quality,
                        hasEmbeddedCover = scanned.hasCover,
                        source = if (dl != null) LocalTrackSource.DOWNLOAD else LocalTrackSource.EXTERNAL
                    )
                }
                if ((index + 1) % SCAN_PROGRESS_STEP == 0 || index == candidateFiles.lastIndex) {
                    _scanState.value = LocalScanState.Scanning(index + 1, candidateFiles.size)
                }
            }
        }
        sharedLibrary.syncEntries(entries)
        // 下载曲目自动绑定 downloadRecord.songId
        for (dl in downloadList) {
            val norm = normalizePath(dl.mediaStoreUri)
            val entity = trackDao.getByLocation(norm)
            if (entity != null && entity.songId != dl.songId) {
                trackDao.updateMatch(norm, SharedMatchState.MANUAL.name, dl.songId)
            }
        }
        _scanState.value = LocalScanState.Idle
        if (onlineState.isOnline() && playlists.isNotEmpty()) startAutoMatch()
    }

    // ─── 标签编辑 ───

    fun audioFilesIn(folder: File): List<File> =
        scanner.collectFiles(folder.path, sharedLibrary.scanFilter.value.excludedFolders.toList()).map(::File)

    fun readEditableTags(path: String): LocalTagEdit? = LocalTagIo.readEditable(File(path))

    suspend fun updateTags(path: String, edit: LocalTagEdit, cover: CoverChange): Result<Unit> =
        writeTags(path, edit, cover)

    suspend fun writeTags(path: String, edit: LocalTagEdit, cover: CoverChange): Result<Unit> {
        val result = withContext(ioDispatcher) { LocalTagIo.write(File(path), edit, cover) }
        if (result.isSuccess) refreshPaths(listOf(path))
        return result
    }

    // ─── 云端匹配 ───

    suspend fun searchCandidates(keyword: String) = matchService.search(keyword)

    fun isDownloadedFile(path: String): Boolean = records.value.any { samePath(normalizePath(it.mediaStoreUri), path) }

    fun setManualMatch(path: String, songId: Long) {
        scope.launch {
            ready.await()
            trackDao.updateMatch(normalizePath(path), SharedMatchState.MANUAL.name, songId)
        }
    }

    fun detachMatch(path: String) {
        scope.launch {
            ready.await()
            trackDao.updateMatch(normalizePath(path), SharedMatchState.DETACHED.name, null)
        }
    }

    private fun startAutoMatch() {
        if (matchJob?.isActive == true) return
        matchJob = scope.launch {
            ready.await()
            val downloads = records.value.mapTo(HashSet()) { normalizePath(it.mediaStoreUri).lowercase() }
            val pending = trackDao.getAll()
                .map { it.toDomain() }
                .filter { it.matchState == SharedMatchState.PENDING && (it.path ?: "").lowercase() !in downloads }
            for (track in pending) {
                if (!isActive || !onlineState.isOnline()) break
                if (!LocalMatchService.isMatchable(track)) {
                    trackDao.updateMatch(track.location, SharedMatchState.NOT_FOUND.name, null)
                    continue
                }
                val result = matchService.findMatch(track)
                if (result.isFailure) {
                    AppLogger.w(TAG, "自动匹配中断", result.exceptionOrNull())
                    break
                }
                val matchedId = result.getOrNull()?.id
                val state = if (matchedId != null) SharedMatchState.AUTO else SharedMatchState.NOT_FOUND
                trackDao.updateMatch(track.location, state.name, matchedId)
                delay(MATCH_INTERVAL_MS)
            }
        }
    }
}

internal fun SharedLocalTrack.toDesktopTrack(): LocalTrack = LocalTrack(
    path = path ?: location,
    title = title,
    artist = artist,
    album = album.orEmpty(),
    year = year ?: 0,
    trackNumber = trackNumber ?: 0,
    durationMs = durationMs,
    size = sizeBytes,
    lastModified = dateModifiedMs,
    hasCover = hasEmbeddedCover,
    songId = songId,
    matchState = when (matchState) {
        SharedMatchState.PENDING -> LocalMatchState.PENDING
        SharedMatchState.AUTO -> LocalMatchState.AUTO
        SharedMatchState.MANUAL -> LocalMatchState.MANUAL
        SharedMatchState.NOT_FOUND -> LocalMatchState.NOT_FOUND
        SharedMatchState.DETACHED -> LocalMatchState.DETACHED
    },
    quality = qualityLevel
)

private fun LocalTrackEntity.toDesktopTrack(): LocalTrack = LocalTrack(
    path = location,
    title = title,
    artist = artist,
    album = album.orEmpty(),
    year = year ?: 0,
    trackNumber = trackNumber ?: 0,
    durationMs = durationMs,
    size = sizeBytes,
    lastModified = dateModifiedMs,
    hasCover = hasEmbeddedCover,
    songId = songId,
    matchState = runCatching { LocalMatchState.valueOf(matchState) }.getOrDefault(LocalMatchState.PENDING),
    quality = qualityLevel
)

private fun LocalTrack.toEntity(
    source: LocalTrackSource,
    overrideSongId: Long? = songId,
    overrideMatchState: SharedMatchState = matchState.toShared()
): LocalTrackEntity = LocalTrackEntity(
    location = path,
    mediaStoreId = null,
    songId = overrideSongId,
    title = title,
    artist = artist,
    album = album.ifBlank { null },
    durationMs = durationMs,
    sizeBytes = size,
    path = path,
    dateAddedMs = lastModified,
    dateModifiedMs = lastModified,
    source = source.name,
    albumArtist = null,
    year = year ?: 0,
    trackNumber = trackNumber ?: 0,
    qualityLevel = quality,
    matchState = overrideMatchState.name,
    matchedSongId = overrideSongId,
    hasEmbeddedCover = hasCover,
    folder = folderPathOf(path)
)

private fun LocalMatchState.toShared(): SharedMatchState = when (this) {
    LocalMatchState.PENDING -> SharedMatchState.PENDING
    LocalMatchState.AUTO -> SharedMatchState.AUTO
    LocalMatchState.MANUAL -> SharedMatchState.MANUAL
    LocalMatchState.NOT_FOUND -> SharedMatchState.NOT_FOUND
    LocalMatchState.DETACHED -> SharedMatchState.DETACHED
}

private fun SharedLocalPlaylist.toDesktopPlaylist(): LocalPlaylist = LocalPlaylist(
    id = id,
    name = name,
    createdAt = createdAt,
    isPinned = isPinned,
    lastPlayedAt = lastPlayedAt,
    folders = folderSources,
    files = tracks.mapNotNull { it.path },
    removed = emptyList()
)

internal class LibraryView(
    private val tracks: List<LocalTrack>,
    private val playlists: List<SharedLocalPlaylist>,
    private val records: List<DownloadRecord>,
    private val builtins: Map<Long, LocalBuiltinState>,
    private val scanner: LocalScanner
) {
    private val byPath: Map<String, LocalTrack> = tracks.associateBy { it.path.lowercase() }
    private val collator: Collator = Collator.getInstance(Locale.CHINA)

    private val playlistTracks: Map<Long, List<LocalTrack>> = playlists.associate { playlist ->
        playlist.id to playlist.tracks.map { it.toDesktopTrack() }
    }

    private val downloadTracks: List<LocalTrack> = records.sortedByDescending { it.downloadedAt }.mapNotNull { record ->
        val path = normalizePath(record.mediaStoreUri)
        val indexed = byPath[path.lowercase()]
        when {
            indexed != null -> indexed.copy(songId = record.songId, matchState = LocalMatchState.MANUAL)
            File(path).isFile -> LocalTrack(
                path = path,
                title = record.songName.ifBlank { File(path).nameWithoutExtension },
                artist = record.artistName.ifBlank { UNKNOWN_ARTIST },
                size = record.fileSize,
                songId = record.songId,
                matchState = LocalMatchState.MANUAL,
                quality = record.quality.ifBlank { null }
            )
            else -> null
        }
    }

    private val allTracks: List<LocalTrack> by lazy {
        val seen = HashSet<String>()
        (tracks + downloadTracks)
            .filter { seen.add(it.path.lowercase()) }
            .sortedWith(compareBy(collator) { it.title })
    }

    val summaries: List<LocalPlaylistSummary> by lazy {
        fun builtin(id: Long, name: String, list: List<LocalTrack>) = LocalPlaylistSummary(
            id = id,
            name = name,
            trackCount = list.size,
            coverUri = firstCover(list),
            isPinned = builtins[id]?.isPinned ?: true,
            lastPlayedAt = builtins[id]?.lastPlayedAt ?: 0L,
            createdAt = 0L
        )
        listOf(
            builtin(LOCAL_ALL_TRACKS_ID, "全部本地歌曲", allTracks),
            builtin(LOCAL_DOWNLOADS_ID, "已下载", downloadTracks)
        ) + playlists.map { playlist ->
            val list = playlistTracks[playlist.id].orEmpty()
            LocalPlaylistSummary(
                id = playlist.id,
                name = playlist.name,
                trackCount = list.size,
                coverUri = firstCover(list),
                isPinned = playlist.isPinned,
                lastPlayedAt = playlist.lastPlayedAt,
                createdAt = playlist.createdAt,
                systemKind = playlist.systemKind
            )
        }
    }

    val pathIndex: Map<Long, String> by lazy {
        val index = HashMap<Long, String>(tracks.size * 2)
        tracks.forEach { track ->
            index[track.placeholderId] = track.path
            track.songId?.let { index[it] = track.path }
        }
        records.forEach { record -> index[record.songId] = record.mediaStoreUri }
        index
    }

    fun tracksOf(id: Long): List<LocalTrack> = when (id) {
        LOCAL_DOWNLOADS_ID -> downloadTracks
        LOCAL_ALL_TRACKS_ID -> allTracks
        else -> playlistTracks[id].orEmpty()
    }

    private fun firstCover(list: List<LocalTrack>): String? = list.firstOrNull { it.hasCover }?.let(scanner::coverUri)
}
