package com.lin0721.linmusic.desktop.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.MenuScope
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.nowplaying.ScrollArrow
import com.lin0721.linmusic.desktop.ui.palette.FallbackCoverPalette
import com.lin0721.linmusic.desktop.ui.palette.extractCoverPaletteFromUrl
import com.lin0721.linmusic.desktop.ui.palette.themedCoverBackground
import com.lin0721.linmusic.desktop.ui.playlist.DAILY_RECOMMEND_ID
import com.lin0721.linmusic.desktop.ui.playlist.DAILY_RECOMMEND_NAME
import com.lin0721.linmusic.desktop.ui.podcast.PodcastTab
import com.lin0721.linmusic.desktop.ui.podcast.PodcastTabActions
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.feature.home.domain.HomeCard
import com.lin0721.linmusic.feature.home.domain.HomeShelf
import com.lin0721.linmusic.feature.home.ui.HomeFeedData
import com.lin0721.linmusic.feature.home.ui.HomeUiState
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.music.ui.MusicViewModel
import com.lin0721.linmusic.feature.newworks.ui.NewWorksViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastHomeViewModel
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.LocalDate

internal val CardWidth = 168.dp

// 距离底部还剩几项时提前拉下一页货架
private const val LOAD_MORE_THRESHOLD = 2

// 最近播放网格：条目数固定，列数随内容区宽度切换，格子不会被侧栏挤窄
private const val RECENT_ITEM_COUNT = 8
private const val RECENT_MIN_COLUMNS = 2
private const val RECENT_MAX_COLUMNS = 4
private val RecentTileMinWidth = 200.dp
private val RecentGridGap = 8.dp
private val RecentGridPadding = 24.dp
// 格子高度（即封面边长）与字号随格子宽度在区间内线性变化，宽屏下不显得扁长
private val RecentTileMinHeight = 48.dp
private val RecentTileMaxHeight = 72.dp
private val RecentTileScaleStartWidth = 200.dp
private val RecentTileScaleEndWidth = 420.dp
private const val RECENT_TITLE_MIN_SP = 13f
private const val RECENT_TITLE_MAX_SP = 16f

private val TileRadius = 6.dp
private const val HOVER_FADE_MS = 150

// 卡片外宽仍是 CardWidth，内边距让出悬停衬底
private val CardInnerPadding = 8.dp
private val CardCoverSize = CardWidth - CardInnerPadding * 2
private val ShelfCardGap = 8.dp

// 抵消卡片内边距，让封面左缘与标题对齐在 24dp
private val ShelfContentPadding = 24.dp - CardInnerPadding

private val SpotlightCoverSize = 96.dp

// 封面取色后再压暗，保证白字在任意封面色上都看得清
private const val SPOTLIGHT_DARKEN_FRACTION = 0.35f
private const val SPOTLIGHT_TRACK_PREVIEW_COUNT = 8

// 顶部渐变参数：主色压暗比例、高度与渐变色彩过渡时间
private const val HOME_GRADIENT_DARKEN_FRACTION = 0.35f
private val HomeGradientHeight = 300.dp
private const val HOME_GRADIENT_ANIM_MS = 350

