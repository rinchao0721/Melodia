package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.data.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.components.trackTextInputFocus
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.local.RemoveLocalPlaylistDialog
import com.lin0721.linmusic.desktop.ui.local.RenameLocalPlaylistDialog
import com.lin0721.linmusic.desktop.ui.playlist.EditPlaylistDialog
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.DangerColor
import com.lin0721.linmusic.feature.library.ui.LibraryFilter
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.library.ui.LibrarySortOrder
import com.lin0721.linmusic.feature.library.ui.LibraryUiState
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private const val VIEW_SWITCH_MS = 150
private val SearchHeight = FilterChipDefaults.Height
private val ShadowHeight = 8.dp
// 网格单元内边距：越小封面越大
private val LargeCardInset = 6.dp
private val SmallTileInset = 2.dp
private const val LargeGridMinColumns = 2
private const val SmallGridMinColumns = 3
// 网格封面随栏宽连续变化，固定请求尺寸，避免拖动时每帧换缩略图地址而反复加载
private val GridCoverRequestSize = 320.dp

private val LibraryFilters = listOf(
    LibraryFilter.PLAYLIST to "歌单",
    LibraryFilter.ALBUM to "专辑",
    LibraryFilter.ARTIST to "艺人"
)

// 默认态：标题栏 + 筛选 + 工具行 + 列表/网格
@Composable
fun LibrarySidebar(
    viewModel: LibraryViewModel,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onItemClick: (LibraryItem) -> Unit,
    onModeChange: (LibraryMode) -> Unit,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onFilterToggle: (LibraryFilter) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    extras: LibraryExtrasEntry,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {}
) {
    val paneHover = remember { MutableInteractionSource() }
    val paneHovered by paneHover.collectIsHoveredAsState()
    Column(modifier.fillMaxSize().hoverable(paneHover).padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LibraryTitleToggle(
                baseIcon = null,
                hoverIcon = Icons.Rounded.KeyboardDoubleArrowLeft,
                hint = "收起音乐库",
                showTitle = true,
                iconRevealed = paneHovered,
                onTitleClick = { viewModel.loadLibraryData() }
            ) { onModeChange(LibraryMode.RAIL) }
            Spacer(Modifier.weight(1f))
            LibraryCreateButton(viewModel, extras, isLoggedIn, pill = false)
            Spacer(Modifier.width(4.dp))
            LibraryIconButton(Icons.Rounded.OpenInFull, "展开音乐库") { onModeChange(LibraryMode.EXPANDED) }
        }
        Column(Modifier.weight(1f)) {
            LibraryBody(
                viewModel = viewModel,
                isLoggedIn = isLoggedIn,
                onLoginClick = onLoginClick,
                onItemClick = onItemClick,
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                onFilterToggle = onFilterToggle,
                onSortOrderChange = onSortOrderChange,
                extras = extras,
                wideSearch = false,
                edgePadding = 16.dp,
                cardMinWidth = 128.dp,
                smallTileMinWidth = 88.dp
            )
        }
        footer()
    }
}

