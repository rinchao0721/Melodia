package com.lin0721.linmusic.feature.home.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.ForYouHotColor
import com.lin0721.linmusic.core.ui.theme.ForYouRadarColor
import com.lin0721.linmusic.core.ui.theme.ForYouRoamingColor
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.feature.home.data.PersonalizedPlaylist
import com.lin0721.linmusic.feature.home.domain.ToplistInfo
import com.lin0721.linmusic.feature.home.domain.pickHotlist

// 设计稿 fm_ui7.html 的基准正方形尺寸，按卡片实宽等比缩放
private const val REF_CARD_PX = 138f
private val TranslucentWhiteFallback = Color.White.copy(alpha = 0.22f)

internal sealed interface ForYouArtwork {
    data class PersonalFm(
        val coverUrls: List<String>,
        val isPlaying: Boolean
    ) : ForYouArtwork

    data class HotChart(val coverUrl: String) : ForYouArtwork

    data class Radar(val coverUrl: String) : ForYouArtwork

    data class Roaming(
        val coverUrl: String,
        val isPlaying: Boolean
    ) : ForYouArtwork
}

// 卡片底色取色所依据的封面：与每日推荐一样跟随封面，拿不到时退回入口固定色
private fun ForYouArtwork.colorSourceUrl(): String = when (this) {
    is ForYouArtwork.PersonalFm -> coverUrls.firstOrNull { it.isNotBlank() }.orEmpty()
    is ForYouArtwork.HotChart -> coverUrl
    is ForYouArtwork.Radar -> coverUrl
    is ForYouArtwork.Roaming -> coverUrl
}

internal data class ForYouEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val backgroundColor: Color,
    val artwork: ForYouArtwork,
    val onClick: () -> Unit
)

// 依赖具体歌单 id 的入口在拿不到 id 时直接不出现，避免出现点进去是无关内容的死入口
@Composable
internal fun rememberForYouEntries(
    toplists: List<ToplistInfo>,
    recommendPlaylists: List<PersonalizedPlaylist>,
    personalFmMode: PersonalFmMode,
    fmCoverUrls: List<String>,
    isFmPlaying: Boolean,
    roamingCoverUrl: String,
    hotTopCoverUrl: String,
    isRoamingPlaying: Boolean,
    onHotlistClick: (Long) -> Unit,
    onPersonalFmClick: () -> Unit,
    onRadarClick: (Long) -> Unit,
    onRoamingClick: () -> Unit
): List<ForYouEntry> {
    val latestOnHotlistClick by rememberUpdatedState(onHotlistClick)
    val latestOnPersonalFmClick by rememberUpdatedState(onPersonalFmClick)
    val latestOnRadarClick by rememberUpdatedState(onRadarClick)
    val latestOnRoamingClick by rememberUpdatedState(onRoamingClick)

    val hotlist = remember(toplists) {
        toplists.pickHotlist()
    }

    // 只认名字里带「雷达」的歌单，匹配不上就不放这个入口
    val radarPlaylist = remember(recommendPlaylists) {
        recommendPlaylists.firstOrNull { it.name.contains("雷达") }
    }

    return remember(
        hotlist,
        radarPlaylist,
        personalFmMode,
        fmCoverUrls,
        isFmPlaying,
        roamingCoverUrl,
        hotTopCoverUrl,
        isRoamingPlaying
    ) {
        buildList {
            add(
                ForYouEntry(
                    key = "personal_fm",
                    title = "私人FM",
                    subtitle = if (isFmPlaying) {
                        "${personalFmMode.displayName}模式 · 正在播放"
                    } else {
                        "${personalFmMode.displayName}模式 · ${personalFmMode.description}"
                    },
                    backgroundColor = Color(personalFmMode.baseColor),
                    artwork = ForYouArtwork.PersonalFm(
                        coverUrls = fmCoverUrls,
                        isPlaying = isFmPlaying
                    ),
                    onClick = { latestOnPersonalFmClick() }
                )
            )
            hotlist?.let { list ->
                add(
                    ForYouEntry(
                        key = "hot",
                        title = "热歌榜",
                        subtitle = "全站最热的歌，每天更新",
                        backgroundColor = ForYouHotColor,
                        artwork = ForYouArtwork.HotChart(coverUrl = hotTopCoverUrl.ifBlank { list.coverUrl }),
                        onClick = { latestOnHotlistClick(list.id) }
                    )
                )
            }
            radarPlaylist?.let { list ->
                add(
                    ForYouEntry(
                        key = "radar",
                        title = "私人雷达",
                        subtitle = "你还没听过、但会喜欢",
                        backgroundColor = ForYouRadarColor,
                        artwork = ForYouArtwork.Radar(coverUrl = list.picUrl),
                        onClick = { latestOnRadarClick(list.id) }
                    )
                )
            }
            add(
                ForYouEntry(
                    key = "roaming",
                    title = "音乐漫游",
                    subtitle = "无限探索相似歌曲",
                    backgroundColor = ForYouRoamingColor,
                    artwork = ForYouArtwork.Roaming(
                        coverUrl = roamingCoverUrl,
                        isPlaying = isRoamingPlaying
                    ),
                    onClick = { latestOnRoamingClick() }
                )
            )
        }
    }
}

