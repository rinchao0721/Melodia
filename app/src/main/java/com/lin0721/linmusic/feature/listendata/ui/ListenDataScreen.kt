package com.lin0721.linmusic.feature.listendata.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.FilterChipsRow
import com.lin0721.linmusic.core.ui.components.SearchResultRowSkeleton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.DataEnterSpec
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import org.koin.androidx.compose.koinViewModel

private const val SKELETON_ROW_COUNT = 6
private const val COLLAPSED_RANK_COUNT = 5

// Tab 过渡比数据入场短，切换要跟手不能拖沓
private val TabSwitchSpec = tween<Float>(220, easing = FastOutSlowInEasing)
private val TabSwitchOffsetSpec = tween<IntOffset>(220, easing = FastOutSlowInEasing)

@Composable
fun ListenDataScreen(
    viewModel: ListenDataViewModel = koinViewModel(),
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playerManager.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.playWhenReady.collectAsStateWithLifecycle()

    SecondaryScreenScaffold(title = "听歌数据", onBack = onBack) {
        FilterChipsRow(
            items = ListenTab.entries.map { it.label },
            selectedIndex = uiState.selectedTab.ordinal,
            onSelected = { index -> viewModel.selectTab(ListenTab.entries[index]) }
        )

        // 药丸行固定在顶部，列表须限定在剩余空间内，否则滚动内容会画到药丸上
        Box(modifier = Modifier.weight(1f)) {
            val motion = LocalUiMotion.current
            AnimatedContent(
                targetState = uiState,
                // 顺着药丸的左右次序滑动，切走的方向和手指选中的方向一致
                transitionSpec = {
                    val forward = targetState.selectedTab.ordinal >= initialState.selectedTab.ordinal
                    val offset = if (forward) 1 else -1
                    motion.transform(
                        (slideInHorizontally(TabSwitchOffsetSpec) { it / 6 * offset } + fadeIn(TabSwitchSpec))
                            .togetherWith(
                                slideOutHorizontally(TabSwitchOffsetSpec) { -it / 6 * offset } +
                                    fadeOut(TabSwitchSpec)
                            )
                    )
                },
                // Tab 与内容之外的字段（如累计时长后到）变化时不重播过渡
                contentKey = { it.selectedTab to it.content },
                label = "listen_data_content"
            ) { state ->
                when (val content = state.content) {
                    ListenDataContent.Loading -> {
                        Column(modifier = Modifier.padding(top = MelodiaSpacing.md)) {
                            repeat(SKELETON_ROW_COUNT) { SearchResultRowSkeleton() }
                        }
                    }

                    is ListenDataContent.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ErrorState(message = content.message, onRetry = { viewModel.retry() })
                        }
                    }

                    is ListenDataContent.Today -> TodayList(
                        content = content,
                        currentTrackId = currentTrack?.mediaId,
                        isPlaying = isPlaying,
                        onSongClick = { viewModel.playSongAt(it) }
                    )

                    is ListenDataContent.Period -> PeriodList(
                        content = content,
                        // 取动画帧自己的快照，退场那一份才不会串到新 Tab 的标签
                        totalSeconds = state.totalSeconds,
                        periodLabel = state.selectedTab.label,
                        currentTrackId = currentTrack?.mediaId,
                        isPlaying = isPlaying,
                        onSongClick = { viewModel.playSongAt(it) },
                        onHighlightClick = { viewModel.playHighlight(it) },
                        onArtistClick = onArtistClick
                    )

                    is ListenDataContent.Year -> YearList(content)
                }
            }
        }
    }
}

// 入场进度：在列表外层持有，避免 LazyColumn 回收 item 后动画重播
@Composable
private fun rememberDataEnterProgress(key: Any?): Float {
    val motion = LocalUiMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, motion.spec(DataEnterSpec))
    }
    return progress.value
}

// ======================= 今日 =======================

@Composable
private fun TodayList(
    content: ListenDataContent.Today,
    currentTrackId: String?,
    isPlaying: Boolean,
    onSongClick: (Int) -> Unit
) {
    if (content.songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.Insights,
                title = "今天还没有收听记录",
                subtitle = "听几首歌之后再回来看看"
            )
        }
        return
    }

    LazyColumn(contentPadding = bottomInsetPadding()) {
        paddedItem("today_header") {
            Column(modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.sm)) {
                Text(
                    text = "今日播放",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Text(
                    text = "${content.songs.size} 首",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 34.sp
                )
            }
        }
        itemsIndexed(content.songs.size) { index ->
            val song = content.songs[index]
            SongRow(
                data = SongRowData(
                    id = song.id,
                    title = song.name,
                    artist = song.artistName,
                    coverUrl = song.coverUrl
                ),
                isActive = currentTrackId == song.id.toString(),
                isPlaying = isPlaying,
                index = index + 1,
                onClick = { onSongClick(index) }
            )
        }
    }
}

// ======================= 周 / 月 =======================