// 展开态：标题、创建按钮、收回，其余与默认态共用
@Composable
fun LibraryExpanded(
    viewModel: LibraryViewModel,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onItemClick: (LibraryItem) -> Unit,
    onModeChange: (LibraryMode) -> Unit,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onFilterToggle: (LibraryFilter) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    extras: LibraryExtrasEntry,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            DesktopTooltip("刷新音乐库", side = TooltipSide.Bottom) {
                Text(
                    "音乐库",
                    color = DesktopColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { viewModel.loadLibraryData() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            LibraryCreateButton(viewModel, extras, isLoggedIn, pill = true)
            Spacer(Modifier.width(8.dp))
            LibraryIconButton(Icons.Rounded.CloseFullscreen, "收回音乐库") { onModeChange(LibraryMode.DEFAULT) }
        }
        LibraryBody(
            viewModel = viewModel,
            isLoggedIn = isLoggedIn,
            onLoginClick = onLoginClick,
            onItemClick = onItemClick,
            viewMode = viewMode,
            onViewModeChange = onViewModeChange,
            onFilterToggle = onFilterToggle,
            onSortOrderChange = onSortOrderChange,
            extras = extras,
            wideSearch = true,
            edgePadding = 24.dp,
            cardMinWidth = 190.dp,
            smallTileMinWidth = 120.dp
        )
    }
}

@Composable
private fun LibraryBody(
    viewModel: LibraryViewModel,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onItemClick: (LibraryItem) -> Unit,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onFilterToggle: (LibraryFilter) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    extras: LibraryExtrasEntry,
    wideSearch: Boolean,
    edgePadding: Dp,
    cardMinWidth: Dp,
    smallTileMinWidth: Dp
) {
    // 未登录时仍可使用本地歌单
    if (!isLoggedIn) {
        LoginPrompt(onLoginClick)
        LibraryItems(viewModel, extras, false, onItemClick, edgePadding, cardMinWidth, smallTileMinWidth, LibraryViewMode.LIST)
        return
    }
    if (wideSearch) {
        // 宽版：筛选标签、搜索框、排序与视图切换同一行
        Row(
            Modifier.fillMaxWidth().padding(start = edgePadding, end = edgePadding, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LibraryFilterChips(viewModel, extras, onFilterToggle)
            Spacer(Modifier.weight(1f))
            val query by viewModel.searchQuery.collectAsState()
            Box(
                Modifier.width(260.dp).height(SearchHeight).clip(RoundedCornerShape(16.dp)).background(DesktopColors.Surface)
            ) {
                LibrarySearchInput(
                    query = query,
                    startPadding = 10.dp,
                    showClear = query.isNotEmpty(),
                    onQueryChange = viewModel::updateSearchQuery,
                    onClear = { viewModel.updateSearchQuery("") }
                )
            }
            Spacer(Modifier.width(12.dp))
            LibrarySortViewMenu(viewModel, viewMode, onViewModeChange, onSortOrderChange, showSortLabel = true)
        }
    } else {
        Box(Modifier.padding(start = edgePadding, end = edgePadding, top = 12.dp)) { LibraryFilterChips(viewModel, extras, onFilterToggle) }
        LibraryCompactToolbar(viewModel, edgePadding, viewMode, onViewModeChange, onSortOrderChange)
    }
    LibraryItems(viewModel, extras, true, onItemClick, edgePadding, cardMinWidth, smallTileMinWidth, viewMode)
}

// 窄版工具行：搜索图标点击后展开成输入框（排序标签让位），清空或失焦且无内容时收回
@Composable
private fun LibraryCompactToolbar(
    viewModel: LibraryViewModel,
    edgePadding: Dp,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit
) {
    val query by viewModel.searchQuery.collectAsState()
    var searching by remember { mutableStateOf(query.isNotBlank()) }
    var focusedOnce by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) runCatching { focusRequester.requestFocus() } else focusedOnce = false
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(start = edgePadding, end = edgePadding, top = 8.dp, bottom = 4.dp)
    ) {
        val fieldWidth by animateDpAsState(
            if (searching) maxWidth - SearchHeight - 8.dp else SearchHeight,
            uiTween(SEARCH_ANIMATION_MS, easing = FastOutSlowInEasing),
            label = "librarySearchWidth"
        )
        val background by animateColorAsState(
            if (searching) DesktopColors.Surface else DesktopColors.Surface.copy(alpha = 0f),
            uiTween(SEARCH_ANIMATION_MS),
            label = "librarySearchBackground"
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(fieldWidth).height(SearchHeight).clip(RoundedCornerShape(16.dp)).background(background)
                    .then(
                        if (searching) Modifier else Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { searching = true }
                    )
            ) {
                if (searching) {
                    LibrarySearchInput(
                        query = query,
                        startPadding = 7.dp,
                        showClear = true,
                        onQueryChange = viewModel::updateSearchQuery,
                        onClear = {
                            viewModel.updateSearchQuery("")
                            searching = false
                        },
                        focusRequester = focusRequester,
                        onFocusChange = { focused ->
                            if (focused) focusedOnce = true else if (focusedOnce && query.isEmpty()) searching = false
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Search, "搜索", tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            LibrarySortViewMenu(viewModel, viewMode, onViewModeChange, onSortOrderChange, showSortLabel = !searching)
        }
    }
}

// 输入框内容：图标、输入、清除；外层的宽度与底色由调用方决定。Esc 等同点击清除
@Composable
internal fun LibrarySearchInput(
    query: String,
    startPadding: Dp,
    showClear: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    placeholder: String = "在音乐库中搜索"
) {
    Row(modifier.fillMaxSize().padding(start = startPadding), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, softWrap = false)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = DesktopColors.TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(DesktopColors.TextPrimary),
                modifier = Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .trackTextInputFocus()
                    .onFocusChanged { onFocusChange(it.isFocused) }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            onClear()
                            true
                        } else {
                            false
                        }
                    }
            )
        }
        if (showClear) {
            Icon(
                Icons.Rounded.Close,
                "清除搜索",
                tint = DesktopColors.TextGray,
                modifier = Modifier.padding(horizontal = 8.dp).size(16.dp).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClear)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LibraryFilterChips(viewModel: LibraryViewModel, extras: LibraryExtrasEntry, onToggle: (LibraryFilter) -> Unit) {
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val scrollState = rememberScrollState()
    val wheelStepPx = with(LocalDensity.current) { CHIP_WHEEL_STEP.toPx() }
    // 窄栏放不下全部胶囊时可横向滚动，竖向滚轮也换算成横向
    Row(
        Modifier
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val delta = change.scrollDelta.let { if (it.x != 0f) it.x else it.y }
                if (delta != 0f && scrollState.maxValue > 0 && scrollState.dispatchRawDelta(delta * wheelStepPx) != 0f) {
                    change.consume()
                }
            }
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibraryFilters.forEach { (filter, label) ->
            LibraryFilterChip(label, selected = extras.selected == null && selectedFilter == filter) { onToggle(filter) }
        }
        LibraryFilterChip(LOCAL_FILTER_LABEL, selected = extras.selected == LibraryExtraFilter.LOCAL) {
            extras.onToggle(LibraryExtraFilter.LOCAL)
        }
        LibraryFilterChip(CLOUD_FILTER_LABEL, selected = extras.selected == LibraryExtraFilter.CLOUD) {
            extras.onToggle(LibraryExtraFilter.CLOUD)
        }
    }
}

