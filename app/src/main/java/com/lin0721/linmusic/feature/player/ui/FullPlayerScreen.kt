package com.lin0721.linmusic.feature.player.ui

import kotlinx.coroutines.flow.flowOf
import com.lin0721.linmusic.core.preferences.PlayerCardEntries
import com.lin0721.linmusic.core.plugin.LocalPluginCards
import com.lin0721.linmusic.core.plugin.PlayerLayoutView
import com.lin0721.linmusic.core.plugin.PlayerLayoutArea
import com.lin0721.linmusic.core.plugin.LocalPlayerLayouts
import com.lin0721.linmusic.core.plugin.LayoutNodeType
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.scrollBy
import com.lin0721.linmusic.core.comment.domain.CommentFloorState
import com.lin0721.linmusic.core.comment.ui.CommentFloorScreen
import com.lin0721.linmusic.core.comment.ui.CommentFullScreen
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.isCoverBackdrop
import com.lin0721.linmusic.core.ui.theme.ImmersiveTheme
import com.lin0721.linmusic.core.ui.theme.LightStatusBarIconsWhile
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.lin0721.linmusic.core.download.ui.DownloadQualityPickerSheet
import com.lin0721.linmusic.core.model.getQualityBadgeLabel
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.normalizeFullPlayerCoverUrl
import com.lin0721.linmusic.core.player.rememberQueueItemCoverUrl
import com.lin0721.linmusic.core.ui.components.ArtistPickerEntry
import com.lin0721.linmusic.core.ui.components.ArtistPickerSheet
import com.lin0721.linmusic.core.ui.components.SwipeToSkipCover
import com.lin0721.linmusic.core.share.ShareCardKind
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.playerBackdrop
import com.lin0721.linmusic.core.ui.theme.themedCoverBase
import com.lin0721.linmusic.feature.player.share.LyricShareSheet
import com.lin0721.linmusic.core.ui.theme.FallbackBackdropPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.PaletteMemoryCache
import com.lin0721.linmusic.core.ui.theme.PanelReflowFadeFromAlpha
import com.lin0721.linmusic.core.ui.theme.PanelReflowFadeSpec
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.extractBackdropPaletteFromUrl
import com.lin0721.linmusic.core.ui.theme.melodiaNavigationBarBottomPadding
import com.lin0721.linmusic.core.ui.theme.melodiaStatusBarTopPadding
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaPlayerPanelAtStart
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.preferences.FullPlayerCard
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.theme.uiTween
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// 侧栏→全屏铺开到这个进度时竖排列表淡出完毕，宽屏两栏从这里开始淡入
private const val WideCrossfadeSplit = 0.6f

// 竖屏全屏时内容列占卡片宽度的比例
private const val PortraitFullscreenColumnFraction = 0.62f

@Composable
fun FullPlayerScreen(
    currentTrack: MediaItem?,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    isPlayerOpen: Boolean,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit = {},
    onNavigateToProfile: (Long) -> Unit = {},
    onDragClose: (Float, Float) -> Unit = { _, _ -> },
    // 手机全屏：封面按可用高度收缩，快捷操作行需让开导航栏
    fitCoverAboveNavigationBar: Boolean = false,
    // 以下为平板常驻面板用，手机全屏保持默认值
    // 封面按可用高度收缩，保证首屏完整显示到快捷操作行
    fitCoverToViewport: Boolean = false,
    // 播放内容列的最大宽度，超出部分只铺背景、内容列居中
    contentMaxWidth: Dp = Dp.Unspecified,
    onToggleSidebarFullscreen: (() -> Unit)? = null,
    isSidebarFullscreen: Boolean = false,
    // 侧栏→全屏的铺开进度（0 侧栏、1 全屏）与全屏时的卡片宽度。横屏全屏换成宽屏两栏排版，
    // 铺开过程中两套排版各按最终宽度排一次、交叉淡入淡出；竖屏全屏沿用竖排列表，按比例放宽并居中
    sidebarFullscreenProgress: (() -> Float)? = null,
    fullscreenContentWidth: Dp = Dp.Unspecified,
    onLyricsFullScreenChange: (Boolean) -> Unit = {},
    onLyricsControlsVisibilityChange: (Boolean) -> Unit = {}
) {
    // 播放区透出皮肤背景图时整页按沉浸区深色配色（深色遮罩配白字），浅色主题下也一样
    val playerBackdrop = isCoverBackdrop(CoverGroup.PLAYER)
    LightStatusBarIconsWhile(isPlayerOpen && playerBackdrop && onToggleSidebarFullscreen == null)
    PlayerTintScope(playerBackdrop) {
        FullPlayerScreenContent(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            onClose = onClose,
            isPlayerOpen = isPlayerOpen,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onPlaylistClick = onPlaylistClick,
            onNavigateToProfile = onNavigateToProfile,
            onDragClose = onDragClose,
            fitCoverAboveNavigationBar = fitCoverAboveNavigationBar,
            fitCoverToViewport = fitCoverToViewport,
            contentMaxWidth = contentMaxWidth,
            onToggleSidebarFullscreen = onToggleSidebarFullscreen,
            isSidebarFullscreen = isSidebarFullscreen,
            sidebarFullscreenProgress = sidebarFullscreenProgress,
            fullscreenContentWidth = fullscreenContentWidth,
            onLyricsFullScreenChange = onLyricsFullScreenChange,
            onLyricsControlsVisibilityChange = onLyricsControlsVisibilityChange
        )
    }
}

