package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
// 每日推荐历史日期筛选行
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun PlaylistHistoryDateRow(
    historyDates: List<String>,
    selectedHistoryDate: String,
    onSelectedHistoryDateChange: (String) -> Unit,
    onLoadHistoryDetail: (String) -> Unit,
    onLoadDailyRecommend: () -> Unit
) {
    val allDates = remember(historyDates) {
        listOf("今天") + historyDates
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        this@LazyRow.items(allDates, key = { it }) { date ->
            val isSelected = date == selectedHistoryDate
            val displayText = if (date == "今天") {
                "今天"
            } else {
                val parts = date.split("-")
                if (parts.size == 3) "${parts[1]}/${parts[2]}" else date
            }

            Box(
                modifier = Modifier
                    .height(36.dp)
                    .pressable(MelodiaPress.Pill) {
                        onSelectedHistoryDateChange(date)
                        if (date == "今天") {
                            onLoadDailyRecommend()
                        } else {
                            onLoadHistoryDetail(date)
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
                    text = displayText,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.LightGray,
                    fontSize = 14.sp
                )
            }
        }
    }
}