// 顶部固定「全部 / 音乐 / 播客」胶囊，下方按选中项切换内容；各 tab 的数据切过去才拉，
// 滚动位置在首页内按 tab 各自保留
@Composable
fun HomePage(
    viewModel: HomeViewModel,
    musicViewModel: MusicViewModel,
    podcastViewModel: PodcastHomeViewModel,
    newWorksViewModel: NewWorksViewModel,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    newWorksSelected: Boolean,
    onNewWorksSelectedChange: (Boolean) -> Unit,
    onPlaylistClick: (id: Long, title: String) -> Unit,
    onStyleClick: (id: Long, name: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val homeUiState by viewModel.uiState.collectAsState()
    val musicState by musicViewModel.uiState.collectAsState()
    val podcastState by podcastViewModel.state.collectAsState()
    val newWorksState by newWorksViewModel.uiState.collectAsState()
    val newWorksCollectState by newWorksViewModel.collectState.collectAsState()
    val newWorksImportState by newWorksViewModel.importState.collectAsState()
    val newWorksStatus by newWorksViewModel.releaseStatus.collectAsState()
    val allListState = rememberLazyListState()
    val musicListState = rememberLazyListState()
    val podcastListState = rememberLazyListState()
    val newWorksGridState = rememberLazyGridState()

    val defaultCoverUrl = (homeUiState as? HomeUiState.Success)?.data?.recentPlaylists?.firstOrNull()?.coverUrl.orEmpty()
    var hoveredCoverUrl by remember { mutableStateOf<String?>(null) }
    val activeCoverUrl = hoveredCoverUrl ?: defaultCoverUrl

    var heroBase by remember { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(activeCoverUrl) {
        if (activeCoverUrl.isNotBlank()) {
            heroBase = extractCoverPaletteFromUrl(activeCoverUrl).base
        }
    }
    val gradientColor by animateColorAsState(
        targetValue = if (activeCoverUrl.isNotBlank()) {
            themedCoverBackground(heroBase, HOME_GRADIENT_DARKEN_FRACTION)
        } else {
            DesktopColors.PageFill
        },
        animationSpec = uiTween(HOME_GRADIENT_ANIM_MS),
        label = "homeTopGradient"
    )

    val currentScrollOffset by remember(selectedTab, newWorksSelected) {
        derivedStateOf {
            when {
                selectedTab == HOME_TAB_MUSIC && newWorksSelected -> {
                    if (newWorksGridState.firstVisibleItemIndex == 0) newWorksGridState.firstVisibleItemScrollOffset.toFloat()
                    else 10000f
                }
                selectedTab == HOME_TAB_MUSIC -> {
                    if (musicListState.firstVisibleItemIndex == 0) musicListState.firstVisibleItemScrollOffset.toFloat()
                    else 10000f
                }
                selectedTab == HOME_TAB_PODCAST -> {
                    if (podcastListState.firstVisibleItemIndex == 0) podcastListState.firstVisibleItemScrollOffset.toFloat()
                    else 10000f
                }
                else -> {
                    if (allListState.firstVisibleItemIndex == 0) allListState.firstVisibleItemScrollOffset.toFloat()
                    else 10000f
                }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            HOME_TAB_MUSIC -> musicViewModel.loadIfNeeded()
            HOME_TAB_PODCAST -> podcastViewModel.loadIfNeeded()
        }
    }
    LaunchedEffect(selectedTab, newWorksSelected) {
        if (selectedTab == HOME_TAB_MUSIC && newWorksSelected) newWorksViewModel.loadIfNeeded()
    }

    Column(modifier.fillMaxSize()) {
        HomeTabPills(
            selectedTab = selectedTab,
            onSelect = onTabSelected,
            newWorksSelected = newWorksSelected,
            onNewWorksSelect = { onNewWorksSelectedChange(true) },
            modifier = Modifier.fillMaxWidth().background(gradientColor)
        )
        Box(Modifier.weight(1f).clipToBounds()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HomeGradientHeight)
                    .graphicsLayer { translationY = -currentScrollOffset }
                    .background(Brush.verticalGradient(listOf(gradientColor, DesktopColors.PageFill)))
            )
                when {
                    selectedTab == HOME_TAB_MUSIC && newWorksSelected -> NewWorksTab(
                        uiState = newWorksState,
                        gridState = newWorksGridState,
                        collectState = newWorksCollectState,
                        importState = newWorksImportState,
                        status = newWorksStatus,
                        actions = remember(newWorksViewModel, navigator) {
                            NewWorksTabActions(
                                onAlbumClick = navigator.openAlbum,
                                onTogglePlay = newWorksViewModel::togglePlayRelease,
                                onToggleLibrary = newWorksViewModel::toggleInLibrary,
                                onAddToPlayNext = newWorksViewModel::addToPlayNext,
                                onPrepareCollect = newWorksViewModel::prepareCollectDialog,
                                onSaveCollection = newWorksViewModel::savePlaylistCollection,
                                onSaveNewCollection = newWorksViewModel::createPlaylistAndAddSong,
                                onPrepareImportTargets = newWorksViewModel::prepareImportTargets,
                                onAddToPlaylist = newWorksViewModel::addToPlaylist,
                                onCreatePlaylistAndAdd = newWorksViewModel::createPlaylistAndAdd,
                                onRetry = newWorksViewModel::load,
                                onLoadMore = newWorksViewModel::loadMore
                            )
                        }
                    )
                    selectedTab == HOME_TAB_MUSIC -> MusicTab(
                        uiState = musicState,
                        listState = musicListState,
                        onStyleClick = onStyleClick,
                        onRetry = musicViewModel::loadStyles
                    )
                    selectedTab == HOME_TAB_PODCAST -> PodcastTab(
                        state = podcastState,
                        listState = podcastListState,
                        actions = remember(podcastViewModel, navigator) {
                            PodcastTabActions(
                                onFilterSelect = podcastViewModel::selectFilter,
                                onResume = podcastViewModel::resume,
                                onPickPlay = podcastViewModel::playPicks,
                                onRadioClick = { navigator.openRadio(it.id) },
                                onOpenSubscribed = navigator.openPodcastSubscribed,
                                onOpenToplist = navigator.openPodcastToplist,
                                onOpenCategory = navigator.openPodcastCategory,
                                onLoginClick = navigator.openLogin,
                                onRetryAll = podcastViewModel::refresh,
                                onRetrySubscribed = podcastViewModel::retrySubscribed,
                                onRetryPicks = podcastViewModel::retryPicks,
                                onRetryCategoryGroups = podcastViewModel::retryCategoryGroups,
                                onRetryToplist = podcastViewModel::retryToplistRadios,
                                onRetryCategoryRadios = podcastViewModel::retryCategoryRadios
                            )
                        }
                    )
                    else -> HomeAllContent(
                        viewModel = viewModel,
                        listState = allListState,
                        onPlaylistClick = onPlaylistClick,
                        onTileHover = { url, isHovered ->
                            if (isHovered) {
                                hoveredCoverUrl = url
                            } else if (hoveredCoverUrl == url) {
                                hoveredCoverUrl = null
                            }
                        }
                    )
                }
            }
        }
    }

@Composable
internal fun HomeTabLoading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DesktopColors.Accent)
    }
}

