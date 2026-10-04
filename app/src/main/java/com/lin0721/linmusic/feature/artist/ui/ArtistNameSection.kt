package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.ui.interaction.copyTextOnLongPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 顶占位透明高，用于显示出大图 Header，底部叠加歌手名与译名
@Composable
fun ArtistNameSection(artist: ArtistDetailInfo, progress: Float, tintColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        // 滚动色调蒙层：随折叠进度加深，与悬浮返回栏的淡入节奏保持同步
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tintColor.copy(alpha = progress))
        )

        // 歌手名称和粉丝数区域的渐变蒙层
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
                .padding(horizontal = MelodiaSpacing.md)
                .padding(bottom = MelodiaSpacing.md, top = MelodiaSpacing.lg)
        ) {
            Text(
                text = artist.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 48.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.copyTextOnLongPress(artist.name, clipboardLabel = "歌手名")
            )

            Spacer(Modifier.height(6.dp))

            // 显示歌手的译名
            val chineseName = (artist.trans?.takeIf { it.isNotBlank() }
                ?: artist.alias?.firstOrNull { it.isNotBlank() })?.takeIf { it != artist.name }

            if (!chineseName.isNullOrBlank()) {
                Text(
                    text = chineseName,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
