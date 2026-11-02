package com.lin0721.linmusic.desktop.ui.podcast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.home.CardWidth
import com.lin0721.linmusic.desktop.ui.home.HomeTabError
import com.lin0721.linmusic.desktop.ui.home.HomeTabLoading
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.ui.PodcastFilter
import com.lin0721.linmusic.feature.podcast.ui.PodcastHomeState
import com.lin0721.linmusic.feature.podcast.ui.PodcastSection
import com.lin0721.linmusic.feature.podcast.ui.itemsOrEmpty

// 总览页「今日优选」最多展示几期
private const val PICK_LIMIT = 8
private val ContinueTileWidth = 240.dp

// 首页各处交互的出口，状态由 PodcastHomeViewModel 持有
class PodcastTabActions(
    val onFilterSelect: (PodcastFilter) -> Unit,
    val onResume: (PodcastProgressEntry) -> Unit,
    val onPickPlay: (Int) -> Unit,
    val onRadioClick: (PodcastRadio) -> Unit,
    val onOpenSubscribed: () -> Unit,
    val onOpenToplist: () -> Unit,
    val onOpenCategory: (id: Long, name: String) -> Unit,
    val onLoginClick: () -> Unit,
    val onRetryAll: () -> Unit,
    val onRetrySubscribed: () -> Unit,
    val onRetryPicks: () -> Unit,
    val onRetryCategoryGroups: () -> Unit,
    val onRetryToplist: () -> Unit,
    val onRetryCategoryRadios: () -> Unit
)

// 「播客」tab：顶部筛选条，下面按筛选展示总览 / 我的订阅 / 某个分类
@Composable
fun PodcastTab(state: PodcastHomeState, listState: LazyListState, actions: PodcastTabActions) {
    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "filters") {
                PodcastFilterBar(state.categories, state.filter, actions.onFilterSelect)
            }
            when (val filter = state.filter) {
                PodcastFilter.All -> overviewSections(state, actions)
                PodcastFilter.Subscribed -> subscribedSections(state, actions)
                is PodcastFilter.Category -> categorySections(filter, state, actions)
            }
        }
    }
}

// 总览：继续收听 → 我的订阅 → 今日优选 → 分类货架 → 热门电台榜
private fun LazyListScope.overviewSections(state: PodcastHomeState, actions: PodcastTabActions) {
    val publicSections = listOf(state.picks, state.categoryGroups, state.toplistRadios)
    val noLocalContent = state.continueListening.isEmpty()
    if (noLocalContent && publicSections.all { it is PodcastSection.Loading }) {
        item(key = "loading") { HomeTabLoading(Modifier.fillMaxWidth().height(320.dp)) }
        return
    }
    val firstError = publicSections.filterIsInstance<PodcastSection.Error>().firstOrNull()
    if (noLocalContent && firstError != null && publicSections.all { it is PodcastSection.Error }) {
        item(key = "error") { HomeTabError(firstError.message, actions.onRetryAll, Modifier.fillMaxWidth().height(320.dp)) }
        return
    }

    if (state.continueListening.isNotEmpty()) {
        item(key = "continue") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PodcastSectionHeader("继续收听")
                PodcastGrid(state.continueListening, ContinueTileWidth, gap = 8.dp, stretch = true) { entry, modifier ->
                    PodcastContinueTile(entry, onClick = { actions.onResume(entry) }, modifier = modifier)
                }
            }
        }
    }

    subscribedShelf(state, actions)

    when (val picks = state.picks) {
        PodcastSection.Loading -> item(key = "picks_loading") { PodcastInlineLoading() }
        is PodcastSection.Error -> item(key = "picks_error") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PodcastSectionHeader("今日优选")
                PodcastInlineError(picks.message, actions.onRetryPicks)
            }
        }
        is PodcastSection.Success -> if (picks.data.isNotEmpty()) {
            item(key = "picks") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PodcastSectionHeader("今日优选")
                    Column(Modifier.padding(horizontal = PodcastEdgePadding - 12.dp)) {
                        picks.data.take(PICK_LIMIT).forEachIndexed { index, program ->
                            PodcastProgramRow(
                                program = program,
                                progress = state.progress[program.songId],
                                onPlay = { actions.onPickPlay(index) }
                            )
                        }
                    }
                }
            }
        }
    }

    when (val groups = state.categoryGroups) {
        PodcastSection.Loading -> item(key = "groups_loading") { PodcastInlineLoading() }
        is PodcastSection.Error -> item(key = "groups_error") {
            PodcastInlineError(groups.message, actions.onRetryCategoryGroups)
        }
        is PodcastSection.Success -> groups.data.forEach { group ->
            item(key = "group_${group.categoryId}") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PodcastSectionHeader(group.categoryName, "全部") {
                        actions.onOpenCategory(group.categoryId, group.categoryName)
                    }
                    PodcastRadioShelf(group.radios, actions.onRadioClick)
                }
            }
        }
    }

    when (val toplist = state.toplistRadios) {
        PodcastSection.Loading -> item(key = "toplist_loading") { PodcastInlineLoading() }
        is PodcastSection.Error -> item(key = "toplist_error") {
            PodcastInlineError(toplist.message, actions.onRetryToplist)
        }
        is PodcastSection.Success -> if (toplist.data.isNotEmpty()) {
            item(key = "toplist") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PodcastSectionHeader("热门电台榜", "全部", actions.onOpenToplist)
                    PodcastRadioShelf(toplist.data, actions.onRadioClick, showRank = true)
                }
            }
        }
    }
}

