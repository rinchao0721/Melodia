package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.SimilarRoamingController
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.desktop.data.LibraryShortcut
import com.lin0721.linmusic.desktop.data.LibraryShortcutSetting
import com.lin0721.linmusic.desktop.data.local.LOCAL_ALL_TRACKS_ID
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.playlist.DAILY_RECOMMEND_ID
import com.lin0721.linmusic.desktop.ui.shell.DesktopAppDependencies
import com.lin0721.linmusic.desktop.ui.shell.DesktopAppState
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

// 按钮内边距、圆角与图标尺寸与 LibraryTitleToggle 保持一致（点击区 32x36dp，图标 24dp）
private val ShortcutIconSize = 24.dp
private val ShortcutSpacing = 4.dp
private val ShortcutWheelStep = 48.dp
private val ShortcutMenuWidth = 196.dp
private const val LOCAL_ALL_TRACKS_NAME = "全部本地歌曲"

@Immutable
class LibraryShortcutsState(
    val shortcuts: List<LibraryShortcutSetting>,
    val activeShortcuts: Set<LibraryShortcut>,
    val roamingEnabled: Boolean,
    val onClick: (LibraryShortcut) -> Unit,
    val onToggleVisible: (LibraryShortcut) -> Unit
) {
    val visibleShortcuts: List<LibraryShortcut>
        get() = shortcuts.filter { it.visible }.map { it.item }
}

internal enum class LibraryShortcutClickAction {
    START_PERSONAL_FM,
    DISABLE_PERSONAL_FM,
    START_INTELLIGENCE,
    DISABLE_INTELLIGENCE,
    START_SIMILAR_ROAMING,
    DISABLE_SIMILAR_ROAMING,
    OPEN_DAILY_RECOMMEND,
    OPEN_CLOUD,
    OPEN_LOCAL_MUSIC,
    NAVIGATE_BACK
}

internal fun activeLibraryShortcuts(
    playContext: String?,
    currentRoute: DesktopRoute?
): Set<LibraryShortcut> = buildSet {
    when (playContext) {
        PlaybackController.CONTEXT_PERSONAL_FM -> add(LibraryShortcut.PERSONAL_FM)
        PlaybackController.CONTEXT_INTELLIGENCE -> add(LibraryShortcut.INTELLIGENCE)
        SimilarRoamingController.CONTEXT_ROAMING -> add(LibraryShortcut.SIMILAR_ROAMING)
    }
    when (currentRoute) {
        is DesktopRoute.Playlist -> if (currentRoute.id == DAILY_RECOMMEND_ID) add(LibraryShortcut.DAILY_RECOMMEND)
        DesktopRoute.Cloud -> add(LibraryShortcut.CLOUD)
        is DesktopRoute.LocalPlaylist -> if (currentRoute.id == LOCAL_ALL_TRACKS_ID) add(LibraryShortcut.LOCAL_MUSIC)
        else -> Unit
    }
}

internal fun resolveShortcutClickAction(
    item: LibraryShortcut,
    isActive: Boolean
): LibraryShortcutClickAction = when (item) {
    LibraryShortcut.PERSONAL_FM ->
        if (isActive) LibraryShortcutClickAction.DISABLE_PERSONAL_FM else LibraryShortcutClickAction.START_PERSONAL_FM
    LibraryShortcut.INTELLIGENCE ->
        if (isActive) LibraryShortcutClickAction.DISABLE_INTELLIGENCE else LibraryShortcutClickAction.START_INTELLIGENCE
    LibraryShortcut.SIMILAR_ROAMING ->
        if (isActive) LibraryShortcutClickAction.DISABLE_SIMILAR_ROAMING else LibraryShortcutClickAction.START_SIMILAR_ROAMING
    LibraryShortcut.DAILY_RECOMMEND ->
        if (isActive) LibraryShortcutClickAction.NAVIGATE_BACK else LibraryShortcutClickAction.OPEN_DAILY_RECOMMEND
    LibraryShortcut.CLOUD ->
        if (isActive) LibraryShortcutClickAction.NAVIGATE_BACK else LibraryShortcutClickAction.OPEN_CLOUD
    LibraryShortcut.LOCAL_MUSIC ->
        if (isActive) LibraryShortcutClickAction.NAVIGATE_BACK else LibraryShortcutClickAction.OPEN_LOCAL_MUSIC
}

internal fun isShortcutEnabled(
    item: LibraryShortcut,
    isActive: Boolean,
    isLoggedIn: Boolean,
    hasCloudSong: Boolean
): Boolean = when (item) {
    // 相似漫游已开启时，即使当前没有云端曲目也要允许点击退出
    LibraryShortcut.SIMILAR_ROAMING -> isActive || !isLoggedIn || hasCloudSong
    else -> true
}