@Composable
internal fun HomeTabError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = DesktopColors.TextGray)
            TextButton(onClick = onRetry) {
                Text("重试", color = DesktopColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun HomeAllContent(
    viewModel: HomeViewModel,
    listState: LazyListState,
    onPlaylistClick: (id: Long, title: String) -> Unit,
    onTileHover: (coverUrl: String, isHovered: Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    when (val state = uiState) {
        HomeUiState.Loading -> HomeTabLoading()
        is HomeUiState.Error -> HomeTabError(state.message, viewModel::refreshHomeData)
        is HomeUiState.Success -> HomeFeed(state.data, viewModel, listState, onPlaylistClick, onTileHover, Modifier)
    }
}

@Composable
private fun HomeFeed(
    data: HomeFeedData,
    viewModel: HomeViewModel,
    listState: LazyListState,
    onPlaylistClick: (id: Long, title: String) -> Unit,
    onTileHover: (coverUrl: String, isHovered: Boolean) -> Unit,
    modifier: Modifier
) {
    val shouldLoadMore by remember(data) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            data.hasMore && !data.isLoadingMore &&
                lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreShelves()
    }

    LookaheadScope {
        CompositionLocalProvider(LocalHomeLookaheadScope provides this) {
            HoverScrollbarBox(listState) {
                LazyColumn(
                    state = listState,
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    homeItems(data, viewModel, onPlaylistClick, onTileHover)
                }
            }
        }
    }
}

// 区块高度随列数变化时，下方区块平滑让位；只做位移，不加淡入淡出
@Composable
private fun LazyItemScope.reflow(): Modifier =
    if (LocalPaneResizing.current || !LocalUiMotion.current.enabled) Modifier
    else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)

private fun LazyListScope.homeItems(
    data: HomeFeedData,
    viewModel: HomeViewModel,
    onPlaylistClick: (id: Long, title: String) -> Unit,
    onTileHover: (coverUrl: String, isHovered: Boolean) -> Unit
) {
    if (data.recentPlaylists.isNotEmpty()) {
        item(key = "recent") {
            RecentGrid(
                data = data,
                viewModel = viewModel,
                modifier = reflow(),
                onClick = { id, title -> onPlaylistClick(id, title) },
                onPlay = { id -> viewModel.playCollection(id, isAlbum = false) },
                onTileHover = onTileHover
            )
        }
    }
    if (data.dailySongs.isNotEmpty()) {
        item(key = "daily") {
            Box(reflow()) {
                DailySpotlight(
                    songs = data.dailySongs,
                    onOpen = { onPlaylistClick(DAILY_RECOMMEND_ID, DAILY_RECOMMEND_NAME) },
                    onPlay = { viewModel.playDailySong() }
                )
            }
        }
    }
    // 服务端货架已含推荐歌单，仅在货架缺失时兜底展示
    if (data.shelves.isEmpty() && data.recommendPlaylists.isNotEmpty()) {
        item(key = "recommend") {
            Box(reflow()) {
                val navigator = LocalDesktopNavigator.current
                val subscribedPlaylistIds by viewModel.subscribedPlaylistIds.collectAsState()
                ShelfRow("推荐歌单", data.recommendPlaylists) { playlist ->
                    val isSubscribed = playlist.id in subscribedPlaylistIds
                    CardTile(
                        coverUrl = playlist.picUrl,
                        title = playlist.name,
                        caption = "",
                        typeLabel = "歌单",
                        onPlay = { viewModel.playCollection(playlist.id, isAlbum = false) },
                        contextMenuContent = { onDismiss ->
                            MenuItem("立即播放", icon = Icons.Rounded.PlayArrow, onClick = {
                                onDismiss()
                                viewModel.playCollection(playlist.id, isAlbum = false)
                            })
                            MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
                                onDismiss()
                                viewModel.addCollectionToPlayNext(playlist.id, isAlbum = false)
                            })
                            if (!isSubscribed) {
                                MenuItem("收藏歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = {
                                    onDismiss()
                                    if (navigator.isLoggedIn) {
                                        viewModel.subscribePlaylist(playlist.id)
                                    } else {
                                        navigator.showMessage("请先登录账号")
                                    }
                                })
                            }
                            MenuItem("下载", icon = Icons.Rounded.Download, onClick = {
                                onDismiss()
                                viewModel.downloadCollection(playlist.id, isAlbum = false, level = navigator.downloadLevel)
                            })
                            MenuItem("复制链接", icon = Icons.Rounded.Link, onClick = {
                                onDismiss()
                                val ok = copyLink("https://music.163.com/playlist?id=${playlist.id}")
                                navigator.showMessage(if (ok) "已复制链接" else "复制失败")
                            })
                        }
                    ) { onPlaylistClick(playlist.id, playlist.name) }
                }
            }
        }
    }
    items(data.shelves, key = { "shelf_${it.blockCode}_${it.title}" }) { shelf ->
        Box(reflow()) { ServerShelf(shelf, viewModel, onPlaylistClick) }
    }
    if (data.isLoadingMore) {
        item(key = "loading_more") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

private fun copyLink(url: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(
        StringSelection(url), null
    )
}.isSuccess

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentGrid(
    data: HomeFeedData,
    viewModel: HomeViewModel,
    modifier: Modifier,
    onClick: (Long, String) -> Unit,
    onPlay: (Long) -> Unit,
    onTileHover: (coverUrl: String, isHovered: Boolean) -> Unit
) {
    BoxWithConstraints(modifier) {
        val available = maxWidth - RecentGridPadding * 2
        val columns = ((available + RecentGridGap) / (RecentTileMinWidth + RecentGridGap)).toInt()
            .coerceIn(RECENT_MIN_COLUMNS, RECENT_MAX_COLUMNS)
        val tileWidth = (available - RecentGridGap * (columns - 1)) / columns
        val scale = ((tileWidth - RecentTileScaleStartWidth) / (RecentTileScaleEndWidth - RecentTileScaleStartWidth))
            .coerceIn(0f, 1f)
        val tileHeight = lerp(RecentTileMinHeight, RecentTileMaxHeight, scale)
        val titleSize = (RECENT_TITLE_MIN_SP + (RECENT_TITLE_MAX_SP - RECENT_TITLE_MIN_SP) * scale).sp
        Column(Modifier.padding(horizontal = RecentGridPadding), verticalArrangement = Arrangement.spacedBy(RecentGridGap)) {
            SectionTitle("最近播放", horizontalPadding = 0)
            // 列数变化时格子从旧位置平滑移到新位置，整体高度同步过渡
            FlowRow(
                modifier = Modifier.fillMaxWidth().then(
                    if (LocalPaneResizing.current) Modifier
                    else Modifier.animateContentSize(uiTween(LAYOUT_REFLOW_MS, easing = FastOutSlowInEasing))
                ),
                horizontalArrangement = Arrangement.spacedBy(RecentGridGap),
                verticalArrangement = Arrangement.spacedBy(RecentGridGap),
                maxItemsInEachRow = columns
            ) {
                val shown = data.recentPlaylists.take(RECENT_ITEM_COUNT)
                shown.forEach { playlist ->
                    key(playlist.id) {
                        RecentTile(
                            playlist = playlist,
                            viewModel = viewModel,
                            height = tileHeight,
                            titleSize = titleSize,
                            modifier = Modifier.weight(1f),
                            onClick = { onClick(playlist.id, playlist.name) },
                            onPlay = { onPlay(playlist.id) },
                            onHover = { isHovered -> onTileHover(playlist.coverUrl, isHovered) }
                        )
                    }
                }
                // 末行不足时补位，保持列宽一致
                repeat((columns - shown.size % columns) % columns) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RecentTile(
    playlist: RecentPlaylist,
    viewModel: HomeViewModel,
    height: Dp,
    titleSize: TextUnit,
    modifier: Modifier,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onHover: (Boolean) -> Unit = {}
) {
    val navigator = LocalDesktopNavigator.current
    val subscribedPlaylistIds by viewModel.subscribedPlaylistIds.collectAsState()
    val isSubscribed = playlist.id in subscribedPlaylistIds
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    LaunchedEffect(hovered) {
        onHover(hovered)
    }
    DisposableEffect(playlist.coverUrl) {
        onDispose {
            onHover(false)
        }
    }
    val density = LocalDensity.current
    var tileHeightPx by remember { mutableIntStateOf(0) }
    var contextMenuOffset by remember { mutableStateOf<DpOffset?>(null) }
    val background by animateColorAsState(
        if (hovered || contextMenuOffset != null) DesktopColors.Overlay.copy(alpha = 0.18f) else DesktopColors.Overlay.copy(alpha = 0.08f),
        uiTween(HOVER_FADE_MS),
        label = "recentTileBg"
    )
    Box(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().height(height).homeReflowBounds().clip(RoundedCornerShape(TileRadius))
                .background(background)
                .hoverable(interaction)
                .onSizeChanged { tileHeightPx = it.height }
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) {
                        val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                        contextMenuOffset = with(density) {
                            DpOffset(position.x.toDp(), (position.y - tileHeightPx).toDp())
                        }
                    }
                }
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 尺寸随拖动连续变化，固定请求尺寸避免反复换缩略图
            Cover(playlist.coverUrl, height, shape = RoundedCornerShape(0.dp), requestSize = RecentTileMaxHeight)
            Text(
                playlist.name,
                color = DesktopColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            HoverReveal(revealed = hovered, modifier = Modifier.padding(end = 12.dp)) {
                HomePlayButton(size = 32.dp, description = "播放${playlist.name}", onClick = onPlay)
            }
        }

        if (contextMenuOffset != null) {
            DesktopMenu(
                expanded = true,
                offset = contextMenuOffset ?: DpOffset.Zero,
                onDismiss = { contextMenuOffset = null }
            ) {
                MenuItem("立即播放", icon = Icons.Rounded.PlayArrow, onClick = {
                    contextMenuOffset = null
                    onPlay()
                })
                MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
                    contextMenuOffset = null
                    viewModel.addCollectionToPlayNext(playlist.id, isAlbum = false)
                })
                if (!isSubscribed) {
                    MenuItem("收藏歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = {
                        contextMenuOffset = null
                        if (navigator.isLoggedIn) {
                            viewModel.subscribePlaylist(playlist.id)
                        } else {
                            navigator.showMessage("请先登录账号")
                        }
                    })
                }
                MenuItem("下载", icon = Icons.Rounded.Download, onClick = {
                    contextMenuOffset = null
                    viewModel.downloadCollection(playlist.id, isAlbum = false, level = navigator.downloadLevel)
                })
                MenuItem("复制链接", icon = Icons.Rounded.Link, onClick = {
                    contextMenuOffset = null
                    val ok = copyLink("https://music.163.com/playlist?id=${playlist.id}")
                    navigator.showMessage(if (ok) "已复制链接" else "复制失败")
                })
            }
        }
    }
}

