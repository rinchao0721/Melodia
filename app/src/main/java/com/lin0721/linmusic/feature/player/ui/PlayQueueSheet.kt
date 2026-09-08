package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.ui.components.DraggableSongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.SurfaceLight
import com.lin0721.linmusic.core.ui.theme.TextGray
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayQueueSheet(
    queue: List<QueueItem>,
    currentIndex: Int,
    playMode: PlayMode,
    playContext: String?,
    isPlaying: Boolean,
    onPlayAtIndex: (Int) -> Unit,
    onRemoveAtIndex: (Int) -> Unit,
    onMoveItem: (from: Int, to: Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onClearQueue: () -> Unit,
    onDisableRoaming: () -> Unit,
    onDisableIntelligence: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isRoaming = playContext == "similar_roaming"

    LaunchedEffect(currentIndex) {
        if (isRoaming) {
            listState.scrollToItem(0)
        } else if (currentIndex in queue.indices) {
            // 已播放占用的项数 = 1 (已播放头部) + currentIndex (已播放的歌曲数量)
            val scrollIndex = if (currentIndex > 0) currentIndex + 1 else 0
            listState.scrollToItem(scrollIndex)
        }
    }

    // 拖拽状态
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settlingIndex by remember { mutableIntStateOf(-1) }
    val settleOffset = remember { Animatable(0f) }
    val isDraggingActive = draggedIndex >= 0
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // 只有"接下来播放"段可拖，越界判定全部以这段的首尾为准
    val upcomingStart = currentIndex + 1

    val upcomingKeys = remember(queue, upcomingStart) {
        val seen = mutableMapOf<Long, Int>()
        queue.drop(upcomingStart).map { item ->
            val occurrence = seen.getOrElse(item.songId) { 0 }
            seen[item.songId] = occurrence + 1
            "upcoming_${item.songId}_$occurrence"
        }
    }

    // 边缘检测和 composed range 都取自 layoutInfo，必须先换算
    val playedBlockCount = if (!isRoaming && currentIndex > 0) 1 + currentIndex else 0
    val currentBlockCount = if (currentIndex in queue.indices) 2 else 0
    val upcomingFirstLazyIndex = playedBlockCount + currentBlockCount + 1
    fun lazyIndexOf(queueIndex: Int): Int = upcomingFirstLazyIndex + (queueIndex - upcomingStart)
    fun queueIndexOf(lazyIndex: Int): Int = upcomingStart + (lazyIndex - upcomingFirstLazyIndex)
    val fallbackItemHeightPx = with(density) { 60.dp.toPx() }
    fun draggedItemHeight(): Float =
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == lazyIndexOf(draggedIndex) }
            ?.size?.toFloat() ?: fallbackItemHeightPx

    // 滚出组合范围的行会被销毁并杀死其手势协程，因此不能把 draggedIndex 换到尚未组合出来的位置
    fun maxComposedQueueIndex(): Int =
        listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index }
            ?.let { queueIndexOf(it) } ?: queue.lastIndex
    fun minComposedQueueIndex(): Int =
        listState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index }
            ?.let { queueIndexOf(it) } ?: upcomingStart

    // 上限取一整行高，与下面的交换阈值一致：更小会在触边瞬间把 dragOffset 夹断产生可见回跳
    fun clampDragOffset(offset: Float): Float {
        val itemHeight = draggedItemHeight()
        return when {
            (draggedIndex <= upcomingStart || draggedIndex <= minComposedQueueIndex()) && offset < 0f ->
                max(offset, -itemHeight)
            (draggedIndex >= queue.lastIndex || draggedIndex >= maxComposedQueueIndex()) && offset > 0f ->
                min(offset, itemHeight)
            else -> offset
        }
    }

    // 手势与自动滚屏共用的交换推进：dragOffset 每越过一整行就与相邻行换位并扣掉一行高，
    // 使拖拽行的视觉位置在换位前后保持连续
    // LazyColumn 记的是视口首项的 key，重排后会让锚点跟着这个 key 跑到新 index，视口便整体
    // 跳一行，把拖拽行顶出组合范围直接销毁掉，手势协程随之被杀而断触。
    // 换位只是交换相邻两行内容，列表长度和行高都没变，滚动位置本就该原地不动，
    // 因此换位涉及锚点时按原 index 重新钉一次（requestScrollToItem 只认 index、不再跟 key）
    fun neutralizeAnchorShift(from: Int, to: Int) {
        val anchor = listState.firstVisibleItemIndex
        if (lazyIndexOf(from) != anchor && lazyIndexOf(to) != anchor) return
        listState.requestScrollToItem(anchor, listState.firstVisibleItemScrollOffset)
    }

    fun advanceSwaps() {
        var swapped = false
        val itemHeight = draggedItemHeight()
        while (dragOffset > itemHeight && draggedIndex < queue.lastIndex && draggedIndex < maxComposedQueueIndex()) {
            neutralizeAnchorShift(draggedIndex, draggedIndex + 1)
            onMoveItem(draggedIndex, draggedIndex + 1)
            draggedIndex += 1
            dragOffset = clampDragOffset(dragOffset - itemHeight)
            swapped = true
        }
        while (dragOffset < -itemHeight && draggedIndex > upcomingStart && draggedIndex > minComposedQueueIndex()) {
            neutralizeAnchorShift(draggedIndex, draggedIndex - 1)
            onMoveItem(draggedIndex, draggedIndex - 1)
            draggedIndex -= 1
            dragOffset = clampDragOffset(dragOffset + itemHeight)
            swapped = true
        }
        if (swapped) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // 拖到列表边缘时自动滚屏慢慢加速。按帧回调驱动，速度以 px/秒 表达再乘真实帧间隔
    val topEdgeThreshold = with(density) { 80.dp.toPx() }
    val bottomEdgeThreshold = with(density) { 100.dp.toPx() }
    LaunchedEffect(isDraggingActive) {
        if (!isDraggingActive) return@LaunchedEffect
        var edgeDurationMs = 0f
        var lastFrameNanos = 0L
        while (isActive && draggedIndex >= 0) {
            val frameNanos = withFrameNanos { it }
            val deltaMs = if (lastFrameNanos == 0L) {
                16f
            } else {
                ((frameNanos - lastFrameNanos) / 1_000_000f).coerceIn(1f, 64f)
            }
            lastFrameNanos = frameNanos

            val layoutInfo = listState.layoutInfo
            val draggingItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazyIndexOf(draggedIndex) }
            if (draggingItem == null) {
                edgeDurationMs = 0f
                continue
            }

            val currentTop = draggingItem.offset + dragOffset
            val currentBottom = currentTop + draggingItem.size
            val viewportEnd = layoutInfo.viewportEndOffset

            val isNearTop = currentTop < topEdgeThreshold && draggedIndex > upcomingStart
            val isNearBottom = currentBottom > (viewportEnd - bottomEdgeThreshold) && draggedIndex < queue.lastIndex
            if (!isNearTop && !isNearBottom) {
                edgeDurationMs = 0f
                continue
            }

            edgeDurationMs += deltaMs
            // 加速系数：边缘停留 1.5s 内从 1.0 渐进提升到 3.5 倍
            val timeMultiplier = 1f + (edgeDurationMs / 600f).coerceAtMost(2.5f)
            val depthRatio = if (isNearTop) {
                ((topEdgeThreshold - currentTop) / topEdgeThreshold).coerceIn(0f, 1f)
            } else {
                ((currentBottom - (viewportEnd - bottomEdgeThreshold)) / bottomEdgeThreshold).coerceIn(0f, 1f)
            }
            val speedPxPerSec = (depthRatio * 1000f + 300f) * timeMultiplier
            val stepPx = speedPxPerSec * deltaMs / 1000f

            val scrolled = listState.scrollBy(if (isNearTop) -stepPx else stepPx)
            dragOffset = clampDragOffset(dragOffset + scrolled)
            advanceSwaps()
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("清空播放队列", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("确定要清空播放队列吗？", color = TextGray, fontSize = 14.sp) },
            confirmButton = {
                MelodiaTextButton(
                    onClick = {
                        onClearQueue()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeteaseRed)
                ) {
                    Text("是的", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                MelodiaTextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消", color = Color.White)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(10.dp)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundDark,
        shape = BottomSheetShape,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = MelodiaSpacing.xs)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(DragHandleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            QueueHeader(
                queueSize = queue.size,
                onClearClick = { showClearConfirmDialog = true },
                onDismiss = onDismiss
            )

            PlayModeInfoRow(
                playMode = playMode,
                playContext = playContext,
                onToggleShuffle = onToggleShuffle,
                onDisableRoaming = onDisableRoaming,
                onDisableIntelligence = onDisableIntelligence
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = MelodiaSpacing.md)
            )

            LazyColumn(
                state = listState,
                // 拖拽时滚动完全交给自动滚屏协程接管
                userScrollEnabled = !isDraggingActive,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                // 1. "已播放" (仅在非漫游模式下显示)
                if (!isRoaming && currentIndex > 0) {
                    item(key = "header_played") {
                        SectionLabel("已播放")
                    }
                    itemsIndexed(
                        items = queue.subList(0, currentIndex),
                        key = { idx, item -> "played_${item.songId}_$idx" }
                    ) { idx, item ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    onRemoveAtIndex(idx)
                                    true
                                } else false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = { SwipeDeleteBackground() },
                            enableDismissFromStartToEnd = false
                        ) {
                            DraggableSongRow(
                                data = SongRowData(id = item.songId, title = item.title, artist = item.artist, coverUrl = item.coverUrl),
                                isCurrent = false,
                                isPlaying = false,
                                isPlayed = true,
                                isDragging = false,
                                dragOffsetY = 0f,
                                onClick = { onPlayAtIndex(idx) },
                                onDragStart = {},
                                onDrag = { _ -> },
                                onDragEnd = {}
                            )
                        }
                    }
                }

                // 2. "正在播放"
                if (currentIndex in queue.indices) {
                    item(key = "header_current") {
                        SectionLabel("正在播放")
                    }
                    item(key = "current_${queue[currentIndex].songId}") {
                        if (isRoaming) {
                            // 漫游模式下禁用侧滑删除
                            DraggableSongRow(
                                data = SongRowData(id = queue[currentIndex].songId, title = queue[currentIndex].title, artist = queue[currentIndex].artist, coverUrl = queue[currentIndex].coverUrl),
                                isCurrent = true,
                                isPlaying = isPlaying,
                                isPlayed = false,
                                isDragging = false,
                                dragOffsetY = 0f,
                                onClick = { onPlayAtIndex(currentIndex) },
                                onDragStart = {},
                                onDrag = { _ -> },
                                onDragEnd = {}
                            )
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        onRemoveAtIndex(currentIndex)
                                        true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = { SwipeDeleteBackground() },
                                enableDismissFromStartToEnd = false
                            ) {
                                DraggableSongRow(
                                    data = SongRowData(id = queue[currentIndex].songId, title = queue[currentIndex].title, artist = queue[currentIndex].artist, coverUrl = queue[currentIndex].coverUrl),
                                    isCurrent = true,
                                    isPlaying = isPlaying,
                                    isPlayed = false,
                                    isDragging = false,
                                    dragOffsetY = 0f,
                                    onClick = { onPlayAtIndex(currentIndex) },
                                    onDragStart = {},
                                    onDrag = { _ -> },
                                    onDragEnd = {}
                                )
                            }
                        }
                    }
                }

                // 3. "接下来播放" (仅在非漫游模式下显示)
                if (!isRoaming) {
                    if (upcomingStart < queue.size) {
                        item(key = "header_upcoming") {
                            SectionLabel(
                                if (playMode == PlayMode.SHUFFLE) "× 随机播放来源：${playContext ?: ""}"
                                else "接下来播放"
                            )
                        }
                        itemsIndexed(
                            items = queue.subList(upcomingStart, queue.size),
                            key = { idx, item -> upcomingKeys.getOrElse(idx) { "upcoming_${item.songId}_$idx" } }
                        ) { idx, item ->
                            val actualIndex = upcomingStart + idx
                            val isDragging = draggedIndex == actualIndex
                            val isSettling = settlingIndex == actualIndex
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        onRemoveAtIndex(actualIndex)
                                        true
                                    } else false
                                }
                            )
                            Box(
                                // 抬起中的行自己负责位移，交给 animateItem 会和手动 translationY 打架
                                modifier = if (isDragging || isSettling) Modifier.zIndex(1f) else Modifier.animateItem()
                            ) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = { SwipeDeleteBackground() },
                                    enableDismissFromStartToEnd = false,
                                    // 拖拽期间关掉侧滑
                                    enableDismissFromEndToStart = !isDraggingActive
                                ) {
                                    DraggableSongRow(
                                        data = SongRowData(id = item.songId, title = item.title, artist = item.artist, coverUrl = item.coverUrl),
                                        isCurrent = false,
                                        isPlaying = false,
                                        isPlayed = false,
                                        isDragging = isDragging,
                                        dragOffsetY = when {
                                            isDragging -> dragOffset
                                            isSettling -> settleOffset.value
                                            else -> 0f
                                        },
                                        onClick = { onPlayAtIndex(actualIndex) },
                                        onDragStart = {
                                            // 同一时刻只允许一行处于拖拽态。多指分别长按两行时后者会覆盖 draggedIndex，
                                            // 而前一条手势协程仍然存活并继续推送位移，两条手势会同时驱动换位把队列搅乱
                                            if (draggedIndex < 0) {
                                                settlingIndex = -1
                                                draggedIndex = actualIndex
                                                dragOffset = 0f
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        },
                                        onDrag = { delta ->
                                            // 换位后本行 actualIndex 与 draggedIndex 同步变化，这个判等对拖拽行恒真，
                                            // 只会滤掉未被受理的那条并发手势
                                            if (draggedIndex == actualIndex) {
                                                dragOffset = clampDragOffset(dragOffset + delta)
                                                advanceSwaps()
                                            }
                                        },
                                        onDragEnd = {
                                            if (draggedIndex == actualIndex) {
                                                val releasedOffset = dragOffset
                                                draggedIndex = -1
                                                dragOffset = 0f
                                                if (releasedOffset != 0f) {
                                                    settlingIndex = actualIndex
                                                    scope.launch {
                                                        settleOffset.snapTo(releasedOffset)
                                                        settleOffset.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                        )
                                                        settlingIndex = -1
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            BottomActionRow(
                playMode = playMode,
                playContext = playContext,
                onToggleShuffle = onToggleShuffle,
                onDisableRoaming = onDisableRoaming,
                onDisableIntelligence = onDisableIntelligence
            )
        }
    }
}

@Composable
private fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeteaseRed)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun QueueHeader(
    queueSize: Int,
    onClearClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "队列",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (queueSize > 1) {
            MelodiaIconButton(
                onClick = onClearClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "清空队列",
                    tint = TextGray,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            "共 $queueSize 首",
            color = TextGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = MelodiaSpacing.sm)
        )
        MelodiaIconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PlayModeInfoRow(
    playMode: PlayMode,
    playContext: String?,
    onToggleShuffle: () -> Unit,
    onDisableRoaming: () -> Unit,
    onDisableIntelligence: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isRoaming = playContext == "similar_roaming"
        val isIntelligence = playContext == "intelligence"
        val (icon, label) = when {
            isRoaming -> Icons.Rounded.AllInclusive to "相似歌曲漫游"
            isIntelligence -> Icons.Rounded.AutoAwesome to "心动模式"
            playMode == PlayMode.LIST_LOOP -> Icons.Default.Repeat to "列表循环"
            playMode == PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne to "单曲循环"
            else -> Icons.Default.Shuffle to "随机播放"
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .clickable {
                    when {
                        isRoaming -> onDisableRoaming()
                        isIntelligence -> onDisableIntelligence()
                        else -> onToggleShuffle()
                    }
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = NeteaseRed, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 12.sp)
        }
        if (!playContext.isNullOrBlank() && !isRoaming && !isIntelligence) {
            Spacer(Modifier.width(12.dp))
            Text(
                "来自：$playContext",
                color = TextGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextGray,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun BottomActionRow(
    playMode: PlayMode,
    playContext: String?,
    onToggleShuffle: () -> Unit,
    onDisableRoaming: () -> Unit,
    onDisableIntelligence: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val isRoaming = playContext == "similar_roaming"
        val isIntelligence = playContext == "intelligence"
        MelodiaButton(
            onClick = {
                when {
                    isRoaming -> onDisableRoaming()
                    isIntelligence -> onDisableIntelligence()
                    else -> onToggleShuffle()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRoaming || isIntelligence || playMode == PlayMode.SHUFFLE) NeteaseRed else SurfaceDark
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Icon(
                imageVector = when {
                    isRoaming -> Icons.Rounded.AllInclusive
                    isIntelligence -> Icons.Rounded.AutoAwesome
                    else -> Icons.Default.Shuffle
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    isRoaming -> "关闭漫游"
                    isIntelligence -> "关闭心动模式"
                    else -> "随机播放"
                },
                color = Color.White,
                fontSize = 13.sp
            )
        }
        MelodiaButton(
            onClick = { },
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("定时器", color = Color.White, fontSize = 13.sp)
        }
    }
}