internal fun shortcutTooltip(
    item: LibraryShortcut,
    isActive: Boolean,
    enabled: Boolean = true
): String = when {
    isActive -> when (item) {
        LibraryShortcut.PERSONAL_FM,
        LibraryShortcut.INTELLIGENCE,
        LibraryShortcut.SIMILAR_ROAMING -> "${item.title} · 点击退出"
        LibraryShortcut.DAILY_RECOMMEND,
        LibraryShortcut.CLOUD,
        LibraryShortcut.LOCAL_MUSIC -> "${item.title} · 点击返回"
    }
    item == LibraryShortcut.SIMILAR_ROAMING && !enabled -> "相似歌曲漫游（请先播放一首云端歌曲）"
    else -> item.title
}

@Composable
internal fun rememberLibraryShortcutsState(
    appState: DesktopAppState,
    deps: DesktopAppDependencies,
    isLoggedIn: Boolean
): LibraryShortcutsState {
    val shortcuts by deps.desktopSettingsViewModel.libraryShortcuts.collectAsState()
    val playContext by deps.playbackController.playContext.collectAsState()
    val nowPlaying by deps.playbackController.nowPlaying.collectAsState()
    val detailState by deps.playerViewModel.songDetailState.collectAsState()
    val currentRoute = appState.backStack.current

    val cloudSongId = nowPlaying?.songId?.takeIf { it > 0L && !detailState.isLocalOnly }
    val active = remember(playContext, currentRoute) {
        activeLibraryShortcuts(playContext, currentRoute)
    }
    val roamingEnabled = isShortcutEnabled(
        item = LibraryShortcut.SIMILAR_ROAMING,
        isActive = LibraryShortcut.SIMILAR_ROAMING in active,
        isLoggedIn = isLoggedIn,
        hasCloudSong = cloudSongId != null
    )

    return remember(shortcuts, active, roamingEnabled, isLoggedIn, nowPlaying, cloudSongId, appState, deps) {
        LibraryShortcutsState(
            shortcuts = shortcuts,
            activeShortcuts = active,
            roamingEnabled = roamingEnabled,
            onClick = { item ->
                performLibraryShortcutClick(
                    item = item,
                    isActive = item in active,
                    appState = appState,
                    deps = deps,
                    isLoggedIn = isLoggedIn,
                    nowPlaying = nowPlaying,
                    cloudSongId = cloudSongId
                )
            },
            onToggleVisible = deps.desktopSettingsViewModel::toggleLibraryShortcut
        )
    }
}

// 侧栏与标题栏共用的快捷入口点击行为，保证开启/退出/返回逻辑一致
internal fun performLibraryShortcutClick(
    item: LibraryShortcut,
    isActive: Boolean,
    appState: DesktopAppState,
    deps: DesktopAppDependencies,
    isLoggedIn: Boolean,
    nowPlaying: NowPlaying? = null,
    cloudSongId: Long? = null
) {
    when (resolveShortcutClickAction(item, isActive = isActive)) {
        LibraryShortcutClickAction.DISABLE_PERSONAL_FM -> deps.playerViewModel.disablePersonalFm()
        LibraryShortcutClickAction.DISABLE_INTELLIGENCE -> deps.playbackController.disableIntelligence()
        LibraryShortcutClickAction.DISABLE_SIMILAR_ROAMING -> deps.playbackController.disableRoaming()
        LibraryShortcutClickAction.NAVIGATE_BACK -> appState.popBack()
        LibraryShortcutClickAction.START_PERSONAL_FM -> {
            if (!isLoggedIn) {
                appState.showMessage("请先登录账号")
                appState.showLogin = true
            } else {
                deps.playerViewModel.startPersonalFm()
            }
        }
        LibraryShortcutClickAction.START_INTELLIGENCE -> {
            if (!isLoggedIn) {
                appState.showMessage("请先登录账号")
                appState.showLogin = true
            } else {
                val track = nowPlaying
                if (track == null) {
                    deps.homeViewModel.startIntelligenceMode()
                } else if (cloudSongId != null) {
                    deps.playerViewModel.startIntelligenceMode(
                        songId = cloudSongId,
                        currentTitle = track.title,
                        currentArtist = track.artist,
                        currentCoverUrl = track.artworkUri.orEmpty()
                    )
                } else {
                    appState.showMessage("当前歌曲暂无云端信息，无法开启心动模式")
                }
            }
        }
        LibraryShortcutClickAction.START_SIMILAR_ROAMING -> {
            if (!isLoggedIn) {
                appState.showMessage("请先登录账号")
                appState.showLogin = true
            } else {
                val track = nowPlaying
                if (track == null || cloudSongId == null) {
                    appState.showMessage("请先播放一首云端歌曲")
                } else {
                    deps.playerViewModel.startSimilarSongsRoaming(
                        songId = cloudSongId,
                        currentTitle = track.title,
                        currentArtist = track.artist,
                        currentCoverUrl = track.artworkUri.orEmpty()
                    )
                }
            }
        }
        LibraryShortcutClickAction.OPEN_DAILY_RECOMMEND -> {
            if (!isLoggedIn) {
                appState.showMessage("请先登录账号")
                appState.showLogin = true
            } else {
                appState.openDailyRecommend()
            }
        }
        LibraryShortcutClickAction.OPEN_CLOUD -> {
            if (!isLoggedIn) {
                appState.showMessage("请先登录账号")
                appState.showLogin = true
            } else {
                appState.openCloud()
            }
        }
        LibraryShortcutClickAction.OPEN_LOCAL_MUSIC ->
            appState.openLocalPlaylist(LOCAL_ALL_TRACKS_ID, LOCAL_ALL_TRACKS_NAME)
    }
}

