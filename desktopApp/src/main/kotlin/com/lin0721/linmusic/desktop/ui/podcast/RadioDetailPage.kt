package com.lin0721.linmusic.desktop.ui.podcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.home.HomeTabError
import com.lin0721.linmusic.desktop.ui.home.HomeTabLoading
import com.lin0721.linmusic.desktop.ui.home.OutlineChip
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadioDetail
import com.lin0721.linmusic.feature.podcast.domain.formatSubCount
import com.lin0721.linmusic.feature.podcast.ui.PodcastSection
import com.lin0721.linmusic.feature.podcast.ui.RadioDetailUiState
import com.lin0721.linmusic.feature.podcast.ui.RadioDetailViewModel
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Play

// 距离列表底部还剩几项时加载下一页节目
private const val PROGRAM_LOAD_MORE_THRESHOLD = 6
private val CoverSize = 200.dp

// 电台详情页：封面与简介 → 播放与订阅 → 排序 → 带期号的节目表（双击播放）
@Composable
fun RadioDetailPage(radioId: Long, viewModel: RadioDetailViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(radioId) { viewModel.load(radioId) }
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        RadioDetailUiState.Loading -> HomeTabLoading(modifier)
        is RadioDetailUiState.Error -> HomeTabError(state.message, { viewModel.load(radioId) }, modifier)
        is RadioDetailUiState.Success -> RadioDetailContent(state, viewModel, modifier)
    }
}

@Composable
private fun RadioDetailContent(
    state: RadioDetailUiState.Success,
    viewModel: RadioDetailViewModel,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(state.hasMore, state.isLoadingMore, state.isReloadingPrograms, state.loadMoreError) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && !state.isLoadingMore && !state.isReloadingPrograms && state.loadMoreError == null &&
                lastVisible >= listState.layoutInfo.totalItemsCount - PROGRAM_LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 24.dp)
        ) {
            item(key = "header") { RadioHeader(state.detail) }
            item(key = "actions") {
                RadioActions(
                    state = state,
                    onPlay = viewModel::playPrimary,
                    onToggleSubscribe = viewModel::toggleSubscribe
                )
            }
            item(key = "sort") {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlineChip("最新优先", !state.sortAscending) { viewModel.setSortAscending(false) }
                    OutlineChip("最早优先", state.sortAscending) { viewModel.setSortAscending(true) }
                }
            }
            if (state.isReloadingPrograms) {
                item(key = "reloading") { PodcastInlineLoading() }
            } else {
                itemsIndexed(state.programs, key = { index, program -> "${program.id}_$index" }) { index, program ->
                    PodcastProgramRow(
                        program = program,
                        progress = state.progress[program.songId],
                        onPlay = { viewModel.playAt(index) },
                        index = program.serialNum.takeIf { it > 0 }
                    )
                }
            }
            if (state.isLoadingMore) {
                item(key = "loading_more") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DesktopColors.Accent)
                    }
                }
            } else if (!state.isReloadingPrograms) {
                state.loadMoreError?.let { message ->
                    item(key = "load_more_error") {
                        PodcastInlineError(message = message, onRetry = viewModel::loadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioHeader(detail: PodcastRadioDetail) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Bottom) {
        Cover(detail.picUrl, CoverSize, shape = RoundedCornerShape(8.dp))
        Column(Modifier.weight(1f).padding(start = 24.dp)) {
            Text("电台", color = DesktopColors.TextGray, fontSize = 13.sp)
            Text(
                text = detail.name,
                color = DesktopColors.TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (detail.djName.isNotBlank()) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (detail.djAvatarUrl.isNotBlank()) {
                        Cover(detail.djAvatarUrl, 24.dp, shape = CircleShape)
                    }
                    Text(
                        text = detail.djName,
                        color = DesktopColors.TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = if (detail.djAvatarUrl.isNotBlank()) 8.dp else 0.dp)
                    )
                }
            }
            val stats = listOfNotNull(
                detail.category.takeIf { it.isNotBlank() },
                detail.programCount.takeIf { it > 0 }?.let { "$it 期" },
                formatSubCount(detail.subCount).takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (stats.isNotBlank()) {
                Text(stats, color = DesktopColors.TextGray, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (detail.desc.isNotBlank()) {
                Text(
                    text = detail.desc,
                    color = DesktopColors.TextGray,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { expanded = !expanded }
                )
            }
        }
    }
}

@Composable
private fun RadioActions(
    state: RadioDetailUiState.Success,
    onPlay: () -> Unit,
    onToggleSubscribe: () -> Unit
) {
    val playLabel = when {
        state.resumeProgram != null -> "继续播放"
        state.sortAscending -> "从头播放"
        else -> "播放最新一期"
    }
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPlay,
            enabled = state.programs.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = DesktopColors.Accent,
                contentColor = DesktopColors.OnAccent
            )
        ) {
            Icon(MelodiaIcons.Play, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(playLabel, modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onToggleSubscribe,
            enabled = !state.isSubscribing,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.TextPrimary)
        ) {
            if (state.isSubscribing) {
                CircularProgressIndicator(Modifier.size(16.dp), color = DesktopColors.TextPrimary, strokeWidth = 2.dp)
            } else {
                Text(if (state.detail.subscribed) "已订阅" else "订阅")
            }
        }
    }
}
