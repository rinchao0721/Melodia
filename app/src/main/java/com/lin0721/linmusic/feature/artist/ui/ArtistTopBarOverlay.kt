package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.headerCoverBase
import com.lin0721.linmusic.core.ui.theme.melodiaStatusBarTopPadding
import com.lin0721.linmusic.core.ui.theme.paneBackdrop
import com.lin0721.linmusic.core.ui.theme.uiTween

private val TOP_BAR_HEIGHT = 56.dp

// 悬浮返回栏：底色随滚动进度淡入，折叠后补显歌手名
@Composable
fun ArtistTopBarOverlay(
    artistName: String,
    progress: Float,
    dominantColor: Color,
    onBack: () -> Unit,
    // 仅音乐 Tab 提供歌曲搜索入口；searchOpen 为 true 时按钮变为关闭
    showSearch: Boolean = false,
    searchOpen: Boolean = false,
    onSearchClick: () -> Unit = {}
) {
    val statusBarHeight = melodiaStatusBarTopPadding()
    val overlayHeight = TOP_BAR_HEIGHT + statusBarHeight
    val overlayBgAlpha = progress
    val isCollapsed = progress >= 0.8f
    val animatedDominant by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = uiTween(800),
        label = "artist_topbar_color"
    )
    val fillColor = headerCoverBase(animatedDominant, 0.35f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(overlayHeight)
            .then(
                if (fillColor == null) {
                    Modifier.paneBackdrop(MelodiaColors.Background, alpha = { overlayBgAlpha })
                } else {
                    Modifier.background(fillColor.copy(alpha = overlayBgAlpha))
                }
            )
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

        if (showSearch) {
            MelodiaIconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = MelodiaSpacing.xs)
            ) {
                Icon(
                    imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (searchOpen) "关闭搜索" else "搜索歌曲",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
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
