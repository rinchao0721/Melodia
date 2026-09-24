package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.melodiaStatusBarTopPadding

private val TOP_BAR_HEIGHT = 56.dp

// 悬浮返回栏：底色随滚动进度淡入，折叠后补显歌手名
@Composable
fun ArtistTopBarOverlay(
    artistName: String,
    progress: Float,
    dominantColor: Color,
    onBack: () -> Unit
) {
    val statusBarHeight = melodiaStatusBarTopPadding()
    val overlayHeight = TOP_BAR_HEIGHT + statusBarHeight
    val overlayBgAlpha = progress
    val isCollapsed = progress >= 0.8f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(overlayHeight)
            .background(dominantColor.copy(alpha = overlayBgAlpha))
            .padding(top = statusBarHeight)
            .zIndex(8f)
    ) {
        MelodiaIconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = MelodiaSpacing.xs)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
        }

        if (isCollapsed) {
            Text(
                text = artistName,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 60.dp)
            )
        }
    }
}
