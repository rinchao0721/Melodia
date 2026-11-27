package com.lin0721.linmusic.feature.podcast.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.download.ui.DownloadQualityPickerSheet
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.CoverPreviewBounds
import com.lin0721.linmusic.core.ui.components.LocalCoverPreview
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.SelectionAction
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.components.TrackSelectionOverlay
import com.lin0721.linmusic.core.ui.components.TrackSelectionUi
import com.lin0721.linmusic.core.ui.components.rememberSelectionBottomPadding
import com.lin0721.linmusic.core.ui.components.toCoverPreviewBounds
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.feature.home.ui.ErrorContent
import com.lin0721.linmusic.feature.home.ui.LoadingIndicator
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadioDetail
import com.lin0721.linmusic.feature.podcast.domain.formatListenerCount
import com.lin0721.linmusic.feature.podcast.domain.formatProgramDate
import com.lin0721.linmusic.feature.podcast.domain.formatProgramDuration
import com.lin0721.linmusic.feature.podcast.domain.formatSubCount
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Play
import com.lin0721.linmusic.core.ui.icons.ArrowBack
import com.lin0721.linmusic.core.ui.icons.AddToQueue
import com.lin0721.linmusic.core.ui.icons.CheckCircleFilled
import com.lin0721.linmusic.core.ui.icons.Checklist
import com.lin0721.linmusic.core.ui.icons.Download
import com.lin0721.linmusic.core.ui.icons.Tune
import com.lin0721.linmusic.core.ui.icons.Unchecked

