package com.lin0721.linmusic.core.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.model.isLikedSongsPlaylist
import com.lin0721.linmusic.core.ui.icons.FavoriteFilled
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Sort
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.launch

private val RowCoverSize = 48.dp
private val RowHeight = 64.dp
private val HeaderHeight = 52.dp
private val SearchBarHeight = 56.dp
private val DoneBarHeight = 72.dp
private val DividerHeight = 1.dp
private const val COLLAPSED_PLAYLIST_COUNT = 2

// 吸附判定：快速甩动阈值（px/s）、低于小弹窗高度该比例即关闭
private const val FLING_VELOCITY = 1500f
private const val DISMISS_FRACTION = 0.7f

private enum class CollectSortMode(val label: String) {
    Default("默认排序"),
    Name("按名称")
}

// ────────────────────────────────────────────────────────────────────────────
// "保存位置"加入歌单弹窗：默认小弹窗（已点赞的歌曲 + 前两个歌单），
// 上拉跟手增高到全屏（搜索、排序、取消/完成），松手按位置与速度吸附或关闭。
// 点 ⊕/✓ 立即生效，每次只提交单个歌单的变更。
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistCollectSheet(
    songId: Long,
    collectState: PlaylistCollectState,
    onDismiss: () -> Unit,
    onSaveCollection: (Long, List<PlaylistCollectItem>) -> Unit,
    onSaveNewCollection: (String, Long) -> Unit,
    sheetShape: Shape = BottomSheetShape,
    itemCornerRadius: Dp = RadiusCompact
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // 本次弹窗内的勾选结果，覆盖后台回填的初始状态
    val overrides = remember(songId) { mutableStateMapOf<Long, Boolean>() }
    var query by remember(songId) { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(CollectSortMode.Default) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val stateMatches = collectState.songId == songId
    val loading = collectState.isLoading || !stateMatches
    val allItems = if (stateMatches) collectState.collectItems else emptyList()
    val likedItem = allItems.firstOrNull { isLikedSongsPlaylist(it.playlistName, it.playlistId, null) }
    val playlists = allItems.filter { it.playlistId != likedItem?.playlistId }
    val visiblePlaylists = remember(playlists, query, sortMode) {
        val keyword = query.trim()
        val filtered = if (keyword.isNotEmpty()) {
            playlists.filter { it.playlistName.contains(keyword, ignoreCase = true) }
        } else {
            playlists
        }
        when (sortMode) {
            CollectSortMode.Default -> filtered
            CollectSortMode.Name -> {
                val collator = Collator.getInstance(Locale.CHINA)
                filtered.sortedWith { a, b -> collator.compare(a.playlistName, b.playlistName) }
            }
        }
    }

    fun isChecked(item: PlaylistCollectItem): Boolean = overrides[item.playlistId] ?: item.isContains

    // 即时增删后本地修正歌曲数，不等服务端回刷
    fun displayCount(item: PlaylistCollectItem): Int {
        val delta = (if (isChecked(item)) 1 else 0) - (if (item.isContains) 1 else 0)
        return (item.trackCount + delta).coerceAtLeast(0)
    }

    fun toggle(item: PlaylistCollectItem) {
        val target = !isChecked(item)
        overrides[item.playlistId] = target
        onSaveCollection(songId, listOf(item.copy(isInitiallyContains = !target, isContains = target)))
    }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 拖动全部交给内容区的高度控制，避免与面板自身拖动打架
        sheetGesturesEnabled = false,
        // 底部导航栏由内容自己留白，避免与面板默认内边距重复扣掉一截
        contentWindowInsets = { WindowInsets(0) },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = sheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        val density = LocalDensity.current
        val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val collapsedPx = with(density) {
                (HeaderHeight + RowHeight * (1 + COLLAPSED_PLAYLIST_COUNT) + DividerHeight).toPx()
            }
            // 全屏停在状态栏下方，露出状态栏
            val statusTopPx = WindowInsets.statusBars.getTop(density).toFloat() + with(density) { 8.dp.toPx() }
            val fullPx = (constraints.maxHeight - statusTopPx).coerceAtLeast(collapsedPx)
            var heightPx by remember { mutableFloatStateOf(collapsedPx) }
            val progress = if (fullPx > collapsedPx) {
                ((heightPx - collapsedPx) / (fullPx - collapsedPx)).coerceIn(0f, 1f)
            } else {
                0f
            }
            val showFull = progress > 0.5f

            fun dragBy(delta: Float) {
                heightPx = (heightPx - delta).coerceIn(0f, fullPx)
            }

            fun settle(velocity: Float) {
                val target = when {
                    velocity < -FLING_VELOCITY -> fullPx
                    velocity > FLING_VELOCITY -> if (heightPx > collapsedPx + 1f) collapsedPx else null
                    heightPx < collapsedPx * DISMISS_FRACTION -> null
                    heightPx > (collapsedPx + fullPx) / 2f -> fullPx
                    else -> collapsedPx
                }
                if (target == null) {
                    close()
                    return
                }
                scope.launch {
                    animate(heightPx, target, initialVelocity = -velocity) { value, _ -> heightPx = value }
                }
            }

            // 列表优先让面板增高；列表滚到顶后继续下拉再让面板变矮
            val nestedScroll = remember(collapsedPx, fullPx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < 0f && heightPx < fullPx) {
                            val before = heightPx
                            dragBy(available.y)
                            return Offset(0f, before - heightPx)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (available.y > 0f && source == NestedScrollSource.UserInput) {
                            val before = heightPx
                            dragBy(available.y)
                            return Offset(0f, before - heightPx)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        val atAnchor = heightPx == collapsedPx || heightPx == fullPx
                        if (atAnchor && !(heightPx == collapsedPx && available.y < 0f)) return Velocity.Zero
                        settle(available.y)
                        return available
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { heightPx.toDp() })
                    .clipToBounds()
                    .nestedScroll(nestedScroll)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> dragBy(delta) },
                        onDragStopped = { velocity -> settle(velocity) }
                    )
            ) {
                Crossfade(targetState = showFull, label = "collectHeader") { full ->
                    if (full) {
                        ExpandedHeader(onCancel = ::close, onDone = ::close)
                    } else {
                        CollapsedHeader(onCreate = { showCreateDialog = true })
                    }
                }

                if (likedItem != null) {
                    CollectTargetRow(
                        title = "已点赞的歌曲",
                        subtitle = "${displayCount(likedItem)} 首歌曲",
                        checked = isChecked(likedItem),
                        onToggle = { toggle(likedItem) },
                        cover = { LikedSongsMiniCover(size = RowCoverSize) }
                    )
                }

                // 搜索栏随展开进度同步撑开
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SearchBarHeight * progress)
                        .clipToBounds()
                        .alpha(progress)
                ) {
                    SearchAndSortBar(
                        query = query,
                        onQueryChange = { query = it },
                        sortMode = sortMode,
                        onSortChange = { sortMode = it },
                        onCreate = { showCreateDialog = true }
                    )
                }

                HorizontalDivider(
                    thickness = DividerHeight,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = MelodiaSpacing.lg)
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading && allItems.isEmpty() -> CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = MelodiaSpacing.md)
                        )
                        visiblePlaylists.isEmpty() -> Text(
                            text = if (query.isNotBlank()) "没有找到相关歌单" else "暂无其他可用歌单",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(MelodiaSpacing.lg)
                        )
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = with(density) { navBottomPx.toDp() } + DoneBarHeight * progress)
                        ) {
                            items(visiblePlaylists, key = { it.playlistId }) { item ->
                                CollectTargetRow(
                                    title = item.playlistName,
                                    subtitle = "${displayCount(item)} 首歌曲",
                                    checked = isChecked(item),
                                    onToggle = { toggle(item) },
                                    cover = {
                                        SubcomposeAsyncImage(
                                            model = "${item.coverUrl}?param=100y100",
                                            contentDescription = item.playlistName,
                                            contentScale = ContentScale.Crop,
                                            loading = { CoverPlaceholder() },
                                            error = { CoverPlaceholder() },
                                            modifier = Modifier
                                                .size(RowCoverSize)
                                                .clip(RoundedCornerShape(itemCornerRadius))
                                        )
                                    }
                                )
                            }
                        }
                    }
                
                    // 全屏时悬浮在列表之上、导航栏上方
                    if (progress > 0f) {
                        MelodiaButton(
                            onClick = ::close,
                            shape = RoundedCornerShape(PillRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = with(density) { navBottomPx.toDp() } + MelodiaSpacing.md)
                                .alpha(progress)
                        ) {
                            Text("完成", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

            }
        }

        if (showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name -> onSaveNewCollection(name, songId) }
            )
        }
    }
}