private val CHIP_WHEEL_STEP = 48.dp

@Composable
private fun LibraryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp, maxLines = 1, softWrap = false) },
        shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = DesktopColors.Surface,
            labelColor = DesktopColors.TextPrimary,
            selectedContainerColor = DesktopColors.TextPrimary,
            selectedLabelColor = DesktopColors.Pane
        ),
        border = null
    )
}

@Composable
private fun LibraryItems(
    viewModel: LibraryViewModel,
    extras: LibraryExtrasEntry,
    isLoggedIn: Boolean,
    onItemClick: (LibraryItem) -> Unit,
    edgePadding: Dp,
    cardMinWidth: Dp,
    smallTileMinWidth: Dp,
    viewMode: LibraryViewMode
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val displayItems = rememberLibraryDisplayItems(viewModel, extras, isLoggedIn)
    val handlers = rememberLibraryItemHandlers(viewModel, extras)
    val openItem: (LibraryItem) -> Unit = { item -> extras.open(item, onItemClick) }

    Box(Modifier.fillMaxSize()) {
        val state = uiState
        when {
            displayItems == null && state is LibraryUiState.Error -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = DesktopColors.TextGray, fontSize = 13.sp)
                    TextButton(onClick = { viewModel.loadLibraryData() }) {
                        Text("重试", color = DesktopColors.TextPrimary)
                    }
                }
            }
            displayItems == null -> CenteredBox { CircularProgressIndicator(color = DesktopColors.Accent) }
            extras.selected == LibraryExtraFilter.CLOUD && !isLoggedIn -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("登录后即可查看云盘", color = DesktopColors.TextGray, fontSize = 13.sp)
                    TextButton(onClick = LocalDesktopNavigator.current.openLogin) {
                        Text("登录", color = DesktopColors.TextPrimary)
                    }
                }
            }
            displayItems.isEmpty() && query.isNotBlank() -> CenteredBox {
                Text("未找到与“$query”相关的内容", color = DesktopColors.TextGray, fontSize = 13.sp)
            }
            else -> {
                val coverTransition = LocalLibraryCoverTransition.current
                val motion = LocalUiMotion.current
                AnimatedContent(
                    targetState = viewMode,
                    transitionSpec = {
                        motion.transform(
                            fadeIn(tween(COVER_TRANSITION_MS)) togetherWith fadeOut(tween(COVER_TRANSITION_MS)) using null
                        )
                    },
                    label = "libraryView"
                ) { mode ->
                    CompositionLocalProvider(LocalLibraryCoverTransition provides coverTransition?.nested(this)) {
                        when (mode) {
                            LibraryViewMode.COMPACT_LIST -> LibraryList(displayItems, openItem, viewModel, handlers, compact = true)
                            LibraryViewMode.LIST -> LibraryList(displayItems, openItem, viewModel, handlers, compact = false)
                            LibraryViewMode.SMALL_GRID -> LibraryGrid(displayItems, openItem, viewModel, handlers, edgePadding, smallTileMinWidth, small = true)
                            LibraryViewMode.LARGE_GRID -> LibraryGrid(displayItems, openItem, viewModel, handlers, edgePadding, cardMinWidth, small = false)
                        }
                    }
                }
            }
        }
    }
}

