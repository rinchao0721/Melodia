package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.plugin.LocalPluginCommands
import com.lin0721.linmusic.core.plugin.PluginCommandItem
import com.lin0721.linmusic.core.plugin.PluginMenuLocation
import com.lin0721.linmusic.core.plugin.rememberPluginCommands
import com.lin0721.linmusic.core.ui.badge.DraggableUnreadBadge
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.CloseWindow
import com.lin0721.linmusic.core.ui.icons.Minimize
import com.lin0721.linmusic.core.ui.icons.Check
import com.lin0721.linmusic.core.ui.icons.Close
import com.lin0721.linmusic.core.ui.icons.Download
import com.lin0721.linmusic.core.ui.icons.Drag
import com.lin0721.linmusic.core.ui.icons.Remove
import com.lin0721.linmusic.core.ui.icons.ChevronLeft
import com.lin0721.linmusic.core.ui.icons.ChevronRight
import com.lin0721.linmusic.core.ui.icons.Maximize
import com.lin0721.linmusic.core.ui.icons.RestoreWindow
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.data.TitleBarButton
import com.lin0721.linmusic.desktop.data.TitleBarLayout
import com.lin0721.linmusic.desktop.data.TitleBarSlot
import com.lin0721.linmusic.desktop.data.download.DownloadTask
import com.lin0721.linmusic.desktop.data.download.DownloadTaskStatus
import com.lin0721.linmusic.desktop.ui.cloud.CloudMusicActions
import com.lin0721.linmusic.desktop.ui.cloud.CloudUploadsButton
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DefaultMenuWidth
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.MenuScope
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.components.trackTextInputFocus
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.download.DownloadRoundTracker
import com.lin0721.linmusic.desktop.ui.library.MenuHeader
import com.lin0721.linmusic.desktop.ui.library.MenuOption
import com.lin0721.linmusic.desktop.ui.settings.icon
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.desktop.ui.track.DangerColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CloseHover = Color(0xFFE81123)

private const val CHECK_SHOW_MS = 1_500L
private const val FILL_ANIMATION_MS = 200

private val CENTER_BUTTON_SIZE = 48.dp
// 与底栏侧边图标按钮（PlayerBar）同尺寸、同悬停放大系数
private val DOWNLOAD_BUTTON_SIZE = 32.dp
private val DOWNLOAD_ICON_SIZE = 20.dp
private const val DOWNLOAD_HOVER_SCALE = 1.1f
private val TITLE_BAR_MENU_WIDTH = 196.dp

