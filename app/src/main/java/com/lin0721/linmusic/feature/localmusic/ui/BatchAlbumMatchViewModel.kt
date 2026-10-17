package com.lin0721.linmusic.feature.localmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.feature.localmusic.data.LocalAlbumAssociation
import com.lin0721.linmusic.feature.localmusic.data.LocalAlbumAssociationStore
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.feature.localmusic.data.PreferredLocalAudioRepository
import com.lin0721.linmusic.feature.localmusic.data.localAlbumMatchFingerprint
import com.lin0721.linmusic.feature.localmusic.data.batch.AlbumBatchRecord
import com.lin0721.linmusic.feature.localmusic.data.batch.AlbumBatchStatus as StoredStatus
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchAlbumMatchStore
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchAlbumOnlineRepository
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchLocalMatchRepository
import com.lin0721.linmusic.feature.localmusic.domain.*
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/** User-started, finite album passes. Only the current row is opened at commit time. */
class BatchAlbumMatchViewModel(
    private val library: LocalLibraryRepository,
    private val preferred: PreferredLocalAudioRepository,
    private val associations: LocalAlbumAssociationStore,
    private val reports: BatchAlbumMatchStore,
    private val online: BatchAlbumOnlineRepository,
    private val songs: BatchLocalMatchRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BatchAlbumMatchState(loading = true))
    val state = mutableState.asStateFlow()
    private var observer: Job? = null
    private var runner: Job? = null
    private var stopRequested = false
    private var committing = false
    private var activeKey: String? = null
    private var activeCandidates = emptyList<Album>()
    private var pauseReason: String? = null
    private var inputs: Inputs? = null
    private data class PlanCache(val local: LocalAlbum, val binding: LocalAlbumAssociation,
        val decisions: Map<String, AlbumScopedMatchDecision>)
    private val fingerprints = ConcurrentHashMap<String, Pair<LocalAlbum, String>>()
    private val plans = ConcurrentHashMap<String, PlanCache>()

    private data class Inputs(
        val albums: List<LocalAlbum>,
        val records: Map<String, AlbumBatchRecord>,
        val bindings: Map<String, LocalAlbumAssociation>,
        val links: Map<Long, String>,
    )

    fun ensureLoaded() {
        if (observer != null) return
        observer = viewModelScope.launch {
            val albums = library.tracks.distinctUntilChanged().map { tracks ->
                withContext(Dispatchers.Default) { buildLocalLibraryIndex(tracks).knownAlbums }
            }
            combine(albums, reports.recordsFlow, associations.associations,
                preferred.manualLinkReferences) { localAlbums, records, bindings, links ->
                Inputs(localAlbums, records, bindings, links)
            }.catch { error ->
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loading = false, message = "读取专辑报告失败，请关闭后重试") }
                observer = null
            }.collectLatest { latest ->
                inputs = latest
                render(latest)
            }
        }
        viewModelScope.launch {
            try {
                if (library.hasPermission()) library.sync()
                else mutableState.update { it.copy(message = "请先在本地音乐中授权并扫描或导入音乐") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(message = "扫描未完成，已显示现有曲库；可在本地音乐重新扫描") } }
        }
    }

    fun filter(value: AlbumBatchFilter) { mutableState.update { it.copy(filter = value) } }
    fun query(value: String) { mutableState.update { it.copy(query = value) } }

    private suspend fun render(latest: Inputs) {
        val directUris = latest.links.values.toHashSet()
        val rows = withContext(Dispatchers.Default) { latest.albums.map { album ->
            val binding = latest.bindings[album.key]
            val saved = latest.records[album.key]?.takeIf { it.fingerprint == fingerprint(album) }
            val linked = linkedCount(album, binding, latest.links, directUris)
            val completion = AlbumReviewPolicy.completion(fingerprint(album), binding?.reviewedLocalFingerprint,
                linked, album.tracks.size)
            val status = when {
                activeKey == album.key -> AlbumBatchStatus.SEARCHING
                completion != AlbumCompletion.NONE -> AlbumBatchStatus.MATCHED
                saved?.status == StoredStatus.ERROR -> AlbumBatchStatus.ERROR
                binding != null -> AlbumBatchStatus.REVIEW
                saved?.status == StoredStatus.MATCHED -> AlbumBatchStatus.UNPROCESSED
                saved != null -> AlbumBatchStatus.valueOf(saved.status.name)
                else -> AlbumBatchStatus.UNPROCESSED
            }
            AlbumBatchRow(album, status, saved?.candidates.orEmpty(), binding?.album ?: saved?.selected,
                reason = when {
                    activeKey == album.key -> "正在查找专辑与核对曲目…"
                    completion == AlbumCompletion.MANUALLY_REVIEWED -> "专辑已人工核验，后续批量会跳过；歌曲关联数量按实际显示"
                    completion == AlbumCompletion.ALL_SONGS_LINKED -> "本专辑歌曲均已有关联，后续批量会跳过"
                    saved?.status == StoredStatus.ERROR -> saved.reason
                    binding != null -> "专辑已对应；${album.tracks.size - linked} 首歌曲仍待核验或继续处理"
                    saved?.status == StoredStatus.MATCHED -> "歌曲关联已变化，等待重新核对"
                    saved != null -> saved.reason
                    else -> "等待按专辑名和歌手查找对应专辑"
                }, linkedSongs = linked, totalSongs = album.tracks.size)
        } }
        mutableState.update { it.copy(rows = rows, loading = false) }
    }

    fun start() {
        if (state.value.running || runner?.isActive == true || state.value.loading) return
        stopRequested = false
        pauseReason = null
        mutableState.update { it.copy(running = true, completed = 0, total = 0, message = "正在准备批量专辑匹配…") }
        runner = viewModelScope.launch {
            try {
                songs.pauseAndAwaitForAlbumMatching()
                val first = snapshot()
                inputs = first
                render(first)
                val queue = state.value.rows.filter { it.status != AlbumBatchStatus.MATCHED }.map { it.local.key }
                mutableState.update { it.copy(total = queue.size) }
                for (key in queue) {
                    ensureActive()
                    if (stopRequested) break
                    val latest = inputs ?: snapshot()
                    val local = latest.albums.firstOrNull { it.key == key } ?: continue
                    val completion = AlbumReviewPolicy.completion(fingerprint(local),
                        latest.bindings[key]?.reviewedLocalFingerprint,
                        linkedCount(local, latest.bindings[key], latest.links), local.tracks.size)
                    if (completion != AlbumCompletion.NONE) continue
                    activeKey = key
                    activeCandidates = emptyList()
                    mutableState.update { it.copy(current = local.name, message = "已完成专辑跳过；正在核对当前专辑和曲目") }
                    render(latest)
                    try {
                        process(local, latest.bindings[key])
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        val message = when (error) {
                            AppError.NetworkError -> "网络连接失败；请联网后继续，未处理专辑保留"
                            AppError.Unauthorized -> "当前请求需要登录；请登录后继续"
                            AppError.RiskControl -> "在线服务暂不可用；已暂停，请稍后继续"
                            else -> "专辑搜索或保存失败；已有关联保留，可稍后继续"
                        }
                        val previous = inputs?.records?.get(key)
                        save(local, StoredStatus.ERROR, activeCandidates.ifEmpty { previous?.candidates.orEmpty() },
                            selected = inputs?.bindings?.get(key)?.album ?: previous?.selected,
                            reason = message, linked = linkedCount(local, inputs?.bindings?.get(key),
                                inputs?.links.orEmpty()))
                        if (stopForNetwork(error)) {
                            stopRequested = true
                            pauseReason = message
                        }
                    } finally { activeKey = null; activeCandidates = emptyList() }
                    mutableState.update { it.copy(completed = it.completed + 1) }
                    inputs?.let { render(it) }
                    // Bound requests to a serial stream; pause remains immediately cancellable here.
                    if (!stopRequested) delay(450)
                }
                mutableState.update { it.copy(message = if (stopRequested) pauseReason ?: "已暂停；已完成结果保留"
                    else "本轮完成；已完成专辑会跳过，其余可核验或继续重试") }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(message = "已暂停；已完成结果保留，点击继续处理剩余专辑") }
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(message = "本轮未完成，已有结果保留；请检查网络后继续") }
            } finally {
                activeKey = null
                activeCandidates = emptyList()
                committing = false
                mutableState.update { it.copy(running = false, current = null) }
                withContext(NonCancellable) { inputs?.let { runCatching { render(it) } } }
            }
        }
    }

    fun pause() {
        if (!state.value.running && runner?.isActive != true) return
        stopRequested = true
        mutableState.update { it.copy(message = "正在暂停；当前保存完成后停止") }
        if (!committing) runner?.cancel()
    }

    suspend fun pauseAndAwait() {
        pause()
        runner?.join()
    }

    private suspend fun process(local: LocalAlbum, binding: LocalAlbumAssociation?) {
        if (binding != null) {
            activeCandidates = listOf(binding.album)
            val verified = online.detail(binding.album)
            if (!verified.complete) {
                save(local, StoredStatus.REVIEW, listOf(binding.album), binding.album,
                    "专辑已对应，但在线曲目未完整取得；保留原关联，请核验")
                return
            }
            commit(local, verified, "沿用已确认的专辑对应")
            return
        }
        val source = descriptor(local)
        val queries = BatchAlbumMatchPolicy.queries(source).take(2)
        if (queries.isEmpty()) {
            save(local, StoredStatus.REVIEW, reason = "缺少可识别的专辑名或歌手，请核验后手动选择")
            return
        }
        val found = linkedMapOf<Long, Album>()
        var exhaustive = true
        for (query in queries) {
            var offset = 0
            for (page in 0 until 3) {
                currentCoroutineContext().ensureActive()
                val result = online.search(query, offset)
                result.items.filterIsInstance<SearchResultItem.AlbumItem>().forEach { item ->
                    if (item.album.id > 0L) found.putIfAbsent(item.album.id, item.album)
                }
                if (!result.hasMore) break
                if (page == 2 || result.rawFetchedCount <= 0) { exhaustive = false; break }
                offset += result.rawFetchedCount
                delay(450)
            }
            if (BatchAlbumMatchPolicy.rank(source, found.values.toList()).any { it.isStrong }) break
            delay(450)
        }
        val ranked = BatchAlbumMatchPolicy.rank(source, found.values.toList())
        val candidates = ranked.map { it.album }.take(50)
        activeCandidates = candidates
        val strong = ranked.filter { it.isStrong }
        if (ranked.isEmpty()) {
            save(local, StoredStatus.NO_MATCH, reason = "未找到对应专辑；可修改名称或手动搜索")
            return
        }
        if (!exhaustive || strong.size > 6) {
            save(local, StoredStatus.REVIEW, candidates, reason = "同名候选较多，已保留推荐；请核验对应版本")
            return
        }
        if (strong.isEmpty()) {
            save(local, StoredStatus.REVIEW, candidates, reason = "找到相近专辑，但专辑名或歌手不够明确，请核验")
            return
        }
        val details = mutableListOf<BatchAlbumDetail>()
        var unread = false
        for (candidate in strong) {
            currentCoroutineContext().ensureActive()
            try { details += online.detail(candidate.album) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (stopForNetwork(error)) throw error
                unread = true
            }
            delay(450)
        }
        if (unread || details.any { !it.complete }) {
            save(local, StoredStatus.REVIEW, candidates, reason = "部分候选曲目未能完整核对，已保留推荐；请联网核验")
            return
        }
        val decision = withContext(Dispatchers.Default) { BatchAlbumMatchPolicy.decide(source, details) }
        if (decision.status == BatchAlbumMatchStatus.AUTO && decision.selected != null) {
            commit(local, decision.selected, decision.reason, candidates)
        } else save(local, if (decision.status == BatchAlbumMatchStatus.NO_MATCH) StoredStatus.NO_MATCH else StoredStatus.REVIEW,
            candidates, reason = decision.reason)
    }

    private suspend fun commit(local: LocalAlbum, selected: BatchAlbumDetail, reason: String,
                               candidates: List<Album> = listOf(selected.album)) {
        val live = liveAlbum(local.key)
        if (live == null || fingerprint(live) != fingerprint(local)) {
            save(local, StoredStatus.REVIEW, candidates, reason = "本地专辑内容已变化，请重新扫描后继续")
            return
        }
        if (stopRequested) return
        val binding = LocalAlbumAssociation(local.key, selected.album, selected.tracks)
        // Once a reference edit starts it finishes atomically before pause/review is admitted.
        committing = true
        try {
            withContext(NonCancellable) { associations.put(binding) }
            val decisions = withContext(Dispatchers.Default) {
                BatchAlbumWritePolicy.plan(descriptor(live).tracks.mapIndexed { index, scoped ->
                    BatchAlbumWritableLocal(scoped, live.tracks[index].sizeBytes)
                }, selected.tracks)
            }
            val links = preferred.manualLinksSnapshot()
            var issue = false
            for (decision in decisions) {
                if (stopRequested) break
                val target = decision.match ?: continue
                if (decision.status != AlbumScopedMatchStatus.MATCH || target.id in links || decision.localUri in links.values) continue
                val current = liveAlbum(local.key)
                if (current == null || fingerprint(current) != fingerprint(live)) { issue = true; break }
                val track = current.tracks.firstOrNull { it.uri.toString() == decision.localUri } ?: continue
                if (track.source == LocalTrackSource.MELODIA_DOWNLOAD) continue
                withContext(NonCancellable) {
                    val result = preferred.linkAlbumMatch(track, selected.tracks, target)
                    if (result.status == BatchAssociationSaveStatus.SAVED || result.status == BatchAssociationSaveStatus.ALREADY_LINKED) {
                        try { songs.recordManualAssociation(track, target) }
                        catch (_: Exception) { issue = true }
                    } else issue = true
                }
            }
            val current = liveAlbum(local.key) ?: live
            val actualLinks = preferred.manualLinksSnapshot()
            inputs = inputs?.let { it.copy(bindings = it.bindings + (binding.localAlbumKey to binding), links = actualLinks) }
            val linked = linkedCount(current, binding, actualLinks)
            val status = if (linked == current.tracks.size) StoredStatus.MATCHED else StoredStatus.REVIEW
            withContext(NonCancellable) {
                save(current, status, candidates, selected.album,
                    if (status == StoredStatus.MATCHED) "$reason；歌曲已关联，后续跳过"
                    else if (issue) "专辑已对应；部分歌曲未能保存或报告待刷新，可展开核验"
                    else "专辑已对应；${current.tracks.size - linked} 首歌曲仍待核验", linked)
            }
        } finally { committing = false }
    }

    private suspend fun save(local: LocalAlbum, status: StoredStatus, candidates: List<Album> = emptyList(),
                             selected: Album? = null, reason: String, linked: Int = 0) {
        val record = AlbumBatchRecord(local.key, fingerprint(local), status, candidates, selected, reason,
            linked, local.tracks.size, System.currentTimeMillis())
        reports.put(record)
        inputs = inputs?.let { it.copy(records = it.records + (record.localAlbumKey to record)) }
    }

    private suspend fun snapshot(): Inputs = Inputs(
        withContext(Dispatchers.Default) { buildLocalLibraryIndex(library.tracks.first()).knownAlbums },
        reports.recordsFlow.first(), associations.associations.first(), preferred.manualLinksSnapshot())

    private fun liveAlbum(key: String): LocalAlbum? = inputs?.albums?.firstOrNull { it.key == key }

    private fun linkedCount(local: LocalAlbum, binding: LocalAlbumAssociation?, links: Map<Long, String>,
                            direct: Set<String> = links.values.toHashSet()): Int {
        val decisions = binding?.let { saved ->
            val previous = plans[local.key]
            if (previous?.local == local && previous.binding == saved) previous.decisions
            else descriptor(local).tracks.map { AlbumScopedMatchPolicy.evaluate(it.candidate, saved.tracks) }.associateBy { it.localUri }
                .also { plans[local.key] = PlanCache(local, saved, it) }
        }.orEmpty()
        return local.tracks.count { track ->
            track.uri.toString() in direct || (track.source == LocalTrackSource.MELODIA_DOWNLOAD && track.songId != null) ||
                decisions[track.uri.toString()]?.let { it.status == AlbumScopedMatchStatus.MATCH && it.match?.id in links } == true
        }
    }

    private fun descriptor(local: LocalAlbum) = BatchAlbumLocal(local.name, local.artist, local.tracks.map { track ->
        AlbumScopedLocal(LocalAudioCandidate(track.uri.toString(), track.title, track.artist, track.durationMs,
            album = track.album, fileName = track.path?.replace('\\', '/')?.substringAfterLast('/')), track.trackNumber)
    })

    private fun stopForNetwork(error: Exception): Boolean = error == AppError.NetworkError ||
        error == AppError.RiskControl || error == AppError.Unauthorized ||
        (error is AppError.BizError && error.code in setOf(301, 401, 403, 405, 429, 509, -462))

    private fun fingerprint(local: LocalAlbum): String {
        fingerprints[local.key]?.takeIf { it.first == local }?.let { return it.second }
        return localAlbumMatchFingerprint(local).also { fingerprints[local.key] = local to it }
    }
}
