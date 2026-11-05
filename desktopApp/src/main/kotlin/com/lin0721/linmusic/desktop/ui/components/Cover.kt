package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaStyle
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

// 网易封面按显示尺寸请求缩略图，避免桌面端拉原图
fun sizedCoverUrl(url: String?, px: Int): String? {
    if (url.isNullOrBlank()) return null
    if (!url.startsWith("http")) return url
    return if (url.contains("?")) url else "$url?param=${px}y$px"
}

private val SquareCoverShape = RoundedCornerShape(0.dp)

@Composable
fun Cover(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    // 显示尺寸随拖动连续变化时传固定的请求尺寸，否则每帧换一个缩略图地址会不停重新加载
    requestSize: Dp = size
) {
    // 皮肤指定封面圆角时覆盖圆角矩形，圆形与直角保持原样
    val coverRadius = LocalMelodiaStyle.current.coverRadius
    val effectiveShape = if (coverRadius != null && shape != CircleShape && shape != RectangleShape && shape != SquareCoverShape) {
        RoundedCornerShape(coverRadius)
    } else {
        shape
    }
    Box(modifier.size(size).clip(effectiveShape).background(DesktopColors.CoverPlaceholder)) {
        val model = sizedCoverUrl(url, (requestSize.value * 2).toInt())
        if (model != null) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { CoverFallback(size) }
            )
        } else {
            CoverFallback(size)
        }
    }
}

@Composable
private fun CoverFallback(size: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = DesktopColors.TextGray, modifier = Modifier.size(size * 0.4f))
    }
}