private val AccentActiveButtons = setOf(
    TitleBarButton.PERSONAL_FM,
    TitleBarButton.DAILY_RECOMMEND,
    TitleBarButton.LOCAL_MUSIC
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.TitleBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    titleBarLayout: TitleBarLayout,
    onTitleBarLayoutChange: (TitleBarLayout) -> Unit,
    onToggleTitleBarButton: (TitleBarButton) -> Unit,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onTitleBarBoundsChange: (Rect) -> Unit,
    activeTitleBarButtons: Set<TitleBarButton>,
    titleBarTooltips: Map<TitleBarButton, String>,
    onTitleBarButtonClick: (TitleBarButton) -> Unit,
    isMaximized: Boolean,
    userProfile: UserProfile?,
    searchQuery: String,
    searchPlaceholder: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onSearchBoundsChange: (Rect) -> Unit,
    onSearchMove: (Int) -> Boolean,
    onSearchSubmit: () -> Unit,
    searchFocusRequests: Flow<Unit>,
    isBrowseActive: Boolean,
    onBrowseClick: () -> Unit,
    cloudMusic: CloudMusicActions,
    privateUnreadCount: Int = 0,
    onDismissUnread: () -> Unit = {},
    downloadTasks: List<DownloadTask>,
    onLoginClick: () -> Unit,
    accounts: List<UserProfile> = emptyList(),
    onSwitchAccount: (uid: Long) -> Unit = {},
    onAddAccount: () -> Unit = {},
    activeKugouAccount: StoredAccount? = null,
    kugouAccounts: List<StoredAccount> = emptyList(),
    onKugouLoginClick: () -> Unit = {},
    onKugouSwitchAccount: (AccountId) -> Unit = {},
    onKugouAddAccount: () -> Unit = {},
    onKugouLogoutAccount: (AccountId) -> Unit = {},
    onProfileClick: () -> Unit,
    onRecentClick: () -> Unit,
    onCloudClick: () -> Unit,
    onSettingsClick: () -> Unit,
    hasUpdate: Boolean,
    onLogoutClick: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    val editState = rememberTitleBarEditState(titleBarLayout)
    val displayLayout = if (editMode) editState.workingLayout else titleBarLayout
    val currentOnLayoutChange by rememberUpdatedState(onTitleBarLayoutChange)
    val currentOnEditModeChange by rememberUpdatedState(onEditModeChange)
    var titleBarHeightPx by remember { mutableIntStateOf(0) }
    var searchBoxBounds by remember { mutableStateOf(Rect.Zero) }
    var contextMenuOffset by remember { mutableStateOf<DpOffset?>(null) }

    LaunchedEffect(editMode) {
        if (!editMode) editState.cancelDrag()
    }

    // 整轮下载进度状态放在标题栏层级，跨槽拖动下载按钮时不会丢失动画进度
    val downloadTracker = remember { DownloadRoundTracker() }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadShowCheck by remember { mutableStateOf(false) }
    LaunchedEffect(downloadTasks) {
        val summary = downloadTracker.update(downloadTasks)
        downloadProgress = summary.progress
        if (summary.progress != null) downloadShowCheck = false
        if (summary.finished && summary.allSucceeded) downloadShowCheck = true
    }
    LaunchedEffect(downloadShowCheck) {
        if (downloadShowCheck) {
            delay(CHECK_SHOW_MS)
            downloadShowCheck = false
        }
    }
    val downloadFill by animateFloatAsState(downloadProgress ?: 0f, uiTween(FILL_ANIMATION_MS), label = "downloadFill")
    val downloadNeedsAttention = downloadTasks.any {
        it.status == DownloadTaskStatus.PAUSED || it.status == DownloadTaskStatus.FAILED
    }

    val editGestureModifier = if (editMode) {
        Modifier.pointerInput(editState) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                val rootPos = editState.titleBarTopLeft + down.position
                val hitButton = editState.buttonAt(rootPos)
                if (hitButton == null) {
                    val up = waitForUpOrCancellation()
                    if (up != null && !up.isConsumed) {
                        currentOnEditModeChange(false)
                    }
                    return@awaitEachGesture
                }
                down.consume()
                val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    ?: return@awaitEachGesture
                if (!editState.startDrag(hitButton)) return@awaitEachGesture
                editState.dragBy(dragStart.position - down.position)
                try {
                    drag(down.id) { change ->
                        val delta = change.positionChange()
                        if (delta != Offset.Zero) {
                            change.consume()
                            editState.dragBy(delta)
                        }
                    }
                } finally {
                    editState.endDrag(currentOnLayoutChange)
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        Modifier.fillMaxWidth().height(DesktopDimens.TitleBarHeight)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInRoot()
                editState.titleBarTopLeft = bounds.topLeft
                onTitleBarBoundsChange(bounds)
            }
            .onSizeChanged { titleBarHeightPx = it.height }
            .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
                if (event.buttons.isSecondaryPressed) {
                    val pos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    val rootPos = editState.titleBarTopLeft + pos
                    if (!searchBoxBounds.contains(rootPos)) {
                        contextMenuOffset = with(density) {
                            DpOffset(pos.x.toDp(), (pos.y - titleBarHeightPx).toDp())
                        }
                    }
                }
            }
            .then(editGestureModifier)
    ) {
        // 整条标题栏作拖动区，按钮叠在上层自行消费点击；编辑模式禁用拖窗以免抢占按钮拖拽
        if (editMode) {
            Box(Modifier.fillMaxSize())
        } else {
            // 双击取 AWT 自带的 clickCount 判断，不消费事件；
            // 须在松开时切换：按下时切换会被拖动逻辑按旧位置把窗口挪回去，最大化时也不启用拖动
            val doubleClickToggle = Modifier.fillMaxSize().onPointerEvent(PointerEventType.Release) { event ->
                if (event.awtEventOrNull?.clickCount == 2) onToggleMaximize()
            }
            if (isMaximized) {
                Box(doubleClickToggle)
            } else {
                WindowDragArea(doubleClickToggle) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavArrow(MelodiaIcons.ChevronLeft, "后退", canGoBack, onBack)
                NavArrow(MelodiaIcons.ChevronRight, "前进", canGoForward, onForward)
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TitleBarSlotRow(
                        slot = TitleBarSlot.CENTER_START,
                        buttons = displayLayout.centerStart,
                        editMode = editMode,
                        editState = editState,
                        activeButtons = activeTitleBarButtons,
                        tooltips = titleBarTooltips,
                        unreadCount = privateUnreadCount,
                        onDismissUnread = onDismissUnread,
                        downloadFill = downloadFill,
                        downloadProgress = downloadProgress,
                        downloadShowCheck = downloadShowCheck,
                        downloadNeedsAttention = downloadNeedsAttention,
                        onButtonClick = onTitleBarButtonClick
                    )
                    SearchBox(
                        query = searchQuery,
                        placeholder = searchPlaceholder,
                        onQueryChange = onSearchQueryChange,
                        onFocusChange = onSearchFocusChange,
                        onBoundsChange = { bounds ->
                            searchBoxBounds = bounds
                            onSearchBoundsChange(bounds)
                        },
                        onMove = onSearchMove,
                        onSubmit = onSearchSubmit,
                        focusRequests = searchFocusRequests,
                        isBrowseActive = isBrowseActive,
                        onBrowseClick = onBrowseClick,
                        enabled = !editMode,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    TitleBarSlotRow(
                        slot = TitleBarSlot.CENTER_END,
                        buttons = displayLayout.centerEnd,
                        editMode = editMode,
                        editState = editState,
                        activeButtons = activeTitleBarButtons,
                        tooltips = titleBarTooltips,
                        unreadCount = privateUnreadCount,
                        onDismissUnread = onDismissUnread,
                        downloadFill = downloadFill,
                        downloadProgress = downloadProgress,
                        downloadShowCheck = downloadShowCheck,
                        downloadNeedsAttention = downloadNeedsAttention,
                        onButtonClick = onTitleBarButtonClick
                    )
                }
            }
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                rememberPluginCommands(PluginMenuLocation.TOOLBAR).forEach { item -> PluginToolbarButton(item) }
                CloudUploadsButton(cloudMusic)
                TitleBarSlotRow(
                    slot = TitleBarSlot.TRAILING,
                    buttons = displayLayout.trailing,
                    editMode = editMode,
                    editState = editState,
                    activeButtons = activeTitleBarButtons,
                    tooltips = titleBarTooltips,
                    unreadCount = privateUnreadCount,
                    onDismissUnread = onDismissUnread,
                    downloadFill = downloadFill,
                    downloadProgress = downloadProgress,
                    downloadShowCheck = downloadShowCheck,
                    downloadNeedsAttention = downloadNeedsAttention,
                    onButtonClick = onTitleBarButtonClick,
                    modifier = Modifier.padding(end = 8.dp)
                )
                AvatarMenu(
                    userProfile = userProfile,
                    accounts = accounts,
                    onLoginClick = onLoginClick,
                    onSwitchAccount = onSwitchAccount,
                    onAddAccount = onAddAccount,
                    activeKugouAccount = activeKugouAccount,
                    kugouAccounts = kugouAccounts,
                    onKugouLoginClick = onKugouLoginClick,
                    onKugouSwitchAccount = onKugouSwitchAccount,
                    onKugouAddAccount = onKugouAddAccount,
                    onKugouLogoutAccount = onKugouLogoutAccount,
                    onProfileClick = onProfileClick,
                    onRecentClick = onRecentClick,
                    onCloudClick = onCloudClick,
                    onSettingsClick = onSettingsClick,
                    hasUpdate = hasUpdate,
                    onLogoutClick = onLogoutClick
                )
                WindowButton(MelodiaIcons.Minimize, "最小化", onClick = onMinimize)
                WindowButton(
                    if (isMaximized) MelodiaIcons.RestoreWindow else MelodiaIcons.Maximize,
                    if (isMaximized) "还原" else "最大化",
                    onClick = onToggleMaximize
                )
                WindowButton(MelodiaIcons.CloseWindow, "关闭", hoverColor = CloseHover, onClick = onClose)
            }
        }

        // 拖动中的悬浮按钮独立画在顶层，避免跨槽重排时销毁原组合节点并打断手势
        val dragged = editState.draggedButton
        if (editMode && dragged != null) {
            val targetSlot = editState.workingLayout.slotOf(dragged) ?: TitleBarSlot.TRAILING
            val inCenter = targetSlot != TitleBarSlot.TRAILING
            val buttonSize = if (inCenter) CENTER_BUTTON_SIZE else DOWNLOAD_BUTTON_SIZE
            val buttonSizePx = with(density) { buttonSize.toPx() }
            val center = editState.currentDragCenterInTitleBar
            val topLeftOffset = IntOffset(
                (center.x - buttonSizePx / 2f).roundToInt(),
                (center.y - buttonSizePx / 2f).roundToInt()
            )
            Box(
                Modifier
                    .offset { topLeftOffset }
                    .zIndex(10f)
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                    }
                    .themedShadow(8.dp, CircleShape)
            ) {
                TitleBarButtonContent(
                    button = dragged,
                    inCenter = inCenter,
                    active = dragged in activeTitleBarButtons,
                    tooltip = titleBarTooltips[dragged] ?: dragged.title,
                    unreadCount = privateUnreadCount,
                    onDismissUnread = {},
                    downloadFill = downloadFill,
                    downloadProgress = downloadProgress,
                    downloadShowCheck = downloadShowCheck,
                    downloadNeedsAttention = downloadNeedsAttention,
                    editMode = true,
                    onClick = {}
                )
            }
        }

        DesktopMenu(
            expanded = contextMenuOffset != null,
            onDismiss = { contextMenuOffset = null },
            offset = contextMenuOffset ?: DpOffset.Zero,
            width = TITLE_BAR_MENU_WIDTH
        ) {
            MenuHeader("标题栏按钮")
            TitleBarButton.entries.forEach { button ->
                MenuOption(
                    label = button.title,
                    selected = displayLayout.isVisible(button),
                    onClick = { onToggleTitleBarButton(button) }
                )
            }
            MenuDivider()
            MenuItem(
                text = if (editMode) "退出编辑" else "编辑排序",
                icon = if (editMode) MelodiaIcons.Check else MelodiaIcons.Drag,
                onClick = {
                    contextMenuOffset = null
                    onEditModeChange(!editMode)
                }
            )
        }
    }
}

