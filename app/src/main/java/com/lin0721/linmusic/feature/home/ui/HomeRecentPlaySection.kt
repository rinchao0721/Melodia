package com.lin0721.linmusic.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist

// 紧凑横条列表，不带标题；手机 2 列 4 行、平板竖屏 3 列 3 行、平板横屏 4 列 2 行
// 手机竖排 4 行，压低行高，避免首屏被这一块占满
private val RowHeightCompact = 48.dp
private val RowHeightExpanded = 56.dp
private val ItemGapCompact = 7.dp
private val ItemGapExpanded = 9.dp

@Composable
fun RecentPlaySection(
    items: List<RecentPlaylist>,
    onClick: (RecentPlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val windowSizeClass = LocalMelodiaWindowSizeClass.current
    val orientationClass = LocalMelodiaOrientationClass.current
    val (columns, rows) = when {
        windowSizeClass == MelodiaWindowSizeClass.Expanded && orientationClass == MelodiaOrientationClass.Landscape -> 4 to 2
        windowSizeClass == MelodiaWindowSizeClass.Expanded -> 3 to 3
        else -> 2 to 4
    }

    val isCompact = windowSizeClass == MelodiaWindowSizeClass.Compact
    val rowHeight = if (isCompact) RowHeightCompact else RowHeightExpanded
    val itemGap = if (isCompact) ItemGapCompact else ItemGapExpanded

    val shownItems = items.take(columns * rows)

    Column(modifier = modifier.fillMaxWidth().padding(top = MelodiaSpacing.sm)) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HomeEdgePadding),
            horizontalArrangement = Arrangement.spacedBy(itemGap),
            verticalArrangement = Arrangement.spacedBy(itemGap),
            maxItemsInEachRow = columns
        ) {
            shownItems.forEach { item ->
                key(item.id) {
                    RecentPlayRow(
                        item = item,
                        rowHeight = rowHeight,
                        modifier = Modifier
                            .weight(1f)
                            .homeReflowBounds(),
                        onClick = { onClick(item) }
                    )
                }
            }
            // 残行补齐等宽占位，避免最后一行被拉宽
            repeat((columns - shownItems.size % columns) % columns) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecentPlayRow(
    item: RecentPlaylist,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(rowHeight)
            .pressable(MelodiaPress.Card) { onClick() }
            .clip(RoundedCornerShape(RadiusCompact))
            .background(MelodiaColors.Overlay.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = item.coverUrl.withCoverParam("200y200"),
            contentDescription = null,
            modifier = Modifier.size(rowHeight),
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() }
        )
        Text(
            text = item.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 9.dp, end = 8.dp)
        )
    }
}