// 为你定制：电台封面式方卡横滑，尺寸、圆角与下面推荐歌单完全一致
@Composable
internal fun ForYouSection(
    entries: List<ForYouEntry>,
    cardWidth: Dp,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val motionEnabled = LocalUiMotion.current.enabled
    val lifecycleOwner = LocalLifecycleOwner.current
    var isStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier) {
        HomeSectionTitle(text = "为你定制", modifier = Modifier.padding(contentPadding))
        LazyRow(
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(HomeCardGap)
        ) {
            items(entries, key = { it.key }) { entry ->
                ForYouCard(
                    entry = entry,
                    cardWidth = cardWidth,
                    canAnimate = motionEnabled && isStarted
                )
            }
        }
    }
}

@Composable
private fun ForYouCard(
    entry: ForYouEntry,
    cardWidth: Dp,
    canAnimate: Boolean,
    modifier: Modifier = Modifier
) {
    var isOnScreen by remember { mutableStateOf(true) }
    val shouldAnimate = canAnimate && isOnScreen
    val cardColor = rememberCoverCardColor(
        slot = "home_for_you_${entry.key}",
        coverUrl = entry.artwork.colorSourceUrl(),
        initialBase = null,
        fallback = entry.backgroundColor,
        label = "for_you_${entry.key}_color"
    )
    val density = LocalDensity.current
    val titleShadow = remember(density) {
        with(density) {
            Shadow(
                color = Color.Black.copy(alpha = 0.18f),
                offset = Offset(0f, 1.dp.toPx()),
                blurRadius = 8.dp.toPx()
            )
        }
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .onGloballyPositioned { coordinates ->
                isOnScreen = coordinates.isAttached && !coordinates.boundsInWindow().isEmpty
            }
            .pressable(MelodiaPress.Card) { entry.onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(RadiusCompact))
                .background(cardColor)
        ) {
            when (val artwork = entry.artwork) {
                is ForYouArtwork.PersonalFm -> PrivateFmCardVisual(
                    coverUrls = artwork.coverUrls,
                    isPlaying = artwork.isPlaying,
                    shouldAnimate = shouldAnimate,
                    cardWidth = cardWidth
                )

                is ForYouArtwork.HotChart -> HotChartCardVisual(
                    coverUrl = artwork.coverUrl,
                    cardWidth = cardWidth
                )

                is ForYouArtwork.Radar -> RadarCardVisual(
                    coverUrl = artwork.coverUrl,
                    shouldAnimate = shouldAnimate,
                    cardWidth = cardWidth
                )

                is ForYouArtwork.Roaming -> RoamingCardVisual(
                    coverUrl = artwork.coverUrl,
                    isPlaying = artwork.isPlaying,
                    cardWidth = cardWidth
                )
            }

            Text(
                text = entry.title,
                color = Color.White,
                style = TextStyle(
                    fontSize = 19.sp,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.6).sp,
                    fontWeight = FontWeight.Black,
                    shadow = titleShadow
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 10.dp, bottom = 9.dp)
            )
        }

        Text(
            text = entry.subtitle,
            color = MelodiaColors.TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

@Composable
private fun PrivateFmCardVisual(
    coverUrls: List<String>,
    isPlaying: Boolean,
    shouldAnimate: Boolean,
    cardWidth: Dp
) {
    val scale = cardWidth / REF_CARD_PX
    val centerSize = scale * 64f
    val sideSize = scale * 42f
    val sideTop = scale * 42f
    val sideInset = scale * 12f
    val centerTop = scale * 30f

    Box(modifier = Modifier.fillMaxSize()) {
        // 左侧小圆（第 2 首）
        ForYouCoverSlot(
            coverUrl = coverUrls.getOrNull(1).orEmpty(),
            shape = CircleShape,
            shadowElevation = 0.dp,
            alpha = 0.92f,
            modifier = Modifier
                .offset(x = sideInset, y = sideTop)
                .size(sideSize)
        )
        // 右侧小圆（第 3 首）
        ForYouCoverSlot(
            coverUrl = coverUrls.getOrNull(2).orEmpty(),
            shape = CircleShape,
            shadowElevation = 0.dp,
            alpha = 0.92f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = -sideInset, y = sideTop)
                .size(sideSize)
        )
        // 中间大圆（当前或下一首，压在两侧小圆前面）
        ForYouCoverSlot(
            coverUrl = coverUrls.getOrNull(0).orEmpty(),
            shape = CircleShape,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = centerTop)
                .size(centerSize)
        )

        if (isPlaying) {
            FmPlayingEqualizer(
                shouldAnimate = shouldAnimate,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 10.dp)
            )
        }
    }
}

