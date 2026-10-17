package com.lin0721.linmusic.feature.localmusic.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.feature.localmusic.data.PreferredLocalAudioRepository
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchLocalMatchRepository
import com.lin0721.linmusic.feature.localmusic.domain.LocalAudioCandidate
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.feature.localmusic.domain.ManualAssociationCommitResult
import com.lin0721.linmusic.feature.localmusic.domain.OnlineLocalAudioFilter
import com.lin0721.linmusic.feature.localmusic.domain.OnlineLocalAudioRecommendation
import com.lin0721.linmusic.feature.localmusic.domain.OnlineLocalAudioRecommendations
import com.lin0721.linmusic.feature.localmusic.domain.commitManualAssociation
import com.lin0721.linmusic.feature.localmusic.domain.onlineAssociationMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Browsing reads the existing library only. File/descriptor work starts after an explicit choice. */
class OnlineLocalAudioPickerViewModel(
    private val library: LocalLibraryRepository,
    private val preferred: PreferredLocalAudioRepository,
    private val batch: BatchLocalMatchRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow<OnlineLocalPickerState?>(null)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var observer: Job? = null
    private var rendering: Job? = null
    private var tracksByUri = emptyMap<String, LocalTrack>()
    private var ranked = emptyList<OnlineLocalAudioRecommendation>()

    fun open(online: Track) {
        if (online.id <= 0 || mutableState.value?.savingUri != null) return
        observer?.cancel()
        rendering?.cancel()
        val request = ++generation
        tracksByUri = emptyMap()
        ranked = emptyList()
        mutableState.value = OnlineLocalPickerState(online)
        batch.pauseForReview()
        observer = viewModelScope.launch {
            try {
                val target = try { onlineAssociationMetadata(online, preferred.confirmedTrack(online.id)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { online }
                if (request != generation) return@launch
                mutableState.update { it?.copy(online = target) }
                combine(library.tracks, preferred.manualUri(online.id).distinctUntilChanged()) { tracks, current -> tracks to current }
                    .collectLatest { (tracks, current) ->
                        val eligible = tracks.filter { it.source != LocalTrackSource.MELODIA_DOWNLOAD }
                            .distinctBy { it.uri.toString() }
                        val result = withContext(Dispatchers.Default) {
                            OnlineLocalAudioRecommendations.rank(target, eligible.map { local ->
                                LocalAudioCandidate(local.uri.toString(), local.title, local.artist, local.durationMs,
                                    album = local.album, fileName = local.path?.substringAfterLast('/')?.substringAfterLast('\\'))
                            })
                        }
                        if (request != generation) return@collectLatest
                        tracksByUri = eligible.associateBy { it.uri.toString() }
                        ranked = result
                        mutableState.update { it?.copy(libraryCount = eligible.size, currentUri = current) }
                        render(request)
                    }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (request == generation) mutableState.update { it?.copy(loading = false,
                    error = "读取本地曲库失败，请返回重试；也可从文件选择。") }
            }
        }
    }

    fun close() {
        if (mutableState.value?.savingUri != null) return
        ++generation
        observer?.cancel()
        rendering?.cancel()
        mutableState.value = null
        tracksByUri = emptyMap()
        ranked = emptyList()
    }

    fun updateQuery(query: String) {
        val current = mutableState.value ?: return
        if (current.savingUri != null || query == current.query) return
        // Typing starts a whole-library search; the user can then narrow it with the chips.
        mutableState.value = current.copy(query = query,
            filter = if (current.query.isBlank() && query.isNotBlank()) OnlineLocalPickerFilter.ALL else current.filter)
        render(generation, debounce = true)
    }

    fun updateFilter(filter: OnlineLocalPickerFilter) {
        val current = mutableState.value ?: return
        if (current.savingUri != null || current.filter == filter) return
        mutableState.value = current.copy(filter = filter)
        render(generation)
    }

    private fun render(request: Long, debounce: Boolean = false) {
        rendering?.cancel()
        rendering = viewModelScope.launch {
            if (debounce) delay(150)
            val current = mutableState.value ?: return@launch
            val candidates = ranked
            val localTracks = tracksByUri
            val (items, counts) = withContext(Dispatchers.Default) {
                val selections = OnlineLocalPickerFilter.entries.associateWith { filter ->
                    OnlineLocalAudioRecommendations.filter(candidates, filter.toDomain(), current.query)
                }
                selections.getValue(current.filter).mapNotNull { candidate ->
                    localTracks[candidate.candidate.uri]?.let { OnlineLocalPickerItem(it, listOf(candidate.reason)) }
                } to selections.mapValues { it.value.size }
            }
            if (request == generation && mutableState.value?.query == current.query && mutableState.value?.filter == current.filter) {
                mutableState.update { it?.copy(items = items, counts = counts, loading = false) }
            }
        }
    }

    fun select(local: LocalTrack, expectedSongId: Long) {
        val current = mutableState.value ?: return
        if (current.online.id != expectedSongId || current.savingUri != null ||
            current.items.none { it.track.uri == local.uri }) return
        val live = tracksByUri[local.uri.toString()] ?: return
        save(current, live.uri.toString(),
            link = { preferred.linkLocalTrack(live, current.online) },
            report = { batch.recordManualAssociation(live, current.online) })
    }

    /** Cancellation of the system picker leaves this page and its scroll position intact. */
    fun selectFile(uri: Uri, expectedSongId: Long) {
        val current = mutableState.value ?: return
        if (current.online.id != expectedSongId || current.savingUri != null) return
        val local = tracksByUri[uri.toString()]
        save(current, uri.toString(),
            link = { if (local != null) preferred.linkLocalTrack(local, current.online)
                else preferred.link(current.online.id, uri, current.online) },
            report = { if (local != null) batch.recordManualAssociation(local, current.online)
                else batch.refreshAfterManualAssociation() })
    }

    private fun save(current: OnlineLocalPickerState, uri: String,
        link: suspend () -> Boolean, report: suspend () -> Unit) {
        val request = generation
        mutableState.value = current.copy(savingUri = uri, error = null)
        viewModelScope.launch {
            try {
                when (val result = commitManualAssociation(link, report)) {
                    ManualAssociationCommitResult.Unreadable -> if (request == generation) mutableState.update {
                        it?.copy(savingUri = null, error = "无法读取该音频，请检查文件或授权后重试，也可选择另一首。")
                    }
                    is ManualAssociationCommitResult.Saved -> {
                        if (request == generation) {
                            mutableState.update { it?.copy(savingUri = null) }
                            close()
                        }
                        val enabled = preferred.enabled.first()
                        ToastManager.showToast(when {
                            result.reportError != null -> "关联已保存，匹配报告更新失败；可刷新记录恢复"
                            !enabled -> "已关联；请在播放与下载设置中开启「优先使用本地音频」"
                            else -> "已关联本地音频，下次播放时优先读取"
                        })
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (request == generation) mutableState.update { it?.copy(savingUri = null,
                    error = "保存关联失败，请重试；原文件保留。") }
            }
        }
    }
}

private fun OnlineLocalPickerFilter.toDomain(): OnlineLocalAudioFilter = when (this) {
    OnlineLocalPickerFilter.RECOMMENDED -> OnlineLocalAudioFilter.RECOMMENDED
    OnlineLocalPickerFilter.ARTIST -> OnlineLocalAudioFilter.SAME_ARTIST
    OnlineLocalPickerFilter.ALBUM -> OnlineLocalAudioFilter.SAME_ALBUM
    OnlineLocalPickerFilter.ALL -> OnlineLocalAudioFilter.ALL
}
