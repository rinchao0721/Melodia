package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.interaction.pressScale
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius

// 操作控制行：粉丝数、关注状态、更多菜单与随机/全部播放
@Composable
fun ArtistActionBar(
    artist: ArtistDetailInfo,
    fansCount: Long,
    isFollowed: Boolean,
    onFollowClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    // 操作栏背景线性渐变
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp) // 固定高度为 80.dp
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.60f), // 顶部：80% 不透明
                        MaterialTheme.colorScheme.background.copy(alpha = 1.00f)  // 底部：90% 不透明
                    )
                )
            )
            .padding(start = MelodiaSpacing.md, end = MelodiaSpacing.md, top = MelodiaSpacing.xs, bottom = MelodiaSpacing.xs),
        verticalAlignment = Alignment.Bottom
    ) {
        // 左侧控制列 (包含粉丝数与头像控制项)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "共有 ${formatFansCount(fansCount)} 位听众",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(MelodiaSpacing.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 圆角头像小缩略图
                AsyncImage(
                    model = "${artist.avatar}?param=100y100",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                )

                Spacer(Modifier.width(12.dp))

                // 关注状态卡片
                Box(
                    modifier = Modifier
                        .pressable(
                            style = MelodiaPress.Action,
                            shape = RoundedCornerShape(PillRadius),
                            onClick = onFollowClick
                        )
                        .border(
                            border = if (isFollowed) BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                            else BorderStroke(0.dp, Color.Transparent),
                            shape = RoundedCornerShape(PillRadius)
                        )
                        .background(if (isFollowed) Color.Transparent else MaterialTheme.colorScheme.primary)
                        .padding(horizontal = MelodiaSpacing.md, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isFollowed) "已关注" else "关注",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(MelodiaSpacing.md))

                MelodiaIconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
        }

        // 右侧播放控制项
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Bottom)
        ) {
            // 随机播放
            MelodiaIconButton(
                onClick = onPlayAll,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Shuffle, "Shuffle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 红色圆圈大播放键
            val playInteraction = remember { MutableInteractionSource() }
            FloatingActionButton(
                onClick = onPlayAll,
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                interactionSource = playInteraction,
                modifier = Modifier
                    .pressScale(MelodiaPress.Transport, playInteraction)
                    .size(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, "Play All", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
            }
        }
    }
}
