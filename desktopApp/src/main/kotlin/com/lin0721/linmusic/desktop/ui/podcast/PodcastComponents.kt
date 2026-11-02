package com.lin0721.linmusic.desktop.ui.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.onDoubleClick
import com.lin0721.linmusic.desktop.ui.home.CardWidth
import com.lin0721.linmusic.desktop.ui.home.HomeTabLoading
import com.lin0721.linmusic.desktop.ui.home.OutlineChip
import com.lin0721.linmusic.desktop.ui.home.SectionTitle
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
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
import com.lin0721.linmusic.feature.podcast.ui.PodcastFilter

internal val PodcastEdgePadding = 24.dp
private val ProgressTrack: Color @Composable @ReadOnlyComposable get() = DesktopColors.Overlay.copy(alpha = 0.16f)

// 区块标题；带 actionText 与 onAction 时右侧显示「全部 ›」入口
@Composable
internal fun PodcastSectionHeader(title: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PodcastEdgePadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        SectionTitle(title, horizontalPadding = 0)
        if (actionText != null && onAction != null) {
            Text(
                text = "$actionText ›",
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// 吸顶筛选条：全部 / 已订阅 / 各分类
@Composable
internal fun PodcastFilterBar(
    categories: List<PodcastCategory>,
    filter: PodcastFilter,
    onSelect: (PodcastFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = PodcastEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") { OutlineChip("全部", filter == PodcastFilter.All) { onSelect(PodcastFilter.All) } }
        item(key = "subscribed") {
            OutlineChip("已订阅", filter == PodcastFilter.Subscribed) { onSelect(PodcastFilter.Subscribed) }
        }
        itemsIndexed(categories, key = { _, item -> item.id }) { _, category ->
            OutlineChip(category.name, (filter as? PodcastFilter.Category)?.id == category.id) {
                onSelect(PodcastFilter.Category(category.id))
            }
        }
    }
}

@Composable
private fun PodcastBadge(text: String, background: Color, modifier: Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(background).padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

// 电台卡片。rank 非空时封面左上角标名次，hasUpdate 为真时右上角标「新」
@Composable
internal fun PodcastRadioTile(
    radio: PodcastRadio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(CardWidth),
    rank: Int? = null,
    hasUpdate: Boolean = false
) {
    Column(modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)) {
        Box {
            Cover(radio.picUrl, CardWidth, shape = RoundedCornerShape(6.dp))
            if (rank != null) {
                PodcastBadge("$rank", Color.Black.copy(alpha = 0.55f), Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            if (hasUpdate) {
                PodcastBadge("新", DesktopColors.Accent, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Text(
            text = radio.name,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        val meta = listOfNotNull(
            radio.programCount.takeIf { it > 0 }?.let { "$it 期" },
            formatSubCount(radio.subCount).takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// 电台横向货架
@Composable
internal fun PodcastRadioShelf(
    radios: List<PodcastRadio>,
    onClick: (PodcastRadio) -> Unit,
    updatedRadioIds: Set<Long> = emptySet(),
    showRank: Boolean = false
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = PodcastEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(radios, key = { index, item -> "${item.id}_$index" }) { index, radio ->
            PodcastRadioTile(
                radio = radio,
                onClick = { onClick(radio) },
                rank = if (showRank) index + 1 else null,
                hasUpdate = radio.id in updatedRadioIds
            )
        }
    }
}

// 在 LazyColumn 的一项里按可用宽度铺网格。stretch 为真时格子等宽撑满一行，否则固定 cellWidth 靠左排列
@Composable
internal fun <T> PodcastGrid(
    items: List<T>,
    cellWidth: Dp,
    gap: Dp,
    stretch: Boolean,
    cell: @Composable (item: T, modifier: Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth - PodcastEdgePadding * 2 + gap) / (cellWidth + gap)).toInt().coerceAtLeast(1)
        Column(Modifier.padding(horizontal = PodcastEdgePadding), verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { cell(it, if (stretch) Modifier.weight(1f) else Modifier.width(cellWidth)) }
                    if (stretch) repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// 继续收听块：封面 + 标题 + 剩余时长，底部一条已听进度线
@Composable
internal fun PodcastContinueTile(entry: PodcastProgressEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(DesktopColors.Surface)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Cover(entry.coverUrl, 56.dp, shape = RoundedCornerShape(0.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = entry.title,
                    color = DesktopColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val remaining = formatRemaining(entry.remainingMs)
                if (remaining.isNotBlank()) {
                    Text(remaining, color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(entry.fraction)
                .height(2.dp)
                .background(DesktopColors.Accent)
        )
    }
}

// 节目表格行：序号（悬停变播放键）· 封面 · 标题与来源 · 日期 · 时长与进度。双击播放
@Composable
internal fun PodcastProgramRow(
    program: PodcastProgram,
    progress: PodcastProgressEntry?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) DesktopColors.Hover else Color.Transparent)
            .hoverable(interaction)
            .onDoubleClick(onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            if (index != null && !hovered) {
                Text("$index", color = DesktopColors.TextGray, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            HoverReveal(revealed = hovered) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放",
                    tint = DesktopColors.TextPrimary,
                    modifier = Modifier.size(22.dp).clickable(onClick = onPlay)
                )
            }
        }
        Cover(program.coverUrl, 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = program.name,
                color = DesktopColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val source = podcastSubtitle(program.radioName, program.djName)
            if (source.isNotBlank()) {
                Text(source, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (program.description.isNotBlank()) {
                Text(
                    text = program.description,
                    color = DesktopColors.TextGray.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = formatProgramDate(program.createTimeMs),
            color = DesktopColors.TextGray,
            fontSize = 13.sp,
            modifier = Modifier.width(96.dp)
        )
        Column(Modifier.width(112.dp), horizontalAlignment = Alignment.End) {
            val duration = formatProgramDuration(program.durationMs)
            if (duration.isNotBlank()) {
                Text(duration, color = DesktopColors.TextGray, fontSize = 13.sp)
            }
            ProgramProgress(program, progress)
        }
    }
}

@Composable
private fun ProgramProgress(program: PodcastProgram, progress: PodcastProgressEntry?) {
    when {
        progress == null -> {
            val listeners = formatListenerCount(program.listenerCount)
            if (listeners.isNotBlank()) {
                Text(listeners, color = DesktopColors.TextGray.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1)
            }
        }
        progress.isFinished -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(14.dp))
            Text("已听完", color = DesktopColors.AccentText, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
        }
        progress.fraction > 0f -> LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.padding(top = 6.dp).width(72.dp).height(2.dp),
            color = DesktopColors.AccentText,
            trackColor = ProgressTrack
        )
    }
}

@Composable
internal fun PodcastInlineLoading() {
    HomeTabLoading(Modifier.fillMaxWidth().height(120.dp))
}

// 区块级的轻量错误提示，不占满整页
@Composable
internal fun PodcastInlineError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PodcastEdgePadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message.ifBlank { "加载失败" }, color = DesktopColors.TextGray, fontSize = 13.sp)
        TextButton(onClick = onRetry) { Text("重试", color = DesktopColors.TextPrimary) }
    }
}

@Composable
internal fun PodcastHint(text: String, modifier: Modifier = Modifier, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = DesktopColors.TextGray, fontSize = 14.sp)
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionText, color = DesktopColors.TextPrimary) }
        }
    }
}
