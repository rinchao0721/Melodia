package com.lin0721.linmusic.feature.podcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.paneBackdrop
import com.lin0721.linmusic.feature.podcast.domain.PodcastCategory
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.formatListenerCount
import com.lin0721.linmusic.feature.podcast.domain.formatProgramDate
import com.lin0721.linmusic.feature.podcast.domain.formatProgramDuration
import com.lin0721.linmusic.feature.podcast.domain.formatRemaining
import com.lin0721.linmusic.feature.podcast.domain.formatSubCount
import com.lin0721.linmusic.feature.podcast.domain.podcastSubtitle
import kotlinx.coroutines.flow.distinctUntilChanged

internal val PodcastEdgePadding = 20.dp

private val RadioCardWidthCompact = 120.dp
private val RadioCardWidthExpanded = 150.dp
private val GridGap = 12.dp
private val TrackColor: Color @Composable @ReadOnlyComposable get() = MelodiaColors.Overlay.copy(alpha = 0.16f)

// 电台封面自带查询串的情况与歌单同理，追加 param 前需判断
internal fun String.withPodcastCoverParam(param: String): String =
    if (contains('?')) this else "$this?param=$param"

@Composable
internal fun PodcastCover(
    url: String,
    param: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(RadiusCompact)
) {
    SubcomposeAsyncImage(
        model = url.withPodcastCoverParam(param),
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = ContentScale.Crop,
        loading = { CoverPlaceholder() },
        error = { CoverPlaceholder() }
    )
}

