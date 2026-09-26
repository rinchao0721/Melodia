package com.lin0721.linmusic.feature.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.feature.home.ui.ErrorContent
import com.lin0721.linmusic.feature.home.ui.LoadingIndicator
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadioDetail
import com.lin0721.linmusic.feature.podcast.domain.formatListenerCount
import com.lin0721.linmusic.feature.podcast.domain.formatProgramDate
import com.lin0721.linmusic.feature.podcast.domain.formatProgramDuration
import com.lin0721.linmusic.feature.podcast.domain.formatSubCount
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel

// 电台详情页：封面居中大图 + 主播 + 订阅数，下面是带期号的节目列表
@Composable
fun RadioDetailScreen(
    radioId: Long,
    viewModel: RadioDetailViewModel = koinViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(radioId) { viewModel.load(radioId) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    val success = uiState as? RadioDetailUiState.Success
    val canLoadMore = success != null && success.hasMore && !success.isLoadingMore

    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { if (it) viewModel.loadMore() }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is RadioDetailUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { LoadingIndicator() }

            is RadioDetailUiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { ErrorContent(message = state.message, onRetry = { viewModel.load(radioId) }) }

            is RadioDetailUiState.Success -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
            ) {
                item {
                    RadioDetailHeader(
                        detail = state.detail,
                        isSubscribing = state.isSubscribing,
                        onPlayLatest = { viewModel.playAt(0) },
                        onToggleSubscribe = { viewModel.toggleSubscribe() }
                    )
                }

                item {
                    PodcastSectionTitle("全部节目", "${state.detail.programCount} 期")
                }

                itemsIndexed(
                    items = state.programs,
                    key = { index, program -> "${program.id}_$index" }
                ) { index, program ->
                    RadioProgramRow(program = program, onClick = { viewModel.playAt(index) })
                }

                if (state.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
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

        // 返回键浮在最上层，保证加载态与错误态也能退出
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .size(36.dp)
                .pressable(MelodiaPress.Icon) { onBack() }
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RadioDetailHeader(
    detail: PodcastRadioDetail,
    isSubscribing: Boolean,
    onPlayLatest: () -> Unit,
    onToggleSubscribe: () -> Unit
) {
    var descExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            // 封面拉伸铺底再压暗，省掉一次取色计算也不会有色差
            if (detail.picUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = detail.picUrl.withPodcastCoverParam("500y500"),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.55f),
                                BackgroundDark
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = PodcastEdgePadding, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (detail.picUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = detail.picUrl.withPodcastCoverParam("400y400"),
                        contentDescription = detail.name,
                        modifier = Modifier.size(132.dp).clip(RoundedCornerShape(RadiusCompact)),
                        contentScale = ContentScale.Crop,
                        loading = { CoverPlaceholder() },
                        error = { CoverPlaceholder() }
                    )
                }
                Text(
                    text = detail.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp)
                )
                if (detail.djName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.padding(top = 7.dp)
                    ) {
                        if (detail.djAvatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = detail.djAvatarUrl.withPodcastCoverParam("80y80"),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(text = detail.djName, color = Color(0xFFD8D8D8), fontSize = 12.sp)
                    }
                }
                val stats = listOfNotNull(
                    detail.category.takeIf { it.isNotBlank() },
                    detail.programCount.takeIf { it > 0 }?.let { "$it 期" },
                    formatSubCount(detail.subCount).takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (stats.isNotBlank()) {
                    Text(
                        text = stats,
                        color = TextGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PodcastEdgePadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .pressable(MelodiaPress.Action, shape = CircleShape) { onPlayLatest() }
                    .background(MaterialTheme.colorScheme.primary),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "播放最新一期",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 7.dp)
                )
            }

            // 已订阅用描边、未订阅用实心，两态在深色底上都能看清
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .height(40.dp)
                    .pressable(
                        style = MelodiaPress.Action,
                        shape = CircleShape,
                        enabled = !isSubscribing
                    ) { onToggleSubscribe() }
                    .then(
                        if (detail.subscribed) {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        } else {
                            Modifier.background(Color.White.copy(alpha = 0.14f))
                        }
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSubscribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = if (detail.subscribed) "已订阅" else "订阅",
                        color = if (detail.subscribed) Color(0xFFBDBDBD) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (detail.desc.isNotBlank()) {
            Text(
                text = detail.desc,
                color = TextGray,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                maxLines = if (descExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { descExpanded = !descExpanded }
                    .padding(horizontal = PodcastEdgePadding)
            )
            Text(
                text = if (descExpanded) "收起" else "展开",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { descExpanded = !descExpanded }
                    .padding(horizontal = PodcastEdgePadding, vertical = 4.dp)
            )
        }
    }
}

// 详情页节目行：左侧期号，与主页那种带播放按钮的行区分开
@Composable
private fun RadioProgramRow(program: PodcastProgram, onClick: () -> Unit) {
    // 平板 Expanded 断点下行高/封面/字号统一加码，结构不变
    val isExpanded = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded
    val coverSize = if (isExpanded) 58.dp else 50.dp
    val verticalPadding = if (isExpanded) 13.dp else 9.dp
    val titleFontSize = if (isExpanded) 15.sp else 13.sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = PodcastEdgePadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = program.serialNum.takeIf { it > 0 }?.toString().orEmpty(),
            color = TextGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(width = 26.dp, height = 20.dp)
        )
        SubcomposeAsyncImage(
            model = program.coverUrl.withPodcastCoverParam("160y160"),
            contentDescription = program.name,
            modifier = Modifier.size(coverSize).clip(RoundedCornerShape(RadiusCompact)),
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() }
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = program.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                formatProgramDate(program.createTimeMs).takeIf { it.isNotBlank() },
                formatProgramDuration(program.durationMs).takeIf { it.isNotBlank() },
                formatListenerCount(program.listenerCount).takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = TextGray.copy(alpha = 0.75f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}
