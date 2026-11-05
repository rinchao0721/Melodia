package com.lin0721.linmusic.feature.podcast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.feature.home.ui.ErrorContent
import com.lin0721.linmusic.feature.home.ui.LoadingIndicator
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.formatSubCount
import org.koin.androidx.compose.koinViewModel

// 播客榜单二级页：电台榜与节目榜两个分段
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastToplistScreen(
    onBack: () -> Unit,
    onRadioClick: (Long) -> Unit,
    viewModel: PodcastToplistViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.loadIfNeeded() }

    SecondaryScreenScaffold(title = "播客榜单", onBack = onBack) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PodcastEdgePadding, vertical = 8.dp)
        ) {
            PodcastToplistTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = state.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PodcastToplistTab.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MelodiaColors.TextPrimary,
                        activeContentColor = MelodiaColors.Background,
                        activeBorderColor = MelodiaColors.Surface,
                        inactiveContainerColor = MelodiaColors.Surface,
                        inactiveContentColor = MelodiaColors.TextSecondary,
                        inactiveBorderColor = MelodiaColors.Surface
                    )
                ) {
                    Text(text = tab.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        when (state.tab) {
            PodcastToplistTab.RADIO -> RadioToplist(state.radios, onRadioClick, viewModel::retry)
            PodcastToplistTab.PROGRAM -> ProgramToplist(
                state = state,
                onProgramClick = viewModel::playProgram,
                onLoadMore = viewModel::loadMorePrograms,
                onRetry = viewModel::retry
            )
        }
    }
}

@Composable
private fun RadioToplist(
    section: PodcastSection<List<PodcastRadio>>,
    onRadioClick: (Long) -> Unit,
    onRetry: () -> Unit
) {
    when (section) {
        PodcastSection.Loading -> LoadingIndicator()
        is PodcastSection.Error -> ErrorContent(message = section.message, onRetry = onRetry)
        is PodcastSection.Success -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
        ) {
            itemsIndexed(section.data, key = { index, radio -> "${radio.id}_$index" }) { index, radio ->
                RankedRadioRow(rank = index + 1, radio = radio, onClick = { onRadioClick(radio.id) })
            }
        }
    }
}

@Composable
private fun ProgramToplist(
    state: PodcastToplistState,
    onProgramClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    when (val section = state.programs) {
        PodcastSection.Loading -> LoadingIndicator()
        is PodcastSection.Error -> ErrorContent(message = section.message, onRetry = onRetry)
        is PodcastSection.Success -> {
            val listState = rememberLazyListState()
            LoadMoreEffect(
                listState,
                enabled = state.programsHasMore && !state.isLoadingMorePrograms,
                onLoadMore = onLoadMore
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
            ) {
                itemsIndexed(section.data, key = { index, program -> "${program.id}_$index" }) { index, program ->
                    RankedProgramRow(
                        rank = index + 1,
                        program = program,
                        progress = state.progress[program.songId],
                        onClick = { onProgramClick(index) }
                    )
                }
                if (state.isLoadingMorePrograms) {
                    item(key = "loading_more") {
                        Box(modifier = Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// 前三名用主题色突出名次
@Composable
private fun RankNumber(rank: Int) {
    Text(
        text = "$rank",
        color = if (rank <= 3) MaterialTheme.colorScheme.primary else MelodiaColors.TextSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(28.dp)
    )
}

@Composable
private fun RankedRadioRow(rank: Int, radio: PodcastRadio, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = PodcastEdgePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankNumber(rank)
        PodcastCover(
            url = radio.picUrl,
            param = "200y200",
            contentDescription = radio.name,
            modifier = Modifier.padding(start = 6.dp).size(52.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = radio.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                radio.djName.takeIf { it.isNotBlank() },
                radio.programCount.takeIf { it > 0 }?.let { "$it 期" },
                formatSubCount(radio.subCount).takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = MelodiaColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun RankedProgramRow(
    rank: Int,
    program: PodcastProgram,
    progress: PodcastProgressEntry?,
    onClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = 22.dp)) { RankNumber(rank) }
        PodcastEpisodeCard(
            program = program,
            progress = progress,
            onClick = onClick,
            modifier = Modifier.weight(1f),
            horizontalPadding = 8.dp
        )
    }
}
