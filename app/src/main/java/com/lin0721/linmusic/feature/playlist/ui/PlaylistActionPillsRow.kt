package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 歌单详情页自建歌单快捷操作小胶囊行
@Composable
fun PlaylistActionPillsRow(
    isOwnedPlaylist: Boolean,
    isTracksEmpty: Boolean,
    currentSortOption: PlaylistSortOption,
    onAddMusicClick: () -> Unit,
    onEditOrderClick: () -> Unit,
    onSortClick: () -> Unit,
    onEditInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOwnedPlaylist) return

    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = MelodiaSpacing.md)
            .padding(bottom = MelodiaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 添加音乐
        ActionPill(
            icon = Icons.Rounded.Add,
            label = "添加",
            enabled = true,
            onClick = onAddMusicClick
        )

        // 2. 调整顺序
        ActionPill(
            icon = Icons.AutoMirrored.Rounded.Sort,
            label = "编辑",
            enabled = !isTracksEmpty,
            onClick = onEditOrderClick
        )

        // 3. 切换排序
        ActionPill(
            icon = Icons.Rounded.FilterList,
            label = currentSortOption.label,
            enabled = !isTracksEmpty,
            onClick = onSortClick
        )

        // 4. 编辑信息
        ActionPill(
            icon = Icons.Rounded.Edit,
            label = "歌单信息",
            enabled = true,
            onClick = onEditInfoClick
        )
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (enabled) {
                    Modifier.pressable(MelodiaPress.Pill) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
