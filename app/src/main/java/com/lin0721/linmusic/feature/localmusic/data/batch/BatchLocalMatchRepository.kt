package com.lin0721.linmusic.feature.localmusic.data.batch

import android.content.Context
import android.provider.OpenableColumns
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.feature.localmusic.data.PreferredLocalAudioRepository
import com.lin0721.linmusic.feature.localmusic.data.transfer.inspectAssociationFile
import com.lin0721.linmusic.feature.localmusic.domain.*
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** User-started work, serialized requests, independently persisted results and explicit error recovery. */
class BatchLocalMatchRepository(
    private val context: Context,
    private val preferredAudio: PreferredLocalAudioRepository,
    private val search: SearchRepository,
    private val covers: LocalMatchCoverRepository,
    private val library: LocalLibraryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = BatchLocalMatchStore(context)
    private val storeMutex = Mutex()
    private val commandMutex = Mutex()
    private val mutableState = MutableStateFlow(BatchLocalMatchState())
    val state: StateFlow<BatchLocalMatchState> = mutableState.asStateFlow()
    private var runningJob: Job? = null
    private var initialized = false
    private var pausing = false
    private val libraryRefreshRequested = AtomicBoolean(false)
    @Volatile private var activeRow: BatchLocalMatchRow? = null

    fun refresh() { scope.launch { commandMutex.withLock { refreshRows() } } }
    fun refreshAfterManualAssociation() = refresh()

    /** Only the explicit refresh action scans MediaStore; report reconciliation stays metadata-only. */
    fun refreshLibrary() {
        // Claim before launching so rapid refresh/start/review taps cannot use the old rows.
        if (!libraryRefreshRequested.compareAndSet(false, true)) return
        mutableState.update { it.copy(isLoading = true) }
        scope.launch {
            try {
                pauseAndAwaitForAlbumMatching()
                commandMutex.withLock {
                    check(library.hasPermission()) { "缺少本地音乐读取权限，请先在本地音乐页面授权" }
                    library.sync()
                    // sync commits Room before this snapshot; new files now enter the report.
                    refreshRows()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.update { it.copy(message = "刷新曲库失败：${error.message.orEmpty().take(160)}；已有匹配记录保留") }
            } finally {
                libraryRefreshRequested.set(false)
                mutableState.update { it.copy(isLoading = libraryRefreshRequested.get()) }
            }
        }
    }

    /** Reopening a report with an unchanged library must not re-read and rewrite every result. */
    fun refreshIfNeeded() {
        scope.launch { commandMutex.withLock { if (!initialized) refreshRows() } }
    }

    /**
     * Called after the reference edit succeeds. Only affected rows are checked and persisted, then
     * published together. Storage errors propagate so callers can report a saved-reference/report error.
     */
    suspend fun recordManualAssociation(local: LocalTrack, online: Track) = withContext(Dispatchers.IO) {
        commandMutex.withLock { storeMutex.withLock {
            val current = state.value.rows
            if (current.isEmpty()) return@withLock
            val selectedUri = local.uri.toString()
            val oldRow = current.firstOrNull { it.local.uri.toString() == selectedUri }
            val relatedIds = setOfNotNull(online.id.takeIf { it > 0L }, local.songId,
                oldRow?.local?.songId, oldRow?.matchedTrack?.id)
            val affected = affectedManualReportUris(current.map { row ->
                ManualReportRowReference(row.local.uri.toString(), row.local.title, row.local.songId, row.matchedTrack?.id)
            }, selectedUri, local.title, relatedIds)
            val playable = HashMap<Long, Track?>()
            val changed = LinkedHashMap<String, BatchLocalMatchRow>()
            val affectedRows = current.filter { it.local.uri.toString() in affected }
            val confirmed = preferredAudio.confirmedForLocalTracks(affectedRows.map { it.local })
            affectedRows.forEach { row ->
                val uri = row.local.uri.toString()
                val linked = confirmed[uri]
                val existing = if (linked == null) row.matchedTrack?.id?.let { id ->
                    if (!playable.containsKey(id)) playable[id] = preferredAudio.confirmedPlayableTrack(id)
                    playable[id]
                }?.takeIf {
                    BatchOnlineMatchPolicy.evaluate(candidateFor(row.local), listOf(it)).status == BatchOnlineMatchStatus.MATCHED
                } else null
                val update = reconcileManualReportRow(row.status, row.matchedTrack?.id, linked, existing,
                    selected = uri == selectedUri) ?: return@forEach
                val reconciled = row.copy(local = row.local.copy(songId = linked?.id),
                    status = update.status, matchedTrack = update.track,
                    candidates = update.track?.let(::listOf) ?: row.candidates,
                    reason = update.reason, usesExistingSource = update.usesExistingSource, updatedAtMs = now())
                if (reconciled != row) changed[uri] = reconciled
            }
            if (changed.isNotEmpty()) {
                store.writeRows(changed.values.toList())
                mutableState.update { it.copy(rows = it.rows.map { row -> changed[row.local.uri.toString()] ?: row }) }
            }
        } }
    }

    fun startOrResume() {
        scope.launch {
            commandMutex.withLock {
                if (libraryRefreshRequested.get() || pausing || runningJob?.isActive == true) return@withLock
                refreshRows()
                // One user-started pass revisits unresolved rows, then stops. Confirmed sources stay intact.
                storeMutex.withLock {
                    val rows = state.value.rows.map { if (BatchMatchRetryPolicy.shouldRetryOnStart(it.status) && it.status != BatchLocalMatchStatus.PENDING)
                        it.copy(status = BatchLocalMatchStatus.PENDING, reason = "等待重新匹配", updatedAtMs = now()) else it }
                    rows.filter { it.status == BatchLocalMatchStatus.PENDING }.forEach(store::writeRow)
                    mutableState.update { it.copy(rows = rows) }
                }
                if (state.value.rows.none { it.status == BatchLocalMatchStatus.PENDING }) {
                    setRunState(false, "已匹配的歌曲均已保留；扫描新歌曲后可继续匹配")
                    return@withLock
                }
                setRunState(true, null)
                runningJob = scope.launch { runBatch() }
            }
        }
    }

    fun pause() {
        scope.launch {
            // All pause entry points share the same finally-protected ownership and last-write
            // reconciliation. A failed progress write must never leave future starts locked out.
            pauseAndAwaitForAlbumMatching()
        }
    }

    /** Review opens immediately; cancel background work without a full-library refresh on its way out. */
    fun pauseForReview() {
        scope.launch { pauseAndAwaitForAlbumMatching() }
    }

    /** Album matching waits until the previous song pass and its last reference edit are reconciled. */
    suspend fun pauseAndAwaitForAlbumMatching() {
        while (true) {
            val (ownsPause, toCancel) = commandMutex.withLock {
                when {
                    pausing -> false to null
                    runningJob?.isActive != true && !state.value.isRunning && activeRow == null -> return
                    else -> {
                        pausing = true
                        true to runningJob
                    }
                }
            }
            if (!ownsPause) {
                // Another pause owns cancel/join/reconciliation. Recheck under the command mutex
                // after it releases pausing instead of returning while that work is still running.
                delay(20L)
                continue
            }
            var issue: String? = null
            try {
                // Once this caller owns the pause, cancellation must not publish a stopped state
                // before the previous job finishes unwinding or loses its final saved reference.
                withContext(NonCancellable) {
                    toCancel?.cancelAndJoin()
                    // A preference write can finish just before cancellation reaches the report commit.
                    // Reconcile that one actual reference without delaying review behind the whole library.
                    activeRow?.let { row ->
                        preferredAudio.confirmedForLocalTrack(row.local)?.let { track ->
                            recordManualAssociation(row.local, track)
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { issue = "本条记录核对失败：${error.message.orEmpty().take(120)}；可手动刷新重试" }
            finally {
                withContext(NonCancellable) {
                    commandMutex.withLock {
                        activeRow = null
                        if (runningJob === toCancel) runningJob = null
                        val message = "已暂停自动匹配，可逐首处理候选；已完成结果保留" + (issue?.let { "；$it" } ?: "")
                        try { setRunState(false, message) }
                        catch (error: Exception) {
                            mutableState.update { it.copy(isRunning = false,
                                message = "$message；暂停状态保存失败：${error.message.orEmpty().take(120)}") }
                        } finally { pausing = false }
                    }
                }
            }
            return
        }
    }

    fun retryUnmatched() = startOrResume()

    private suspend fun refreshRows() {
        mutableState.update { it.copy(isLoading = true) }
        try {
            val tracks = preferredAudio.associatedTracks.first().filter { it.source != LocalTrackSource.MELODIA_DOWNLOAD }
                .distinctBy { it.uri.toString() }
            val confirmed = preferredAudio.confirmedTracks()
            val formats = tracks.associate { it.uri.toString() to formatRank(it) }
            storeMutex.withLock {
                val saved = store.readRows().associateBy { it.local.uri.toString() }
                val rows = tracks.sortedWith(compareByDescending<LocalTrack> { formats[it.uri.toString()] ?: 0 }
                    .thenBy { LocalMatchTitle.forSearch(it.title).lowercase(Locale.ROOT) }.thenBy { it.uri.toString() })
                    .map { track ->
                        val old = saved[track.uri.toString()]
                        val query = queryFor(track)
                        val linked = track.songId?.let(confirmed::get)
                        val existing = if (linked == null) old?.matchedTrack?.id?.let(confirmed::get)?.takeIf {
                            BatchOnlineMatchPolicy.evaluate(candidateFor(track), listOf(it)).status == BatchOnlineMatchStatus.MATCHED
                        }?.let { preferredAudio.confirmedPlayableTrack(it.id) } else null
                        when {
                            linked != null -> BatchLocalMatchRow(track, query,
                                if (old?.status == BatchLocalMatchStatus.MATCHED && old.matchedTrack?.id == linked.id)
                                    BatchLocalMatchStatus.MATCHED else BatchLocalMatchStatus.ALREADY_LINKED,
                                linked, listOf(linked), old?.reason?.takeIf { old.matchedTrack?.id == linked.id }
                                    ?: "已有关联，跳过网络查询", old?.updatedAtMs ?: now())
                            existing != null -> BatchLocalMatchRow(track, query, BatchLocalMatchStatus.ALREADY_LINKED,
                                existing, listOf(existing), "同一歌曲已匹配，沿用已有优先音源", old?.updatedAtMs ?: now(),
                                usesExistingSource = true)
                            old == null -> BatchLocalMatchRow(track, query)
                            old.status == BatchLocalMatchStatus.MATCHED || old.status == BatchLocalMatchStatus.ALREADY_LINKED ->
                                BatchLocalMatchRow(track, query, BatchLocalMatchStatus.NEEDS_CONFIRMATION,
                                    candidates = old.candidates, reason = "原关联已解除或不再可用，请手动核对", updatedAtMs = now())
                            !sameLibraryEvidence(track, old.local) -> BatchLocalMatchRow(track, query,
                                reason = "扫描信息已变化，等待重新检查")
                            else -> old.copy(local = track, query = query)
                        }
                    }
                store.replaceLibrary(rows)
                val message = if (store.damagedRowCount > 0)
                    "已恢复 ${store.damagedRowCount} 条损坏的匹配报告；已确认关联保留"
                else if (!initialized) store.runMessage() else state.value.message
                initialized = true
                mutableState.update { it.copy(rows = rows, isLoading = libraryRefreshRequested.get(), message = message) }
                if (!state.value.isRunning) store.setRunState(false, message)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutableState.update { it.copy(isLoading = libraryRefreshRequested.get(), message = "无法读取匹配记录：${error.message.orEmpty().take(160)}") }
        }
    }

    private suspend fun runBatch() {
        // Equivalent encodings are adjacent; bound successful-response reuse rather than retaining a whole library's searches.
        val queryCache = LinkedHashMap<String, BatchSearchPage>()
        try {
            val halted = BatchMatchRunner(
                nextPending = { state.value.rows.firstOrNull { it.status == BatchLocalMatchStatus.PENDING } },
                process = { row: BatchLocalMatchRow ->
                    activeRow = row
                    processRow(row, queryCache)
                },
                commit = { row ->
                    commitRow(row)
                    activeRow = null
                }
            ).run()
            setRunState(false, halted ?: "本轮匹配完成；可直接处理剩余候选，或点击继续重新匹配未成功项")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            setRunState(false, "匹配因错误暂停：${error.message.orEmpty().take(160)}；已完成结果保留")
        } finally {
            mutableState.update { it.copy(isRunning = false) }
        }
    }

    private suspend fun processRow(row: BatchLocalMatchRow, cache: MutableMap<String, BatchSearchPage>): BatchRunnerStep<BatchLocalMatchRow> {
        val local = row.local
        val already = preferredAudio.confirmedForLocalTrack(local)
        if (already != null) return BatchRunnerStep(row.copy(status = BatchLocalMatchStatus.ALREADY_LINKED,
            matchedTrack = already, candidates = listOf(already), reason = "已有关联，跳过网络查询", updatedAtMs = now()))
        val inspected = inspectAssociationFile(context, local.uri.toString(), local)
            ?: return BatchRunnerStep(row.copy(status = BatchLocalMatchStatus.ERROR,
                reason = "文件不可读取或音频信息不完整，请检查授权并重新扫描", updatedAtMs = now()))
        val candidate = LocalAudioCandidate(inspected.uri, inspected.title, inspected.artist, inspected.durationMs,
            inspected.identity, inspected.album, inspected.fileName)
        val resolved = LocalMatchMetadata.resolve(candidate)
        val query = queryForCandidate(candidate)
        if (resolved.title.isBlank()) return BatchRunnerStep(row.copy(query = query,
            status = BatchLocalMatchStatus.NEEDS_CONFIRMATION, reason = "缺少歌名，需要手动关联", updatedAtMs = now()))
        if (!PreferredLocalAudioMatcher.hasKnownArtist(resolved.artist) ||
            inspected.album?.trim()?.lowercase(Locale.ROOT).orEmpty() in unknownAlbums) return BatchRunnerStep(row.copy(query = query,
            status = BatchLocalMatchStatus.NEEDS_CONFIRMATION,
            reason = "缺少完整歌手或专辑标签，无法安全自动关联；请手动核对", updatedAtMs = now()))
        if (PreferredLocalAudioMatcher.hiddenVersion(candidate)) return BatchRunnerStep(row.copy(query = query,
            status = BatchLocalMatchStatus.NEEDS_CONFIRMATION,
            reason = "文件名含有标签未注明的版本信息，请手动确认", updatedAtMs = now()))
        val confirmed = preferredAudio.confirmedTracksForCandidate(candidate)
        val evidence = try {
            batchMatchEvidence(candidate, confirmed) {
                // A previous report may become decisive under newer matching rules. Reuse it before searching.
                val previous = BatchOnlineMatchPolicy.evaluate(candidate, row.candidates)
                if (previous.status == BatchOnlineMatchStatus.MATCHED) BatchSearchPage(row.candidates, false)
                else cache[query] ?: withTimeout(20_000L) {
                    val response = search.search(query, SearchType.SONG, offset = 0, limit = 100).first().getOrThrow()
                    BatchSearchPage(response.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track }, !response.hasMore)
                }.also {
                    cache[query] = it
                    if (cache.size > 128) cache.remove(cache.keys.first())
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            return pausedError(row.copy(query = query), "搜索超时；请检查网络后继续")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            return pausedError(row.copy(query = query), searchErrorReason(error))
        }
        if (evidence is BatchMatchEvidence.Confirmed) {
            // A confirmed song already has a file. Equivalent MP3/FLAC copies must not replace it.
            val known = evidence.decision
            val actual = preferredAudio.confirmedForLocalTrack(local)
            if (actual != null) return BatchRunnerStep(row.copy(query = query, status = BatchLocalMatchStatus.ALREADY_LINKED,
                matchedTrack = actual, candidates = listOf(actual), reason = "此文件已有关联，跳过网络查询", updatedAtMs = now()))
            val existing = known.matchedTrack?.let { preferredAudio.confirmedPlayableTrack(it.id) }
            if (existing != null) {
                val enriched = covers.fillCovers(listOf(existing)).first()
                preferredAudio.cacheConfirmedCovers(listOf(enriched))
                return BatchRunnerStep(row.copy(query = query, status = BatchLocalMatchStatus.ALREADY_LINKED,
                    matchedTrack = enriched, candidates = listOf(enriched), usesExistingSource = true,
                    reason = "同一歌曲已匹配，沿用已有优先音源", updatedAtMs = now()))
            }
            return BatchRunnerStep(row.copy(query = query, status = BatchLocalMatchStatus.NEEDS_CONFIRMATION,
                candidates = covers.fillCovers(known.candidates.take(20)),
                reason = "已有优先音源不可读取，请核对授权或选择本地文件重新关联", updatedAtMs = now()))
        }
        val page = (evidence as BatchMatchEvidence.Search).page
        val decision = BatchOnlineMatchPolicy.evaluate(candidate, page.tracks)
        val withCovers = covers.fillCovers(decision.candidates.take(20))
        if (decision.status != BatchOnlineMatchStatus.MATCHED) return BatchRunnerStep(row.copy(query = query,
            status = if (page.tracks.isEmpty()) BatchLocalMatchStatus.NO_MATCH else BatchLocalMatchStatus.NEEDS_CONFIRMATION,
            candidates = withCovers, reason = decision.reason, updatedAtMs = now()))
        // Broad search pagination also contains covers and unrelated versions. Decide from the
        // retrieved candidates' complete metadata rather than treating hasMore as ambiguity.
        val matched = withCovers.firstOrNull { it.id == decision.matchedTrack!!.id } ?: decision.matchedTrack!!
        val result = preferredAudio.linkBatchMatch(local, candidate, matched)
        val status = when (result.status) {
            BatchAssociationSaveStatus.SAVED -> BatchLocalMatchStatus.MATCHED
            BatchAssociationSaveStatus.ALREADY_LINKED -> BatchLocalMatchStatus.ALREADY_LINKED
            BatchAssociationSaveStatus.UNREADABLE -> BatchLocalMatchStatus.ERROR
            BatchAssociationSaveStatus.CONFLICT, BatchAssociationSaveStatus.CHANGED -> BatchLocalMatchStatus.NEEDS_CONFIRMATION
        }
        return BatchRunnerStep(row.copy(query = query, status = status,
            matchedTrack = matched.takeIf { status == BatchLocalMatchStatus.MATCHED || status == BatchLocalMatchStatus.ALREADY_LINKED },
            candidates = withCovers, usesExistingSource = false, reason = if (result.status == BatchAssociationSaveStatus.SAVED)
                decision.reason + "，已建立关联" else result.reason, updatedAtMs = now()))
    }

    private suspend fun commitRow(row: BatchLocalMatchRow) {
        storeMutex.withLock {
            if (state.value.rows.none { it.local.uri == row.local.uri }) return@withLock
            // Network work can overlap a user's explicit link/unlink. The current confirmed reference
            // wins over an older search outcome, both in the visible report and in its stored row.
            val linked = preferredAudio.confirmedForLocalTrack(row.local)
            val latest = state.value.rows.first { it.local.uri == row.local.uri }
            val existing = if (row.usesExistingSource) row.matchedTrack?.id?.let {
                preferredAudio.confirmedPlayableTrack(it)
            }?.takeIf { BatchOnlineMatchPolicy.evaluate(candidateFor(row.local), listOf(it)).status == BatchOnlineMatchStatus.MATCHED }
                else null
            val reconciled = when {
                linked != null -> row.copy(local = row.local.copy(songId = linked.id), matchedTrack = linked, candidates = listOf(linked),
                    usesExistingSource = false,
                    status = if ((row.status == BatchLocalMatchStatus.MATCHED && row.matchedTrack?.id == linked.id) ||
                        (latest.status == BatchLocalMatchStatus.MATCHED && latest.matchedTrack?.id == linked.id))
                        BatchLocalMatchStatus.MATCHED else BatchLocalMatchStatus.ALREADY_LINKED,
                    reason = if (row.status == BatchLocalMatchStatus.MATCHED && row.matchedTrack?.id == linked.id)
                        row.reason else "当前已有关联，已保留用户确认的歌曲 ID ${linked.id}")
                existing != null -> row.copy(status = BatchLocalMatchStatus.ALREADY_LINKED,
                    matchedTrack = existing, candidates = listOf(existing), usesExistingSource = true,
                    reason = "同一歌曲已匹配，沿用已有优先音源")
                row.status == BatchLocalMatchStatus.MATCHED || row.status == BatchLocalMatchStatus.ALREADY_LINKED ->
                    row.copy(status = BatchLocalMatchStatus.NEEDS_CONFIRMATION, matchedTrack = null, usesExistingSource = false,
                        reason = "匹配期间关联已被解除或发生变化，请手动核对")
                else -> row
            }
            store.writeRow(reconciled)
            mutableState.update { current -> current.copy(rows = current.rows.map { if (it.local.uri == row.local.uri) reconciled else it }) }
        }
    }

    private suspend fun setRunState(running: Boolean, message: String?) {
        storeMutex.withLock {
            store.setRunState(running, message)
            mutableState.update { it.copy(isRunning = running, message = message) }
        }
    }

    private fun pausedError(row: BatchLocalMatchRow, reason: String) = BatchRunnerStep(row.copy(
        status = BatchLocalMatchStatus.ERROR, reason = reason, updatedAtMs = now()), halt = true,
        message = "$reason；已暂停，剩余歌曲保持待处理")

    private fun searchErrorReason(error: Exception): String = when (error) {
        AppError.RiskControl -> "搜索受到网易云风控，请稍后再继续"
        AppError.NetworkError -> "搜索网络错误，请检查网络后继续"
        AppError.Unauthorized -> "搜索需要登录，请登录后继续"
        AppError.ParseError -> "搜索响应无法解析，请稍后再继续"
        is AppError.BizError -> if (error.code == 406 || error.code == 429) "搜索请求受限（${error.code}），请稍后再继续"
            else "搜索失败（${error.code}），请稍后再继续"
        else -> "搜索失败：${error.message.orEmpty().take(160).ifBlank { "未知网络错误" }}"
    }

    private fun queryFor(track: LocalTrack): String = queryForCandidate(candidateFor(track))
    private fun candidateFor(track: LocalTrack) = LocalAudioCandidate(track.uri.toString(), track.title, track.artist,
        track.durationMs, album = track.album, fileName = track.path?.substringAfterLast('/'))
    private fun queryForCandidate(candidate: LocalAudioCandidate): String {
        val resolved = LocalMatchMetadata.resolve(candidate)
        val title = LocalMatchTitle.forSearch(resolved.title)
        val artist = resolved.artist.trim().takeUnless { it.lowercase(Locale.ROOT) in unknownArtists }
        return listOfNotNull(title, artist).filter(String::isNotBlank).joinToString(" ")
    }
    private fun sameLibraryEvidence(a: LocalTrack, b: LocalTrack): Boolean = a.title == b.title && a.artist == b.artist &&
        a.album == b.album && a.durationMs == b.durationMs && a.sizeBytes == b.sizeBytes && a.path == b.path
    private fun formatRank(t: LocalTrack): Int {
        val fileName = t.path?.substringAfterLast('/') ?: runCatching {
            context.contentResolver.query(t.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        return when (fileName?.substringAfterLast('.')?.lowercase(Locale.ROOT)) {
            "flac", "ape" -> 4; "wav", "aiff", "aif" -> 3; "m4a", "alac" -> 2; else -> 1
        }
    }
    private fun now() = System.currentTimeMillis()
    private val unknownArtists = setOf("", "<unknown>", "unknown", "未知艺术家", "未知歌手")
    private val unknownAlbums = setOf("", "<unknown>", "unknown", "unknown album", "未知专辑")
}
