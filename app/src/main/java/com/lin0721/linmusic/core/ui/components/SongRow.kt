package com.lin0721.linmusic.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 歌曲行的轻量 UI 数据模型，各调用方把自己的领域模型（Track/QueueItem 等）映射到这里
data class SongRowData(
    val id: Long,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val isVip: Boolean = false,
    val durationText: String? = null
)

// 通用歌曲行：Playlist/Artist/Home/Search 共用，compact 控制紧凑尺寸，index 控制是否显示序号列
@Composable
fun SongRow(
    data: SongRowData,
    isActive: Boolean = false,
    isPlaying: Boolean = true,
    compact: Boolean = false,
    index: Int? = null,
    onClick: () -> Unit,
    onArtistClick: (() -> Unit)? = null,
    trailingSlot: @Composable RowScope.() -> Unit = {}
) {
    val coverSize = if (compact) 42.dp else 48.dp
    val coverShape: Shape = RoundedCornerShape(RadiusCompact)
    val titleFontSize = if (compact) 13.sp else 15.sp
    val artistFontSize = if (compact) 11.sp else 13.sp
    val coverSpacing = if (compact) 10.dp else 12.dp
    val verticalPadding = if (compact) 9.dp else 10.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MelodiaSpacing.md, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Text(
                text = index.toString(),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Start
            )
        }

        val context = LocalContext.current
        val imageRequest = remember(data.coverUrl) {
            if (!data.coverUrl.isNullOrBlank()) {
                ImageRequest.Builder(context)
                    .data("${data.coverUrl}?param=100y100")
                    .crossfade(true)
                    .build()
            } else {
                null
            }
        }
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = data.title,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() },
            modifier = Modifier.size(coverSize).clip(coverShape)
        )
        Spacer(Modifier.width(coverSpacing))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    PlayingEqualizerBars(color = MaterialTheme.colorScheme.primary, animate = isPlaying)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = data.title,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = titleFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (data.isVip) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(3.dp))
                            .padding(horizontal = MelodiaSpacing.xs, vertical = 1.dp)
                    ) {
                        Text(
                            text = "VIP",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 10.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(MelodiaSpacing.xxs))
            Text(
                text = data.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = artistFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onArtistClick != null) Modifier.clickable { onArtistClick() } else Modifier
            )
        }

        if (data.durationText != null) {
            Text(
                text = data.durationText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = MelodiaSpacing.sm)
            )
        }

        trailingSlot()
    }
}

// 播放中标识：歌名前的三根音柱。animate 为 false（暂停）时，各自定格在暂停那一刻的高度上，而不是跳到另一套固定姿态；
// 用 Animatable 手动循环而非 InfiniteTransition，是因为暂停时需要保留 value 原地冻结，恢复播放时也从当前高度接着动
@Composable
private fun PlayingEqualizerBars(color: Color, animate: Boolean) {
    val bar1 = remember { Animatable(4f) }
    val bar2 = remember { Animatable(13f) }
    val bar3 = remember { Animatable(8f) }

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        coroutineScope {
            launch {
                while (isActive) {
                    bar1.animateTo(14f, tween(600, easing = FastOutSlowInEasing))
                    bar1.animateTo(4f, tween(600, easing = FastOutSlowInEasing))
                }
            }
            launch {
                while (isActive) {
                    bar2.animateTo(6f, tween(750, easing = FastOutSlowInEasing))
                    bar2.animateTo(13f, tween(750, easing = FastOutSlowInEasing))
                }
            }
            launch {
                while (isActive) {
                    bar3.animateTo(15f, tween(500, easing = FastOutSlowInEasing))
                    bar3.animateTo(8f, tween(500, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(16.dp)
    ) {
        listOf(bar1.value, bar2.value, bar3.value).forEach { barHeight ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
    }
}

// 可拖拽排序的歌曲行，供播放队列与歌单重排使用；
// enabled=false 时卸载手势，用于保存等不该再接受拖拽的时段
@Composable
fun DraggableSongRow(
    data: SongRowData,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isPlayed: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    enabled: Boolean = true
) {
    val liftSpec = remember { spring<Dp>(stiffness = Spring.StiffnessMediumLow) }
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = liftSpec,
        label = "drag_elev"
    )
    val isLifted = isDragging || dragOffsetY != 0f
    val bgColor by animateColorAsState(
        when {
            isDragging -> MaterialTheme.colorScheme.surfaceVariant
            isCurrent -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.background
        },
        label = "row_bg"
    )

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLifted) Modifier
                    .zIndex(1f)
                    .graphicsLayer { translationY = dragOffsetY }
                    .shadow(elevation, RoundedCornerShape(8.dp))
                else Modifier
            )
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = MelodiaSpacing.sm, top = MelodiaSpacing.sm, bottom = MelodiaSpacing.sm)
            .then(if (isPlayed) Modifier.alpha(0.5f) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            val context = LocalContext.current
            val imageRequest = remember(data.coverUrl) {
                if (!data.coverUrl.isNullOrBlank()) {
                    ImageRequest.Builder(context)
                        .data("${data.coverUrl}?param=100y100")
                        .crossfade(true)
                        .build()
                } else {
                    null
                }
            }
            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(RadiusCompact))
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(RadiusCompact))
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                data.title,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                data.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 拖拽手柄

        Icon(
            Icons.Default.Menu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(44.dp)
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { currentOnDragStart() },
                                onDrag = { change, offset ->
                                    change.consume()
                                    currentOnDrag(offset.y)
                                },
                                onDragEnd = { currentOnDragEnd() },
                                onDragCancel = { currentOnDragEnd() }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(12.dp)
        )
    }
}
