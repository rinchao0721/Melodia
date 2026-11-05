package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 播放页通用白底胶囊按钮（显示歌词、编辑卡片、恢复默认）
@Composable
fun PlayerCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    MelodiaButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MelodiaColors.TextPrimary,
            contentColor = MelodiaColors.InverseText,
            disabledContainerColor = MelodiaColors.Overlay.copy(alpha = 0.12f),
            disabledContentColor = MelodiaColors.TextPrimary.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs)
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
