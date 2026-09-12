package com.lin0721.linmusic.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist

// 双列紧凑横条，一屏放得下六条。
private const val MAX_ITEMS = 6
private val RowHeight = 56.dp

@Composable
fun RecentPlaySection(
    items: List<RecentPlaylist>,
    onClick: (RecentPlaylist) -> Unit
) {
    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.sm)) {
        Text(
            text = "最近播放",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = HomeEdgePadding, end = HomeEdgePadding, bottom = 13.dp)
        )

        Column(
            modifier = Modifier.padding(horizontal = HomeEdgePadding),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items.take(MAX_ITEMS).chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    rowItems.forEach { item ->
                        RecentPlayRow(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { onClick(item) }
                        )
                    }
                    // 奇数条时补等宽占位，避免最后一条被拉成整行
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecentPlayRow(
    item: RecentPlaylist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(RowHeight)
            .pressable(MelodiaPress.Card) { onClick() }
            .clip(RoundedCornerShape(RadiusCompact))
            .background(Color.White.copy(alpha = 0.08f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = item.coverUrl.withCoverParam("200y200"),
            contentDescription = null,
            modifier = Modifier.size(RowHeight),
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() }
        )
        Text(
            text = item.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 9.dp, end = 8.dp)
        )
    }
}
