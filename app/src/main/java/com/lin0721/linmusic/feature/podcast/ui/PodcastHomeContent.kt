package com.lin0721.linmusic.feature.podcast.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaGridColumns
import com.lin0721.linmusic.feature.home.ui.ErrorContent
import com.lin0721.linmusic.feature.home.ui.LoadingIndicator
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio

// 首页各处交互的出口，状态由 PodcastHomeViewModel 持有
class PodcastHomeActions(
    val onFilterSelect: (PodcastFilter) -> Unit,
    val onResume: (PodcastProgressEntry) -> Unit,
    val onPickClick: (Int) -> Unit,
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

// 「播客」tab：顶部吸顶筛选条，下面按筛选展示总览 / 我的订阅 / 某个分类
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PodcastHomeContent(state: PodcastHomeState, actions: PodcastHomeActions) {
    val expanded = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded
    val layout = PodcastHomeLayout(
        tileColumns = rememberMelodiaGridColumns(compact = 2, expandedPortrait = 3, expandedLandscape = 4),
        radioColumns = rememberMelodiaGridColumns(compact = 3, expandedPortrait = 5, expandedLandscape = 6),
        episodeColumns = if (expanded) 2 else 1,
        pickLimit = if (expanded) 8 else 6
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        stickyHeader(key = "filters") {
            PodcastFilterBar(
                categories = state.categories,
                filter = state.filter,
                onSelect = actions.onFilterSelect
            )
        }
        when (val filter = state.filter) {
            PodcastFilter.All -> overviewSections(state, actions, layout)
            PodcastFilter.Subscribed -> subscribedSections(state, actions, layout)
            is PodcastFilter.Category -> categorySections(filter, state, actions, layout)
        }
    }
}

private class PodcastHomeLayout(
    val tileColumns: Int,
    val radioColumns: Int,
    val episodeColumns: Int,
    val pickLimit: Int
)

// 总览：继续收听 → 我的订阅 → 今日优选 → 分类货架 → 热门电台榜
private fun LazyListScope.overviewSections(
    state: PodcastHomeState,
    actions: PodcastHomeActions,
    layout: PodcastHomeLayout
) {
    val publicSections = listOf(state.picks, state.categoryGroups, state.toplistRadios)
    val noLocalContent = state.continueListening.isEmpty()
    if (noLocalContent && publicSections.all { it is PodcastSection.Loading }) {
        item(key = "loading") { LoadingIndicator() }
        return
    }
    val firstError = publicSections.filterIsInstance<PodcastSection.Error>().firstOrNull()
    if (noLocalContent && publicSections.all { it is PodcastSection.Error } && firstError != null) {
        item(key = "error") { ErrorContent(message = firstError.message, onRetry = actions.onRetryAll) }
        return
    }

    if (state.continueListening.isNotEmpty()) {
        item(key = "continue_title") { PodcastSectionTitle("继续收听") }
        podcastGridItems(
            items = state.continueListening,
            columns = layout.tileColumns,
            keyPrefix = "continue",
            columnGap = 8.dp,
            rowGap = 8.dp
        ) { entry, modifier ->
            ContinueListeningTile(entry = entry, onClick = { actions.onResume(entry) }, modifier = modifier)
        }
    }

    subscribedShelf(state, actions)

    when (val picks = state.picks) {
        PodcastSection.Loading -> item(key = "picks_loading") { SectionLoading() }
        is PodcastSection.Error -> {
            item(key = "picks_title") { PodcastSectionTitle("今日优选") }
            item(key = "picks_error") { SectionError(picks.message, actions.onRetryPicks) }
        }
        is PodcastSection.Success -> if (picks.data.isNotEmpty()) {
            item(key = "picks_title") { PodcastSectionTitle("今日优选") }
            podcastEpisodeItems(
                programs = picks.data.take(layout.pickLimit),
                columns = layout.episodeColumns,
                keyPrefix = "picks",
                progress = state.progress,
                onClick = actions.onPickClick
            )
        }
    }

    when (val groups = state.categoryGroups) {
        PodcastSection.Loading -> item(key = "groups_loading") { SectionLoading() }
        is PodcastSection.Error -> item(key = "groups_error") {
            SectionError(groups.message, actions.onRetryCategoryGroups)
        }
        is PodcastSection.Success -> groups.data.forEach { group ->
            item(key = "group_title_${group.categoryId}") {
                PodcastSectionTitle(group.categoryName, "全部") {
                    actions.onOpenCategory(group.categoryId, group.categoryName)
                }
            }
            item(key = "group_shelf_${group.categoryId}") {
                PodcastRadioShelf(radios = group.radios, onClick = actions.onRadioClick)
            }
        }
    }

    when (val toplist = state.toplistRadios) {
        PodcastSection.Loading -> item(key = "toplist_loading") { SectionLoading() }
        is PodcastSection.Error -> item(key = "toplist_error") {
            SectionError(toplist.message, actions.onRetryToplist)
        }
        is PodcastSection.Success -> if (toplist.data.isNotEmpty()) {
            item(key = "toplist_title") { PodcastSectionTitle("热门电台榜", "全部", actions.onOpenToplist) }
            item(key = "toplist_shelf") {
                PodcastRadioShelf(radios = toplist.data, onClick = actions.onRadioClick, showRank = true)
            }
        }
    }
}

// 总览里的订阅货架：未登录、没有订阅时整块不出现
private fun LazyListScope.subscribedShelf(state: PodcastHomeState, actions: PodcastHomeActions) {
    if (!state.isLoggedIn) return
    when (val subscribed = state.subscribed) {
        PodcastSection.Loading -> Unit
        is PodcastSection.Error -> {
            item(key = "subs_title") { PodcastSectionTitle("我的订阅") }
            item(key = "subs_error") { SectionError(subscribed.message, actions.onRetrySubscribed) }
        }
        is PodcastSection.Success -> if (subscribed.data.isNotEmpty()) {
            item(key = "subs_title") { PodcastSectionTitle("我的订阅", "全部", actions.onOpenSubscribed) }
            item(key = "subs_shelf") {
                PodcastRadioShelf(
                    radios = subscribed.data,
                    onClick = actions.onRadioClick,
                    updatedRadioIds = state.updatedRadioIds
                )
            }
        }
    }
}

// 「已订阅」筛选：订阅的电台铺成网格
private fun LazyListScope.subscribedSections(
    state: PodcastHomeState,
    actions: PodcastHomeActions,
    layout: PodcastHomeLayout
) {
    if (!state.isLoggedIn) {
        item(key = "subs_login") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(PodcastEdgePadding).padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "登录后查看你订阅的电台", color = MelodiaColors.TextSecondary, fontSize = 13.sp)
                MelodiaButton(onClick = actions.onLoginClick) { Text("去登录") }
            }
        }
        return
    }
    when (val subscribed = state.subscribed) {
        PodcastSection.Loading -> item(key = "subs_loading") { LoadingIndicator() }
        is PodcastSection.Error -> item(key = "subs_error") {
            ErrorContent(message = subscribed.message, onRetry = actions.onRetrySubscribed)
        }
        is PodcastSection.Success -> {
            if (subscribed.data.isEmpty()) {
                item(key = "subs_empty") {
                    EmptyState(
                        icon = Icons.Rounded.Headphones,
                        title = "还没有订阅的电台",
                        subtitle = "在电台详情页点「订阅」，更新会出现在这里"
                    )
                }
                return
            }
            item(key = "subs_title") { PodcastSectionTitle("我的订阅", "全部", actions.onOpenSubscribed) }
            podcastGridItems(subscribed.data, layout.radioColumns, "subs_grid") { radio, modifier ->
                PodcastRadioCard(
                    radio = radio,
                    onClick = { actions.onRadioClick(radio) },
                    modifier = modifier,
                    hasUpdate = radio.id in state.updatedRadioIds
                )
            }
        }
    }
}

