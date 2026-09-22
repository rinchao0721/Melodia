package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.PillRadius

// ────────────────────────────────────────────────────────────────────────────
// 听歌排行时间范围筛选行
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun PlaylistRecordFilterRow(
    selectedHistoryDate: String,
    onSelectedHistoryDateChange: (String) -> Unit,
    onLoadHistoryDetail: (String) -> Unit
) {
    val allFilters = listOf("最近一周", "所有时间")
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(allFilters, key = { it }) { filter ->
            val isSelected = filter == selectedHistoryDate
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .pressable(MelodiaPress.Pill) {
                        onSelectedHistoryDateChange(filter)
                        if (filter == "最近一周") {
                            onLoadHistoryDetail("weekly")
                        } else {
                            onLoadHistoryDetail("all")
                        }
                    }
                    .clip(RoundedCornerShape(PillRadius))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.LightGray,
                    fontSize = 14.sp
                )
            }
        }
    }
}
