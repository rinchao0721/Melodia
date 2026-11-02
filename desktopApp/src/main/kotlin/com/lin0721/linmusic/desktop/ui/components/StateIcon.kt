package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 二态图标切换：新图标弹性放大淡入、旧图标缩小淡出，让点击结果立刻可见
@Composable
fun StateIcon(active: Boolean, content: @Composable (Boolean) -> Unit) {
    val motion = LocalUiMotion.current
    AnimatedContent(
        targetState = active,
        transitionSpec = {
            motion.transform(
                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                    (scaleOut() + fadeOut())
            )
        },
        label = "stateIcon"
    ) { content(it) }
}

// 播放/暂停图标，切换时带弹性过渡
@Composable
fun PlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null
) {
    StateIcon(isPlaying) { playing ->
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.then(Modifier.size(size))
        )
    }
}
