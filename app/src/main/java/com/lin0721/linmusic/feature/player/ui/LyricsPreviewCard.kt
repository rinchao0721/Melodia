package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.darken
import com.lin0721.linmusic.core.ui.theme.lighten
import com.lin0721.linmusic.core.ui.theme.saturate
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.lyricLineKey

private val LYRICS_CARD_BLUR_RADIUS = 32.dp

private val ShowLyricsButtonHeight = 36.dp
private val ShowLyricsButtonTopGap = MelodiaSpacing.md

// 歌词预览区尺寸估算跟滚动定位共用同一套常量，避免两处数值漂移
private val LyricItemSpacing = 14.dp
private val LyricItemHeightNoTransEst = 36.dp
private val LyricItemHeightWithTransEst = 56.dp
private val LyricItemStride = LyricItemHeightNoTransEst + LyricItemSpacing
private const val VisibleLyricLines = 4
// 基准高度按「4 行单语原文」估算；当前行如果换行或带翻译，会在这个基准上动态往高长
private val LyricsBaseViewportHeight = LyricItemHeightNoTransEst * VisibleLyricLines + LyricItemSpacing * (VisibleLyricLines - 1)
private val LyricTranslationExtraHeight = 24.dp

// ────────────────────────────────────────────────────────────────────────────
// 折叠播放页的歌词预览卡（哑光渐变背景 + 自动滚动预览列表 + 底部展开按钮）
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun LyricsCard(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    isLoading: Boolean,
    base: Color,
    onOpenFullScreen: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluid_mesh")

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

    val vividBase = remember(base) { base.saturate(0.6f) }
    val fillColor = remember(vividBase) { vividBase.darken(0.35f) }
    val darkBlob = remember(vividBase) { vividBase.darken(0.15f) }
    // 未唱到的歌词颜色跟全屏歌词页的 textHighlight 用同一套配方，背景用的 vividBase 幅度较小，
    // 文字这里单独再提一档饱和度+明度，不然混完白会发灰
    val textVividBase = remember(base) { base.saturate(0.8f).lighten(1.0f) }
    val inactiveLyricColor = remember(textVividBase) { lerp(textVividBase, Color.White, 0.5f) }

    // 当前行实际换行数，由 LyricsPreview 里当前行 Text 的 onTextLayout 回报，用来动态撑高预览区
    var currentLineWrapLines by remember { mutableIntStateOf(1) }
    val hasCurrentTranslation = lyrics.getOrNull(currentIndex)?.translation != null
    val extraWrapLines = (currentLineWrapLines - 1).coerceAtLeast(0)
    val targetViewportHeight = LyricsBaseViewportHeight +
        LyricItemHeightNoTransEst * extraWrapLines +
        (if (hasCurrentTranslation) LyricTranslationExtraHeight else 0.dp)
    val animatedViewportHeight by animateDpAsState(
        targetValue   = targetViewportHeight,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label         = "lyrics_viewport_height"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            .clip(RoundedCornerShape(InfoCardRadius))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(LYRICS_CARD_BLUR_RADIUS)
                .drawBehind {
                    val baseSize = size.minDimension
                    drawSingleHueMesh(
                        fill = fillColor,
                        darkBlob = darkBlob,
                        darkCenter = Offset(size.width * darkCenterX, size.height * darkCenterY),
                        darkRadius = baseSize * darkRadiusScale
                    )
                }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                "歌词预览",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(MelodiaSpacing.md))

            Box(modifier = Modifier.height(animatedViewportHeight)) {
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
                        inactiveColor = inactiveLyricColor,
                        onCurrentLineLayout = { currentLineWrapLines = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(ShowLyricsButtonTopGap))

            MelodiaButton(
                onClick = onOpenFullScreen,
                modifier = Modifier.height(ShowLyricsButtonHeight),
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs)
            ) {
                Text(
                    "显示歌词",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun LyricsPreview(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    inactiveColor: Color,
    onCurrentLineLayout: (Int) -> Unit
) {
    val itemSpacingDp = LyricItemSpacing
    val itemHeightNoDpEst    = LyricItemHeightNoTransEst
    val itemHeightWithTransDpEst = LyricItemHeightWithTransEst
    val itemStrideDp      = LyricItemStride

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
            // 纯音乐段的空白占位行不占用预览区域，避免滚动时出现大段空隙
            if (line.text.isBlank()) return@itemsIndexed

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
                              else (0.85f - distance * 0.12f).coerceAtLeast(0.4f)
            val animatedAlpha by animateFloatAsState(
                targetValue   = targetAlpha,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label         = "lyric_alpha_$index"
            )

            val targetTransAlpha = if (isCurrent) 0.85f else 0.7f
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
                    fontSize   = if (isCurrent) 20.sp else 18.sp,
                    color      = if (isCurrent) Color.White else inactiveColor,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign  = TextAlign.Start,
                    onTextLayout = { if (isCurrent) onCurrentLineLayout(it.lineCount) },
                    modifier   = Modifier.fillMaxWidth()
                )
                if (line.translation != null) {
                    Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
                    Text(
                        text      = line.translation,
                        fontSize  = if (isCurrent) 15.sp else 14.sp,
                        color     = (if (isCurrent) Color.White else inactiveColor).copy(alpha = animatedTransAlpha),
                        textAlign = TextAlign.Start,
                        modifier  = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
