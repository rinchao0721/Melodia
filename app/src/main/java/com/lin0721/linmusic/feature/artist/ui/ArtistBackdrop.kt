package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.theme.extractBaseColorFromUrl

// 顶部大图背景层：固定不动，由上层滚动列表的不透明内容自然覆盖，并回传封面主色
@Composable
fun BoxScope.ArtistBackdrop(
    artist: ArtistDetailInfo,
    dominantColor: Color,
    onDominantColorChange: (Color) -> Unit
) {
    val context = LocalContext.current
    val bgUrl = artist.cover.ifEmpty { artist.avatar }

    // 取色跟背景图显示解码完全脱钩，单独发一次固定尺寸的请求
    LaunchedEffect(bgUrl) {
        if (bgUrl.isNotEmpty()) {
            onDominantColorChange(extractBaseColorFromUrl(context, bgUrl))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(if (bgUrl.isNotEmpty()) "$bgUrl?param=640y640" else "")
                .allowHardware(false)
                .crossfade(true)
                .build(),
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() },
            modifier = Modifier.fillMaxSize()
        )
    }

    // dominantColor 独立底色层：压在封面图上方，高度延伸到操作栏底部，与 Tab 栏接在一起
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp)
            .align(Alignment.TopCenter)
    ) {
        // 纯色底色向下延伸,底边缘与底部的 tab_bar 接在一起
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(dominantColor)
        )
    }
}