@Composable
private fun FmPlayingEqualizer(
    shouldAnimate: Boolean,
    modifier: Modifier = Modifier
) {
    val heights: List<Dp> = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "fm_equalizer")
        val h1 by transition.animateFloat(
            initialValue = 3f,
            targetValue = 11f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_1"
        )
        val h2 by transition.animateFloat(
            initialValue = 3f,
            targetValue = 11f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(250)
            ),
            label = "eq_2"
        )
        val h3 by transition.animateFloat(
            initialValue = 3f,
            targetValue = 11f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(500)
            ),
            label = "eq_3"
        )
        listOf(h1.dp, h2.dp, h3.dp)
    } else {
        listOf(6.dp, 11.dp, 4.5.dp)
    }

    Row(
        modifier = modifier.height(11.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { barHeight ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun HotChartCardVisual(
    coverUrl: String,
    cardWidth: Dp
) {
    val scale = cardWidth / REF_CARD_PX
    val density = LocalDensity.current
    val strokePx = with(density) { 2.dp.toPx() }
    val numberFontSize = with(density) { (scale * 118f).toSp() }
    val coverSize = scale * 62f

    Box(modifier = Modifier.fillMaxSize()) {
        // 描边大号「1」置于榜单封面之后
        Text(
            text = "1",
            style = TextStyle(
                color = Color.White.copy(alpha = 0.28f),
                fontSize = numberFontSize,
                lineHeight = numberFontSize,
                fontWeight = FontWeight.Black,
                drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = scale * 6f, y = scale * 4f)
        )

        ForYouCoverSlot(
            coverUrl = coverUrl,
            shape = RoundedCornerShape(5.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset(x = scale * 22f, y = scale * 28f)
                .size(coverSize)
                .graphicsLayer { rotationZ = -7f }
        )
    }
}

@Composable
private fun RadarCardVisual(
    coverUrl: String,
    shouldAnimate: Boolean,
    cardWidth: Dp
) {
    val scale = cardWidth / REF_CARD_PX
    val coverSize = scale * 46f
    val centerY = scale * 60f

    val sweepAngle = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "radar_sweep")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "radar_angle"
        )
        angle
    } else {
        -55f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * (60f / REF_CARD_PX)
            val outerRadius = size.width * (124f / REF_CARD_PX) * 0.5f
            val innerRadius = size.width * (88f / REF_CARD_PX) * 0.5f
            val ringStroke = 1.5.dp.toPx()
            val ringColor = Color.White.copy(alpha = 0.20f)

            drawCircle(
                color = ringColor,
                radius = outerRadius,
                center = Offset(cx, cy),
                style = Stroke(width = ringStroke)
            )
            drawCircle(
                color = ringColor,
                radius = innerRadius,
                center = Offset(cx, cy),
                style = Stroke(width = ringStroke)
            )

            rotate(degrees = sweepAngle, pivot = Offset(cx, cy)) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0f to Color.White.copy(alpha = 0.28f),
                        (70f / 360f) to Color.Transparent,
                        1f to Color.Transparent,
                        center = Offset(cx, cy)
                    ),
                    radius = outerRadius,
                    center = Offset(cx, cy)
                )
            }
        }

        ForYouCoverSlot(
            coverUrl = coverUrl,
            shape = CircleShape,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = centerY - coverSize * 0.5f)
                .size(coverSize)
        )
    }
}