@Composable
private fun PlayerTintScope(immersive: Boolean, content: @Composable () -> Unit) {
    if (immersive) ImmersiveTheme(content) else content()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullPlayerScreenContent(
    currentTrack: MediaItem?,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    isPlayerOpen: Boolean,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit = {},
    onNavigateToProfile: (Long) -> Unit = {},
    onDragClose: (Float, Float) -> Unit = { _, _ -> },
    // 手机全屏：封面按可用高度收缩，快捷操作行需让开导航栏
    fitCoverAboveNavigationBar: Boolean = false,
    // 以下为平板常驻面板用，手机全屏保持默认值
    // 封面按可用高度收缩，保证首屏完整显示到快捷操作行
    fitCoverToViewport: Boolean = false,
    // 播放内容列的最大宽度，超出部分只铺背景、内容列居中
    contentMaxWidth: Dp = Dp.Unspecified,
    onToggleSidebarFullscreen: (() -> Unit)? = null,
    isSidebarFullscreen: Boolean = false,
    // 侧栏→全屏的铺开进度（0 侧栏、1 全屏）与全屏时的卡片宽度。横屏全屏换成宽屏两栏排版，
    // 铺开过程中两套排版各按最终宽度排一次、交叉淡入淡出；竖屏全屏沿用竖排列表，按比例放宽并居中
    sidebarFullscreenProgress: (() -> Float)? = null,
    fullscreenContentWidth: Dp = Dp.Unspecified,
    onLyricsFullScreenChange: (Boolean) -> Unit = {},
    onLyricsControlsVisibilityChange: (Boolean) -> Unit = {}
) {
    val motion = LocalUiMotion.current
    if (currentTrack == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel: PlayerViewModel = koinViewModel()
    val settingsPreferences: SettingsPreferences = koinInject()
    // 输出设备切换为 Android 专属能力，不在跨平台接口里
    val playerManager: PlayerManager = koinInject()
    val lyricPositionProvider: () -> Long = {
        // 保留采样状态的读取依赖，让暂停时的手动跳转也触发重绘；
        // 扫色进度使用共用的绘制时钟，只在 PlayerManager 中平滑推进和校正。
        currentPositionProvider()
        playerManager.currentPositionNow()
    }
    val songDetailState by viewModel.songDetailState.collectAsStateWithLifecycle()
    val songDetail = songDetailState.songDetail
    // 大播放按钮专用：弱网缓冲期间也要立刻显示"暂停中"图标，不能等音频真正流出的 isPlaying；
    // 歌词区/顶栏/队列等其他地方仍按严格的 isPlaying 判断，不受影响
    val playWhenReady by viewModel.playerManager.playWhenReady.collectAsStateWithLifecycle()
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsStateWithLifecycle()
    val activeLyricIndices by viewModel.activeLyricIndices.collectAsStateWithLifecycle()
    val playContext by viewModel.playerManager.playContext.collectAsStateWithLifecycle()
    val playSource by viewModel.playerManager.playSource.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val activeQuality by viewModel.activeQuality.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val playMode by viewModel.playerManager.playMode.collectAsStateWithLifecycle()
    val queue by viewModel.playerManager.queue.collectAsStateWithLifecycle()
    val currentQueueIndex by viewModel.playerManager.currentIndex.collectAsStateWithLifecycle()
    val currentQueueItem by viewModel.playerManager.currentQueueItem.collectAsStateWithLifecycle()
    val previousQueueItem by viewModel.playerManager.previousQueueItem.collectAsStateWithLifecycle()
    val nextQueueItem by viewModel.playerManager.nextQueueItem.collectAsStateWithLifecycle()
    var showQueueSheet by remember { mutableStateOf(false) }
    var isLyricsFullScreen by remember { mutableStateOf(false) }
    // 手机全屏歌词盖住状态栏且是有色底，浅色主题下状态栏图标切为浅色
    LightStatusBarIconsWhile(isPlayerOpen && isLyricsFullScreen && onToggleSidebarFullscreen == null)

    LaunchedEffect(isLyricsFullScreen) {
        onLyricsFullScreenChange(isLyricsFullScreen)
        if (!isLyricsFullScreen) {
            onLyricsControlsVisibilityChange(true)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            onLyricsFullScreenChange(false)
            onLyricsControlsVisibilityChange(true)
        }
    }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    var showShareCard by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showCommentFloor by remember { mutableStateOf(false) }
    val composerState by viewModel.composerState.collectAsStateWithLifecycle()
    val floorState by viewModel.floorState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var showOutputDeviceSheet by remember { mutableStateOf(false) }
    var showDownloadQualitySheet by remember { mutableStateOf(false) }
    var showCardEditorSheet by remember { mutableStateOf(false) }
    var artistPickerEntries by remember { mutableStateOf<List<ArtistPickerEntry>>(emptyList()) }
    // 歌手入口统一走这里：单歌手直接跳转，多歌手弹选择面板（头像优先取已加载的歌手卡片资料）
    val openSongArtist: () -> Unit = {
        val avatars = songDetailState.artists.associate { it.artistId to it.artistDetail?.avatar }
        val artists = songDetail?.ar.orEmpty()
            .filter { it.id > 0L }
            .distinctBy { it.id }
            .map { ar ->
                ArtistPickerEntry(
                    id = ar.id,
                    name = ar.name,
                    avatarUrl = avatars[ar.id]?.takeIf { it.isNotBlank() }
                        ?: ar.picUrl.ifBlank { ar.img1v1Url }.takeIf { it.isNotBlank() }
                )
            }
        when (artists.size) {
            0 -> ToastManager.showToast("未找到歌手信息")
            1 -> onArtistClick(artists.first().id)
            else -> artistPickerEntries = artists
        }
    }
    val openSongAlbum: () -> Unit = {
        val albumId = songDetail?.al?.id ?: 0L
        if (albumId > 0L) {
            onAlbumClick(albumId)
        } else {
            ToastManager.showToast("未找到专辑信息")
        }
    }
    val openPlaylistSource: (() -> Unit)? = if (playSource?.kind == PlaySource.Kind.PLAYLIST && (playSource?.id ?: 0L) > 0L) {
        { onPlaylistClick(playSource!!.id) }
    } else {
        null
    }
    val podcast by viewModel.podcastState.collectAsStateWithLifecycle()
    // 播客节目没有歌词、歌曲评论与歌手专辑信息，信息卡与小歌词整体不展示
    val storedCardLayout by viewModel.fullPlayerCardLayout.collectAsStateWithLifecycle()
    val cardLayout = if (podcast.isPodcast) emptyList() else storedCardLayout
    val pluginCards = LocalPluginCards.current
    val storedCardEntries by (pluginCards?.source?.entries ?: remember { flowOf(emptyList()) })
        .collectAsStateWithLifecycle(emptyList())
    val cardEntries = when {
        podcast.isPodcast -> emptyList()
        storedCardEntries.isEmpty() -> PlayerCardEntries.merge(null, emptyList())
        else -> storedCardEntries
    }
    val storedShowMiniLyric by viewModel.showMiniLyric.collectAsStateWithLifecycle()
    val showMiniLyric = storedShowMiniLyric && !podcast.isPodcast
    val connectedDevice = rememberCurrentOutputDevice()

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { message ->
            ToastManager.showToast(message)
        }
    }

    BackHandler(enabled = showQueueSheet) {
        showQueueSheet = false
    }

    BackHandler(enabled = isLyricsFullScreen) {
        isLyricsFullScreen = false
    }

    BackHandler(enabled = showMoreOptionsSheet) {
        showMoreOptionsSheet = false
    }

    BackHandler(enabled = showTimerSheet) {
        showTimerSheet = false
    }

    BackHandler(enabled = showCommentsSheet) {
        showCommentsSheet = false
    }

    BackHandler(enabled = showCommentFloor) {
        showCommentFloor = false
    }

    BackHandler(enabled = showOutputDeviceSheet) {
        showOutputDeviceSheet = false
    }

    BackHandler(enabled = showCardEditorSheet) {
        showCardEditorSheet = false
    }

    BackHandler(enabled = showDownloadQualitySheet) {
        showDownloadQualitySheet = false
    }

    var showLyricsSettingsSheet by remember { mutableStateOf(false) }
    BackHandler(enabled = showLyricsSettingsSheet) {
        showLyricsSettingsSheet = false
    }

    val fullScreenLyricTextSize by settingsPreferences.fullScreenLyricTextSize.collectAsStateWithLifecycle(initialValue = 22)
    val fullScreenLyricAlignment by settingsPreferences.fullScreenLyricAlignment.collectAsStateWithLifecycle(initialValue = "left")
    val fullScreenLyricSecondaryMode by settingsPreferences.fullScreenLyricSecondaryMode.collectAsStateWithLifecycle(initialValue = "translation")
    val fullScreenKaraokeAdvancedEffect by settingsPreferences.fullScreenKaraokeAdvancedEffect.collectAsStateWithLifecycle(initialValue = true)
    val fullScreenKaraokeGlowEffect by settingsPreferences.fullScreenKaraokeGlowEffect.collectAsStateWithLifecycle(initialValue = false)
    val neteaseLyricsEnabled by settingsPreferences.neteaseLyricsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val qqLyricsEnabled by settingsPreferences.qqLyricsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val amllLyricsEnabled by settingsPreferences.amllLyricsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val fullScreenLyricLineSpacing by settingsPreferences.fullScreenLyricLineSpacing.collectAsStateWithLifecycle(initialValue = 24)
    val fullScreenLyricSecondarySpacing by settingsPreferences.fullScreenLyricSecondarySpacing.collectAsStateWithLifecycle(initialValue = 6)

    val hasTranslation = remember(songDetailState.lyrics) { songDetailState.lyrics.any { it.translation != null } }
    val hasRoma = remember(songDetailState.lyrics) { songDetailState.lyrics.any { it.roma != null } }

    val handleToggleSecondaryMode: () -> Unit = {
        val nextMode = when {
            hasTranslation && hasRoma -> when (fullScreenLyricSecondaryMode) {
                "translation" -> "roma"
                "roma" -> "none"
                else -> "translation"
            }
            hasTranslation -> when (fullScreenLyricSecondaryMode) {
                "translation" -> "none"
                else -> "translation"
            }
            hasRoma -> when (fullScreenLyricSecondaryMode) {
                "roma" -> "none"
                else -> "roma"
            }
            else -> "none"
        }
        coroutineScope.launch {
            settingsPreferences.saveFullScreenLyricSecondaryMode(nextMode)
        }
    }

    // 宽屏两栏的歌词区当前是否在组合中（滑到卡片区后移出）
    var isWideLyricsVisible by remember { mutableStateOf(false) }

    // 全屏歌词与宽屏歌词区的逐字滚动都需要更密的进度回调
    DisposableEffect(isLyricsFullScreen || isWideLyricsVisible, isPlaying) {
        if ((isLyricsFullScreen || isWideLyricsVisible) && isPlaying) {
            viewModel.playerManager.setPositionUpdateInterval(50L)
        } else {
            viewModel.playerManager.setPositionUpdateInterval(1000L)
        }
        onDispose {
            viewModel.playerManager.setPositionUpdateInterval(1000L)
        }
    }

    var colorPalette by remember(currentTrack.mediaId) {
        mutableStateOf(
            PaletteMemoryCache.get(currentTrack.mediaId) ?: FallbackBackdropPalette
        )
    }
    val colors = rememberFullPlayerColors(colorPalette)

    val title = currentTrack.mediaMetadata.title?.toString() ?: ""
    val artist = currentTrack.mediaMetadata.artist?.toString() ?: ""
    val showPlaybackQuality by viewModel.showPlaybackQuality.collectAsStateWithLifecycle()
    val playbackLevel by viewModel.playerManager.playbackLevel.collectAsStateWithLifecycle()
    // 档位按 songId 绑定，切歌瞬间旧歌的档位不会串到新歌上
    val qualityLabel = if (showPlaybackQuality && !podcast.isPodcast) {
        playbackLevel
            ?.takeIf { it.songId.toString() == currentTrack.mediaId }
            ?.let { getQualityBadgeLabel(it.level) }
    } else null
    val rawCoverUrl = normalizeFullPlayerCoverUrl(
        currentTrack.mediaMetadata.artworkUri?.toString().orEmpty()
    )
    val fallbackCoverUrl = currentQueueItem?.let {
        normalizeFullPlayerCoverUrl(rememberQueueItemCoverUrl(it.coverUrl, it.songId, it.localUri))
    }.orEmpty()
    val coverUrl = rawCoverUrl.ifBlank { fallbackCoverUrl }
    // 预览封面解析
    val previousCoverUrl = previousQueueItem?.let {
        normalizeFullPlayerCoverUrl(rememberQueueItemCoverUrl(it.coverUrl, it.songId, it.localUri))
    }
    val nextCoverUrl = nextQueueItem?.let {
        normalizeFullPlayerCoverUrl(rememberQueueItemCoverUrl(it.coverUrl, it.songId, it.localUri))
    }

    // 取色跟封面显示解码完全脱钩，单独发一次固定尺寸的请求；放在宿主里，竖排与宽屏两栏排版共用一份结果
    LaunchedEffect(currentTrack.mediaId, coverUrl) {
        val cached = PaletteMemoryCache.get(currentTrack.mediaId)
        if (cached != null) {
            colorPalette = cached
        } else if (coverUrl.isNotEmpty()) {
            val palette = extractBackdropPaletteFromUrl(context, coverUrl)
            colorPalette = palette
            PaletteMemoryCache.put(currentTrack.mediaId, palette)
        }
    }

    // 歌名/歌手要跟封面一起冻结：队列已经先切过去、currentTrack 还没跟上时，
    // 直接用实时值会让封面下方的文字在滑动/切歌过程中先于封面硬跳
    val previousKeyStr = previousQueueItem?.songId?.toString()
    val nextKeyStr = nextQueueItem?.songId?.toString()
    var displayedTitle by remember { mutableStateOf(title) }
    var displayedArtist by remember { mutableStateOf(artist) }
    if ((previousKeyStr != null && previousKeyStr == currentTrack.mediaId) ||
        (nextKeyStr != null && nextKeyStr == currentTrack.mediaId)
    ) {
        // 队列已经先切过去，曲目数据还没跟上，先保留原文字，等 currentTrack 落地后再刷新
    } else {
        displayedTitle = title
        displayedArtist = artist
    }

    // 未匹配云端的本地歌曲只有负数占位 id，没有可复制的链接
    val songLink = currentTrack.mediaId.toLongOrNull()
        ?.takeIf { it > 0 && !songDetailState.isLocalOnly && !podcast.isPodcast }
        ?.let { "https://music.163.com/song?id=$it" }

    // 分享卡片不依赖云端 id，本地歌曲同样可用
    fun shareCurrentSong() {
        showMoreOptionsSheet = false
        showShareCard = true
    }

    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val lyricsCoverage = remember { LyricsOverlayCoverage() }
    val scrollMetrics = rememberFullPlayerScrollMetrics(listState)
    val density = LocalDensity.current
    val navigationBarBottomPadding = melodiaNavigationBarBottomPadding()
    val pluginLayouts = LocalPlayerLayouts.current
    val pluginFullLayout = pluginLayouts[PlayerLayoutArea.ANDROID_FULL_PLAYER]
    val pluginWideLayout = pluginLayouts[PlayerLayoutArea.ANDROID_FULL_PLAYER_WIDE]
    val coverFitInsetPx = rememberCoverFitInsetPx(
        listState = listState,
        // 插件布局的首屏高度固定为一屏，不存在需要缩封面的情况
        enabled = pluginFullLayout == null && (fitCoverToViewport || fitCoverAboveNavigationBar),
        bottomReservePx = if (fitCoverAboveNavigationBar) with(density) { navigationBarBottomPadding.toPx() } else 0f
    )
    val coverExtraInset = with(density) { coverFitInsetPx.toDp() }
    val allCardsHidden = cardLayout.none { it.visible }
    val fillTail = MelodiaSpacing.md + navigationBarBottomPadding
    val fillGap = rememberFullPlayerFillGap(
        listState = listState,
        enabled = allCardsHidden,
        gapCount = FullPlayerFillGapCount,
        contentKeys = if (showMiniLyric) FullPlayerPlaybackItemKeys else FullPlayerPlaybackItemKeysWithoutMiniLyric,
        bottomReserve = fillTail
    )
    val animatedCoverPadding by animateDpAsState(
        targetValue = if (showMiniLyric) MelodiaSpacing.lg else 13.dp,
        animationSpec = uiTween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "cover_padding"
    )
    val animatedItemGap by animateDpAsState(
        targetValue = if (showMiniLyric) 0.dp else 3.dp,
        animationSpec = uiTween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "item_gap"
    )

    val scrollToLyricsCard: () -> Unit = {
        val visible = visibleInfoCards(songDetailState, commentsState, cardLayout)
        val lyricCardIndex = visible.cards.indexOf(FullPlayerCard.LYRICS)
        if (lyricCardIndex >= 0) {
            val fallbackIndex = 6 + lyricCardIndex
            coroutineScope.launch {
                val layoutInfo = listState.layoutInfo
                val visibleItem = layoutInfo.visibleItemsInfo.find { it.key == FullPlayerCard.LYRICS.key }
                val targetIndex = visibleItem?.index ?: fallbackIndex
                val cardHeightPx = visibleItem?.size ?: with(density) { 300.dp.roundToPx() }
                val bottomReservePx = with(density) { (80.dp + navigationBarBottomPadding).roundToPx() }
                val desiredTopPx = (layoutInfo.viewportSize.height - cardHeightPx - bottomReservePx).coerceAtLeast(0)

                if (visibleItem != null) {
                    val deltaToScroll = (visibleItem.offset - desiredTopPx).toFloat()
                    if (kotlin.math.abs(deltaToScroll) > 1f) {
                        motion.scrollBy(listState, deltaToScroll)
                    }
                } else {
                    motion.scrollToItem(
                        listState,
                        index = targetIndex,
                        scrollOffset = -desiredTopPx
                    )
                }
            }
        }
    }

    val isLandscape = rememberMelodiaOrientationClass() == MelodiaOrientationClass.Landscape
    val isPanelAtStart = LocalMelodiaPlayerPanelAtStart.current
    val fullscreenProgress: () -> Float = { sidebarFullscreenProgress?.invoke()?.coerceIn(0f, 1f) ?: 0f }
    val canUseWideLayout = sidebarFullscreenProgress != null && isLandscape && fullscreenContentWidth.isSpecified
    val showColumnLayout by remember(canUseWideLayout) {
        derivedStateOf { !canUseWideLayout || fullscreenProgress() < WideCrossfadeSplit }
    }
    val showWideLayout by remember(canUseWideLayout) {
        derivedStateOf { canUseWideLayout && fullscreenProgress() > WideCrossfadeSplit }
    }
    // 竖屏全屏：铺开到位后竖排列表放宽到卡片宽度的一定比例并居中，这一帧重排用一次轻微淡入盖住
    val fullscreenColumnMaxWidth = if (fullscreenContentWidth.isSpecified) {
        fullscreenContentWidth * PortraitFullscreenColumnFraction
    } else {
        Dp.Unspecified
    }
    val isColumnFullscreenWidth by remember(sidebarFullscreenProgress, isLandscape) {
        derivedStateOf { sidebarFullscreenProgress != null && !isLandscape && fullscreenProgress() >= 1f }
    }
    val columnReflowAlpha = remember { Animatable(1f) }
    var hasSettledColumnWidth by remember { mutableStateOf(false) }
    LaunchedEffect(isColumnFullscreenWidth) {
        if (hasSettledColumnWidth) {
            columnReflowAlpha.snapTo(PanelReflowFadeFromAlpha)
            columnReflowAlpha.animateTo(1f, motion.spec(PanelReflowFadeSpec))
        }
        hasSettledColumnWidth = true
    }

    // 手势状态同时被内联逻辑和嵌套滚动连接读写，持有 MutableState 本体便于透传
    val offsetYState = remember { mutableStateOf(0f) }
    var offsetY by offsetYState
    val isScrollGestureActiveState = remember { mutableStateOf(false) }
    var isScrollGestureActive by isScrollGestureActiveState
    val isGestureStartedAtTopState = remember { mutableStateOf(true) }
    var isGestureStartedAtTop by isGestureStartedAtTopState

    LaunchedEffect(isPlayerOpen) {
        if (isPlayerOpen) {
            offsetY = 0f
            isScrollGestureActive = false
            isGestureStartedAtTop = true
            isLyricsFullScreen = false
        }
    }

    var screenHeightPx by remember { mutableStateOf(0f) }
    val topCornerRadius by remember {
        derivedStateOf {
            if (offsetY > 0f) 24.dp else 0.dp
        }
    }
    var dragReleaseJob by remember { mutableStateOf<Job?>(null) }

    // 松手后决定关闭播放器还是回弹，从列表中途开始的手势要求更严
    fun handleDragRelease(velocity: Float = 0f) {
        dragReleaseJob?.cancel()
        dragReleaseJob = coroutineScope.launch {
            val shouldClose = if (isGestureStartedAtTop) {
                offsetY > screenHeightPx * 0.10f || velocity > 450f
            } else {
                offsetY > screenHeightPx * 0.20f
            }

            if (offsetY > 0f && shouldClose) {
                val finalOffset = offsetY
                offsetY = 0f
                onDragClose(finalOffset, velocity)
            } else {
                animate(
                    initialValue = offsetY,
                    targetValue = 0f,
                    initialVelocity = velocity,
                    animationSpec = motion.spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { value, _ ->
                    offsetY = value.coerceAtLeast(0f)
                }
            }
        }
    }

    val pluginParts = if (pluginFullLayout != null || pluginWideLayout != null) {
        FullPlayerLayoutParts(
            title = displayedTitle,
            artist = displayedArtist,
            songState = songDetailState,
            podcast = podcast,
            colors = colors,
            coverUrl = coverUrl,
            previousCoverUrl = previousCoverUrl,
            nextCoverUrl = nextCoverUrl,
            currentKey = currentTrack.mediaId,
            previousKey = previousQueueItem?.songId?.toString(),
            nextKey = nextQueueItem?.songId?.toString(),
            currentLyricIndex = currentLyricIndex,
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            playMode = playMode,
            animateLyricsMesh = isPlaying && !isLyricsFullScreen,
            connectedDevice = connectedDevice,
            qualityLabel = qualityLabel,
            onToggleLike = viewModel::toggleLike,
            onToggleSubscribe = viewModel::toggleSubscribe,
            onArtistClick = openSongArtist,
            onAlbumClick = openSongAlbum,
            onMiniLyricClick = scrollToLyricsCard,
            onSeek = viewModel::seekToTime,
            onTogglePlay = onTogglePlay,
            onPlayNext = viewModel.playerManager::playNext,
            onPlayPrevious = viewModel.playerManager::playPrevious,
            onCancelSkip = viewModel.playerManager::cancelPendingSkip,
            onToggleShuffle = viewModel.playerManager::toggleShuffle,
            onToggleRepeat = viewModel.playerManager::toggleRepeat,
            onOutputDeviceClick = { showOutputDeviceSheet = true },
            onQueueClick = { showQueueSheet = true },
            onShareClick = { shareCurrentSong() },
            onMoreClick = { showMoreOptionsSheet = true },
            onOpenFullScreenLyrics = { isLyricsFullScreen = true }
        )
    } else {
        null
    }

    val nestedScrollConnection = rememberFullPlayerNestedScrollConnection(
        listState = listState,
        offsetYState = offsetYState,
        isScrollGestureActiveState = isScrollGestureActiveState,
        isGestureStartedAtTopState = isGestureStartedAtTopState,
        onDragRelease = { handleDragRelease(velocity = it) }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenHeightPx = it.height.toFloat() }
            .nestedScroll(nestedScrollConnection)
            .graphicsLayer {
                translationY = offsetY
            }
            .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
            .then(
                if (isCoverBackdrop(CoverGroup.PLAYER)) {
                    Modifier.playerBackdrop(MaterialTheme.colorScheme.background)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.background)
                }
            )
    ) {
        PlayerBackdrop(
            base = colors.base,
            mode = BackdropMode.Collapsed,
            translationYProvider = { if (showColumnLayout) scrollMetrics.backgroundTranslationY else 0f }
        )

        // 竖排列表：手机、侧栏与竖屏全屏。横屏铺开时贴面板停靠侧固定不动、随进度淡出，让位给宽屏两栏
        if (showColumnLayout) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val crossfade = if (canUseWideLayout) {
                            (1f - fullscreenProgress() / WideCrossfadeSplit).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                        alpha = crossfade * columnReflowAlpha.value
                    }
            ) {
                // 列宽用左右内边距留出而不是收窄 LazyColumn 本身，否则两侧空白区不在可滚动范围内，
                // 手指落在那里既滚不动列表也触发不了下拉收起
                val columnMaxWidth = if (isColumnFullscreenWidth) fullscreenColumnMaxWidth else contentMaxWidth
                val sideSpace = if (columnMaxWidth.isSpecified) {
                    (maxWidth - columnMaxWidth).coerceAtLeast(0.dp)
                } else {
                    0.dp
                }
                // 宽屏铺开过程中竖排列表贴住面板停靠的一侧，不随卡片变宽而平移
                val startInset = when {
                    !canUseWideLayout -> sideSpace / 2
                    isPanelAtStart -> 0.dp
                    else -> sideSpace
                }
                val endInset = when {
                    !canUseWideLayout -> sideSpace / 2
                    isPanelAtStart -> sideSpace
                    else -> 0.dp
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .haze(hazeState)
                        // 全屏歌词完整盖住时整页不可见，跳过绘制，避免随进度刷新反复重绘并上传封面贴图
                        .drawWithContent { if (!lyricsCoverage.isCovering) drawContent() },
                    contentPadding = PaddingValues(
                        start = startInset,
                        end = endInset,
                        top = melodiaStatusBarTopPadding(),
                        bottom = (if (allCardsHidden) MelodiaSpacing.md else 80.dp) + melodiaNavigationBarBottomPadding()
                    )
                ) {
                    if (pluginFullLayout != null && pluginParts != null) {
                        fullPlayerPluginSection(pluginFullLayout, pluginParts) { barModifier ->
                            FullPlayerSourceBar(
                                playContext = playContext,
                                podcastRadioName = podcast.radioName,
                                onClose = onClose,
                                onMoreClick = { showMoreOptionsSheet = true },
                                onToggleSidebarFullscreen = onToggleSidebarFullscreen,
                                isSidebarFullscreen = isSidebarFullscreen,
                                onPlaylistClick = openPlaylistSource,
                                modifier = barModifier.padding(horizontal = MelodiaSpacing.lg)
                            )
                        }
                    } else fullPlayerPlaybackSection(
                        songState = songDetailState,
                        colors = colors,
                        coverUrl = coverUrl,
                        previousCoverUrl = previousCoverUrl,
                        nextCoverUrl = nextCoverUrl,
                        onSwipeToPrevious = viewModel.playerManager::skipToPrevious,
                        onCancelSwipe = viewModel.playerManager::cancelPendingSkip,
                        currentKey = currentTrack.mediaId,
                        previousKey = previousQueueItem?.songId?.toString(),
                        nextKey = nextQueueItem?.songId?.toString(),
                        coverExtraInset = coverExtraInset,
                        onToggleSidebarFullscreen = onToggleSidebarFullscreen,
                        isSidebarFullscreen = isSidebarFullscreen,
                        title = displayedTitle,
                        artist = displayedArtist,
                        playContext = playContext,
                        podcast = podcast,
                        onToggleSubscribe = viewModel::toggleSubscribe,
                        currentLyricIndex = currentLyricIndex,
                        isPlaying = isPlaying,
                        playWhenReady = playWhenReady,
                        currentPositionProvider = currentPositionProvider,
                        duration = duration,
                        playMode = playMode,
                        onClose = onClose,
                        onMoreClick = { showMoreOptionsSheet = true },
                        onToggleLike = viewModel::toggleLike,
                        onArtistClick = openSongArtist,
                        onAlbumClick = openSongAlbum,
                        onPlaylistClick = openPlaylistSource,
                        onMiniLyricClick = scrollToLyricsCard,
                        onSeek = viewModel::seekToTime,
                        onTogglePlay = onTogglePlay,
                        onPlayNext = viewModel.playerManager::playNext,
                        onPlayPrevious = viewModel.playerManager::playPrevious,
                        onToggleShuffle = viewModel.playerManager::toggleShuffle,
                        onToggleRepeat = viewModel.playerManager::toggleRepeat,
                        onDisableRoaming = { viewModel.playerManager.disableRoaming() },
                        onDisableIntelligence = { viewModel.playerManager.disableIntelligence() },
                        onOutputDeviceClick = { showOutputDeviceSheet = true },
                        onQueueClick = { showQueueSheet = true },
                        onShareClick = { shareCurrentSong() },
                        connectedDevice = connectedDevice,
                        qualityLabel = qualityLabel,
                        showMiniLyric = showMiniLyric,
                        baseCoverPadding = animatedCoverPadding,
                        itemGap = animatedItemGap,
                        fillGap = if (allCardsHidden) fillGap else null,
                        fillTail = fillTail
                    )

                    fullPlayerInfoSection(
                        songState = songDetailState,
                        colors = colors,
                        commentsState = commentsState,
                        currentLyricIndex = currentLyricIndex,
                        // 全屏歌词盖住播放页时歌词卡不可见，光斑无需游走
                        animateLyricsMesh = isPlaying && !isLyricsFullScreen,
                        // 插件布局已放了歌词卡时，下方列表不再重复
                        cardLayout = if (pluginFullLayout?.contains(LayoutNodeType.LYRICS_CARD) == true) {
                            cardLayout.filter { it.card != FullPlayerCard.LYRICS }
                        } else {
                            cardLayout
                        },
                        cardEntries = cardEntries,
                        onOpenFullScreenLyrics = { isLyricsFullScreen = true },
                        onCommentsClick = { showCommentsSheet = true },
                        onRetryComments = viewModel::retryComments,
                        onFollowArtistClick = { artistId -> viewModel.toggleArtistFollow(artistId) },
                        onArtistClick = onArtistClick,
                        onEditCardsClick = { showCardEditorSheet = true },
                        onAlbumClick = onAlbumClick,
                        onSelectArtist = { index -> viewModel.selectArtist(index) }
                    )
                }

                FullPlayerTopBar(
                    onClose = onClose,
                    title = title,
                    artist = artist,
                    showTitle = scrollMetrics.showTitleInBar,
                    isPlaying = isPlaying,
                    onTogglePlay = onTogglePlay,
                    isLiked = if (podcast.isPodcast) podcast.subscribed else songDetailState.isLiked,
                    onToggleLike = if (podcast.isPodcast) viewModel::toggleSubscribe else viewModel::toggleLike,
                    backgroundColor = themedCoverBase(colors.base),
                    currentPositionProvider = currentPositionProvider,
                    duration = duration,
                    onArtistClick = openSongArtist,
                    modifier = Modifier.draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            offsetY = (offsetY + delta).coerceAtLeast(0f)
                        },
                        onDragStarted = {
                            isGestureStartedAtTop = true
                        },
                        onDragStopped = { velocity ->
                            handleDragRelease(velocity = velocity)
                        }
                    )
                )
            }
        }

        // 宽屏两栏：按全屏最终宽度排一次、贴面板停靠侧放置，铺开过程中卡片另一侧边缘逐步露出，同时淡入
        if (showWideLayout) {
            val wideLayoutModifier = Modifier
                .align(if (isPanelAtStart) Alignment.TopStart else Alignment.TopEnd)
                .wrapContentWidth(align = if (isPanelAtStart) Alignment.Start else Alignment.End, unbounded = true)
                .width(fullscreenContentWidth)
                .padding(
                    top = melodiaStatusBarTopPadding(),
                    bottom = melodiaNavigationBarBottomPadding()
                )
                .graphicsLayer {
                    alpha = ((fullscreenProgress() - WideCrossfadeSplit) / (1f - WideCrossfadeSplit))
                        .coerceIn(0f, 1f)
                }

            FullPlayerWideLayout(
                sourceBar = { barModifier ->
                    FullPlayerSourceBar(
                        playContext = playContext,
                        podcastRadioName = podcast.radioName,
                        onClose = onClose,
                        onMoreClick = { showMoreOptionsSheet = true },
                        onToggleSidebarFullscreen = onToggleSidebarFullscreen,
                        isSidebarFullscreen = isSidebarFullscreen,
                        onFullscreenLyricsClick = { isLyricsFullScreen = true },
                        onPlaylistClick = openPlaylistSource,
                        modifier = barModifier
                    )
                },
                cover = { coverModifier ->
                    SwipeToSkipCover(
                        coverUrl = coverUrl,
                        previousCoverUrl = previousCoverUrl,
                        nextCoverUrl = nextCoverUrl,
                        onConfirmPrevious = viewModel.playerManager::skipToPrevious,
                        onConfirmNext = viewModel.playerManager::playNext,
                        currentKey = currentTrack.mediaId,
                        previousKey = previousQueueItem?.songId?.toString(),
                        nextKey = nextQueueItem?.songId?.toString(),
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(RadiusCompact),
                        elevation = 24.dp,
                        modifier = coverModifier,
                        onCancelPending = viewModel.playerManager::cancelPendingSkip
                    )
                },
                playbackControls = {
                    SongInfo(
                        title = displayedTitle,
                        artist = displayedArtist,
                        isLiked = songDetailState.isLiked,
                        onToggleLike = viewModel::toggleLike,
                        onArtistClick = openSongArtist,
                        onAlbumClick = openSongAlbum,
                        podcast = podcast,
                        onToggleSubscribe = viewModel::toggleSubscribe
                    )
                    ProgressSection(
                        currentPositionProvider = currentPositionProvider,
                        duration = if (duration > 0L) duration else (songDetail?.dt ?: 0L),
                        onSeek = viewModel::seekToTime,
                        chorusStartMs = songDetailState.chorusStartMs
                    )
                    PlaybackControls(
                        isPlaying = playWhenReady,
                        onTogglePlay = onTogglePlay,
                        onPlayNext = viewModel.playerManager::playNext,
                        onPlayPrevious = viewModel.playerManager::playPrevious,
                        onToggleShuffle = viewModel.playerManager::toggleShuffle,
                        onToggleRepeat = viewModel.playerManager::toggleRepeat,
                        playMode = playMode,
                        isRoaming = playContext == "similar_roaming",
                        onDisableRoaming = { viewModel.playerManager.disableRoaming() },
                        isIntelligence = playContext == "intelligence",
                        onDisableIntelligence = { viewModel.playerManager.disableIntelligence() },
                        showModeButtons = !podcast.isPodcast
                    )
                    ActionButtons(
                        onOutputDeviceClick = { showOutputDeviceSheet = true },
                        onQueueClick = { showQueueSheet = true },
                        onShareClick = { shareCurrentSong() },
                        connectedDevice = connectedDevice,
                        qualityLabel = qualityLabel,
                        showLyricsControls = !podcast.isPodcast,
                        secondaryMode = fullScreenLyricSecondaryMode,
                        hasTranslation = hasTranslation,
                        hasRoma = hasRoma,
                        onToggleSecondaryMode = handleToggleSecondaryMode,
                        onLyricsSettingsClick = { showLyricsSettingsSheet = true }
                    )
                },
                lyrics = songDetailState.lyrics,
                isLyricsLoading = songDetailState.isLyricsLoading,
                currentLyricIndex = currentLyricIndex,
                activeLyricIndices = activeLyricIndices,
                // 宽屏两栏的歌词直接铺在页面上，浅色下未唱到的行改用三级文字色
                highlightColor = if (MelodiaColors.IsDark) colors.textHighlight else MelodiaColors.TextTertiary,
                currentPositionProvider = lyricPositionProvider,
                isPlaying = isPlaying,
                onLyricClick = { line -> viewModel.seekToTime(line.timeMs) },
                onSeek = viewModel::seekToTime,
                onLyricsVisibleChange = { isWideLyricsVisible = it },
                pluginLeftColumn = if (pluginWideLayout != null && pluginParts != null) {
                    {
                        PlayerLayoutView(pluginWideLayout, Modifier.fillMaxSize()) { node, nodeModifier ->
                            pluginParts.Component(node, nodeModifier)
                        }
                    }
                } else {
                    null
                },
                infoCards = {
                    fullPlayerInfoGrid(
                        songState = songDetailState,
                        colors = colors,
                        commentsState = commentsState,
                        cardLayout = cardLayout,
                        cardEntries = cardEntries,
                        onCommentsClick = { showCommentsSheet = true },
                        onRetryComments = viewModel::retryComments,
                        onFollowArtistClick = { artistId -> viewModel.toggleArtistFollow(artistId) },
                        onArtistClick = onArtistClick,
                        onAlbumClick = onAlbumClick,
                        onSelectArtist = { index -> viewModel.selectArtist(index) },
                        onEditCardsClick = { showCardEditorSheet = true }
                    )
                },
                modifier = wideLayoutModifier
            )
        }

        FullPlayerLyricsOverlay(
            visible = isLyricsFullScreen,
            coverage = lyricsCoverage,
            songState = songDetailState,
            colors = colors,
            currentLyricIndex = currentLyricIndex,
            activeLyricIndices = activeLyricIndices,
            title = title,
            artist = artist,
            coverUrl = coverUrl,
            songLink = songLink,
            hazeState = hazeState,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            lyricPositionProvider = lyricPositionProvider,
            duration = duration,
            playMode = playMode,
            onSeek = { timeMs ->
                viewModel.seekToTime(timeMs)
            },
            onClose = { isLyricsFullScreen = false },
            onControlsVisibilityChange = onLyricsControlsVisibilityChange,
            onTogglePlay = onTogglePlay,
            onPlayNext = viewModel.playerManager::playNext,
            onPlayPrevious = viewModel.playerManager::playPrevious,
            onToggleShuffle = viewModel.playerManager::toggleShuffle,
            onToggleRepeat = viewModel.playerManager::toggleRepeat,
            onMoreClick = { showMoreOptionsSheet = true }
        )

        if (showShareCard) {
            LyricShareSheet(
                lyrics = songDetailState.lyrics,
                currentIndex = currentLyricIndex,
                title = title,
                artist = artist,
                coverUrl = coverUrl,
                durationMs = duration,
                songLink = songLink,
                initialKind = ShareCardKind.SONG,
                onDismiss = { showShareCard = false }
            )
        }

        if (artistPickerEntries.isNotEmpty()) {
            ArtistPickerSheet(
                artists = artistPickerEntries,
                onArtistClick = onArtistClick,
                onDismiss = { artistPickerEntries = emptyList() }
            )
        }

        FullPlayerSheets(
            songState = songDetailState,
            showQueueSheet = showQueueSheet,
            showMoreOptionsSheet = showMoreOptionsSheet,
            collectSongId = collectSongId,
            collectState = collectState,
            showTimerSheet = showTimerSheet,
            showOutputDeviceSheet = showOutputDeviceSheet,
            queue = queue,
            currentQueueIndex = currentQueueIndex,
            playMode = playMode,
            playContext = playContext,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            coverUrl = coverUrl,
            sleepTimerRemaining = sleepTimerRemaining,
            activeQuality = activeQuality,
            showMiniLyric = showMiniLyric,
            isPodcast = podcast.isPodcast,
            onToggleMiniLyric = viewModel::toggleMiniLyric,
            onPlayAtIndex = { viewModel.playerManager.playAtIndex(it) },
            onRemoveAtIndex = { viewModel.playerManager.removeFromQueue(it) },
            onMoveQueueItem = { from, to -> viewModel.playerManager.moveInQueue(from, to) },
            onToggleShuffle = viewModel.playerManager::toggleShuffle,
            onClearQueue = {
                viewModel.clearQueue()
                showQueueSheet = false
            },
            onDisableRoaming = { viewModel.playerManager.disableRoaming() },
            onDisableIntelligence = { viewModel.playerManager.disableIntelligence() },
            onQueueDismiss = { showQueueSheet = false },
            onToggleLike = viewModel::toggleLike,
            onAlbumClick = {
                showMoreOptionsSheet = false
                openSongAlbum()
            },
            onArtistClick = {
                showMoreOptionsSheet = false
                openSongArtist()
            },
            onShowTimerClick = {
                showTimerSheet = true
            },
            onQualitySelected = viewModel::updateQuality,
            onToggleIntelligence = { checked ->
                if (checked) {
                    val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                    viewModel.startIntelligenceMode(songId, title, artist, coverUrl)
                } else {
                    viewModel.playerManager.disableIntelligence()
                }
            },
            onStartSimilarRoaming = {
                val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                viewModel.startSimilarSongsRoaming(songId, title, artist, coverUrl)
            },
            onInsertSimilarSongs = {
                val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                viewModel.insertSimilarSongs(songId)
            },
            onCollectClick = {
                val songId = currentTrack.mediaId.toLongOrNull()
                if (songId != null) {
                    collectSongId = songId
                    viewModel.prepareCollectDialog(songId)
                }
            },
            onShareClick = { shareCurrentSong() },
            onDownloadClick = { showDownloadQualitySheet = true },
            onSaveCollection = { songId, items -> viewModel.savePlaylistCollection(songId, items) },
            onSaveNewCollection = { name, songId -> viewModel.createPlaylistAndAddSong(name, songId) },
            onCollectDismiss = { collectSongId = null },
            onMoreOptionsDismiss = { showMoreOptionsSheet = false },
            onSetTimer = { minutes ->
                viewModel.setSleepTimer(minutes)
                showTimerSheet = false
            },
            onTimerDismiss = { showTimerSheet = false },
            onOutputDeviceSelected = { deviceId -> playerManager.setPreferredAudioDevice(deviceId) },
            onOutputDeviceDismiss = { showOutputDeviceSheet = false }
        )

        AnimatedVisibility(
            visible = showCommentsSheet,
            enter = slideInVertically(uiTween(ScreenSlideDurationMs)) { it } + fadeIn(uiTween(ScreenSlideDurationMs)),
            exit = slideOutVertically(uiTween(ScreenSlideDurationMs)) { it } + fadeOut(uiTween(ScreenSlideDurationMs))
        ) {
            CommentFullScreen(
                commentsState = commentsState,
                currentUserId = userProfile?.uid,
                composerState = composerState,
                onBack = { showCommentsSheet = false },
                onSortChange = viewModel::changeCommentSort,
                onLikeComment = viewModel::likeComment,
                onUserClick = { uid ->
                    showCommentsSheet = false
                    onNavigateToProfile(uid)
                },
                onExpandFloor = { comment ->
                    showCommentFloor = true
                    viewModel.openCommentFloor(comment)
                },
                onDeleteClick = { comment -> viewModel.deleteCommentItem(comment) },
                onSubmitComment = { content, target ->
                    if (target != null) {
                        viewModel.submitCommentReply(target.commentId, content)
                    } else {
                        viewModel.submitComment(content)
                    }
                },
                onRequireLogin = { ToastManager.showToast("请先登录账号") },
                onLoadMore = viewModel::loadMoreComments,
                onRetry = { viewModel.retryComments() }
            )
        }

        AnimatedVisibility(
            visible = showCommentFloor,
            enter = slideInVertically(uiTween(ScreenSlideDurationMs)) { it } + fadeIn(uiTween(ScreenSlideDurationMs)),
            exit = slideOutVertically(uiTween(ScreenSlideDurationMs)) { it } + fadeOut(uiTween(ScreenSlideDurationMs))
        ) {
            CommentFloorScreen(
                floorState = floorState,
                currentUserId = userProfile?.uid,
                composerState = composerState,
                onBack = { showCommentFloor = false },
                onLoadMore = viewModel::loadMoreCommentFloor,
                onSubmitReply = { parentCommentId, content ->
                    viewModel.submitCommentReply(parentCommentId, content)
                },
                onRequireLogin = { ToastManager.showToast("请先登录账号") },
                onDeleteClick = { comment -> viewModel.deleteCommentItem(comment) },
                onLikeClick = { comment -> viewModel.likeComment(comment) },
                onRetry = { (floorState as? CommentFloorState.Success)?.ownerComment?.let(viewModel::openCommentFloor) },
                onUserClick = onNavigateToProfile
            )
        }

        if (showDownloadQualitySheet) {
            DownloadQualityPickerSheet(
                songId = currentTrack.mediaId.toLongOrNull(),
                maxDownloadLevel = songDetail?.privilege?.dlLevel,
                onQualitySelected = { level ->
                    val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                    val artistNames = songDetail?.ar?.joinToString("/") { it.name }?.takeIf { it.isNotBlank() } ?: artist
                    val albumName = songDetail?.al?.name ?: ""
                    val albumYear = com.lin0721.linmusic.core.download.yearFromEpochMillis(songDetail?.publishTime ?: 0)
                    viewModel.downloadCurrentSong(songId, title, artistNames, albumName, coverUrl, albumYear, level)
                    showDownloadQualitySheet = false
                },
                onDismiss = { showDownloadQualitySheet = false },
                headline = title,
                supportingText = artist
            )
        }

        if (showCardEditorSheet) {
            FullPlayerCardEditorSheet(
                layout = cardEntries,
                onLayoutChange = { entries -> coroutineScope.launch { pluginCards?.source?.save(entries) } },
                onDismiss = { showCardEditorSheet = false }
            )
        }

        if (showLyricsSettingsSheet) {
            FullScreenLyricsSettingsSheet(
                fontSize = fullScreenLyricTextSize,
                onFontSizeChange = { size ->
                    coroutineScope.launch { settingsPreferences.saveFullScreenLyricTextSize(size) }
                },
                lineSpacing = fullScreenLyricLineSpacing,
                onLineSpacingChange = { spacing ->
                    coroutineScope.launch { settingsPreferences.saveFullScreenLyricLineSpacing(spacing) }
                },
                secondarySpacing = fullScreenLyricSecondarySpacing,
                onSecondarySpacingChange = { spacing ->
                    coroutineScope.launch { settingsPreferences.saveFullScreenLyricSecondarySpacing(spacing) }
                },
                alignment = fullScreenLyricAlignment,
                onAlignmentChange = { align ->
                    coroutineScope.launch { settingsPreferences.saveFullScreenLyricAlignment(align) }
                },
                secondaryMode = fullScreenLyricSecondaryMode,
                onSecondaryModeChange = { mode ->
                    coroutineScope.launch { settingsPreferences.saveFullScreenLyricSecondaryMode(mode) }
                },
                hasTranslation = hasTranslation,
                hasRoma = hasRoma,
                advancedKaraokeEffect = fullScreenKaraokeAdvancedEffect,
                onAdvancedKaraokeEffectChange = { enabled ->
                    coroutineScope.launch { settingsPreferences.saveFullScreenKaraokeAdvancedEffect(enabled) }
                },
                karaokeGlowEffect = fullScreenKaraokeGlowEffect,
                onKaraokeGlowEffectChange = { enabled ->
                    coroutineScope.launch { settingsPreferences.saveFullScreenKaraokeGlowEffect(enabled) }
                },
                neteaseLyricsEnabled = neteaseLyricsEnabled,
                onNeteaseLyricsEnabledChange = { enabled ->
                    coroutineScope.launch { settingsPreferences.saveNeteaseLyricsEnabled(enabled) }
                },
                qqLyricsEnabled = qqLyricsEnabled,
                onQqLyricsEnabledChange = { enabled ->
                    coroutineScope.launch { settingsPreferences.saveQqLyricsEnabled(enabled) }
                },
                amllLyricsEnabled = amllLyricsEnabled,
                onAmllLyricsEnabledChange = { enabled ->
                    coroutineScope.launch { settingsPreferences.saveAmllLyricsEnabled(enabled) }
                },
                onDismiss = { showLyricsSettingsSheet = false }
            )
        }
    }
}
