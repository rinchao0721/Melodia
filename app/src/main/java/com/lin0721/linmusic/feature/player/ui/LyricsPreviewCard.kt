package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.darken
import com.lin0721.linmusic.core.ui.theme.lighten
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.lyricLineKey

// 卡片尺寸比全屏背景小得多，模糊半径按比例调小，避免整块糊成一片看不出光斑层次
private val LYRICS_CARD_BLUR_RADIUS = 32.dp

// ────────────────────────────────────────────────────────────────────────────
// 折叠播放页的歌词预览卡（流体光雾背景 + 自动滚动预览列表）
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun LyricsCard(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    isLoading: Boolean,
    base: Color,
    highlightColor: Color,
    onOpenFullScreen: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluid_mesh")

    // 左上角光斑动画
    val lightCenterX by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "light_x"
    )
    val lightCenterY by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "light_y"
    )
    val lightRadiusScale by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "light_radius"
    )

    // 右下角光斑动画
    val darkCenterX by infiniteTransition.animateFloat(
        initialValue = 1.20f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dark_x"
    )
    val darkCenterY by infiniteTransition.animateFloat(
        initialValue = 1.20f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(13000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dark_y"
    )
    val darkRadiusScale by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dark_radius"
    )

    val fillColor = remember(base) { base.darken(0.35f) }
    val lightBlob = remember(base) { base.lighten(0.05f) }
    val darkBlob = remember(base) { base.darken(0.15f) }

    val cardWidth = (LocalConfiguration.current.screenWidthDp - 32).dp
    val cardHeight = cardWidth * 0.88f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            .height(cardHeight)
            .clip(RoundedCornerShape(InfoCardRadius))
            .clickable(onClick = onOpenFullScreen)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(LYRICS_CARD_BLUR_RADIUS)
                .drawBehind {
                    val baseSize = size.minDimension
                    drawSingleHueMesh(
                        fill = fillColor,
                        lightBlob = lightBlob,
                        lightCenter = Offset(size.width * lightCenterX, size.height * lightCenterY),
                        lightRadius = baseSize * lightRadiusScale,
                        darkBlob = darkBlob,
                        darkCenter = Offset(size.width * darkCenterX, size.height * darkCenterY),
                        darkRadius = baseSize * darkRadiusScale
                    )
                }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "歌词",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Rounded.OpenInFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onOpenFullScreen() }
                        .padding(MelodiaSpacing.xs)
                )
            }

            Spacer(modifier = Modifier.height(MelodiaSpacing.md))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                LyricsPreview(
                    lyrics = lyrics,
                    currentIndex = currentIndex,
                    highlightColor = highlightColor
                )
            }
        }
    }
}

@Composable
fun LyricsPreview(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    highlightColor: Color
) {
    val itemSpacingDp = 14.dp
    val itemHeightNoDpEst    = 36.dp
    val itemHeightWithTransDpEst = 56.dp
    val itemStrideDp      = itemHeightNoDpEst + itemSpacingDp

    val density       = LocalDensity.current
    val lazyListState = rememberLazyListState()
    var cardHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentIndex) {
        if (currentIndex < 0 || currentIndex >= lyrics.size) return@LaunchedEffect

        val cardHeightDp = with(density) { cardHeightPx.toDp() }
        val linesAboveCentre = (cardHeightDp / 2 / itemStrideDp).toInt()

        if (currentIndex < linesAboveCentre) {
            lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
            return@LaunchedEffect
        }
        val hasTranslation   = lyrics[currentIndex].translation != null
        val itemHeightPx     = with(density) {
            if (hasTranslation) itemHeightWithTransDpEst.toPx() else itemHeightNoDpEst.toPx()
        }
        val centreOffsetPx = -(((cardHeightPx - itemHeightPx) / 2f).toInt())
        lazyListState.animateScrollToItem(
            index       = currentIndex,
            scrollOffset = centreOffsetPx
        )
    }

    LazyColumn(
        state   = lazyListState,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .onSizeChanged { cardHeightPx = it.height.toFloat() },
        verticalArrangement = Arrangement.spacedBy(itemSpacingDp),
        userScrollEnabled   = false,
        contentPadding = PaddingValues(top = 0.dp, bottom = with(density) { (cardHeightPx / 2).toDp() })
    ) {
        itemsIndexed(items = lyrics, key = ::lyricLineKey) { index, line ->
            val isCurrent = index == currentIndex
            val distance  = kotlin.math.abs(index - currentIndex).coerceAtMost(4)

            val targetScale = if (isCurrent) 1.15f
                              else (1f - distance * 0.07f).coerceAtLeast(0.85f)
            val animatedScale by animateFloatAsState(
                targetValue   = targetScale,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label         = "lyric_scale_$index"
            )

            val targetAlpha = if (isCurrent) 1f
                              else (0.55f - distance * 0.1f).coerceAtLeast(0.2f)
            val animatedAlpha by animateFloatAsState(
                targetValue   = targetAlpha,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label         = "lyric_alpha_$index"
            )

            val targetTransAlpha = 0.65f
            val animatedTransAlpha by animateFloatAsState(
                targetValue   = targetTransAlpha,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label         = "lyric_trans_alpha_$index"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                        alpha  = animatedAlpha
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
            ) {
                Text(
                    text       = line.text,
                    fontSize   = 20.sp,
                    color      = if (isCurrent) Color.White else highlightColor,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign  = TextAlign.Start,
                    modifier   = Modifier.fillMaxWidth()
                )
                if (line.translation != null) {
                    Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
                    Text(
                        text      = line.translation,
                        fontSize  = 15.sp,
                        color     = Color.White.copy(alpha = animatedTransAlpha),
                        textAlign = TextAlign.Start,
                        modifier  = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
