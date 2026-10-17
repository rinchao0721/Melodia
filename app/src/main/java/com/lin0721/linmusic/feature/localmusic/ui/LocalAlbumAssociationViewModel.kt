package com.lin0721.linmusic.feature.localmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.localmusic.data.LocalAlbumAssociation
import com.lin0721.linmusic.feature.localmusic.data.LocalAlbumAssociationStore
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.feature.localmusic.data.PreferredLocalAudioRepository
import com.lin0721.linmusic.feature.localmusic.data.localAlbumMatchFingerprint
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchLocalMatchRepository
import com.lin0721.linmusic.feature.localmusic.domain.*
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Both entry directions converge on the same user-confirmed album scope and guarded song writes. */
class LocalAlbumAssociationViewModel(
    private val library: LocalLibraryRepository,
    private val preferred: PreferredLocalAudioRepository,
    private val store: LocalAlbumAssociationStore,
    private val search: SearchRepository,
    private val playlists: PlaylistRepository,
    private val batch: BatchLocalMatchRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow<LocalAlbumAssociationState?>(null)
    val state = mutableState.asStateFlow()
    private val mutablePicker = MutableStateFlow<OnlineAlbumLocalPickerState?>(null)
    val picker = mutablePicker.asStateFlow()
    private val mutableHub = MutableStateFlow<LocalMatchingHubState?>(null)
    val hub = mutableHub.asStateFlow()
    private var hubObserver: Job? = null
    private var hubAlbums = emptyList<LocalAlbum>()
    private var returnHub: LocalMatchingHubState? = null
    private var generation = 0L
    private var request: Job? = null
    private var pickerObserver: Job? = null
    private var allPickerItems = emptyList<OnlineAlbumLocalPickerItem>()
    private var presetTracks = emptyList<Track>()
    private var stopRequested = false

    private fun busy() = mutableState.value?.let { it.running || it.savingUri != null } == true

    fun open(local: LocalAlbum, online: Album? = null, tracks: List<Track> = emptyList(), fromHub: Boolean = false,
             candidates: List<Album> = emptyList()) {
        if (busy()) return
        if (!fromHub) returnHub = null
        hubObserver?.cancel()
        mutableHub.value = null
        request?.cancel()
        pickerObserver?.cancel()
        mutablePicker.value = null
        val token = ++generation
        val artist = local.artist.takeIf(PreferredLocalAudioMatcher::hasKnownArtist).orEmpty()
        mutableState.value = LocalAlbumAssociationState(local,
            query = "${LocalMatchAlbum.forComparison(local.name)} $artist".trim(), albums = candidates, loading = true)
        batch.pauseForReview()
        request = viewModelScope.launch {
            try {
                val binding = store.associations.first()[local.key]
                if (token != generation) return@launch
                when {
                    online != null && tracks.isNotEmpty() -> preview(online, tracks,
                        binding?.album?.id == online.id, token)
                    online != null -> loadAlbum(online, token)
                    binding != null -> preview(binding.album, binding.tracks, true, token)
                    candidates.isNotEmpty() -> mutableState.update { it?.copy(loading = false) }
                    else -> {
                        mutableState.update { it?.copy(loading = false) }
                        searchAlbums()
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(token, "读取专辑对应失败，请返回重试。") }
        }
    }

    fun openOnline(online: Album, tracks: List<Track>, fromHub: Boolean = false) {
        if (online.id <= 0 || busy()) return
        if (!fromHub) returnHub = null
        hubObserver?.cancel()
        mutableHub.value = null
        request?.cancel()
        pickerObserver?.cancel()
        val token = ++generation
        mutableState.value = null
        presetTracks = tracks.filter { it.id > 0L }
        allPickerItems = emptyList()
        mutablePicker.value = OnlineAlbumLocalPickerState(online)
        pickerObserver = viewModelScope.launch {
            try {
                combine(library.tracks, store.associations) { local, bindings -> local to bindings }
                    .collectLatest { (tracksInLibrary, bindings) ->
                        val onlineTrackCount = presetTracks.size
                        val rows = withContext(Dispatchers.Default) {
                            val name = LocalMatchAlbum.forComparison(online.name)
                            val artists = online.artists.map { key(it.name) }.filter(String::isNotBlank)
                            buildLocalLibraryIndex(tracksInLibrary).knownAlbums.map { album ->
                                val sameAlbum = name.isNotBlank() && LocalMatchAlbum.forComparison(album.name) == name
                                val sameArtist = artists.any { it == key(album.artist) || album.tracks.any { t -> key(t.artist) == it } }
                                val linked = bindings[album.key]?.album?.id == online.id
                                val reason = buildList {
                                    if (linked) add("已对应此网易云专辑")
                                    if (sameAlbum) add("专辑名一致")
                                    if (sameArtist) add("同歌手")
                                }
                                OnlineAlbumLocalPickerItem(album, reason)
                            }.sortedWith(compareByDescending<OnlineAlbumLocalPickerItem> { "已对应此网易云专辑" in it.reasons }
                                .thenByDescending { "专辑名一致" in it.reasons }
                                .thenByDescending { "同歌手" in it.reasons }
                                .thenBy { if (onlineTrackCount == 0) 0 else kotlin.math.abs(it.album.tracks.size - onlineTrackCount) }
                                .thenBy { it.album.name })
                        }
                        if (token != generation) return@collectLatest
                        allPickerItems = rows
                        renderPicker()
                    }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == generation) mutablePicker.update {
                it?.copy(loading = false, error = "读取本地专辑失败，请返回重试。")
            } }
        }
    }

    fun pickerQuery(query: String) {
        mutablePicker.update { it?.copy(query = query, showAll = if (query.isNotBlank()) true else it.showAll) }
        renderPicker()
    }

    fun pickerShowAll(value: Boolean) {
        mutablePicker.update { it?.copy(showAll = value) }
        renderPicker()
    }

    private fun renderPicker() {
        val current = mutablePicker.value ?: return
        val terms = key(current.query).split(' ').filter(String::isNotBlank)
        mutablePicker.value = current.copy(loading = false, items = allPickerItems.filter { row ->
            (current.showAll || row.reasons.isNotEmpty()) &&
                terms.all { it in key("${row.album.name} ${row.album.artist}") }
        })
    }

    fun chooseLocal(local: LocalAlbum, expectedOnlineId: Long) {
        val current = mutablePicker.value ?: return
        if (current.online.id != expectedOnlineId) return
        val live = current.items.firstOrNull { it.album.key == local.key }?.album ?: return
        open(live, current.online, presetTracks, fromHub = returnHub != null)
    }

    fun openHub() {
        if (busy()) return
        close()
        returnHub = null
        mutableHub.value = LocalMatchingHubState(loading = true)
        observeHub()
    }

    private fun observeHub() {
        hubObserver?.cancel()
        hubObserver = viewModelScope.launch {
            try {
                library.tracks.collectLatest { tracks ->
                    val albums = withContext(Dispatchers.Default) { buildLocalLibraryIndex(tracks).knownAlbums }
                    if (mutableHub.value == null) return@collectLatest
                    hubAlbums = albums
                    renderHub()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableHub.update { it?.copy(loading = false, error = "读取本地专辑失败，请返回重试。") } }
        }
    }

    private fun renderHub() {
        mutableHub.update { current -> current?.copy(loading = if (current.onlineMode) current.loading else false,
            albums = hubAlbums.filter { key("${it.name} ${it.artist}").contains(key(current.query)) }) }
    }

    fun hubQuery(value: String) {
        request?.cancel()
        ++generation
        mutableHub.update { it?.copy(query = value, loading = false) }
        if (mutableHub.value?.onlineMode == false) renderHub()
    }

    fun hubMode(online: Boolean) {
        request?.cancel()
        ++generation
        mutableHub.update { it?.copy(onlineMode = online, query = "", loading = false, error = null) }
        if (!online) renderHub()
    }

    fun hubSearch() {
        val current = mutableHub.value ?: return
        if (!current.onlineMode || current.query.isBlank()) return
        request?.cancel()
        val token = ++generation
        mutableHub.value = current.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val result = search.search(current.query, SearchType.ALBUM, limit = 50).first().getOrThrow()
                if (token == generation) mutableHub.update { it?.copy(loading = false,
                    onlineAlbums = result.items.filterIsInstance<SearchResultItem.AlbumItem>().map { it.album }
                        .filter { it.id > 0L }.distinctBy { it.id }) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == generation) mutableHub.update {
                it?.copy(loading = false, error = "搜索网易云专辑失败，请检查网络或修改关键词。")
            } }
        }
    }

    fun hubChooseLocal(album: LocalAlbum) {
        val current = mutableHub.value ?: return
        val local = current.albums.firstOrNull { it.key == album.key } ?: return
        returnHub = current
        open(local, fromHub = true)
    }

    fun hubChooseOnline(album: Album) {
        val current = mutableHub.value ?: return
        val online = current.onlineAlbums.firstOrNull { it.id == album.id } ?: return
        returnHub = current
        openOnline(online, emptyList(), fromHub = true)
    }

    fun openBatchRow(row: AlbumBatchRow) {
        returnHub = mutableHub.value ?: returnHub
        open(row.local, fromHub = true, candidates = row.candidates)
    }

    fun close() {
        if (busy()) return
        ++generation
        request?.cancel()
        pickerObserver?.cancel()
        hubObserver?.cancel()
        mutableState.value = null
        mutablePicker.value = null
        allPickerItems = emptyList()
        presetTracks = emptyList()
        mutableHub.value = returnHub
        returnHub = null
        if (mutableHub.value != null) observeHub()
    }

    fun query(value: String) {
        if (busy()) return
        request?.cancel()
        ++generation
        mutableState.update { it?.copy(query = value, loading = false) }
    }

    fun searchAlbums() {
        val current = mutableState.value ?: return
        if (busy() || current.query.isBlank()) return
        request?.cancel()
        val token = ++generation
        mutableState.value = current.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val result = search.search(current.query.trim(), SearchType.ALBUM, limit = 50).first().getOrThrow()
                if (token != generation) return@launch
                val albums = result.items.filterIsInstance<SearchResultItem.AlbumItem>().map { it.album }
                    .filter { it.id > 0L }.distinctBy { it.id }
                val localName = LocalMatchAlbum.forComparison(current.local.name)
                mutableState.update { it?.copy(loading = false, albums = albums.sortedByDescending { album ->
                    LocalMatchAlbum.forComparison(album.name) == localName
                }) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(token, "搜索专辑失败，请检查网络或修改关键词后重试。") }
        }
    }

    fun chooseAlbum(album: Album) {
        if (busy() || mutableState.value?.albums?.none { it.id == album.id } != false) return
        request?.cancel()
        val token = ++generation
        request = viewModelScope.launch { loadAlbum(album, token) }
    }

    fun refresh() {
        val selected = mutableState.value?.selected ?: return
        if (busy()) return
        request?.cancel()
        val token = ++generation
        request = viewModelScope.launch { loadAlbum(selected, token) }
    }

    private suspend fun loadAlbum(album: Album, token: Long) {
        if (token != generation) return
        mutableState.update { it?.copy(loading = true, error = null) }
        try {
            val detail = playlists.getAlbumDetail(album.id).first().getOrThrow()
            require(detail.id == album.id && detail.tracks.isNotEmpty())
            val full = album.copy(name = detail.name.ifBlank { album.name },
                picUrl = detail.coverImgUrl.ifBlank { album.picUrl },
                artists = detail.artists.ifEmpty { album.artists })
            val wasSaved = store.associations.first()[mutableState.value?.local?.key]?.album?.id == album.id
            preview(full, detail.tracks, wasSaved, token)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fail(token, "读取在线专辑曲目失败，请重试或选择另一张专辑。") }
    }

    private suspend fun preview(album: Album, tracks: List<Track>, saved: Boolean, token: Long) {
        val current = mutableState.value ?: return
        require(tracks.filter { it.id > 0L }.groupBy { it.id }.values.none { duplicates ->
            duplicates.map { it.name to it.dt }.distinct().size > 1
        }) { "在线专辑的重复歌曲信息存在冲突" }
        val valid = tracks.filter { it.id > 0L }.distinctBy { it.id }.map { song ->
            song.copy(al = song.al.copy(id = song.al.id.takeIf { it > 0L } ?: album.id,
                name = song.al.name.ifBlank { album.name }, picUrl = song.al.picUrl.ifBlank { album.picUrl }))
        }
        require(valid.isNotEmpty())
        val links = preferred.manualLinksSnapshot()
        val confirmed = preferred.confirmedTracks()
        val rows = withContext(Dispatchers.Default) {
            val byUri = links.entries.associate { it.value to it.key }
            val decisions = AlbumScopedMatchPolicy.plan(current.local.tracks.map {
                AlbumScopedLocal(candidate(it), it.trackNumber)
            }, valid).associateBy { it.localUri }
            current.local.tracks.map { local ->
                val existing = byUri[local.uri.toString()] ?: local.songId.takeIf {
                    local.source == LocalTrackSource.MELODIA_DOWNLOAD
                }
                val decision = decisions.getValue(local.uri.toString())
                val reusable = AlbumScopedMatchPolicy.evaluate(candidate(local), valid).takeIf {
                    it.status == AlbumScopedMatchStatus.MATCH && it.match?.id in links
                }?.match
                when {
                    local.source == LocalTrackSource.MELODIA_DOWNLOAD -> AlbumSongMatchRow(local,
                        AlbumSongMatchStatus.EXISTING, valid.firstOrNull { it.id == existing },
                        decision.candidates, "Melodia 已下载曲目已有本地播放路径，无需重复关联")
                    existing != null -> AlbumSongMatchRow(local, AlbumSongMatchStatus.EXISTING,
                        valid.firstOrNull { it.id == existing } ?: confirmed[existing], decision.candidates,
                        "保留已有歌曲关联；更换请使用歌曲菜单中的单曲关联入口")
                    reusable != null ->
                        AlbumSongMatchRow(local, AlbumSongMatchStatus.EXISTING, reusable, decision.candidates,
                            "同一在线歌曲已有本地音源，保留原关联；更换请使用歌曲菜单中的单曲关联入口")
                    else -> AlbumSongMatchRow(local, when (decision.status) {
                        AlbumScopedMatchStatus.MATCH -> AlbumSongMatchStatus.READY
                        AlbumScopedMatchStatus.REVIEW -> AlbumSongMatchStatus.REVIEW
                        AlbumScopedMatchStatus.NO_MATCH -> AlbumSongMatchStatus.NO_MATCH
                    }, decision.match, decision.candidates, decision.reason)
                }
            }
        }
        val binding = store.associations.first()[current.local.key]
        val reviewed = binding?.album?.id == album.id &&
            binding?.reviewedLocalFingerprint == localAlbumMatchFingerprint(current.local)
        if (token == generation) mutableState.update { it?.copy(selected = album, onlineTracks = valid,
            rows = rows, loading = false, bindingSaved = saved, reviewCompleted = reviewed,
            error = null, message = null) }
    }

    fun changeAlbum() {
        if (busy()) return
        request?.cancel()
        ++generation
        mutableState.update { it?.copy(selected = null, onlineTracks = emptyList(), rows = emptyList(),
            loading = false, bindingSaved = false, reviewCompleted = false, error = null, message = null) }
        if (mutableState.value?.albums.isNullOrEmpty()) searchAlbums()
    }

    private suspend fun liveFingerprint(localAlbumKey: String): String? = withContext(Dispatchers.Default) {
        buildLocalLibraryIndex(library.tracks.first()).album(localAlbumKey)?.let(::localAlbumMatchFingerprint)
    }

    private suspend fun saveScope(current: LocalAlbumAssociationState): LocalAlbumAssociation {
        val fingerprint = localAlbumMatchFingerprint(current.local)
        check(liveFingerprint(current.local.key) == fingerprint) { "本地专辑内容已变化，请重新打开核对" }
        val existing = store.associations.first()[current.local.key]
        val reviewed = existing?.reviewedLocalFingerprint.takeIf {
            existing?.album?.id == current.selected?.id && it == fingerprint
        }
        val binding = LocalAlbumAssociation(current.local.key, requireNotNull(current.selected),
            current.onlineTracks, reviewed)
        store.put(binding)
        mutableState.update { it?.copy(bindingSaved = true, reviewCompleted = reviewed != null) }
        return binding
    }

    private suspend fun completeReview(current: LocalAlbumAssociationState, binding: LocalAlbumAssociation,
                                       saveFailed: Boolean): Boolean {
        val fingerprint = localAlbumMatchFingerprint(current.local)
        if (!AlbumReviewPolicy.canConfirm(fingerprint, liveFingerprint(current.local.key), stopRequested, saveFailed)) {
            return false
        }
        // Compare-and-set prevents an old dialog from completing a different, newly saved album scope.
        val completed = store.markReviewed(binding, fingerprint)
        if (completed) mutableState.update { it?.copy(reviewCompleted = true) }
        return completed
    }

    fun apply() {
        val current = mutableState.value ?: return
        if (busy() || current.loading || current.selected == null || current.onlineTracks.isEmpty()) return
        val token = generation
        stopRequested = false
        mutableState.value = current.copy(running = true, error = null, message = "正在保存专辑对应…")
        viewModelScope.launch {
            try {
                batch.pauseAndAwaitForAlbumMatching()
                val binding = saveScope(current)
                var saveFailed = false
                for (row in current.rows.filter { it.status == AlbumSongMatchStatus.READY }) {
                    if (stopRequested || token != generation) break
                    val online = row.online
                    if (online == null) {
                        saveFailed = true
                        updateRow(row.local, AlbumSongMatchStatus.ERROR, "候选歌曲信息缺失，请重新检查")
                        continue
                    }
                    mutableState.update { it?.copy(savingUri = row.local.uri.toString(), message = "正在关联：${row.local.title}") }
                    val live = library.tracks.first().firstOrNull { it.uri == row.local.uri && it.albumKey == current.local.key }
                    if (live == null) {
                        saveFailed = true
                        updateRow(row.local, AlbumSongMatchStatus.ERROR, "本地曲目已移出这张专辑，请重新打开核对")
                        mutableState.update { it?.copy(savingUri = null) }
                        continue
                    }
                    val result = preferred.linkAlbumMatch(live, current.onlineTracks, online)
                    when (result.status) {
                        BatchAssociationSaveStatus.SAVED, BatchAssociationSaveStatus.ALREADY_LINKED -> {
                            var reportIssue = false
                            try { batch.recordManualAssociation(live, online) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { reportIssue = true }
                            updateRow(row.local, AlbumSongMatchStatus.SAVED,
                                if (reportIssue) "关联已保存；匹配报告可稍后刷新恢复" else result.reason, online)
                        }
                        BatchAssociationSaveStatus.CONFLICT, BatchAssociationSaveStatus.CHANGED -> {
                            saveFailed = true
                            updateRow(row.local, AlbumSongMatchStatus.REVIEW, result.reason)
                        }
                        BatchAssociationSaveStatus.UNREADABLE -> {
                            saveFailed = true
                            updateRow(row.local, AlbumSongMatchStatus.ERROR, result.reason)
                        }
                    }
                    mutableState.update { it?.copy(savingUri = null) }
                    yield()
                }
                val completed = completeReview(current, binding, saveFailed)
                mutableState.update { it?.copy(message = when {
                    completed -> "专辑已核验，已移入已完成；未关联歌曲仍可在这里单独处理"
                    stopRequested -> "已暂停，已保存的歌曲关联保留；专辑核验尚未完成"
                    else -> "部分歌曲未能保存或专辑内容已变化；请检查后重新确认"
                }) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(token, "本轮处理未完成，已保存的对应与歌曲关联保留；可重试。") }
            finally { if (token == generation) mutableState.update { it?.copy(running = false, savingUri = null) } }
        }
    }

    fun stop() {
        stopRequested = true
        mutableState.update { it?.copy(message = "已请求暂停，当前歌曲处理完成后停止") }
    }

    fun link(local: LocalTrack, online: Track, expectedAlbumId: Long) {
        val current = mutableState.value ?: return
        if (busy() || current.loading || current.selected?.id != expectedAlbumId ||
            current.onlineTracks.none { it.id == online.id } || current.rows.none { it.local.uri == local.uri }) return
        val target = current.onlineTracks.first { it.id == online.id }
        val token = generation
        stopRequested = false
        mutableState.value = current.copy(savingUri = local.uri.toString(), error = null)
        viewModelScope.launch {
            try {
                batch.pauseAndAwaitForAlbumMatching()
                val live = library.tracks.first().firstOrNull { it.uri == local.uri && it.albumKey == current.local.key }
                    ?: error("本地曲目已移出此专辑")
                val binding = saveScope(current)
                when (val result = commitManualAssociation({ preferred.linkLocalTrack(live, target) },
                    { batch.recordManualAssociation(live, target) })) {
                    ManualAssociationCommitResult.Unreadable -> updateRow(local, AlbumSongMatchStatus.ERROR,
                        "文件不可读取，请检查授权或选择其他本地文件")
                    is ManualAssociationCommitResult.Saved -> {
                        val reviewed = try { completeReview(current, binding, saveFailed = false) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { false }
                        // Reconcile this album too: a manual choice can displace another row's reference.
                        var previewIssue = false
                        try { preview(requireNotNull(current.selected), current.onlineTracks, true, token) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { previewIssue = true }
                        updateRow(local, AlbumSongMatchStatus.SAVED,
                            if (result.reportError == null && !previewIssue) "已手动关联"
                            else "关联已保存；列表与报告可稍后刷新恢复", target)
                        mutableState.update { it?.copy(message = if (reviewed)
                            "专辑已核验，已移入已完成；其他歌曲可继续单独处理"
                            else "歌曲关联已保存；专辑核验尚未保存，请重新打开确认") }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(token, "未完成本条处理，请重试；原音频保留。") }
            finally { if (token == generation) mutableState.update { it?.copy(savingUri = null) } }
        }
    }

    fun forget() {
        val current = mutableState.value ?: return
        if (busy()) return
        val token = generation
        mutableState.value = current.copy(running = true)
        viewModelScope.launch {
            try {
                store.remove(current.local.key)
                if (token == generation) mutableState.update { it?.copy(bindingSaved = false, reviewCompleted = false,
                    message = "已解除专辑对应，已有歌曲关联全部保留") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(token, "解除专辑对应失败，请重试。") }
            finally { if (token == generation) mutableState.update { it?.copy(running = false) } }
        }
    }

    private fun updateRow(local: LocalTrack, status: AlbumSongMatchStatus, reason: String, online: Track? = null) {
        mutableState.update { current -> current?.copy(rows = current.rows.map { row ->
            if (row.local.uri == local.uri) row.copy(status = status, reason = reason, online = online ?: row.online) else row
        }) }
    }

    private fun fail(token: Long, message: String) {
        if (token == generation) mutableState.update { it?.copy(loading = false, error = message) }
    }

    private fun candidate(track: LocalTrack) = LocalAudioCandidate(track.uri.toString(), track.title,
        track.artist, track.durationMs, album = track.album,
        fileName = track.path?.replace('\\', '/')?.substringAfterLast('/'))

    private fun key(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
