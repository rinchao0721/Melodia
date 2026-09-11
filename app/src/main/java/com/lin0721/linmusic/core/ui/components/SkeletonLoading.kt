package com.lin0721.linmusic.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.SurfaceLight

// 扫光骨架屏背景：surfaceVariant/surface 之间无限循环平移渐变，贴合项目深色低调基调
@Composable
fun Modifier.shimmerBackground(shape: Shape = RectangleShape): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 400f, 0f)
    )
    return this.background(brush = brush, shape = shape)
}

// 封面图未加载/加载失败时的静态占位：色块 + 居中音符图标，不做动画。
// 配合 AntiFlickerCoverImage 叠层使用，外层的 .clip() 决定最终形状
@Composable
fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceLight),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxSize(0.34f)
        )
    }
}

// 无占位闪烁的封面异步加载：占位图常驻底层，图片直接叠在顶层覆盖，不走 loading/success 插槽整体切换，
// 避免 SubcomposeAsyncImage 那种切片方式在命中内存缓存时仍会闪现一帧占位图。外层 modifier 负责尺寸/裁剪
@Composable
fun AntiFlickerCoverImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var isLoaded by remember(url) { mutableStateOf(false) }
    var isError by remember(url) { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (url.isEmpty() || !isLoaded || isError) {
            CoverPlaceholder()
        }

        if (url.isNotEmpty()) {
            AsyncImage(
                model = remember(url) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false)
                        .build()
                },
                contentDescription = null,
                contentScale = contentScale,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            isLoaded = true
                            isError = false
                        }
                        is AsyncImagePainter.State.Error -> {
                            isLoaded = false
                            isError = true
                        }
                        else -> Unit
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// 模拟一行 EntityRow/SongRow：方块封面 + 两行条状高光，供搜索结果首屏加载使用
@Composable
fun SearchResultRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            modifier = Modifier
                .size(48.dp)
                .shimmerBackground(MaterialTheme.shapes.extraSmall)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Spacer(
                modifier = Modifier
                    .width(160.dp)
                    .height(15.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(8.dp))
            Spacer(
                modifier = Modifier
                    .width(100.dp)
                    .height(13.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
        }
    }
}

// 模拟发现页热搜/歌单网格：两列条状卡片，供发现页首屏加载使用
@Composable
fun DiscoverySectionSkeleton() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.md)) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = MelodiaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
            ) {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .shimmerBackground(RoundedCornerShape(8.dp))
                )
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .shimmerBackground(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}