// 区块标题。trailing 带点击时右侧显示「全部 ›」式入口
@Composable
fun PodcastSectionTitle(
    title: String,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PodcastEdgePadding, end = PodcastEdgePadding, top = 20.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        trailing?.let {
            Row(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .then(if (onTrailingClick != null) Modifier.clickable(onClick = onTrailingClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = it, color = MelodiaColors.TextSecondary, fontSize = 11.5.sp)
                if (onTrailingClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MelodiaColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// 筛选胶囊，选中态为白底
@Composable
fun PodcastFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .pressable(MelodiaPress.Pill) { onClick() }
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(MelodiaColors.TextPrimary)
                else Modifier.border(1.dp, MelodiaColors.Overlay.copy(alpha = 0.18f), CircleShape)
            )
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (selected) MelodiaColors.InverseText else MelodiaColors.PillContent,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// 首页顶部吸顶的筛选条：全部 / 已订阅 / 各分类
@Composable
fun PodcastFilterBar(
    categories: List<PodcastCategory>,
    filter: PodcastFilter,
    onSelect: (PodcastFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .paneBackdrop(MaterialTheme.colorScheme.background)
            .padding(vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = PodcastEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item(key = "all") {
            PodcastFilterChip("全部", filter == PodcastFilter.All) { onSelect(PodcastFilter.All) }
        }
        item(key = "subscribed") {
            PodcastFilterChip("已订阅", filter == PodcastFilter.Subscribed) { onSelect(PodcastFilter.Subscribed) }
        }
        items(categories, key = { it.id }) { category ->
            PodcastFilterChip(
                text = category.name,
                selected = (filter as? PodcastFilter.Category)?.id == category.id
            ) { onSelect(PodcastFilter.Category(category.id)) }
        }
    }
}

// 继续收听块：封面 + 标题 + 剩余时长，底部一条已听进度线
@Composable
fun ContinueListeningTile(
    entry: PodcastProgressEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .pressable(MelodiaPress.Card) { onClick() }
            .clip(RoundedCornerShape(RadiusCompact))
            .background(MelodiaColors.Fill)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            PodcastCover(
                url = entry.coverUrl,
                param = "120y120",
                contentDescription = entry.title,
                modifier = Modifier.size(56.dp),
                shape = RectangleShape
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    text = entry.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val remaining = formatRemaining(entry.remainingMs)
                if (remaining.isNotBlank()) {
                    Text(text = remaining, color = MelodiaColors.TextSecondary, fontSize = 10.5.sp, maxLines = 1)
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(entry.fraction)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

// 电台卡片。rank 非空时在封面左上角标名次，hasUpdate 为真时右上角标「新」
@Composable
fun PodcastRadioCard(
    radio: PodcastRadio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    hasUpdate: Boolean = false
) {
    Column(modifier = modifier.pressable(MelodiaPress.Card) { onClick() }) {
        Box {
            PodcastCover(
                url = radio.picUrl,
                param = "300y300",
                contentDescription = radio.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            if (rank != null) {
                Text(
                    text = "$rank",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            if (hasUpdate) {
                Text(
                    text = "新",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            text = radio.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )
        val meta = listOfNotNull(
            radio.programCount.takeIf { it > 0 }?.let { "$it 期" },
            formatSubCount(radio.subCount).takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = MelodiaColors.TextSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// 电台横向货架。平板上卡片更宽
@Composable
fun PodcastRadioShelf(
    radios: List<PodcastRadio>,
    onClick: (PodcastRadio) -> Unit,
    updatedRadioIds: Set<Long> = emptySet(),
    showRank: Boolean = false
) {
    val cardWidth = if (LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded) {
        RadioCardWidthExpanded
    } else {
        RadioCardWidthCompact
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = PodcastEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(GridGap)
    ) {
        itemsIndexed(radios, key = { index, item -> "${item.id}_$index" }) { index, radio ->
            PodcastRadioCard(
                radio = radio,
                onClick = { onClick(radio) },
                modifier = Modifier.width(cardWidth),
                rank = if (showRank) index + 1 else null,
                hasUpdate = radio.id in updatedRadioIds
            )
        }
    }
}

// 在 LazyColumn 里铺等宽网格：按列数切行，末行不满用空位补齐。
// 行用序号作 key，避免翻页后出现重复条目时 key 冲突
fun <T> LazyListScope.podcastGridItems(
    items: List<T>,
    columns: Int,
    keyPrefix: String,
    columnGap: Dp = GridGap,
    rowGap: Dp = 14.dp,
    cell: @Composable (item: T, modifier: Modifier) -> Unit
) {
    itemsIndexed(items.chunked(columns), key = { rowIndex, _ -> "$keyPrefix-$rowIndex" }) { _, row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PodcastEdgePadding)
                .padding(bottom = rowGap),
            horizontalArrangement = Arrangement.spacedBy(columnGap)
        ) {
            row.forEach { cell(it, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// 平板上节目卡双列时的卡内边距，与外层行的缩进合起来等于页面边距
private val EpisodeGridInset = 8.dp

// 在 LazyColumn 里铺节目列表：columns 为 1 时逐条铺，大于 1 时按列数切行等宽排布。
// onClick 回传该节目在 programs 里的下标
fun LazyListScope.podcastEpisodeItems(
    programs: List<PodcastProgram>,
    columns: Int,
    keyPrefix: String,
    progress: Map<Long, PodcastProgressEntry>,
    onClick: (Int) -> Unit
) {
    if (columns <= 1) {
        itemsIndexed(programs, key = { index, program -> "$keyPrefix-${program.id}-$index" }) { index, program ->
            PodcastEpisodeCard(program, progress[program.songId], onClick = { onClick(index) })
        }
        return
    }
    itemsIndexed(programs.chunked(columns), key = { rowIndex, _ -> "$keyPrefix-row-$rowIndex" }) { rowIndex, row ->
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = PodcastEdgePadding - EpisodeGridInset)) {
            row.forEachIndexed { column, program ->
                PodcastEpisodeCard(
                    program = program,
                    progress = progress[program.songId],
                    onClick = { onClick(rowIndex * columns + column) },
                    modifier = Modifier.weight(1f),
                    horizontalPadding = EpisodeGridInset
                )
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// 节目卡：封面 + 标题 + 来源 + 简介 + 日期时长 + 进度 + 播放键
@Composable
fun PodcastEpisodeCard(
    program: PodcastProgram,
    progress: PodcastProgressEntry?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = PodcastEdgePadding
) {
    val finished = progress?.isFinished == true
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = horizontalPadding, vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        PodcastCover(
            url = program.coverUrl,
            param = "200y200",
            contentDescription = program.name,
            modifier = Modifier.size(64.dp)
        )

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = program.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val source = podcastSubtitle(program.radioName, program.djName)
            if (source.isNotBlank()) {
                Text(
                    text = source,
                    color = MelodiaColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (program.description.isNotBlank()) {
                Text(
                    text = program.description,
                    color = MelodiaColors.TextSecondary.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            EpisodeMetaRow(program = program, progress = progress, finished = finished)
        }

        PlayCircle(modifier = Modifier.padding(start = 10.dp, top = 4.dp))
    }
}

// 日期 · 时长，其后是已听进度条或已听完标记
@Composable
private fun EpisodeMetaRow(program: PodcastProgram, progress: PodcastProgressEntry?, finished: Boolean) {
    Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val meta = listOfNotNull(
            formatProgramDate(program.createTimeMs).takeIf { it.isNotBlank() },
            formatProgramDuration(program.durationMs).takeIf { it.isNotBlank() },
            formatListenerCount(program.listenerCount).takeIf { it.isNotBlank() && progress == null }
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(text = meta, color = MelodiaColors.TextSecondary.copy(alpha = 0.75f), fontSize = 10.5.sp, maxLines = 1)
        }
        when {
            finished -> {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).size(13.dp)
                )
                Text(
                    text = "已听完",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            progress != null && progress.fraction > 0f -> {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.padding(start = 8.dp).weight(1f).height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = TrackColor
                )
            }
        }
    }
}

@Composable
fun PlayCircle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "播放",
            tint = MelodiaColors.OnAccent,
            modifier = Modifier.size(16.dp)
        )
    }
}

// 列表滚到接近末尾时触发翻页；enabled 为假（加载中或已到底）时不监听
@Composable
internal fun LoadMoreEffect(listState: LazyListState, enabled: Boolean, onLoadMore: () -> Unit) {
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { if (it) latestOnLoadMore() }
    }
}