// 分类筛选：该分类的热门电台，更多的翻页在分类二级页
private fun LazyListScope.categorySections(
    filter: PodcastFilter.Category,
    state: PodcastHomeState,
    actions: PodcastHomeActions,
    layout: PodcastHomeLayout
) {
    val categoryName = state.categories.firstOrNull { it.id == filter.id }?.name.orEmpty()
    when (val radios = state.categoryRadios) {
        PodcastSection.Loading -> item(key = "cat_loading") { LoadingIndicator() }
        is PodcastSection.Error -> item(key = "cat_error") {
            ErrorContent(message = radios.message, onRetry = actions.onRetryCategoryRadios)
        }
        is PodcastSection.Success -> {
            if (radios.data.isEmpty()) {
                item(key = "cat_empty") { SectionHint("这个分类暂时没有电台") }
                return
            }
            item(key = "cat_radios_title") {
                PodcastSectionTitle("热门电台", "全部") { actions.onOpenCategory(filter.id, categoryName) }
            }
            podcastGridItems(radios.data, layout.radioColumns, "cat_radios") { radio, modifier ->
                PodcastRadioCard(radio = radio, onClick = { actions.onRadioClick(radio) }, modifier = modifier)
            }
        }
    }
}

@Composable
internal fun SectionLoading() {
    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// 区块级的轻量错误提示，不占满整页
@Composable
internal fun SectionError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PodcastEdgePadding, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message.ifBlank { "加载失败" },
            color = MelodiaColors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
internal fun SectionHint(text: String) {
    Text(
        text = text,
        color = MelodiaColors.TextSecondary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    )
}