// 每日推荐聚焦卡：底色取自首曲封面并压暗。卡片进入每日推荐列表，播放钮直接起播
@Composable
private fun DailySpotlight(songs: List<DailySong>, onOpen: () -> Unit, onPlay: () -> Unit) {
    val coverUrl = songs.firstOrNull()?.al?.picUrl.orEmpty()
    var baseColor by remember { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(coverUrl) {
        if (coverUrl.isNotBlank()) baseColor = extractCoverPaletteFromUrl(coverUrl).base
    }
    val containerColor by animateColorAsState(
        themedCoverBackground(baseColor, SPOTLIGHT_DARKEN_FRACTION),
        uiSpring(),
        label = "dailySpotlightColor"
    )
    val today = remember { LocalDate.now() }
    val countColor = DesktopColors.TextPrimary
    val trackLine = remember(songs, countColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = countColor, fontWeight = FontWeight.Bold)) {
                append("${songs.size} 首")
            }
            songs.take(SPOTLIGHT_TRACK_PREVIEW_COUNT).forEach { song ->
                append(" · ")
                append(song.name)
            }
        }
    }

    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("今日为你推荐", horizontalPadding = 0)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(containerColor)
                .clickable(onClick = onOpen).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(coverUrl, SpotlightCoverSize, shape = RoundedCornerShape(4.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text("每日推荐", color = DesktopColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "根据你的口味 · ${today.monthValue}月${today.dayOfMonth}日",
                    color = DesktopColors.TextPrimary.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    trackLine,
                    color = DesktopColors.TextPrimary.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            HomePlayButton(
                size = 48.dp,
                description = "播放每日推荐",
                containerColor = DesktopColors.TextPrimary,
                iconTint = DesktopColors.InverseText,
                onClick = onPlay
            )
        }
    }
}

