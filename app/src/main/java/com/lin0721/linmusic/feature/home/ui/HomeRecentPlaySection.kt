package com.lin0721.linmusic.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import com.lin0721.linmusic.core.ui.icons.Leaderboard
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.home.domain.HomeCardKind
import com.lin0721.linmusic.feature.home.domain.HomeSectionCard

// 紧凑横条列表，不带标题；手机 2 列 4 行、平板竖屏 3 列 3 行、平板横屏 4 列 2 行
// 手机竖排 4 行，压低行高，避免首屏被这一块占满
private val RowHeightCompact = 48.dp
private val RowHeightExpanded = 56.dp
private val ItemGapCompact = 8.dp
private val ItemGapExpanded = 9.dp

@Composable
fun RecentPlaySection(
    items: List<HomeSectionCard>,
    isPersonalFm: Boolean,
    onClick: (HomeSectionCard) -> Unit,
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
                key("${item.kind}_${item.entityId}") {
                    RecentPlayRow(
                        item = item,
                        rowHeight = rowHeight,
                        isActive = false,
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
    item: HomeSectionCard,
    rowHeight: Dp,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val rowBg = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MelodiaColors.Overlay.copy(alpha = 0.1f)
    }
    Row(
        modifier = modifier
            .height(rowHeight)
            .pressable(MelodiaPress.Card) { onClick() }
            .clip(RoundedCornerShape(4.dp))
            .background(rowBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.kind == HomeCardKind.QUICK_ENTRY) {
            val icon = MelodiaIcons.Cloud
            val iconBg = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            }
            val iconTint = if (isActive) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primary
            }
            Box(
                modifier = Modifier
                    .size(rowHeight)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = item.coverUrl.withCoverParam("200y200"),
                contentDescription = null,
                modifier = Modifier.size(rowHeight),
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() }
            )
        }
        Text(
            text = item.title,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
        )
    }
}