// 条目右键菜单需要的回调；对话框由这里统一承载，默认态与收起态共用
@Immutable
internal class LibraryItemHandlers(
    val extras: LibraryExtrasEntry,
    val onEdit: (LibraryItem) -> Unit,
    val onDelete: (LibraryItem) -> Unit,
    val onRenameLocal: (LibraryItem) -> Unit,
    val onRemoveLocal: (LibraryItem) -> Unit
)

@Composable
internal fun rememberLibraryItemHandlers(viewModel: LibraryViewModel, extras: LibraryExtrasEntry): LibraryItemHandlers {
    var editingItem by remember { mutableStateOf<LibraryItem?>(null) }
    var deletingItem by remember { mutableStateOf<LibraryItem?>(null) }
    var renamingLocal by remember { mutableStateOf<LibraryItem?>(null) }
    var removingLocal by remember { mutableStateOf<LibraryItem?>(null) }
    var isSavingEdit by remember { mutableStateOf(false) }

    editingItem?.let { item ->
        val id = item.id.toLongOrNull()
        if (id != null) {
            EditPlaylistDialog(
                initialName = item.title,
                initialDescription = "",
                coverUrl = item.coverUrl,
                isSaving = isSavingEdit,
                onDismiss = { editingItem = null },
                onConfirm = { name, desc, coverBytes ->
                    isSavingEdit = true
                    viewModel.updatePlaylistInfo(id, item.title, name, null, desc, coverBytes) { success ->
                        isSavingEdit = false
                        if (success) editingItem = null
                    }
                }
            )
        }
    }

    deletingItem?.let { item ->
        val id = item.id.toLongOrNull()
        if (id != null) {
            AlertDialog(
                onDismissRequest = { deletingItem = null },
                title = { Text("删除歌单", color = DesktopColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = { Text("确定要删除歌单「${item.title}」吗？", color = DesktopColors.TextGray, fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deletePlaylist(id)
                        deletingItem = null
                    }) {
                        Text("删除", color = DangerColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingItem = null }) {
                        Text("取消", color = DesktopColors.TextGray)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                containerColor = DesktopColors.PopupSurface
            )
        }
    }

    renamingLocal?.let { item ->
        val id = item.localPlaylistId()
        if (id != null) {
            RenameLocalPlaylistDialog(item.title, onDismiss = { renamingLocal = null }) { name ->
                extras.local.repository.rename(id, name)
                renamingLocal = null
            }
        }
    }

    removingLocal?.let { item ->
        val id = item.localPlaylistId()
        if (id != null) {
            RemoveLocalPlaylistDialog(item.title, onDismiss = { removingLocal = null }) {
                extras.local.repository.removePlaylist(id)
                removingLocal = null
            }
        }
    }

    return remember(viewModel, extras) {
        LibraryItemHandlers(
            extras = extras,
            onEdit = { editingItem = it },
            onDelete = { item ->
                if (item.isOwnedByMe && item.type == LibraryItemType.PLAYLIST) {
                    deletingItem = item
                } else {
                    item.id.toLongOrNull()?.let { id ->
                        when (item.type) {
                            LibraryItemType.PLAYLIST -> viewModel.unsubscribePlaylist(id)
                            LibraryItemType.ALBUM -> viewModel.unsubscribeAlbum(id)
                            LibraryItemType.ARTIST -> viewModel.unsubscribeArtist(id)
                        }
                    }
                }
            },
            onRenameLocal = { renamingLocal = it },
            onRemoveLocal = { removingLocal = it }
        )
    }
}

// 与移动端一致的吸顶边缘弥散阴影，仅在列表已向下滚动时显示
@Composable
internal fun BoxScope.ListTopShadow(visible: Boolean) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, uiTween(VIEW_SWITCH_MS), label = "listTopShadow")
    // 浅色底上阴影需要淡很多
    val strength = if (DesktopColors.IsDark) 1f else 0.18f
    val shadow = DesktopColors.ShadowSpot
    Box(
        Modifier.fillMaxWidth().height(ShadowHeight).align(Alignment.TopCenter).graphicsLayer { this.alpha = alpha }
            .background(
                Brush.verticalGradient(
                    listOf(shadow.copy(alpha = 0.85f * strength), shadow.copy(alpha = 0.35f * strength), shadow.copy(alpha = 0f))
                )
            )
    )
}

internal val LazyListState.isScrolled: Boolean get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

internal val LazyGridState.isScrolled: Boolean get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

@Composable
private fun LibraryList(
    items: List<LibraryItem>,
    onItemClick: (LibraryItem) -> Unit,
    viewModel: LibraryViewModel,
    handlers: LibraryItemHandlers,
    compact: Boolean
) {
    val listState = rememberAnchoredListState(items)
    val scrolled by remember { derivedStateOf { listState.isScrolled } }
    Box(Modifier.fillMaxSize()) {
        HoverScrollbarBox(listState) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(items, key = { it.id }) { item ->
                    LibraryContextMenuBox(item, viewModel, handlers) {
                        if (compact) LibraryCompactRow(item) { onItemClick(item) } else LibraryRow(item) { onItemClick(item) }
                    }
                }
            }
        }
        ListTopShadow(scrolled)
    }
}