@Composable
private fun HomePlayButton(
    size: Dp,
    description: String,
    containerColor: Color = DesktopColors.Accent,
    iconTint: Color = DesktopColors.TextPrimary,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(containerColor).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.PlayArrow, description, tint = iconTint, modifier = Modifier.size(size * 0.6f))
    }
}

private fun HomeCard.typeLabel(): String = when (this) {
    is HomeCard.Playlist -> "歌单"
    is HomeCard.Album -> "专辑"
    is HomeCard.Song -> "单曲"
    is HomeCard.Voice -> "播客"
}

@Composable
private fun ServerShelf(
    shelf: HomeShelf,
    viewModel: HomeViewModel,
    onPlaylistClick: (Long, String) -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val subscribedPlaylistIds by viewModel.subscribedPlaylistIds.collectAsState()
    val songs = shelf.cards.filterIsInstance<HomeCard.Song>()
    val voices = shelf.cards.filterIsInstance<HomeCard.Voice>()
    ShelfRow(shelf.title, shelf.cards.withIndex().toList()) { (index, card) ->
        val play: () -> Unit = {
            when (card) {
                is HomeCard.Playlist -> viewModel.playCollection(card.id, isAlbum = false)
                is HomeCard.Album -> viewModel.playCollection(card.id, isAlbum = true)
                is HomeCard.Song -> viewModel.playShelfSong(shelf.title, songs, card)
                is HomeCard.Voice -> viewModel.playShelfVoice(shelf.title, voices, card)
            }
        }
        val contextMenuContent: (@Composable MenuScope.(onDismiss: () -> Unit) -> Unit) = { onDismiss ->
            when (card) {
                is HomeCard.Playlist -> {
                    val isSubscribed = card.id in subscribedPlaylistIds
                    MenuItem("立即播放", icon = Icons.Rounded.PlayArrow, onClick = {
                        onDismiss()
                        play()
                    })
                    MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
                        onDismiss()
                        viewModel.addCollectionToPlayNext(card.id, isAlbum = false)
                    })
                    if (!isSubscribed) {
                        MenuItem("收藏歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = {
                            onDismiss()
                            if (navigator.isLoggedIn) {
                                viewModel.subscribePlaylist(card.id)
                            } else {
                                navigator.showMessage("请先登录账号")
                            }
                        })
                    }
                    MenuItem("下载", icon = Icons.Rounded.Download, onClick = {
                        onDismiss()
                        viewModel.downloadCollection(card.id, isAlbum = false, level = navigator.downloadLevel)
                    })
                    MenuItem("复制链接", icon = Icons.Rounded.Link, onClick = {
                        onDismiss()
                        val ok = copyLink("https://music.163.com/playlist?id=${card.id}")
                        navigator.showMessage(if (ok) "已复制链接" else "复制失败")
                    })
                }
                is HomeCard.Album -> {
                    MenuItem("立即播放", icon = Icons.Rounded.PlayArrow, onClick = {
                        onDismiss()
                        play()
                    })
                    MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
                        onDismiss()
                        viewModel.addCollectionToPlayNext(card.id, isAlbum = true)
                    })
                    MenuItem("下载", icon = Icons.Rounded.Download, onClick = {
                        onDismiss()
                        viewModel.downloadCollection(card.id, isAlbum = true, level = navigator.downloadLevel)
                    })
                    MenuItem("复制链接", icon = Icons.Rounded.Link, onClick = {
                        onDismiss()
                        val ok = copyLink("https://music.163.com/album?id=${card.id}")
                        navigator.showMessage(if (ok) "已复制链接" else "复制失败")
                    })
                }
                is HomeCard.Song -> {
                    MenuItem("立即播放", icon = Icons.Rounded.PlayArrow, onClick = {
                        onDismiss()
                        play()
                    })
                    MenuItem("下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
                        onDismiss()
                        viewModel.addShelfSongToPlayNext(card)
                    })
                    MenuItem("复制歌曲链接", icon = Icons.Rounded.Link, onClick = {
                        onDismiss()
                        val ok = copyLink("https://music.163.com/song?id=${card.id}")
                        navigator.showMessage(if (ok) "已复制链接" else "复制失败")
                    })
                }
                is HomeCard.Voice -> {
                    MenuItem("立即播放", icon = Icons.Rounded.PlayArrow, onClick = {
                        onDismiss()
                        play()
                    })
                    MenuItem("下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
                        onDismiss()
                        viewModel.addShelfVoiceToPlayNext(card)
                    })
                    MenuItem("复制节目链接", icon = Icons.Rounded.Link, onClick = {
                        onDismiss()
                        val ok = copyLink("https://music.163.com/program?id=${card.songId}")
                        navigator.showMessage(if (ok) "已复制链接" else "复制失败")
                    })
                }
            }
        }
        CardTile(
            coverUrl = card.coverUrl,
            title = card.title,
            caption = card.caption,
            typeLabel = if (shelf.showRank) "${card.typeLabel()} · ${index + 1}" else card.typeLabel(),
            onPlay = play,
            contextMenuContent = contextMenuContent
        ) {
            when (card) {
                is HomeCard.Playlist -> onPlaylistClick(card.id, card.title)
                is HomeCard.Album -> navigator.openAlbum(card.id, card.title)
                is HomeCard.Song, is HomeCard.Voice -> play()
            }
        }
    }
}

