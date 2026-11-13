package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.QualityBadge
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

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
            maxLines = 1
        )
    }
}