// 总览里的订阅货架：未登录、没有订阅时整块不出现
private fun LazyListScope.subscribedShelf(state: PodcastHomeState, actions: PodcastTabActions) {
    if (!state.isLoggedIn) return
    when (val subscribed = state.subscribed) {
        PodcastSection.Loading -> Unit
        is PodcastSection.Error -> item(key = "subs_error") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PodcastSectionHeader("我的订阅")
                PodcastInlineError(subscribed.message, actions.onRetrySubscribed)
            }
        }
        is PodcastSection.Success -> if (subscribed.data.isNotEmpty()) {
            item(key = "subs") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PodcastSectionHeader("我的订阅", "全部", actions.onOpenSubscribed)
                    PodcastRadioShelf(subscribed.data, actions.onRadioClick, updatedRadioIds = state.updatedRadioIds)
                }
            }
        }
    }
}

// 「已订阅」筛选：订阅的电台铺成网格
private fun LazyListScope.subscribedSections(state: PodcastHomeState, actions: PodcastTabActions) {
    if (!state.isLoggedIn) {
        item(key = "subs_login") { PodcastHint("登录后查看你订阅的电台", actionText = "去登录", onAction = actions.onLoginClick) }
        return
    }
    when (val subscribed = state.subscribed) {
        PodcastSection.Loading -> item(key = "subs_loading") { HomeTabLoading(Modifier.fillMaxWidth().height(240.dp)) }
        is PodcastSection.Error -> item(key = "subs_error") {
            HomeTabError(subscribed.message, actions.onRetrySubscribed, Modifier.fillMaxWidth().height(240.dp))
        }
        is PodcastSection.Success -> {
            if (subscribed.data.isEmpty()) {
                item(key = "subs_empty") { PodcastHint("还没有订阅的电台，在电台详情页点「订阅」，更新会出现在这里") }
                return
            }
            item(key = "subs_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PodcastSectionHeader("我的订阅", "全部", actions.onOpenSubscribed)
                    PodcastGrid(subscribed.data, CardWidth, gap = 16.dp, stretch = false) { radio, modifier ->
                        PodcastRadioTile(
                            radio = radio,
                            onClick = { actions.onRadioClick(radio) },
                            modifier = modifier,
                            hasUpdate = radio.id in state.updatedRadioIds
                        )
                    }
                }
            }
        }
    }
}

// 分类筛选：该分类的热门电台，更多的翻页在分类二级页
private fun LazyListScope.categorySections(
    filter: PodcastFilter.Category,
    state: PodcastHomeState,
    actions: PodcastTabActions
) {
    val categoryName = state.categories.firstOrNull { it.id == filter.id }?.name.orEmpty()
    when (val radios = state.categoryRadios) {
        PodcastSection.Loading -> item(key = "cat_loading") { HomeTabLoading(Modifier.fillMaxWidth().height(240.dp)) }
        is PodcastSection.Error -> item(key = "cat_error") {
            HomeTabError(radios.message, actions.onRetryCategoryRadios, Modifier.fillMaxWidth().height(240.dp))
        }
        is PodcastSection.Success -> {
            if (radios.data.isEmpty()) {
                item(key = "cat_empty") { PodcastHint("这个分类暂时没有电台") }
                return
            }
            item(key = "cat_radios") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PodcastSectionHeader("热门电台", "全部") { actions.onOpenCategory(filter.id, categoryName) }
                    PodcastGrid(radios.data, CardWidth, gap = 16.dp, stretch = false) { radio, modifier ->
                        PodcastRadioTile(radio = radio, onClick = { actions.onRadioClick(radio) }, modifier = modifier)
                    }
                }
            }
        }
    }
}