@Composable
private fun PeriodList(
    content: ListenDataContent.Period,
    totalSeconds: Long,
    periodLabel: String,
    currentTrackId: String?,
    isPlaying: Boolean,
    onSongClick: (Int) -> Unit,
    onHighlightClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit
) {
    val report = content.report
    val rank = content.rank
    var expanded by rememberSaveable(periodLabel) { mutableStateOf(false) }
    val rankSongs = rank?.songs.orEmpty()
    val visibleSongs = if (expanded) rankSongs else rankSongs.take(COLLAPSED_RANK_COUNT)
    val enterProgress = rememberDataEnterProgress(content)
    // 图表选中是纯展示状态，不入 ViewModel；切 Tab 时随 periodLabel 重置
    var selectedDay by rememberSaveable(periodLabel) { mutableStateOf<Int?>(null) }
    var selectedPeriod by rememberSaveable(periodLabel) { mutableStateOf<Int?>(null) }

    LazyColumn(contentPadding = bottomInsetPadding()) {
        if (report != null) {
            paddedItem("hero") {
                HeroSection(
                    playMinutes = report.playMinutes,
                    beyondPercentText = report.beyondPercentText,
                    achievementTitle = report.achievementTitle,
                    achievementSubtitle = report.achievementSubtitle,
                    listenDays = report.listenDays,
                    songCount = report.songCount,
                    wallpaperUrls = report.wallpaperUrls,
                    totalSeconds = totalSeconds,
                    periodLabel = periodLabel,
                    progress = enterProgress
                )
            }
            paddedItem("highlight") {
                HighlightSection(report.highlights, periodLabel, onHighlightClick)
            }
            paddedItem("daily") {
                DailyChartSection(
                    days = report.dailyDurations,
                    progress = enterProgress,
                    selectedIndex = selectedDay,
                    onSelect = { selectedDay = it }
                )
            }
            paddedItem("period") {
                TimePeriodSection(
                    periods = report.timePeriods,
                    progress = enterProgress,
                    selectedIndex = selectedPeriod,
                    onSelect = { selectedPeriod = it }
                )
            }
        }

        if (rankSongs.isNotEmpty()) {
            paddedItem("rank_title") {
                SectionTitle("最常听", "共 ${rank?.totalCount ?: rankSongs.size} 首")
            }
            itemsIndexed(visibleSongs.size) { index ->
                val song = visibleSongs[index]
                SongRow(
                    data = SongRowData(
                        id = song.id,
                        title = song.name,
                        artist = song.artistName,
                        coverUrl = song.coverUrl,
                        durationText = "${song.playCount}次"
                    ),
                    isActive = currentTrackId == song.id.toString(),
                    isPlaying = isPlaying,
                    index = index + 1,
                    onClick = { onSongClick(index) }
                )
            }
            if (rankSongs.size > COLLAPSED_RANK_COUNT) {
                item(key = "rank_expand") {
                    Text(
                        text = if (expanded) "收起" else "展开全部 ${rankSongs.size} 首",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable(MelodiaPress.Row) { expanded = !expanded }
                            .padding(vertical = MelodiaSpacing.sm + MelodiaSpacing.xs)
                    )
                }
            }
        }

        if (report != null) {
            paddedItem("artists") { TopArtistsSection(report.topArtists, onArtistClick) }
            paddedItem("preference") {
                PreferenceSection(report.genreName, report.ageLabel, report.languageName)
            }
            paddedItem("podcast_vip") {
                val vip = report.vipItems.firstOrNull()
                PodcastVipSection(
                    podcastMinutes = report.podcastMinutes,
                    podcastEpisodes = report.podcastEpisodes,
                    vipMainText = vip?.mainText.orEmpty(),
                    vipSubText = vip?.subText.orEmpty(),
                    progress = enterProgress
                )
            }
            paddedItem("friends") {
                FriendsSection(report.friendsListening, report.friendKeywords)
            }
        }
    }
}

// ======================= 年度 =======================

@Composable
private fun YearList(content: ListenDataContent.Year) {
    if (content.stats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.Rounded.Insights, title = "还没有历年收听记录")
        }
        return
    }

    val enterProgress = rememberDataEnterProgress(content)
    LazyColumn(contentPadding = bottomInsetPadding()) {
        paddedItem("years") { YearStatsSection(content.stats, enterProgress) }
    }
}

// ======================= 布局工具 =======================

// 各区块自带水平边距，SongRow 内部已有同宽 padding，故列表只处理上下留白
@Composable
private fun bottomInsetPadding(): PaddingValues = PaddingValues(
    top = MelodiaSpacing.sm,
    bottom = LocalBottomOverlayInset.current + 16.dp
)

private fun LazyListScope.paddedItem(key: String, content: @Composable () -> Unit) {
    item(key = key) {
        Column(modifier = Modifier.padding(horizontal = MelodiaSpacing.md)) { content() }
    }
}

private fun LazyListScope.itemsIndexed(count: Int, itemContent: @Composable (Int) -> Unit) {
    items(count = count, key = { "row_$it" }) { index -> itemContent(index) }
}
