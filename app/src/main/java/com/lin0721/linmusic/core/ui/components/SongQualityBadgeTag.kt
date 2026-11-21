package com.lin0721.linmusic.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.lin0721.linmusic.core.model.QualityBadge
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 最高音质徽标出现/切换淡入淡出过渡，尊重减少动画设置
@Composable
fun AnimatedSongQualityBadge(badge: QualityBadge?, modifier: Modifier = Modifier) {
    val motion = LocalUiMotion.current
    AnimatedContent(
        targetState = badge,
        transitionSpec = {
            motion.transform(
                (fadeIn(motion.tween(180)) togetherWith fadeOut(motion.tween(150)))
                    .using(SizeTransform(clip = false) { _, _ -> motion.tween(180) })
            )
        },
        contentAlignment = Alignment.CenterStart,
        label = "songQualityBadge",
        modifier = modifier
    ) { target ->
        if (target != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SongQualityBadgeTag(target)
                Spacer(Modifier.width(MelodiaSpacing.xs))
            }
        }
    }
}

// 歌曲列表里歌手名前的最高音质描边标签
@Composable
fun SongQualityBadgeTag(badge: QualityBadge, modifier: Modifier = Modifier) {
    val color = badge.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), shape)
            .border(BorderStroke(0.5.dp, color), shape)
            .padding(horizontal = MelodiaSpacing.xs, vertical = 1.dp)
    ) {
        Text(
            text = badge.label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 11.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}