// 电台详情页：封面居中大图 + 主播 + 订阅数，下面是带期号的节目列表
@Composable
fun RadioDetailScreen(
    radioId: Long,
    viewModel: RadioDetailViewModel = koinViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val downloadPreferences: DownloadPreferences = koinInject()
    val recordsById = downloadPreferences.recordsByIdSnapshot
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    var showRangeDialog by remember { mutableStateOf(false) }
    var showBatchDownloadSheet by remember { mutableStateOf(false) }
    var pendingSingleDownload by remember { mutableStateOf<PodcastProgram?>(null) }

    BackHandler(enabled = selection.active) {
        viewModel.exitSelection()
    }

    LaunchedEffect(radioId) { viewModel.load(radioId) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    val success = uiState as? RadioDetailUiState.Success
    val canLoadMore = success != null && success.hasMore && !success.isLoadingMore && success.loadMoreError == null && !success.isLoadingRange
    val selectionBottomPadding = rememberSelectionBottomPadding(selection.active)

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

    Box(modifier = Modifier.fillMaxSize().background(MelodiaColors.PageFill)) {
        when (val state = uiState) {
            is RadioDetailUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { LoadingIndicator() }

            is RadioDetailUiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { ErrorContent(message = state.message, onRetry = { viewModel.load(radioId) }) }

            is RadioDetailUiState.Success -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp + selectionBottomPadding)
                ) {
                    item {
                        RadioDetailHeader(
                            detail = state.detail,
                            isSubscribing = state.isSubscribing,
                            playLabel = when {
                                state.resumeProgram != null -> "继续播放"
                                state.sortAscending -> "从头播放"
                                else -> "播放最新一期"
                            },
                            onPlay = { viewModel.playPrimary() },
                            onToggleSubscribe = { viewModel.toggleSubscribe() }
                        )
                    }

                    item {
                        PodcastSectionTitle("全部节目", "${state.detail.programCount} 期")
                    }

                    item(key = "sort") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = PodcastEdgePadding, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                PodcastFilterChip("最新优先", !state.sortAscending) { viewModel.setSortAscending(false) }
                                PodcastFilterChip("最早优先", state.sortAscending) { viewModel.setSortAscending(true) }
                            }
                            if (!selection.active && state.programs.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.09f))
                                        .clickable { viewModel.enterSelection() }
                                        .padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = MelodiaIcons.Checklist,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "多选",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    if (state.isReloadingPrograms) {
                        item(key = "reloading") { SectionLoading() }
                    } else {
                        itemsIndexed(
                            items = state.programs,
                            key = { index, program -> "${program.id}_$index" }
                        ) { index, program ->
                            val isSelected = selection.isSelected(program.songId)
                            val showRangeToChip = selection.active &&
                                selection.anchorId != null &&
                                selection.anchorId != program.songId
                            RadioProgramRow(
                                program = program,
                                progress = state.progress[program.songId],
                                downloadRecord = recordsById[program.songId],
                                selectionActive = selection.active,
                                isSelected = isSelected,
                                showRangeToChip = showRangeToChip,
                                onClick = {
                                    if (selection.active) {
                                        viewModel.toggleSelected(program.songId)
                                    } else {
                                        viewModel.playAt(index)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (selection.active) {
                                        viewModel.selectRangeTo(program.songId)
                                    } else {
                                        viewModel.enterSelection(program.songId)
                                    }
                                },
                                onSelectRangeTo = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.selectRangeTo(program.songId)
                                },
                                onDownloadClick = {
                                    if (state.askDownloadQuality) {
                                        pendingSingleDownload = program
                                    } else {
                                        viewModel.downloadProgram(program)
                                    }
                                },
                                onDownloadLongClick = {
                                    pendingSingleDownload = program
                                }
                            )
                        }
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
                    } else if (!state.isReloadingPrograms) {
                        state.loadMoreError?.let { message ->
                            item(key = "load_more_error") {
                                SectionError(message = message, onRetry = { viewModel.loadMore() })
                            }
                        }
                    }
                }

                val selectionUi = remember(selection) {
                    TrackSelectionUi(
                        active = selection.active,
                        selectedIds = selection.selectedIds,
                        onEnter = { viewModel.enterSelection(it) },
                        onToggle = { viewModel.toggleSelected(it) }
                    )
                }
                val visibleIds = remember(state.programs) { state.programs.map { it.songId } }
                val selectionActions = listOf(
                    SelectionAction(
                        label = "选区间",
                        icon = MelodiaIcons.Tune,
                        enabledWhenEmpty = true,
                        onClick = { showRangeDialog = true }
                    ),
                    SelectionAction(
                        label = "下一首",
                        icon = MelodiaIcons.AddToQueue,
                        onClick = { viewModel.playSelectedNext() }
                    ),
                    SelectionAction(
                        label = "下载",
                        icon = MelodiaIcons.Download,
                        onClick = {
                            if (state.askDownloadQuality) {
                                showBatchDownloadSheet = true
                            } else {
                                viewModel.downloadSelected()
                            }
                        }
                    )
                )

                TrackSelectionOverlay(
                    selectionUi = selectionUi,
                    actions = selectionActions,
                    visibleIds = visibleIds,
                    headerColor = MelodiaColors.PageFill,
                    onToggleSelectAll = { viewModel.toggleSelectAll() },
                    onExit = { viewModel.exitSelection() },
                    unit = "期"
                )

                if (state.isLoadingRange) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 64.dp)
                            .zIndex(25f)
                            .clip(CircleShape)
                            .background(MelodiaColors.Surface)
                            .border(0.5.dp, MelodiaColors.Divider, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "正在拉取指定区间节目...",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                    }
                }

                if (showRangeDialog) {
                    RadioRangeSelectDialog(
                        totalPrograms = state.detail.programCount.coerceAtLeast(state.programs.size),
                        loadedCount = state.programs.size,
                        sortAscending = state.sortAscending,
                        onConfirm = { start, end ->
                            showRangeDialog = false
                            viewModel.selectEpisodeRange(start, end)
                        },
                        onDismiss = { showRangeDialog = false }
                    )
                }

                pendingSingleDownload?.let { program ->
                    DownloadQualityPickerSheet(
                        onQualitySelected = { level ->
                            viewModel.downloadProgram(program, level)
                            pendingSingleDownload = null
                        },
                        onDismiss = { pendingSingleDownload = null },
                        songId = program.songId,
                        headline = program.name,
                        supportingText = state.detail.name
                    )
                }

                if (showBatchDownloadSheet) {
                    DownloadQualityPickerSheet(
                        onQualitySelected = { level ->
                            viewModel.downloadSelected(level)
                            showBatchDownloadSheet = false
                        },
                        onDismiss = { showBatchDownloadSheet = false },
                        headline = "批量下载节目",
                        supportingText = "已选 ${selection.count} 期",
                        batchSongIds = selection.selectedIds.toList()
                    )
                }
            }
        }

        // 返回键浮在最上层，保证加载态与错误态也能退出；多选态下让位给多选顶栏
        if (!selection.active) {
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
                    imageVector = MelodiaIcons.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RadioDetailHeader(
    detail: PodcastRadioDetail,
    isSubscribing: Boolean,
    playLabel: String,
    onPlay: () -> Unit,
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
                                MelodiaColors.PageFill
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
                    val coverPreview = LocalCoverPreview.current
                    val haptic = LocalHapticFeedback.current
                    var coverBounds by remember { mutableStateOf<CoverPreviewBounds?>(null) }
                    val cachedCoverUrl = detail.picUrl.withPodcastCoverParam("400y400")
                    SubcomposeAsyncImage(
                        model = cachedCoverUrl,
                        contentDescription = detail.name,
                        modifier = Modifier
                            .size(132.dp)
                            .onGloballyPositioned { coverBounds = it.toCoverPreviewBounds() }
                            .clip(RoundedCornerShape(RadiusCompact))
                            .pointerInput(cachedCoverUrl, detail.name) {
                                detectTapGestures(
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        coverPreview.open(cachedCoverUrl, detail.name, coverBounds)
                                    }
                                )
                            },
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
                        color = MelodiaColors.TextSecondary,
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
                    .pressable(MelodiaPress.Action, shape = CircleShape) { onPlay() }
                    .background(MaterialTheme.colorScheme.primary),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MelodiaIcons.Play,
                    contentDescription = null,
                    tint = MelodiaColors.OnAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = playLabel,
                    color = MelodiaColors.OnAccent,
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
                color = MelodiaColors.TextSecondary,
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

// 详情页节目行：左侧勾选/期号 + 封面 + 标题信息 + 右侧单曲下载或「连选到此」快捷操作
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RadioProgramRow(
    program: PodcastProgram,
    progress: PodcastProgressEntry?,
    downloadRecord: DownloadRecord?,
    selectionActive: Boolean,
    isSelected: Boolean,
    showRangeToChip: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectRangeTo: () -> Unit,
    onDownloadClick: () -> Unit,
    onDownloadLongClick: () -> Unit
) {
    // 平板 Expanded 断点下行高/封面/字号统一加码，结构不变
    val isExpanded = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded
    val coverSize = if (isExpanded) 58.dp else 50.dp
    val verticalPadding = if (isExpanded) 13.dp else 9.dp
    val titleFontSize = if (isExpanded) 15.sp else 13.sp
    val rowBg = if (selectionActive && isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = PodcastEdgePadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionActive) {
            Icon(
                imageVector = if (isSelected) MelodiaIcons.CheckCircleFilled else MelodiaIcons.Unchecked,
                contentDescription = if (isSelected) "已选中" else "未选中",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MelodiaColors.TextSecondary,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(20.dp)
            )
        } else {
            Text(
                text = program.serialNum.takeIf { it > 0 }?.toString().orEmpty(),
                color = MelodiaColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.size(width = 26.dp, height = 20.dp)
            )
        }
        SubcomposeAsyncImage(
            model = program.coverUrl.withPodcastCoverParam("160y160"),
            contentDescription = program.name,
            modifier = Modifier.size(coverSize).clip(RoundedCornerShape(RadiusCompact)),
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() }
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
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
                    color = MelodiaColors.TextSecondary.copy(alpha = 0.75f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            when {
                progress == null -> Unit
                progress.isFinished -> Text(
                    text = "已听完",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                progress.fraction > 0f -> LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MelodiaColors.Overlay.copy(alpha = 0.16f)
                )
            }
        }
        if (selectionActive) {
            if (showRangeToChip) {
                Text(
                    text = "连选到此",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                        .clickable(onClick = onSelectRangeTo)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else if (program.songId > 0L) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = onDownloadClick,
                        onLongClick = onDownloadLongClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (downloadRecord != null) {
                    Icon(
                        imageVector = MelodiaIcons.CheckCircleFilled,
                        contentDescription = "已下载",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = MelodiaIcons.Download,
                        contentDescription = "下载节目",
                        tint = MelodiaColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RadioRangeSelectDialog(
    totalPrograms: Int,
    loadedCount: Int,
    sortAscending: Boolean,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val safeTotal = totalPrograms.coerceAtLeast(1)
    var startText by remember { mutableStateOf("1") }
    var endText by remember { mutableStateOf(minOf(100, safeTotal).toString()) }
    val presets = remember(safeTotal, loadedCount) {
        listOfNotNull(
            10.takeIf { safeTotal >= 10 }?.let { "前 10 期" to (1 to it) },
            30.takeIf { safeTotal >= 30 }?.let { "前 30 期" to (1 to it) },
            50.takeIf { safeTotal >= 50 }?.let { "前 50 期" to (1 to it) },
            100.takeIf { safeTotal >= 100 }?.let { "前 100 期" to (1 to it) },
            "已加载 ($loadedCount 期)" to (1 to loadedCount.coerceAtLeast(1)),
            if (safeTotal <= 500 && safeTotal > loadedCount) "全部 ($safeTotal 期)" to (1 to safeTotal) else null
        ).distinctBy { it.second }
    }
    val parsedStart = startText.toIntOrNull()
    val parsedEnd = endText.toIntOrNull()
    val isValid = parsedStart != null && parsedEnd != null && parsedStart >= 1 && parsedEnd >= 1

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MelodiaColors.Surface,
        title = {
            Text(
                text = "按位置区间选择节目",
                color = MelodiaColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "按当前列表排序（${if (sortAscending) "最早优先" else "最新优先"}，共 $safeTotal 期），未加载的期数会自动向后拉取。",
                    color = MelodiaColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { (label, range) ->
                        val selected = parsedStart == range.first && parsedEnd == range.second
                        FilterChip(
                            selected = selected,
                            onClick = {
                                startText = range.first.toString()
                                endText = range.second.toString()
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { input -> startText = input.filter { it.isDigit() } },
                        label = { Text("起始序号", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Text("~", color = MelodiaColors.TextSecondary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { input -> endText = input.filter { it.isDigit() } },
                        label = { Text("结束序号", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            MelodiaTextButton(
                enabled = isValid,
                onClick = {
                    if (parsedStart != null && parsedEnd != null) {
                        onConfirm(parsedStart, parsedEnd)
                    }
                }
            ) {
                Text("确认选中", color = if (isValid) MelodiaColors.Accent else MelodiaColors.TextSecondary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            MelodiaTextButton(onClick = onDismiss) {
                Text("取消", color = MelodiaColors.TextSecondary)
            }
        }
    )
}
