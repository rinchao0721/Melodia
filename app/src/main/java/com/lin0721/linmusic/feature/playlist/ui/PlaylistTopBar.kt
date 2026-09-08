package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.interaction.pressScale
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlin.math.roundToInt

// ────────────────────────────────────────────────────────────────────────────
// 固定 Overlay 顶栏
// 进入时背景 alpha=0（完全透明，视觉上不存在）
// 滚动后 alpha 随 progress 增大直到完全不透明
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun PlaylistTopBar(
    title: String,
    progress: Float,
    overlayHeight: Dp,
    statusBarHeight: Dp,
    dominantColor: Color,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(overlayHeight)
            .background(dominantColor.copy(alpha = progress))
            .padding(top = statusBarHeight) // 内容区域被挤到状态栏下方
            .zIndex(8f)
    ) {
        // 返回键
        MelodiaIconButton(
            onClick  = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back",
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
        }
        // 歌单名称：随着滚动淡入及向上微移
        val titleAlpha = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val titleOffsetY = lerp(8.dp, 0.dp, titleAlpha)
        Text(
            text       = title,
            color      = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize   = 17.sp,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 60.dp)
                .offset(y = titleOffsetY)
                .alpha(titleAlpha)
        )
    }
}

// 播放按钮的"共享元素"式停靠：全程跟随它在歌单页里的真实位置，到达停靠位后锁停。
// 用 provider 而不是直接传 Float，是为了让状态读取发生在 offset{} 的布局阶段而不是
// 组合阶段，避免每次位置更新都触发整棵树重组导致跟手时明显的抖动
@Composable
fun BoxScope.PlaylistDockedPlayButton(
    dockedOffsetYProvider: () -> Float,
    isCurrentlyPlayingThis: Boolean,
    onPlayAll: () -> Unit
) {
    val playInteraction = remember { MutableInteractionSource() }
    FloatingActionButton(
        onClick        = onPlayAll,
        containerColor = MaterialTheme.colorScheme.primary,
        shape          = CircleShape,
        interactionSource = playInteraction,
        modifier       = Modifier
            .pressScale(MelodiaPress.Transport, playInteraction)
            .align(Alignment.TopEnd)
            .padding(end = MelodiaSpacing.md)
            .offset { IntOffset(0, dockedOffsetYProvider().roundToInt()) }
            .size(56.dp)
            .zIndex(10f)
            .shadow(8.dp, CircleShape)
    ) {
        Icon(
            imageVector = if (isCurrentlyPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isCurrentlyPlayingThis) "暂停" else "播放",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(32.dp)
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 拖拽调整歌曲顺序专属顶栏
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun PlaylistReorderTopBar(
    overlayHeight: Dp,
    statusBarHeight: Dp,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(overlayHeight)
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarHeight)
            .zIndex(8f)
    ) {
        // 左侧取消
        com.lin0721.linmusic.core.ui.components.MelodiaTextButton(
            onClick = onCancel,
            enabled = !isSaving,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = MelodiaSpacing.sm)
        ) {
            Text(
                text = "取消",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp
            )
        }

        // 中间标题
        Text(
            text = "调整歌曲顺序",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        // 右侧完成 / 保存中指示器
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = MelodiaSpacing.sm),
            contentAlignment = Alignment.Center
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else {
                com.lin0721.linmusic.core.ui.components.MelodiaTextButton(
                    onClick = onDone
                ) {
                    Text(
                        text = "完成",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