@Composable
private fun TitleBarSlotRow(
    slot: TitleBarSlot,
    buttons: List<TitleBarButton>,
    editMode: Boolean,
    editState: TitleBarEditState,
    activeButtons: Set<TitleBarButton>,
    tooltips: Map<TitleBarButton, String>,
    unreadCount: Int,
    onDismissUnread: () -> Unit,
    downloadFill: Float,
    downloadProgress: Float?,
    downloadShowCheck: Boolean,
    downloadNeedsAttention: Boolean,
    onButtonClick: (TitleBarButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val inCenter = slot != TitleBarSlot.TRAILING
    val visible = buttons.isNotEmpty() || editMode
    if (!visible) return

    val spacing = if (inCenter) 8.dp else 6.dp
    val placeholderSize = if (inCenter) CENTER_BUTTON_SIZE else DOWNLOAD_BUTTON_SIZE

    Row(
        modifier = modifier
            .onGloballyPositioned { editState.slotBounds[slot] = it.boundsInRoot() }
            .animateContentSize(uiSpring(stiffness = Spring.StiffnessMediumLow)),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (buttons.isEmpty()) {
            Box(
                Modifier
                    .size(placeholderSize)
                    .clip(CircleShape)
                    .background(DesktopColors.FieldOnCanvas.copy(alpha = 0.25f))
                    .dashedBorder(DesktopColors.TextGray.copy(alpha = 0.45f))
            )
        } else {
            buttons.forEach { button ->
                key(button) {
                    val isDraggedPlaceholder = editMode && editState.draggedButton == button
                    val jiggleModifier = if (editMode && !isDraggedPlaceholder) {
                        Modifier.editJiggle(button)
                    } else {
                        Modifier
                    }
                    Box(
                        Modifier
                            .animateHorizontalReorder(enabled = editMode && !isDraggedPlaceholder)
                            .onGloballyPositioned { editState.buttonBounds[button] = it.boundsInRoot() }
                            .then(jiggleModifier)
                            .then(if (editMode) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                    ) {
                        if (isDraggedPlaceholder) {
                            Box(
                                Modifier
                                    .size(placeholderSize)
                                    .clip(CircleShape)
                                    .background(DesktopColors.Accent.copy(alpha = 0.08f))
                                    .dashedBorder(DesktopColors.Accent.copy(alpha = 0.7f))
                            )
                        } else {
                            Box(
                                if (editMode) {
                                    Modifier.dashedBorder(DesktopColors.TextGray.copy(alpha = 0.55f))
                                } else {
                                    Modifier
                                }
                            ) {
                                TitleBarButtonContent(
                                    button = button,
                                    inCenter = inCenter,
                                    active = button in activeButtons,
                                    tooltip = tooltips[button] ?: button.title,
                                    unreadCount = unreadCount,
                                    onDismissUnread = onDismissUnread,
                                    downloadFill = downloadFill,
                                    downloadProgress = downloadProgress,
                                    downloadShowCheck = downloadShowCheck,
                                    downloadNeedsAttention = downloadNeedsAttention,
                                    editMode = editMode,
                                    onClick = { onButtonClick(button) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleBarButtonContent(
    button: TitleBarButton,
    inCenter: Boolean,
    active: Boolean,
    tooltip: String,
    unreadCount: Int,
    onDismissUnread: () -> Unit,
    downloadFill: Float,
    downloadProgress: Float?,
    downloadShowCheck: Boolean,
    downloadNeedsAttention: Boolean,
    editMode: Boolean,
    onClick: () -> Unit
) {
    val resolvedTooltip = when (button) {
        TitleBarButton.MESSAGES -> if (unreadCount > 0) "消息 · $unreadCount 条未读" else "消息"
        TitleBarButton.DOWNLOADS -> downloadProgress?.let { "下载中 ${(it * 100).roundToInt()}%" } ?: "下载管理"
        else -> tooltip
    }

    val content: @Composable () -> Unit = {
        if (inCenter) {
            CenterTitleBarButton(
                button = button,
                active = active,
                unreadCount = unreadCount,
                onDismissUnread = onDismissUnread,
                downloadFill = downloadFill,
                downloadShowCheck = downloadShowCheck,
                downloadNeedsAttention = downloadNeedsAttention,
                editMode = editMode,
                onClick = onClick
            )
        } else {
            TrailingTitleBarButton(
                button = button,
                active = active,
                unreadCount = unreadCount,
                onDismissUnread = onDismissUnread,
                downloadFill = downloadFill,
                downloadShowCheck = downloadShowCheck,
                downloadNeedsAttention = downloadNeedsAttention,
                editMode = editMode,
                onClick = onClick
            )
        }
    }

    if (editMode) {
        content()
    } else {
        DesktopTooltip(resolvedTooltip, side = TooltipSide.Bottom) {
            content()
        }
    }
}

@Composable
private fun CenterTitleBarButton(
    button: TitleBarButton,
    active: Boolean,
    unreadCount: Int,
    onDismissUnread: () -> Unit,
    downloadFill: Float,
    downloadShowCheck: Boolean,
    downloadNeedsAttention: Boolean,
    editMode: Boolean,
    onClick: () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (isHovered && !editMode) 1.06f else 1f, uiTween(150))
    val highlighted = isHovered || (active && button != TitleBarButton.HOME)
    val bgColor by animateColorAsState(
        if (highlighted) DesktopColors.FieldOnCanvasHover else DesktopColors.FieldOnCanvas,
        uiTween(150)
    )
    val iconTint = if (active && button in AccentActiveButtons) DesktopColors.Accent else DesktopColors.TextPrimary
    val iconSize = 24.dp

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(CENTER_BUTTON_SIZE)
                .then(if (!editMode) Modifier.hoverable(hoverSource) else Modifier)
                .clip(CircleShape)
                .floatingOutline(CircleShape)
                .background(bgColor)
                .then(if (!editMode) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (button == TitleBarButton.DOWNLOADS) {
                DownloadProgressIcon(
                    showCheck = downloadShowCheck,
                    fill = downloadFill,
                    tint = iconTint,
                    size = iconSize
                )
            } else {
                Icon(button.icon(active), button.title, tint = iconTint, modifier = Modifier.size(iconSize))
            }
        }
        if (button == TitleBarButton.MESSAGES && unreadCount > 0) {
            DraggableUnreadBadge(
                count = unreadCount,
                onDismiss = onDismissUnread,
                enabled = !editMode,
                containerColor = DesktopColors.Accent,
                contentColor = DesktopColors.OnAccent,
                fontSize = 9.sp,
                minWidth = 15.dp,
                height = 15.dp,
                horizontalPadding = 4.dp,
                borderWidth = 1.dp,
                borderColor = DesktopColors.WindowBackground,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp)
            )
        }
        if (button == TitleBarButton.DOWNLOADS && downloadNeedsAttention && !downloadShowCheck) {
            AttentionDot(Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp))
        }
    }
}

@Composable
private fun TrailingTitleBarButton(
    button: TitleBarButton,
    active: Boolean,
    unreadCount: Int,
    onDismissUnread: () -> Unit,
    downloadFill: Float,
    downloadShowCheck: Boolean,
    downloadNeedsAttention: Boolean,
    editMode: Boolean,
    onClick: () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        if (hovered && !editMode) DOWNLOAD_HOVER_SCALE else 1f,
        uiTween(FILL_ANIMATION_MS),
        label = "titleBarButtonHover"
    )
    val tint = when {
        active && button in AccentActiveButtons -> DesktopColors.Accent
        hovered || active -> DesktopColors.TextPrimary
        else -> DesktopColors.TextGray
    }

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(DOWNLOAD_BUTTON_SIZE)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .then(
                    if (!editMode) {
                        Modifier.hoverable(hoverSource).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (button == TitleBarButton.DOWNLOADS) {
                DownloadProgressIcon(
                    showCheck = downloadShowCheck,
                    fill = downloadFill,
                    tint = tint,
                    size = DOWNLOAD_ICON_SIZE
                )
            } else {
                Icon(button.icon(active), button.title, tint = tint, modifier = Modifier.size(DOWNLOAD_ICON_SIZE))
            }
        }
        if (button == TitleBarButton.MESSAGES && unreadCount > 0) {
            DraggableUnreadBadge(
                count = unreadCount,
                onDismiss = onDismissUnread,
                enabled = !editMode,
                containerColor = DesktopColors.Accent,
                contentColor = DesktopColors.OnAccent,
                fontSize = 9.sp,
                minWidth = 15.dp,
                height = 15.dp,
                horizontalPadding = 4.dp,
                borderWidth = 1.dp,
                borderColor = DesktopColors.WindowBackground,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp)
            )
        }
        if (button == TitleBarButton.DOWNLOADS && downloadNeedsAttention && !downloadShowCheck) {
            AttentionDot(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp))
        }
    }
}

@Composable
private fun DownloadProgressIcon(
    showCheck: Boolean,
    fill: Float,
    tint: Color,
    size: Dp
) {
    Crossfade(showCheck, animationSpec = uiTween(FILL_ANIMATION_MS), label = "downloadIcon") { check ->
        Box(Modifier.size(size)) {
            val icon = if (check) MelodiaIcons.Check else MelodiaIcons.Download
            Icon(icon, "下载管理", tint = if (check) DesktopColors.TextPrimary else tint, modifier = Modifier.fillMaxSize())
            if (!check && fill > 0f) {
                Icon(
                    icon,
                    null,
                    tint = DesktopColors.Accent,
                    modifier = Modifier.fillMaxSize().drawWithContent {
                        clipRect(top = this.size.height * (1f - fill)) { this@drawWithContent.drawContent() }
                    }
                )
            }
        }
    }
}

private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp = 1.5.dp
): Modifier = drawBehind {
    val strokePx = strokeWidth.toPx()
    val dash = 4.dp.toPx()
    val gap = 3.dp.toPx()
    val inset = strokePx / 2f
    drawOval(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokePx, size.height - strokePx),
        style = Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), 0f)
        )
    )
}

