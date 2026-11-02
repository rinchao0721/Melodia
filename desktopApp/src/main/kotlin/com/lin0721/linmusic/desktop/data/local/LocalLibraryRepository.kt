package com.lin0721.linmusic.desktop.data.local

import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.desktop.data.download.DesktopDownloadPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
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
private const val SAVE_DEBOUNCE_MS = 500L
private const val MATCH_INTERVAL_MS = 400L
private const val SCAN_PROGRESS_STEP = 20
private const val NAME_MAX_LENGTH = 40

// 本地音乐库：歌单、扫描索引、云端匹配；整库存一个 JSON
class LocalLibraryRepository(
    private val store: LocalLibraryStore,
    private val scanner: LocalScanner,
    private val matcher: LocalMatcher,
    downloadPreferences: DesktopDownloadPreferences,
    private val onlineState: OnlineStateProvider,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val ready = CompletableDeferred<Unit>()
    private val writeMutex = Mutex()
    private val scanMutex = Mutex()
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)
    private var matchJob: Job? = null

    private val _data = MutableStateFlow(LocalLibraryData())
    val data: StateFlow<LocalLibraryData> = _data.asStateFlow()

    private val records: StateFlow<List<DownloadRecord>> =
        downloadPreferences.records.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _scanState = MutableStateFlow<LocalScanState>(LocalScanState.Idle)
    val scanState: StateFlow<LocalScanState> = _scanState.asStateFlow()

    private val view: StateFlow<LibraryView> = combine(_data, records) { data, records -> LibraryView(data, records, scanner) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, LibraryView(LocalLibraryData(), emptyList(), scanner))

    val summaries: StateFlow<List<LocalPlaylistSummary>> = view.map { it.summaries }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // 歌词与播放按 songId 反查本地文件
    @Volatile
    private var pathIndex: Map<Long, String> = emptyMap()

    init {
        scope.launch {
            _data.value = withContext(ioDispatcher) { store.load() }
            ready.complete(Unit)
            rescan()
        }
        scope.launch {
            while (true) {
                saveRequests.receive()
                delay(SAVE_DEBOUNCE_MS)
                val snapshot = _data.value
                withContext(ioDispatcher) { store.save(snapshot) }
            }
        }
        scope.launch { view.collect { pathIndex = it.pathIndex } }
        // 新下载完成后补读标签，不必整库重扫
        scope.launch {
            records.drop(1).collect { list ->
                val known = _data.value.tracks.mapTo(HashSet()) { it.path.lowercase() }
                val fresh = list.map { normalizePath(it.mediaStoreUri) }.filter { it.lowercase() !in known }
                if (fresh.isNotEmpty()) refreshPaths(fresh)
            }
        }
        scope.launch {
            onlineState.online.drop(1).distinctUntilChanged().filter { it }.collect { startAutoMatch() }
        }
    }

    fun tracksOf(playlistId: Long): Flow<List<LocalTrack>> = view.map { it.tracksOf(playlistId) }.distinctUntilChanged()

    // 页面首帧直接取当前值，避免先闪一下空列表
    fun currentTracks(playlistId: Long): List<LocalTrack> = view.value.tracksOf(playlistId)

    fun playlist(playlistId: Long): Flow<LocalPlaylistSummary?> =
        view.map { view -> view.summaries.firstOrNull { it.id == playlistId } }.distinctUntilChanged()

    fun coverUri(track: LocalTrack): String? = scanner.coverUri(track)

    fun pathForSongId(songId: Long): String? = pathIndex[songId]

    fun readLyrics(path: String): String? = LocalLyricsReader.read(path)

    fun toQueueItem(track: LocalTrack): QueueItem =
        QueueItem(track.queueSongId, track.title, track.artist, coverUri(track).orEmpty(), localUri = track.path)

    // ─── 歌单 ───

    suspend fun createPlaylist(name: String, folder: File? = null): Long {
        val folderPath = folder?.let { normalizePath(it.path) }
        val finalName = name.trim().ifBlank { folder?.name.orEmpty() }.ifBlank { "本地歌单" }.take(NAME_MAX_LENGTH)
        var createdId = 0L
        mutate { data ->
            val id = maxOf(clock(), (data.playlists.maxOfOrNull { it.id } ?: 0L) + 1)
            createdId = id
            data.copy(
                playlists = data.playlists + LocalPlaylist(
                    id = id,
                    name = finalName,
                    createdAt = clock(),
                    folders = listOfNotNull(folderPath)
                )
            )
        }
        if (folderPath != null) rescan()
        return createdId
    }

    fun addFolder(playlistId: Long, folder: File) = launchMutation(rescanAfter = true) { data ->
        val path = normalizePath(folder.path)
        data.updatePlaylist(playlistId) { playlist ->
            if (playlist.folders.any { samePath(it, path) }) {
                playlist
            } else {
                playlist.copy(folders = playlist.folders + path, removed = playlist.removed.filterNot { isUnder(it, path) })
            }
        }
    }

    fun addFiles(playlistId: Long, files: List<File>) {
        val paths = files.filter { it.isFile && isAudioFile(it) }.map { normalizePath(it.path) }
        if (paths.isEmpty()) return
        scope.launch {
            mutate { data ->
                data.updatePlaylist(playlistId) { playlist ->
                    val added = paths.filter { path -> playlist.files.none { samePath(it, path) } }
                    playlist.copy(files = playlist.files + added, removed = playlist.removed.filterNot { r -> paths.any { samePath(it, r) } })
                }
            }
            refreshPaths(paths)
        }
    }

    fun removeFolder(playlistId: Long, folder: String) = launchMutation { data ->
        data.updatePlaylist(playlistId) { playlist -> playlist.copy(folders = playlist.folders.filterNot { samePath(it, folder) }) }
            .pruneUnreferenced()
    }

    fun rename(playlistId: Long, name: String) {
        val trimmed = name.trim().take(NAME_MAX_LENGTH)
        if (trimmed.isEmpty()) return
        launchMutation { data -> data.updatePlaylist(playlistId) { it.copy(name = trimmed) } }
    }

    fun setPinned(playlistId: Long, pinned: Boolean) = launchMutation { data ->
        if (playlistId <= 0L) {
            data.updateBuiltin(playlistId) { it.copy(isPinned = pinned) }
        } else {
            data.updatePlaylist(playlistId) { it.copy(isPinned = pinned) }
        }
    }

    fun markPlayed(playlistId: Long) = launchMutation { data ->
        val now = clock()
        if (playlistId <= 0L) {
            data.updateBuiltin(playlistId) { it.copy(lastPlayedAt = now) }
        } else {
            data.updatePlaylist(playlistId) { it.copy(lastPlayedAt = now) }
        }
    }

    // 只删记录，不动磁盘文件
    fun removePlaylist(playlistId: Long) = launchMutation { data ->
        data.copy(playlists = data.playlists.filterNot { it.id == playlistId }).pruneUnreferenced()
    }

    fun removeTracks(playlistId: Long, paths: Collection<String>) = launchMutation { data ->
        data.updatePlaylist(playlistId) { playlist ->
            val fromFolders = paths.filter { path -> playlist.folders.any { isUnder(path, it) } }
            playlist.copy(
                files = playlist.files.filterNot { file -> paths.any { samePath(it, file) } },
                removed = (playlist.removed + fromFolders).distinctBy { it.lowercase() }
            )
        }.pruneUnreferenced()
    }

    // ─── 扫描与过滤 ───

    fun updateFilter(filter: LocalScanFilter) = launchMutation(rescanAfter = true) { data ->
        data.copy(
            filter = filter.copy(
                minDurationSec = filter.minDurationSec.coerceAtLeast(0),
                excludedFolders = filter.excludedFolders.map(::normalizePath).distinctBy { it.lowercase() }
            )
        )
    }

    fun rescan() {
        scope.launch { scanMutex.withLock { scanAll() } }
    }

    private suspend fun scanAll() {
        ready.await()
        val snapshot = _data.value
        val downloads = records.value.associateBy { normalizePath(it.mediaStoreUri).lowercase() }
        val sources = withContext(ioDispatcher) {
            val paths = LinkedHashMap<String, String>()
            fun add(path: String) {
                paths.putIfAbsent(path.lowercase(), path)
            }
            snapshot.playlists.forEach { playlist ->
                playlist.folders.forEach { folder -> scanner.collectFiles(folder, snapshot.filter.excludedFolders).forEach(::add) }
                playlist.files.forEach { add(normalizePath(it)) }
            }
            records.value.forEach { add(normalizePath(it.mediaStoreUri)) }
            paths.values.toList()
        }
        val previous = snapshot.tracks.associateBy { it.path.lowercase() }
        val scanned = ArrayList<LocalTrack>(sources.size)
        _scanState.value = LocalScanState.Scanning(0, sources.size)
        try {
            withContext(ioDispatcher) {
                sources.forEachIndexed { index, path ->
                    ensureActive()
                    val track = runCatching { scanner.refresh(path, previous[path.lowercase()]) }
                        .onFailure { AppLogger.w(TAG, "读取失败 $path", it) }
                        .getOrNull()
                    if (track != null) scanned += track.withDownload(downloads[path.lowercase()])
                    if (index % SCAN_PROGRESS_STEP == 0) _scanState.value = LocalScanState.Scanning(index + 1, sources.size)
                }
            }
            mutate { data -> data.copy(tracks = mergeScanned(scanned, data.tracks)) }
            withContext(ioDispatcher) { scanner.pruneCovers(_data.value.tracks) }
        } finally {
            _scanState.value = LocalScanState.Idle
        }
        startAutoMatch()
    }

    private suspend fun refreshPaths(paths: List<String>) = scanMutex.withLock {
        ready.await()
        val downloads = records.value.associateBy { normalizePath(it.mediaStoreUri).lowercase() }
        val previous = _data.value.tracks.associateBy { it.path.lowercase() }
        val refreshed = withContext(ioDispatcher) {
            paths.mapNotNull { path ->
                runCatching { scanner.refresh(path, previous[path.lowercase()]) }.getOrNull()
                    ?.withDownload(downloads[path.lowercase()])
            }
        }
        if (refreshed.isEmpty()) return@withLock
        mutate { data ->
            val byPath = refreshed.associateBy { it.path.lowercase() }
            val kept = data.tracks.filterNot { it.path.lowercase() in byPath }
            data.copy(tracks = kept + refreshed)
        }
        startAutoMatch()
    }

    // 扫描期间手动匹配、改标签等改动以最新记录为准
    private fun mergeScanned(scanned: List<LocalTrack>, latest: List<LocalTrack>): List<LocalTrack> {
        val latestByPath = latest.associateBy { it.path.lowercase() }
        return scanned.map { track ->
            val current = latestByPath[track.path.lowercase()] ?: return@map track
            when {
                current.lastModified > track.lastModified -> current
                track.matchState == LocalMatchState.MANUAL && track.songId != null -> track
                else -> track.copy(songId = current.songId, matchState = current.matchState)
            }
        }
    }

    // 下载记录自带网易 songId，视为已关联
    private fun LocalTrack.withDownload(record: DownloadRecord?): LocalTrack =
        if (record == null) this else copy(songId = record.songId, matchState = LocalMatchState.MANUAL)

    // 按扫描设置的排除规则递归收集文件夹里的音频
    suspend fun audioFilesIn(folder: File): List<File> = withContext(ioDispatcher) {
        scanner.collectFiles(folder.path, _data.value.filter.excludedFolders).map(::File)
    }

    // ─── 标签 ───

    fun readEditableTags(path: String): LocalTagEdit? = LocalTagIo.readEditable(File(path))

    suspend fun updateTags(path: String, edit: LocalTagEdit, cover: CoverChange): Result<Unit> {
        val result = withContext(ioDispatcher) { LocalTagIo.write(File(path), edit, cover) }
        if (result.isSuccess) refreshPaths(listOf(path))
        return result
    }

    // ─── 云端匹配 ───

    suspend fun searchCandidates(keyword: String) = matcher.search(keyword)

    // 下载的歌曲以下载记录的 songId 为准，不提供手动匹配
    fun isDownloadedFile(path: String): Boolean = records.value.any { samePath(normalizePath(it.mediaStoreUri), path) }

    fun setManualMatch(path: String, songId: Long) = launchMutation { data ->
        data.updateTrack(path) { it.copy(songId = songId, matchState = LocalMatchState.MANUAL) }
    }

    fun detachMatch(path: String) = launchMutation { data ->
        data.updateTrack(path) { it.copy(songId = null, matchState = LocalMatchState.DETACHED) }
    }

    private fun startAutoMatch() {
        if (matchJob?.isActive == true) return
        matchJob = scope.launch {
            ready.await()
            val downloads = records.value.mapTo(HashSet()) { normalizePath(it.mediaStoreUri).lowercase() }
            val pending = _data.value.tracks
                .filter { it.matchState == LocalMatchState.PENDING && it.path.lowercase() !in downloads }
                .map { it.path }
            for (path in pending) {
                if (!isActive || !onlineState.isOnline()) break
                val track = _data.value.tracks.firstOrNull { it.path == path } ?: continue
                if (track.matchState != LocalMatchState.PENDING) continue
                if (!LocalMatcher.isMatchable(track)) {
                    mutate { data -> data.updatePendingMatch(path, null) }
                    continue
                }
                val result = matcher.findMatch(track)
                if (result.isFailure) {
                    AppLogger.w(TAG, "自动匹配中断", result.exceptionOrNull())
                    break
                }
                val matchedId = result.getOrNull()?.id
                mutate { data -> data.updatePendingMatch(path, matchedId) }
                delay(MATCH_INTERVAL_MS)
            }
        }
    }

    private fun LocalLibraryData.updatePendingMatch(path: String, songId: Long?): LocalLibraryData =
        updateTrack(path) { track ->
            if (track.matchState != LocalMatchState.PENDING) {
                track
            } else {
                track.copy(songId = songId, matchState = if (songId != null) LocalMatchState.AUTO else LocalMatchState.NOT_FOUND)
            }
        }

    // ─── 写入 ───

    private fun launchMutation(rescanAfter: Boolean = false, transform: (LocalLibraryData) -> LocalLibraryData) {
        scope.launch {
            mutate(transform)
            if (rescanAfter) rescan()
        }
    }

    private suspend fun mutate(transform: (LocalLibraryData) -> LocalLibraryData) {
        ready.await()
        writeMutex.withLock {
            val current = _data.value
            val updated = transform(current)
            if (updated == current) return
            _data.value = updated
            saveRequests.trySend(Unit)
        }
    }

    private fun LocalLibraryData.updatePlaylist(id: Long, transform: (LocalPlaylist) -> LocalPlaylist): LocalLibraryData =
        copy(playlists = playlists.map { if (it.id == id) transform(it) else it })

    private fun LocalLibraryData.updateBuiltin(id: Long, transform: (LocalBuiltinState) -> LocalBuiltinState): LocalLibraryData {
        val existing = builtins.firstOrNull { it.id == id } ?: LocalBuiltinState(id)
        return copy(builtins = builtins.filterNot { it.id == id } + transform(existing))
    }

    private fun LocalLibraryData.updateTrack(path: String, transform: (LocalTrack) -> LocalTrack): LocalLibraryData =
        copy(tracks = tracks.map { if (samePath(it.path, path)) transform(it) else it })

    // 不再被任何歌单或下载记录引用的曲目从索引移除
    private fun LocalLibraryData.pruneUnreferenced(): LocalLibraryData {
        val downloads = records.value.mapTo(HashSet()) { normalizePath(it.mediaStoreUri).lowercase() }
        val kept = tracks.filter { track ->
            track.path.lowercase() in downloads || playlists.any { it.references(track.path) }
        }
        return if (kept.size == tracks.size) this else copy(tracks = kept)
    }
}

