package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.paneBackdrop

// 分页 Tab 栏，索引与 ArtistContent 的内容分支一一对应
@Composable
fun ArtistTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("音乐", "专辑", "关于艺人")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .paneBackdrop(MaterialTheme.colorScheme.background)
            .padding(horizontal = MelodiaSpacing.md, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .pressable(MelodiaPress.Tab) { onTabSelected(index) }
                    .padding(vertical = MelodiaSpacing.xs)
            ) {
                Text(
                    text = title,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                // 指示器：选中时显示横线，未选中时透明
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(28.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}