@Composable
private fun Modifier.editJiggle(button: TitleBarButton): Modifier {
    if (!LocalUiMotion.current.enabled) return this
    val transition = rememberInfiniteTransition(label = "titleBarJiggle")
    val angle by transition.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(160, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset((button.ordinal % 3) * 55)
        ),
        label = "jiggleAngle"
    )
    return graphicsLayer { rotationZ = angle }
}

// 布局坐标立即落位保证命中测试无回跳，视觉偏移用弹簧追赶形成让位动画
@Composable
private fun Modifier.animateHorizontalReorder(enabled: Boolean): Modifier {
    if (!enabled || !LocalUiMotion.current.enabled) return this
    val motion = LocalUiMotion.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var lastX by remember { mutableStateOf<Float?>(null) }
    return this
        .onPlaced { coords ->
            val x = coords.positionInParent().x
            val prev = lastX
            lastX = x
            if (prev != null && prev != x) {
                val delta = prev - x
                scope.launch {
                    offsetX.snapTo(offsetX.value + delta)
                    offsetX.animateTo(0f, motion.spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
        .graphicsLayer { translationX = offsetX.value }
}

@Composable
private fun PluginToolbarButton(item: PluginCommandItem) {
    val commands = LocalPluginCommands.current
    DesktopTooltip(item.title, side = TooltipSide.Bottom, modifier = Modifier.padding(end = 8.dp)) {
        IconButton(onClick = { commands?.run(item) }, modifier = Modifier.size(36.dp)) {
            Icon(MelodiaIcons.Plugin, item.title, tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
        }
    }
}

// 红色提醒圆点，外圈描边与标题栏背景分隔
@Composable
private fun AttentionDot(modifier: Modifier = Modifier) {
    Box(
        modifier.size(8.dp)
            .border(1.dp, DesktopColors.WindowBackground, CircleShape)
            .padding(1.dp)
            .clip(CircleShape)
            .background(DesktopColors.Accent)
    )
}

// 未登录时菜单提供登录入口，设置页始终可达；有新版本时头像与设置项亮红点
@Composable
private fun AvatarMenu(
    userProfile: UserProfile?,
    accounts: List<UserProfile>,
    onLoginClick: () -> Unit,
    onSwitchAccount: (uid: Long) -> Unit,
    onAddAccount: () -> Unit,
    activeKugouAccount: StoredAccount?,
    kugouAccounts: List<StoredAccount>,
    onKugouLoginClick: () -> Unit,
    onKugouSwitchAccount: (AccountId) -> Unit,
    onKugouAddAccount: () -> Unit,
    onKugouLogoutAccount: (AccountId) -> Unit,
    onProfileClick: () -> Unit,
    onRecentClick: () -> Unit,
    onCloudClick: () -> Unit,
    onSettingsClick: () -> Unit,
    hasUpdate: Boolean,
    onLogoutClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val menuWidth = if (userProfile != null || activeKugouAccount != null) AccountMenuWidth else DefaultMenuWidth
    Box(Modifier.padding(end = 12.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(DesktopColors.Surface).clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            if (userProfile != null) {
                Cover(userProfile.avatarUrl, 32.dp, shape = CircleShape)
            } else if (activeKugouAccount != null && activeKugouAccount.avatarUrl.isNotBlank()) {
                Cover(activeKugouAccount.avatarUrl, 32.dp, shape = CircleShape)
            } else {
                Icon(MelodiaIcons.Person, "账户", tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
            }
        }
        if (hasUpdate) AttentionDot(Modifier.align(Alignment.TopEnd))
        DesktopMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            width = menuWidth
        ) {
            if (userProfile != null) {
                AvatarMenuHeader(userProfile) {
                    expanded = false
                    onProfileClick()
                }
                MenuDivider()
                accounts.filter { it.uid != userProfile.uid }.forEach { account ->
                    SimpleMenuItem(
                        text = account.nickname,
                        onClick = {
                            expanded = false
                            onSwitchAccount(account.uid)
                        },
                        leading = { Cover(account.avatarUrl, 20.dp, shape = CircleShape) }
                    )
                }
                val canAddAccount = accounts.size < UserPreferences.MAX_ACCOUNTS
                MenuItem(
                    text = if (canAddAccount) "添加帐号" else "添加帐号（最多 ${UserPreferences.MAX_ACCOUNTS} 个）",
                    onClick = {
                        expanded = false
                        onAddAccount()
                    },
                    icon = MelodiaIcons.PersonAdd,
                    enabled = canAddAccount
                )
            } else {
                AvatarMenuItem(MelodiaIcons.Login, "登录") {
                    expanded = false
                    onLoginClick()
                }
            }

            MenuDivider()
            MenuHeader("酷狗音乐")
            if (activeKugouAccount != null) {
                KugouAvatarMenuAccountRow(
                    account = activeKugouAccount,
                    isActive = true,
                    onClick = {},
                    onLogout = {
                        expanded = false
                        onKugouLogoutAccount(activeKugouAccount.accountId)
                    }
                )
                kugouAccounts.filter { it.accountId != activeKugouAccount.accountId }.forEach { account ->
                    KugouAvatarMenuAccountRow(
                        account = account,
                        isActive = false,
                        onClick = {
                            expanded = false
                            onKugouSwitchAccount(account.accountId)
                        },
                        onLogout = {
                            expanded = false
                            onKugouLogoutAccount(account.accountId)
                        }
                    )
                }
                val canAddKugou = kugouAccounts.size < UserPreferences.MAX_ACCOUNTS
                MenuItem(
                    text = if (canAddKugou) "添加酷狗帐号" else "添加酷狗帐号（最多 ${UserPreferences.MAX_ACCOUNTS} 个）",
                    onClick = {
                        expanded = false
                        onKugouAddAccount()
                    },
                    icon = MelodiaIcons.PersonAdd,
                    enabled = canAddKugou
                )
                AvatarMenuItem(MelodiaIcons.Logout, "退出酷狗账户") {
                    expanded = false
                    onKugouLogoutAccount(activeKugouAccount.accountId)
                }
            } else {
                AvatarMenuItem(MelodiaIcons.Login, "登录酷狗音乐") {
                    expanded = false
                    onKugouLoginClick()
                }
            }

            if (userProfile != null) {
                MenuDivider()
                AvatarMenuItem(MelodiaIcons.History, "最近播放") {
                    expanded = false
                    onRecentClick()
                }
                AvatarMenuItem(MelodiaIcons.Cloud, "我的云盘") {
                    expanded = false
                    onCloudClick()
                }
            } else {
                MenuDivider()
            }
            MenuItem(
                "设置",
                onClick = {
                    expanded = false
                    onSettingsClick()
                },
                icon = MelodiaIcons.Settings,
                trailing = if (hasUpdate) {
                    { Box(Modifier.size(6.dp).clip(CircleShape).background(DesktopColors.Accent)) }
                } else {
                    null
                }
            )
            if (userProfile != null) {
                AvatarMenuItem(MelodiaIcons.Logout, "退出当前账户") {
                    expanded = false
                    onLogoutClick()
                }
            }
        }
    }
}

private val AccountMenuWidth = 224.dp

@Composable
private fun KugouAvatarMenuAccountRow(
    account: StoredAccount,
    isActive: Boolean,
    onClick: () -> Unit,
    onLogout: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val displayName = account.nickname.ifBlank { "酷狗用户 ${account.effectivePlatformUid}" }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) DesktopColors.SurfaceLight else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (account.avatarUrl.isNotBlank()) {
            Cover(account.avatarUrl, 22.dp, shape = CircleShape)
        } else {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(DesktopColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(MelodiaIcons.Person, null, tint = DesktopColors.TextGray, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = displayName,
                color = DesktopColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subText = buildList {
                if (isActive) add("当前活跃")
                if (account.vipLabel.isNotBlank()) add(account.vipLabel)
            }.joinToString(" · ")
            if (subText.isNotEmpty()) {
                Text(
                    text = subText,
                    color = if (isActive) DesktopColors.AccentText else DesktopColors.TextGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HoverReveal(revealed = hovered, reserveSpace = false) {
            Box(
                Modifier.size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onLogout),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    MelodiaIcons.Logout,
                    contentDescription = "退出酷狗账户",
                    tint = DangerColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// 头像菜单头部：大头像 + 昵称，点击进个人主页
@Composable
private fun AvatarMenuHeader(userProfile: UserProfile, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) DesktopColors.SurfaceLight else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(userProfile.avatarUrl, 40.dp, shape = CircleShape)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                userProfile.nickname,
                color = DesktopColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("查看个人主页", color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MenuScope.AvatarMenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    MenuItem(text, onClick, icon = icon)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SearchBox(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onBoundsChange: (Rect) -> Unit,
    onMove: (Int) -> Boolean,
    onSubmit: () -> Unit,
    focusRequests: Flow<Unit>,
    isBrowseActive: Boolean,
    onBrowseClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    LaunchedEffect(focusRequests) {
        focusRequests.collect { runCatching { focusRequester.requestFocus() } }
    }

    val targetBg = when {
        isFocused || (isHovered && enabled) -> DesktopColors.FieldOnCanvasHover
        else -> DesktopColors.FieldOnCanvas
    }
    val animatedBg by animateColorAsState(targetBg, uiTween(180))

    val borderColor = when {
        isFocused -> DesktopColors.TextPrimary
        isHovered && enabled -> DesktopColors.TextPrimary.copy(alpha = 0.55f)
        DesktopColors.IsDark -> Color.Transparent
        else -> DesktopColors.Outline
    }
    val animatedBorderColor by animateColorAsState(borderColor, uiTween(180))

    val scale by animateFloatAsState(
        targetValue = if ((isHovered && enabled) || isFocused) 1.015f else 1f,
        animationSpec = uiTween(180)
    )

    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .widthIn(min = 200.dp, max = 490.dp)
            .height(48.dp)
            .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
            .then(if (enabled) Modifier.hoverable(hoverInteraction) else Modifier)
            .clip(RoundedCornerShape(24.dp))
            .border(2.dp, animatedBorderColor, RoundedCornerShape(24.dp))
            .background(animatedBg)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            MelodiaIcons.Search,
            null,
            tint = if (isFocused || (isHovered && enabled)) DesktopColors.TextPrimary else DesktopColors.TextGray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        placeholder,
                        color = DesktopColors.TextGray,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isHovered && enabled && !isFocused) {
                        Spacer(Modifier.width(8.dp))
                        SearchShortcutKey("Ctrl")
                        Spacer(Modifier.width(3.dp))
                        SearchShortcutKey("F")
                    }
                }
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(color = DesktopColors.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(DesktopColors.TextPrimary),
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(focusRequester)
                    .trackTextInputFocus()
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusChange(it.isFocused)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> onMove(1)
                            Key.DirectionUp -> onMove(-1)
                            Key.Enter -> {
                                onSubmit()
                                // 提交后释放焦点，空格等窗口快捷键才不会被输入框吃掉
                                focusManager.clearFocus()
                                true
                            }
                            else -> false
                        }
                    }
            )
        }
        if (query.isNotEmpty() && enabled) {
            Icon(
                MelodiaIcons.Close,
                "清空",
                tint = DesktopColors.TextGray,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") }
                    .padding(2.dp)
            )
        }
        Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(24.dp).background(DesktopColors.SurfaceLight))
        if (enabled) {
            DesktopTooltip("浏览", side = TooltipSide.Bottom) {
                BrowseCabinetButton(
                    isActive = isBrowseActive,
                    onClick = onBrowseClick
                )
            }
        } else {
            BrowseCabinetButton(
                isActive = isBrowseActive,
                enabled = false,
                onClick = {}
            )
        }
    }
}

// 快捷键按键徽标
@Composable
private fun SearchShortcutKey(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, DesktopColors.Overlay.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
            .background(DesktopColors.Overlay.copy(alpha = 0.05f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = DesktopColors.TextGray,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

// 浏览按钮：独立悬停缩放，激活切实心
@Composable
private fun BrowseCabinetButton(
    isActive: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val buttonHover = remember { MutableInteractionSource() }
    val isButtonHovered by buttonHover.collectIsHoveredAsState()

    val buttonScale by animateFloatAsState(
        targetValue = if (isButtonHovered && enabled) 1.15f else 1f,
        animationSpec = uiTween(150)
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            isActive || (isButtonHovered && enabled) -> DesktopColors.TextPrimary
            else -> DesktopColors.TextGray
        },
        animationSpec = uiTween(150)
    )

    Box(
        Modifier
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .then(if (enabled) Modifier.hoverable(buttonHover) else Modifier)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isActive) MelodiaIcons.BrowseFilled else MelodiaIcons.Browse,
            null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun NavArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            description,
            tint = if (enabled) DesktopColors.TextPrimary else DesktopColors.SurfaceLight,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WindowButton(
    icon: ImageVector,
    description: String,
    hoverColor: Color = DesktopColors.Hover,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        Modifier.width(46.dp).fillMaxHeight()
            .background(if (hovered) hoverColor else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = DesktopColors.TextPrimary, modifier = Modifier.size(16.dp))
    }
}
