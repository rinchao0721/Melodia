package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchLocalMatchRepository
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchRow
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** The existing report/review flow can be opened from settings without a local-library page. */
@Composable
fun BatchLocalMatchManager(
    onDismiss: () -> Unit,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val onlineAssociation by viewModel.onlineAssociation.collectAsStateWithLifecycle()
    val associationCompletion by viewModel.associationCompletion.collectAsStateWithLifecycle()
    val associationSavingUri by viewModel.associationSavingUri.collectAsStateWithLifecycle()
    val batchRepository: BatchLocalMatchRepository = koinInject()
    val batchState by batchRepository.state.collectAsStateWithLifecycle()
    var reviewSession by remember { mutableStateOf<BatchReviewSession?>(null) }
    var openingReview by remember { mutableStateOf(false) }
    var reviewBaselineSequence by remember { mutableStateOf(0L) }
    var batchFilter by rememberSaveable { mutableStateOf(BatchMatchFilter.REVIEW) }
    val batchListState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()

    fun openReviewItem(session: BatchReviewSession) {
        val row = batchRepository.state.value.rows.firstOrNull { it.local.uri.toString() == session.currentUri } ?: return
        viewModel.openOnlineAssociation(
            library.tracks.firstOrNull { it.uri == row.local.uri } ?: row.local,
            initialCandidates = (row.candidates + listOfNotNull(row.matchedTrack)).distinctBy { it.id }
        )
    }

    fun beginReview(row: BatchLocalMatchRow? = null, onOpened: () -> Unit = {}) {
        if (openingReview || batchRepository.state.value.isLoading ||
            viewModel.associationSavingUri.value != null || viewModel.onlineAssociation.value != null) return
        openingReview = true
        uiScope.launch {
            try {
                // A tap may arrive while the automatic pass is preparing its first row. Wait for
                // its final reference/report reconciliation before selecting fresh review rows.
                batchRepository.pauseAndAwaitForAlbumMatching()
                currentCoroutineContext().ensureActive()
                if (batchRepository.state.value.isLoading) return@launch
                val session = BatchReviewSession.start(batchRepository.state.value.rows, batchFilter,
                    row?.local?.uri?.toString()) ?: return@launch
                reviewBaselineSequence = viewModel.associationCompletion.value?.sequence ?: 0L
                reviewSession = session
                openReviewItem(session)
                onOpened()
            } finally { openingReview = false }
        }
    }

    fun advanceReview(completedUri: String? = null) {
        if (viewModel.associationSavingUri.value != null) return
        val current = reviewSession ?: return
        val remaining = batchUnresolvedUris(batchRepository.state.value.rows) - setOfNotNull(completedUri)
        val next = current.advance(remaining)
        reviewBaselineSequence = viewModel.associationCompletion.value?.sequence ?: 0L
        reviewSession = next
        if (next == null) viewModel.closeOnlineAssociation() else openReviewItem(next)
    }

    fun confirmReview(online: Track, expectedUri: String?) {
        val current = viewModel.onlineAssociation.value ?: return
        val local = current.track
        // A second tap from the old frame must never associate its candidate to the next file.
        if (expectedUri == null || local.uri.toString() != expectedUri || current.results.none { it.id == online.id }) return
        val completed = viewModel.associationCompletion.value
        if (completed?.localUri == local.uri.toString() && completed.sequence > reviewBaselineSequence) return
        viewModel.linkOnlineAssociation(local, online, closeOnSuccess = false) {
            batchRepository.recordManualAssociation(local, online)
        }
    }

    LaunchedEffect(viewModel) {
        // Reuse the existing permission check; this screen never launches a permission request.
        viewModel.ensureLoaded()
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }
    LaunchedEffect(batchRepository, library.tracks.size) { batchRepository.refresh() }
    LaunchedEffect(batchRepository) { batchRepository.refreshIfNeeded() }
    LaunchedEffect(associationCompletion?.sequence, associationSavingUri) {
        val completed = associationCompletion ?: return@LaunchedEffect
        if (associationSavingUri == null && completed.sequence > reviewBaselineSequence &&
            reviewSession?.currentUri == completed.localUri) {
            advanceReview(completed.localUri)
        }
    }

    val renderedReviewUri = onlineAssociation?.track?.uri?.toString()
    BatchLocalMatchSheet(
        state = batchState,
        filter = batchFilter,
        onFilterChange = {
            if (batchFilter != it) {
                batchFilter = it
                uiScope.launch { batchListState.scrollToItem(0) }
            }
        },
        listState = batchListState,
        savingUri = associationSavingUri,
        openingReview = openingReview,
        onDismiss = {
            if (viewModel.associationSavingUri.value == null) {
                viewModel.closeOnlineAssociation()
                reviewSession = null
                onDismiss()
            }
        },
        onStartOrResume = batchRepository::startOrResume,
        onPause = batchRepository::pause,
        onRefresh = batchRepository::refreshLibrary,
        onManualAssociation = { row -> beginReview(row) },
        onLinkCandidate = { row, track ->
            beginReview(row) { confirmReview(track, row.local.uri.toString()) }
        },
        associationState = onlineAssociation,
        reviewPosition = reviewSession?.positionLabel,
        onCloseReview = {
            if (viewModel.associationSavingUri.value == null &&
                renderedReviewUri == viewModel.onlineAssociation.value?.track?.uri?.toString()) {
                viewModel.closeOnlineAssociation()
                reviewSession = null
            }
        },
        onSkipReview = {
            if (renderedReviewUri == viewModel.onlineAssociation.value?.track?.uri?.toString()) advanceReview()
        },
        onReviewQueryChange = viewModel::updateOnlineAssociationQuery,
        onReviewSearch = { viewModel.searchOnlineAssociation() },
        onReviewLoadMore = { viewModel.searchOnlineAssociation(loadMore = true) },
        onReviewConfirm = { track -> confirmReview(track, renderedReviewUri) },
        onStartReview = { beginReview() }
    )
}
