package com.lin0721.linmusic.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.CircularProgressIndicator
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
import coil.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.feature.profile.domain.ProfileListenRankItem

private val RANK_SUB_TABS = listOf("所有时间", "最近一周")

// 「听歌排行」Tab 的内容项，作为外层 LazyColumn 的普通 item 加入，理由同 profilePlaylistItems
fun LazyListScope.profileRecordItems(
    items: List<ProfileListenRankItem>,
    isLoading: Boolean,
    subTab: Int,
    onSubTabSelected: (Int) -> Unit
) {
    item(key = "record_subtabs") {
        ProfileRankSubTabs(subTab = subTab, onSubTabSelected = onSubTabSelected)
    }

    if (isLoading) {
        item(key = "record_loading") {
            Box(Modifier.fillMaxSize().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
        }
        return
    }
    if (items.isEmpty()) {
        item(key = "record_empty") {
            EmptyState(icon = Icons.Rounded.Leaderboard, title = "暂无听歌排行数据")
        }
        return
    }

    itemsIndexed(items, key = { index, item -> "${subTab}_${item.songId}_$index" }) { index, item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                color = TextGray,
                fontSize = 12.sp,
                modifier = Modifier.width(24.dp)
            )

            AsyncImage(
                model = "${item.albumCoverUrl}?param=120y120",
                contentDescription = item.songName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(RadiusCompact)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 11.dp, end = 8.dp)
            ) {
                Text(
                    text = item.songName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = item.artistName,
                    color = TextGray,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${item.playCount}次",
                color = TextGray,
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }
    }
}

// 「所有时间/最近一周」子 Tab：不用通用 FilterChipsRow，是因为它自带的 20dp 内容边距
// 加上胶囊自身 20dp 内边距会让文字比上面「歌单/动态/听歌排行」多缩进一倍，看着没对齐；
// 这里直接把胶囊容器本身跟其它区块一样固定在 20dp，垂直间距也跟其它行保持一致
@Composable
private fun ProfileRankSubTabs(
    subTab: Int,
    onSubTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = MelodiaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
    ) {
        RANK_SUB_TABS.forEachIndexed { index, title ->
            val isSelected = index == subTab
            Box(
                modifier = Modifier
                    .pressable(MelodiaPress.Pill) { onSubTabSelected(index) }
                    .clip(RoundedCornerShape(PillRadius))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