@Composable
private fun RoamingCardVisual(
    coverUrl: String,
    isPlaying: Boolean,
    cardWidth: Dp
) {
    val scale = cardWidth / REF_CARD_PX
    val centerSize = scale * 52f
    val dotSize = scale * 14f
    val haloPadding = 3.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = size.width * (10f / REF_CARD_PX)
            val top = size.height * (36f / REF_CARD_PX)
            val orbitWidth = size.width * (118f / REF_CARD_PX)
            val orbitHeight = size.height * (52f / REF_CARD_PX)
            val strokePx = 1.5.dp.toPx()
            val dashPx = 4.dp.toPx()
            val pivot = Offset(left + orbitWidth * 0.5f, top + orbitHeight * 0.5f)

            rotate(degrees = -14f, pivot = pivot) {
                drawOval(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(left, top),
                    size = Size(orbitWidth, orbitHeight),
                    style = Stroke(
                        width = strokePx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx))
                    )
                )
            }
        }

        ForYouCoverSlot(
            coverUrl = if (isPlaying) coverUrl else "",
            shape = CircleShape,
            shadowElevation = if (isPlaying) 6.dp else 0.dp,
            modifier = Modifier
                .offset(x = scale * 43f, y = scale * 36f)
                .size(centerSize)
        )

        // 轨道小圆点 + 外围 3dp 半透明白色光环
        Box(
            modifier = Modifier
                .offset(x = scale * 110f - haloPadding, y = scale * 44f - haloPadding)
                .size(dotSize + haloPadding * 2)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (isPlaying) Color.White else Color.White.copy(alpha = 0.50f))
            )
        }
    }
}

// 为你定制专属封面容器：使用 Coil AsyncImage，缺封面或加载中/失败时退成半透明白色，保持图形完整
@Composable
private fun ForYouCoverSlot(
    coverUrl: String,
    shape: Shape,
    shadowElevation: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val context = LocalContext.current
    val trimmedUrl = coverUrl.trim()
    var isLoaded by remember(trimmedUrl) { mutableStateOf(false) }
    var isError by remember(trimmedUrl) { mutableStateOf(false) }
    val showCover = trimmedUrl.isNotEmpty() && isLoaded && !isError

    Box(
        modifier = modifier
            .then(if (showCover && shadowElevation > 0.dp) Modifier.shadow(shadowElevation, shape) else Modifier)
            .clip(shape)
            .alpha(if (showCover) alpha else 1f)
            .background(TranslucentWhiteFallback)
    ) {
        if (trimmedUrl.isNotEmpty()) {
            val request = remember(trimmedUrl) {
                ImageRequest.Builder(context)
                    .data(trimmedUrl.withCoverParam("300y300"))
                    .size(300)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
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