// 横向货架：悬停整行时标题右侧出现翻页箭头，按可视宽度整屏滚动，已到头的一侧不显示
@Composable
internal fun <T> ShelfRow(title: String, items: List<T>, itemContent: @Composable (T) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pageScroll: (Int) -> Unit = { direction ->
        val page = listState.layoutInfo.viewportSize.width - with(density) { (ShelfContentPadding * 2).toPx() }
        scope.launch { listState.animateScrollBy(direction * page.coerceAtLeast(0f)) }
    }
    Column(Modifier.hoverable(interaction), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle(title, horizontalPadding = 0, modifier = Modifier.weight(1f))
            ScrollArrow(
                revealed = hovered && listState.canScrollBackward,
                left = true,
                description = "上一页",
                modifier = Modifier
            ) { pageScroll(-1) }
            ScrollArrow(
                revealed = hovered && listState.canScrollForward,
                left = false,
                description = "下一页",
                modifier = Modifier
            ) { pageScroll(1) }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = ShelfContentPadding),
            horizontalArrangement = Arrangement.spacedBy(ShelfCardGap)
        ) {
            items(items) { itemContent(it) }
        }
    }
}

// 卡片自带内边距，悬停时整块衬底；外宽仍为 CardWidth，网格页的列宽计算不受影响
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CardTile(
    coverUrl: String,
    title: String,
    caption: String,
    typeLabel: String? = null,
    onPlay: (() -> Unit)? = null,
    contextMenuContent: (@Composable MenuScope.(onDismiss: () -> Unit) -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val density = LocalDensity.current
    var tileHeightPx by remember { mutableIntStateOf(0) }
    var contextMenuOffset by remember { mutableStateOf<DpOffset?>(null) }
    // 从同色全透明起步：Color.Transparent 是透明黑，浅色下插值会先变暗再变亮，看起来像闪烁
    val background by animateColorAsState(
        if (hovered || contextMenuOffset != null) DesktopColors.CardSurface else DesktopColors.CardSurface.copy(alpha = 0f),
        uiTween(HOVER_FADE_MS),
        label = "cardTileBg"
    )
    Box {
        Column(
            Modifier.width(CardWidth).clip(RoundedCornerShape(TileRadius)).background(background)
                .hoverable(interaction)
                .onSizeChanged { tileHeightPx = it.height }
                .then(
                    if (contextMenuContent != null) {
                        Modifier.onPointerEvent(PointerEventType.Press) { event ->
                            if (event.buttons.isSecondaryPressed) {
                                val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                                contextMenuOffset = with(density) {
                                    DpOffset(position.x.toDp(), (position.y - tileHeightPx).toDp())
                                }
                            }
                        }
                    } else Modifier
                )
                .clickable(onClick = onClick).padding(CardInnerPadding)
        ) {
            Box {
                Cover(coverUrl, CardCoverSize, shape = RoundedCornerShape(TileRadius))
                if (onPlay != null) {
                    HoverReveal(revealed = hovered, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                        HomePlayButton(size = 40.dp, description = "播放$title", onClick = onPlay)
                    }
                }
            }
            if (typeLabel != null) {
                Text(
                    typeLabel,
                    color = DesktopColors.TextGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                title,
                color = DesktopColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (typeLabel != null) 2.dp else 8.dp)
            )
            if (caption.isNotBlank()) {
                Text(caption, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (contextMenuContent != null && contextMenuOffset != null) {
            DesktopMenu(
                expanded = true,
                offset = contextMenuOffset ?: DpOffset.Zero,
                onDismiss = { contextMenuOffset = null }
            ) {
                contextMenuContent { contextMenuOffset = null }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String, horizontalPadding: Int = 24, modifier: Modifier = Modifier) {
    Text(
        text,
        color = DesktopColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = horizontalPadding.dp)
    )
}