internal fun LibraryShortcut.icon(): ImageVector = when (this) {
    LibraryShortcut.PERSONAL_FM -> MelodiaIcons.Radio
    LibraryShortcut.INTELLIGENCE -> MelodiaIcons.Heart
    LibraryShortcut.SIMILAR_ROAMING -> MelodiaIcons.Explore
    LibraryShortcut.DAILY_RECOMMEND -> MelodiaIcons.Calendar
    LibraryShortcut.CLOUD -> MelodiaIcons.Cloud
    LibraryShortcut.LOCAL_MUSIC -> MelodiaIcons.LocalMusic
}

// 侧栏快捷入口右键上下文菜单容器，用于勾选各入口显隐
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LibraryShortcutsContextBox(
    state: LibraryShortcutsState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(0) }
    var menuOffset by remember { mutableStateOf<DpOffset?>(null) }

    Box(
        modifier
            .onSizeChanged { heightPx = it.height }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    val pos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    menuOffset = with(density) {
                        DpOffset(pos.x.toDp(), (pos.y - heightPx).toDp())
                    }
                }
            }
    ) {
        content()
        DesktopMenu(
            expanded = menuOffset != null,
            onDismiss = { menuOffset = null },
            offset = menuOffset ?: DpOffset.Zero,
            width = ShortcutMenuWidth
        ) {
            MenuHeader("快捷入口")
            state.shortcuts.forEach { setting ->
                MenuOption(
                    label = setting.item.title,
                    selected = setting.visible,
                    onClick = { state.onToggleVisible(setting.item) }
                )
            }
        }
    }
}

// 默认态 / 展开态：放在标题行内、创建按钮左侧；超出剩余宽度时支持横向滚轮滚动
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LibraryShortcutRow(
    state: LibraryShortcutsState,
    modifier: Modifier = Modifier
) {
    val visible = state.visibleShortcuts
    if (visible.isEmpty()) {
        Spacer(modifier)
        return
    }
    val scrollState = rememberScrollState()
    val wheelStepPx = with(LocalDensity.current) { ShortcutWheelStep.toPx() }
    Box(
        modifier = modifier.padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
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
            horizontalArrangement = Arrangement.spacedBy(ShortcutSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visible.forEach { item ->
                ShortcutIconButton(
                    item = item,
                    state = state,
                    tooltipSide = TooltipSide.Bottom
                )
            }
        }
    }
}

// 收起态：音乐库图标与「＋」按钮之间的竖排图标按钮
@Composable
internal fun LibraryShortcutRailColumn(
    state: LibraryShortcutsState,
    modifier: Modifier = Modifier
) {
    val visible = state.visibleShortcuts
    if (visible.isEmpty()) return
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(ShortcutSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        visible.forEach { item ->
            ShortcutIconButton(
                item = item,
                state = state,
                tooltipSide = TooltipSide.Right
            )
        }
    }
}

@Composable
private fun ShortcutIconButton(
    item: LibraryShortcut,
    state: LibraryShortcutsState,
    tooltipSide: TooltipSide
) {
    val active = item in state.activeShortcuts
    val enabled = if (item == LibraryShortcut.SIMILAR_ROAMING) state.roamingEnabled else true
    val hint = shortcutTooltip(item, isActive = active, enabled = enabled)
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val tint = when {
        !enabled -> DesktopColors.TextGray.copy(alpha = 0.35f)
        active -> DesktopColors.Accent
        hovered -> DesktopColors.TextPrimary
        else -> DesktopColors.TextGray
    }
    DesktopTooltip(hint, side = tooltipSide) {
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (active) DesktopColors.Surface else Color.Transparent)
                .hoverable(source)
                .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .clickable(enabled = enabled) { state.onClick(item) }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(ShortcutIconSize), contentAlignment = Alignment.Center) {
                Icon(item.icon(), hint, tint = tint, modifier = Modifier.size(ShortcutIconSize))
            }
        }
    }
}