@Composable
private fun LibraryGrid(
    items: List<LibraryItem>,
    onItemClick: (LibraryItem) -> Unit,
    viewModel: LibraryViewModel,
    handlers: LibraryItemHandlers,
    edgePadding: Dp,
    cardMinWidth: Dp,
    small: Boolean
) {
    val gridState = rememberAnchoredGridState(items)
    val scrolled by remember { derivedStateOf { gridState.isScrolled } }
    Box(Modifier.fillMaxSize()) {
        HoverScrollbarBox(gridState) {
            LookaheadScope {
                CompositionLocalProvider(LocalLibraryGridLookahead provides this) {
                    val reflow = isGridReflowEnabled()
                    LazyVerticalGrid(
                        columns = AdaptiveAtLeast(cardMinWidth, if (small) SmallGridMinColumns else LargeGridMinColumns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = edgePadding - if (small) SmallTileInset else LargeCardInset, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            // 位移挂在格子上、尺寸挂在内层：animateBounds 不跟随父级位移，滚动不会被当成重排
                            Box(if (reflow) Modifier.animateItem(placementSpec = GridReflowPlacementSpec) else Modifier) {
                                Box(Modifier.libraryGridReflow()) {
                                    LibraryContextMenuBox(item, viewModel, handlers) {
                                        if (small) LibraryCoverTile(item) { onItemClick(item) } else LibraryCard(item) { onItemClick(item) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ListTopShadow(scrolled)
    }
}

private fun copyLibraryItemLink(item: LibraryItem): Boolean {
    val id = item.id.toLongOrNull() ?: return false
    val path = when (item.type) {
        LibraryItemType.PLAYLIST -> "playlist"
        LibraryItemType.ALBUM -> "album"
        LibraryItemType.ARTIST -> "artist"
    }
    return runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            StringSelection("https://music.163.com/$path?id=$id"), null
        )
    }.isSuccess
}

@Composable
private fun LibraryItemMenu(
    expanded: Boolean,
    offset: DpOffset,
    item: LibraryItem,
    viewModel: LibraryViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    DesktopMenu(expanded = expanded, onDismiss = onDismiss, offset = offset) {
        MenuItem("播放", icon = Icons.Rounded.PlayArrow, onClick = {
            onDismiss()
            viewModel.playLibraryItem(item)
        })
        MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
            onDismiss()
            viewModel.addLibraryItemToPlayNext(item)
        })
        if (item.type != LibraryItemType.ARTIST) {
            MenuItem("下载", icon = Icons.Rounded.Download, onClick = {
                onDismiss()
                viewModel.downloadLibraryItem(item, navigator.downloadLevel)
            })
        }
        if (item.isOwnedByMe && !item.isLikedSongs && item.type == LibraryItemType.PLAYLIST) {
            MenuItem("编辑歌单信息", icon = Icons.Rounded.Edit, onClick = {
                onDismiss()
                onEdit()
            })
        }
        MenuItem(
            if (item.isPinned) "取消置顶" else "置顶歌单",
            icon = Icons.Rounded.PushPin,
            onClick = {
                onDismiss()
                viewModel.togglePin(item.id)
            }
        )
        MenuItem(
            when (item.type) {
                LibraryItemType.PLAYLIST -> "复制歌单链接"
                LibraryItemType.ALBUM -> "复制专辑链接"
                LibraryItemType.ARTIST -> "复制歌手链接"
            },
            icon = Icons.Rounded.Link,
            onClick = {
                onDismiss()
                val ok = copyLibraryItemLink(item)
                navigator.showMessage(if (ok) "已复制链接" else "复制失败")
            }
        )
        if (!item.isLikedSongs) {
            MenuDivider()
            val isOwned = item.isOwnedByMe && item.type == LibraryItemType.PLAYLIST
            MenuItem(
                if (isOwned) "删除歌单" else "从音乐库移除",
                icon = Icons.Rounded.Delete,
                danger = true,
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LibraryContextMenuBox(
    item: LibraryItem,
    viewModel: LibraryViewModel,
    handlers: LibraryItemHandlers,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var contextMenuOffset by remember { mutableStateOf<DpOffset?>(null) }
    var heightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier
            .onSizeChanged { heightPx = it.height }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    val pos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    contextMenuOffset = with(density) {
                        DpOffset(pos.x.toDp(), (pos.y - heightPx).toDp())
                    }
                }
            }
    ) {
        content()
        if (contextMenuOffset != null) {
            if (item.isCloud) {
                val cloudState by handlers.extras.cloud.library.state.collectAsState()
                CloudLibraryItemMenu(
                    expanded = true,
                    offset = contextMenuOffset ?: DpOffset.Zero,
                    cloud = handlers.extras.cloud,
                    state = cloudState,
                    onDismiss = { contextMenuOffset = null }
                )
            } else if (item.isLocal) {
                LocalLibraryItemMenu(
                    expanded = true,
                    offset = contextMenuOffset ?: DpOffset.Zero,
                    item = item,
                    extras = handlers.extras,
                    onRename = { handlers.onRenameLocal(item) },
                    onRemove = { handlers.onRemoveLocal(item) },
                    onDismiss = { contextMenuOffset = null }
                )
            } else {
                LibraryItemMenu(
                    expanded = true,
                    offset = contextMenuOffset ?: DpOffset.Zero,
                    item = item,
                    viewModel = viewModel,
                    onEdit = { handlers.onEdit(item) },
                    onDelete = { handlers.onDelete(item) },
                    onDismiss = { contextMenuOffset = null }
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(item.coverUrl, 48.dp, modifier = Modifier.libraryCoverElement(item.id), shape = coverShape(item))
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                item.title,
                color = DesktopColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            LibrarySubtitle(item)
        }
    }
}

// 紧凑列表：单行标题 + 类型，无封面
@Composable
private fun LibraryCompactRow(item: LibraryItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(36.dp).clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.isPinned) {
            Icon(Icons.Rounded.PushPin, "已置顶", tint = DesktopColors.Accent, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            item.title,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (item.isLocal) {
            Spacer(Modifier.width(LocalBadgeSpacing))
            LocalPlaylistBadge()
        } else {
            Text(" · ${typeLabel(item.type)}", color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, softWrap = false)
        }
    }
}

private fun typeLabel(type: LibraryItemType): String = when (type) {
    LibraryItemType.PLAYLIST -> "歌单"
    LibraryItemType.ALBUM -> "专辑"
    LibraryItemType.ARTIST -> "艺人"
}

// 小网格：只有封面，悬停显示标题与副信息
@Composable
private fun LibraryCoverTile(item: LibraryItem, onClick: () -> Unit) {
    DesktopTooltip(tooltipContent = { LibraryItemTooltipCard(item) }, side = TooltipSide.Right) {
        val shape = coverShape(item)
        Box(Modifier.padding(SmallTileInset).clip(shape).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Cover(
                    item.coverUrl, maxWidth, modifier = Modifier.libraryCoverElement(item.id), shape = shape,
                    requestSize = GridCoverRequestSize
                )
            }
        }
    }
}

@Composable
private fun LibraryCard(item: LibraryItem, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(LargeCardInset)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Cover(
                item.coverUrl, maxWidth, modifier = Modifier.libraryCoverElement(item.id), shape = coverShape(item),
                requestSize = GridCoverRequestSize
            )
        }
        Text(
            item.title,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        LibrarySubtitle(item)
    }
}

// 侧边栏收起态与小网格悬停展示的富文本详情卡片，高与左侧封面 48.dp 保持一致
@Composable
internal fun LibraryItemTooltipCard(item: LibraryItem) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DesktopColors.PopupSurface)
            .height(48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = item.title,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LibrarySubtitle(item, lineHeight = 16.sp)
    }
}

// 置顶项在副标题前显示置顶标记
@Composable
internal fun LibrarySubtitle(item: LibraryItem, lineHeight: TextUnit = TextUnit.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.isPinned) {
            Icon(Icons.Rounded.PushPin, "已置顶", tint = DesktopColors.Accent, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        if (item.isLocal) {
            LocalPlaylistBadge()
            Spacer(Modifier.width(LocalBadgeSpacing))
        }
        Text(
            item.subtitle,
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            lineHeight = lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun coverShape(item: LibraryItem): Shape =
    if (item.type == LibraryItemType.ARTIST) CircleShape else RoundedCornerShape(4.dp)

// 形态切换入口：有 baseIcon 时悬停换成收起/展开图标，没有则只在 iconRevealed 时出现
@Composable
internal fun LibraryTitleToggle(
    baseIcon: ImageVector?,
    hoverIcon: ImageVector,
    hint: String,
    showTitle: Boolean,
    iconRevealed: Boolean = true,
    onTitleClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val tint = if (hovered) DesktopColors.TextPrimary else DesktopColors.TextGray

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标默认隐藏，面板悬停时出现并把标题推开；图标自身悬停时换成收起/展开图标
        HoverReveal(revealed = iconRevealed, reserveSpace = false) {
            DesktopTooltip(hint, side = TooltipSide.Right) {
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).hoverable(source).pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.padding(end = if (showTitle) 4.dp else 0.dp).size(24.dp), contentAlignment = Alignment.Center) {
                        if (baseIcon != null) {
                            HoverReveal(revealed = !hovered) { Icon(baseIcon, null, tint = tint) }
                            HoverReveal(revealed = hovered) { Icon(hoverIcon, null, tint = tint) }
                        } else {
                            Icon(hoverIcon, null, tint = tint)
                        }
                    }
                }
            }
        }
        if (showTitle) {
            val titleSource = remember { MutableInteractionSource() }
            val titleHovered by titleSource.collectIsHoveredAsState()
            val titleTint = if (titleHovered) DesktopColors.TextPrimary else DesktopColors.TextGray
            DesktopTooltip("刷新音乐库", side = TooltipSide.Bottom) {
                Text(
                    text = "音乐库",
                    color = titleTint,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .hoverable(titleSource)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onTitleClick?.invoke() ?: onClick() }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
internal fun LibraryIconButton(icon: ImageVector, description: String, filled: Boolean = false, onClick: () -> Unit) {
    DesktopTooltip(description, side = TooltipSide.Right) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (filled) DesktopColors.Surface else Color.Transparent,
                contentColor = DesktopColors.TextGray
            )
        ) {
            Icon(icon, description, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LoginPrompt(onLoginClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(8.dp))
            .background(DesktopColors.CardSurface).padding(16.dp)
    ) {
        Text("登录后查看你的歌单", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(top = 4.dp))
        Text("收藏的歌单、专辑和艺人会显示在这里", color = DesktopColors.TextGray, fontSize = 13.sp)
        Spacer(Modifier.padding(top = 12.dp))
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(DesktopColors.TextPrimary)
                .clickable(onClick = onLoginClick).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("登录", color = DesktopColors.Pane, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        content()
    }
}