internal fun LocalPlaylist.references(path: String): Boolean =
    files.any { samePath(it, path) } || folders.any { isUnder(path, it) }

// 由库数据与下载记录推导出的只读视图
internal class LibraryView(
    private val data: LocalLibraryData,
    private val records: List<DownloadRecord>,
    private val scanner: LocalScanner
) {
    private val byPath: Map<String, LocalTrack> = data.tracks.associateBy { it.path.lowercase() }
    private val collator: Collator = Collator.getInstance(Locale.CHINA)

    private val playlistTracks: Map<Long, List<LocalTrack>> = data.playlists.associate { it.id to resolvePlaylist(it) }

    private val downloadTracks: List<LocalTrack> = records.sortedByDescending { it.downloadedAt }.mapNotNull { record ->
        val path = normalizePath(record.mediaStoreUri)
        val indexed = byPath[path.lowercase()]
        when {
            indexed != null -> indexed.copy(songId = record.songId, matchState = LocalMatchState.MANUAL)
            // 尚未扫描到的新下载先用下载记录里的信息占位
            File(path).isFile -> LocalTrack(
                path = path,
                title = record.songName.ifBlank { File(path).nameWithoutExtension },
                artist = record.artistName.ifBlank { UNKNOWN_ARTIST },
                size = record.fileSize,
                songId = record.songId,
                matchState = LocalMatchState.MANUAL
            )
            else -> null
        }
    }

    private val allTracks: List<LocalTrack> by lazy {
        val seen = HashSet<String>()
        (playlistTracks.values.flatten() + downloadTracks)
            .filter { seen.add(it.path.lowercase()) }
            .sortedWith(compareBy(collator) { it.title })
    }

    val summaries: List<LocalPlaylistSummary> by lazy {
        val builtins = data.builtins.associateBy { it.id }
        fun builtin(id: Long, name: String, tracks: List<LocalTrack>) = LocalPlaylistSummary(
            id = id,
            name = name,
            trackCount = tracks.size,
            coverUri = firstCover(tracks),
            isPinned = builtins[id]?.isPinned == true,
            lastPlayedAt = builtins[id]?.lastPlayedAt ?: 0L,
            createdAt = 0L
        )
        listOf(
            builtin(LOCAL_ALL_TRACKS_ID, "全部本地歌曲", allTracks),
            builtin(LOCAL_DOWNLOADS_ID, "已下载", downloadTracks)
        ) + data.playlists.map { playlist ->
            val tracks = playlistTracks[playlist.id].orEmpty()
            LocalPlaylistSummary(
                id = playlist.id,
                name = playlist.name,
                trackCount = tracks.size,
                coverUri = firstCover(tracks),
                isPinned = playlist.isPinned,
                lastPlayedAt = playlist.lastPlayedAt,
                createdAt = playlist.createdAt
            )
        }
    }

    val pathIndex: Map<Long, String> by lazy {
        val index = HashMap<Long, String>(data.tracks.size * 2)
        data.tracks.forEach { track ->
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

    private fun resolvePlaylist(playlist: LocalPlaylist): List<LocalTrack> {
        val minDurationMs = data.filter.minDurationSec * 1000L
        val removed = playlist.removed.mapTo(HashSet()) { it.lowercase() }
        val fromFolders = if (playlist.folders.isEmpty()) {
            emptyList()
        } else {
            data.tracks.filter { track ->
                playlist.folders.any { isUnder(track.path, it) } &&
                    track.path.lowercase() !in removed &&
                    data.filter.excludedFolders.none { isUnder(track.path, it) } &&
                    (track.durationMs <= 0L || track.durationMs >= minDurationMs)
            }.sortedWith(compareBy(collator) { it.path })
        }
        val folderPaths = fromFolders.mapTo(HashSet()) { it.path.lowercase() }
        val fromFiles = playlist.files.mapNotNull { file ->
            val key = normalizePath(file).lowercase()
            if (key in folderPaths) null else byPath[key]
        }
        return fromFolders + fromFiles
    }

    private fun firstCover(tracks: List<LocalTrack>): String? = tracks.firstOrNull { it.hasCover }?.let(scanner::coverUri)
}