@Composable
private fun CollapsedHeader(onCreate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "保存位置",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        MelodiaTextButton(onClick = onCreate) {
            Text("新建歌单", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExpandedHeader(onCancel: () -> Unit, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(horizontal = MelodiaSpacing.sm)
    ) {
        MelodiaTextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) {
            Text("取消", color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = "保存位置",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
        MelodiaTextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd)) {
            Text("完成", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SearchAndSortBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sortMode: CollectSortMode,
    onSortChange: (CollectSortMode) -> Unit,
    onCreate: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchBarHeight)
            .padding(horizontal = MelodiaSpacing.lg, vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.xs)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(RadiusCompact))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = MelodiaIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            PlaceholderTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "查找歌单",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        Box {
            MelodiaIconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = MelodiaIcons.Sort,
                    contentDescription = "排序",
                    tint = if (sortMode == CollectSortMode.Default) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                CollectSortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = mode.label,
                                color = if (mode == sortMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onSortChange(mode)
                        }
                    )
                }
            }
        }
        MelodiaIconButton(onClick = onCreate) {
            Icon(
                imageVector = MelodiaIcons.Plus,
                contentDescription = "新建歌单",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CollectTargetRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    cover: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(onClick = onToggle)
            .padding(horizontal = MelodiaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cover()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
        CollectToggleIcon(collected = checked, size = 26.dp)
    }
}

private val LikedCoverGradient = listOf(Color(0xFF450AF5), Color(0xFF8E8EE5))

// "已点赞的歌曲"渐变小封面
@Composable
private fun LikedSongsMiniCover(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.linearGradient(LikedCoverGradient)),
        contentAlignment = Alignment.Center
    ) {
        Icon(MelodiaIcons.FavoriteFilled, null, tint = Color.White, modifier = Modifier.size(size * 0.45f))
    }
}
