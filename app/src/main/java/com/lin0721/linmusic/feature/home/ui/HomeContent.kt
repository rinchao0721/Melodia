package com.lin0721.linmusic.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.feature.home.domain.HomeCardKind
import com.lin0721.linmusic.feature.home.domain.HomeSection
import com.lin0721.linmusic.feature.home.domain.HomeSectionCard
import kotlinx.coroutines.flow.distinctUntilChanged

// 首页信息流骨架：单个 LazyColumn 承载平台无关的 HomeSection 序列，顶栏由 HomeScreen 统一渲染。
// 货架与最近播放的网格用 FlowRow 而非懒加载网格：嵌进 LazyColumn 会因无界高度约束崩溃；
// 所有卡片同处一个父级，列数变化时卡片保持身份，才能从旧位置平滑过渡到新排版
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onOpenLocalMusic: () -> Unit = {},
    sections: List<HomeSection>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onQuickCardClick: (HomeSectionCard) -> Unit,
    onDailyOpen: (MusicPlatform) -> Unit,
    onDailyPlay: () -> Unit,
    onShelfCardClick: (HomeSection.Shelf, HomeSectionCard) -> Unit,
    onShelfCardPlay: (HomeSection.Shelf, HomeSectionCard) -> Unit,
    onShelfCardPlayNext: (HomeSectionCard) -> Unit,
    onPlaylistClick: (Long, Boolean) -> Unit,
    onPersonalFmClick: () -> Unit,
    onRoamingClick: () -> Unit,
    personalFmMode: PersonalFmMode,
    fmCoverUrls: List<String>,
    isPersonalFm: Boolean,
    isFmPlaying: Boolean,
    roamingCoverUrl: String,
    hotTopCoverUrl: String,
    isRoamingPlaying: Boolean,
    showForYou: Boolean
) {
    val listState = rememberLazyListState()
    val feed = (uiState as? HomeUiState.Success)?.data

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LookaheadScope {
            CompositionLocalProvider(LocalHomeLookaheadScope provides this) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
                ) {
                    when (uiState) {
                        is HomeUiState.Loading -> item { LoadingIndicator() }

                        is HomeUiState.Error -> item {
                            ErrorContent(message = uiState.message, onRetry = onRetry)
                        }

                        is HomeUiState.Success -> {
                            val data = uiState.data
                            if (data.browsePlatform == MusicPlatform.LOCAL) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 64.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "本地模式",
                                            color = MelodiaColors.TextPrimary,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "当前处于完全离线状态，仅展示本地音乐与已下载内容",
                                            color = MelodiaColors.TextSecondary,
                                            fontSize = 13.sp
                                        )
                                        MelodiaButton(onClick = onOpenLocalMusic) {
                                            Text("打开本地音乐")
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = sections,
                                    key = { it.key }
                                ) { section ->
                                    when (section) {
                                        is HomeSection.Quick -> {
                                            RecentPlaySection(
                                                items = section.items,
                                                isPersonalFm = isPersonalFm,
                                                onClick = onQuickCardClick,
                                                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                                            )
                                        }

                                        is HomeSection.Daily -> {
                                            val dailySongs = remember(section.items, data.dailySongs, section.platform) {
                                                if (section.platform == MusicPlatform.NETEASE && data.dailySongs.isNotEmpty()) {
                                                    data.dailySongs
                                                } else {
                                                    section.items.mapIndexed { index, card ->
                                                        DailySong(
                                                            id = card.entityId.toLongOrNull() ?: index.toLong(),
                                                            name = card.title
                                                        )
                                                    }
                                                }
                                            }
                                            HomeHighlightsSection(
                                                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                                dailySongs = dailySongs,
                                                toplists = data.toplistItems,
                                                recommendPlaylists = data.recommendPlaylists,
                                                personalFmMode = personalFmMode,
                                                fmCoverUrls = fmCoverUrls,
                                                isFmPlaying = isFmPlaying,
                                                roamingCoverUrl = roamingCoverUrl,
                                                hotTopCoverUrl = hotTopCoverUrl,
                                                isRoamingPlaying = isRoamingPlaying,
                                                onDailyOpen = { onDailyOpen(section.platform) },
                                                onDailyPlay = onDailyPlay,
                                                onHotlistClick = { onPlaylistClick(it, false) },
                                                onPersonalFmClick = onPersonalFmClick,
                                                onRadarClick = { onPlaylistClick(it, false) },
                                                onRoamingClick = onRoamingClick,
                                                showForYou = showForYou && section.platform == MusicPlatform.NETEASE
                                            )
                                        }

                                        is HomeSection.Shelf -> {
                                            HomeShelfSection(
                                                shelf = section,
                                                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                                onCardClick = { card ->
                                                    if (card.kind == HomeCardKind.SONG || card.kind == HomeCardKind.VOICE) {
                                                        onShelfCardPlay(section, card)
                                                    } else {
                                                        onShelfCardClick(section, card)
                                                    }
                                                },
                                                onCardPlay = { card -> onShelfCardPlay(section, card) },
                                                onCardPlayNext = onShelfCardPlayNext
                                            )
                                        }
                                    }
                                }

                                if (data.isLoadingMore) {
                                    item { HomeShelfLoadingMore() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 翻页条件只能进 LaunchedEffect 的 key，不能塞进 snapshotFlow 的闭包——
    // uiState 是入参而非 State，闭包会一直捕获创建时那一份，翻页将永远触发不了。
    val canLoadMore = feed != null && feed.browsePlatform == MusicPlatform.NETEASE && feed.hasMore && !feed.isLoadingMore

    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            // 距底部两个区块时预取
            lastVisible >= info.totalItemsCount - 2
        }
            .distinctUntilChanged()
            .collect { if (it) onLoadMore() }
    }
}
