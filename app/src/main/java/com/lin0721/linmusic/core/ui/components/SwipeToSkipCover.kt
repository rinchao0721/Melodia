package com.lin0721.linmusic.core.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lin0721.linmusic.core.ui.theme.SwipeCoverSpringSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 拖动超过容器宽度这个比例，或松手时甩动速度超过阈值，判定为确认切歌
private const val SwipeConfirmFraction = 0.32f
private const val SwipeConfirmVelocityPx = 1200f

// 手势捕获区域跟实际跟手滑动的视觉区域尺寸不一致时使用（比如迷你条：整条悬浮栏都能拖出切歌手势，
// 但只有封面+歌名歌手那一行跟着滑动）。containerWidthPx 由调用方在渲染滑动内容时上报自己的宽度，
// 阈值和归位动画都按它算，跟手势检测区域无关
@Stable
class SwipeToSkipCoverState {
    var offsetX by mutableFloatStateOf(0f)
        internal set
    var containerWidthPx by mutableFloatStateOf(0f)
    internal var settleJob: Job? = null
    private var syncedKey: Any? = null

    // 预览内容不跟着刷新，等 syncCurrentKey 探测到曲目真正切换完成后再放开
    var isTransitioning by mutableStateOf(false)
        private set

    fun beginTransition() {
        isTransitioning = true
    }

    // 切歌确认后先把位移停在滑出的终点，等外部真正的数据
    fun syncCurrentKey(key: Any?) {
        if (syncedKey != key) {
            syncedKey = key
            offsetX = 0f
            isTransitioning = false
        }
    }
}

@Composable
fun rememberSwipeToSkipCoverState(): SwipeToSkipCoverState = remember { SwipeToSkipCoverState() }

// 松手后按阈值/甩动速度判定切歌，供外部驱动手势时复用
private fun settleSwipe(
    state: SwipeToSkipCoverState,
    velocity: Float,
    canSwipeToPrevious: Boolean,
    canSwipeToNext: Boolean,
    onConfirmPrevious: () -> Unit,
    onConfirmNext: () -> Unit,
    scope: CoroutineScope
) {
    val containerWidthPx = state.containerWidthPx
    val threshold = containerWidthPx * SwipeConfirmFraction
    val confirmNext = canSwipeToNext &&
        (state.offsetX < -threshold || velocity < -SwipeConfirmVelocityPx)
    val confirmPrevious = canSwipeToPrevious &&
        (state.offsetX > threshold || velocity > SwipeConfirmVelocityPx)
    if (confirmNext || confirmPrevious) {
        state.beginTransition()
    }
    state.settleJob = scope.launch {
        when {
            confirmNext -> {
                animate(state.offsetX, -containerWidthPx, velocity, SwipeCoverSpringSpec) { value, _ ->
                    state.offsetX = value
                }
                onConfirmNext()
                // 位移停在滑出终点，等 syncCurrentKey 检测到真实曲目数据到位后再归零
            }
            confirmPrevious -> {
                animate(state.offsetX, containerWidthPx, velocity, SwipeCoverSpringSpec) { value, _ ->
                    state.offsetX = value
                }
                onConfirmPrevious()
            }
            else -> {
                animate(state.offsetX, 0f, velocity, SwipeCoverSpringSpec) { value, _ ->
                    state.offsetX = value
                }
            }
        }
    }
}

// 挂在手势捕获区域、上的横向拖拽 Modifier，
// 驱动外部传入的 state，视觉渲染由调用方自行实现（迷你条是封面+歌名歌手整行）
@Composable
fun rememberSwipeToSkipDragModifier(
    state: SwipeToSkipCoverState,
    canSwipeToPrevious: Boolean,
    canSwipeToNext: Boolean,
    onConfirmPrevious: () -> Unit,
    onConfirmNext: () -> Unit
): Modifier {
    val scope = rememberCoroutineScope()
    if (!canSwipeToPrevious && !canSwipeToNext) return Modifier
    return Modifier.draggable(
        orientation = Orientation.Horizontal,
        state = rememberDraggableState { delta ->
            state.settleJob?.cancel()
            val maxNext = if (canSwipeToNext) state.containerWidthPx else 0f
            val maxPrevious = if (canSwipeToPrevious) state.containerWidthPx else 0f
            state.offsetX = (state.offsetX + delta).coerceIn(-maxNext, maxPrevious)
        },
        onDragStopped = { velocity ->
            settleSwipe(state, velocity, canSwipeToPrevious, canSwipeToNext, onConfirmPrevious, onConfirmNext, scope)
        }
    )
}

// 封面区左右滑动切歌：当前/上一首/下一首三张封面各自带着圆角与投影整体跟手平移
@Composable
fun SwipeToSkipCover(
    coverUrl: String,
    previousCoverUrl: String?,
    nextCoverUrl: String?,
    onConfirmPrevious: () -> Unit,
    onConfirmNext: () -> Unit,
    // 当前曲目的唯一标识：切歌确认后先停在滑出终点，
    // 等这个 key 真的变化（网络请求返回、currentTrack 更新）了再把位移归零
    currentKey: Any,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RectangleShape,
    elevation: Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    // 切歌确认后到 currentKey 真正追上来之前冻结预览内容，避免队列 currentIndex 先于
    // currentTrack 更新时，画面被下下首的数据提前顶掉
    var isTransitioning by remember { mutableStateOf(false) }
    var displayedPreviousCoverUrl by remember { mutableStateOf(previousCoverUrl) }
    var displayedNextCoverUrl by remember { mutableStateOf(nextCoverUrl) }
    if (!isTransitioning) {
        displayedPreviousCoverUrl = previousCoverUrl
        displayedNextCoverUrl = nextCoverUrl
    }
    val canSwipeToPrevious = displayedPreviousCoverUrl != null
    val canSwipeToNext = displayedNextCoverUrl != null

    var syncedKey by remember { mutableStateOf(currentKey) }
    if (syncedKey != currentKey) {
        syncedKey = currentKey
        offsetX = 0f
        isTransitioning = false
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
            .then(
                if (containerWidthPx > 0f && (canSwipeToPrevious || canSwipeToNext)) {
                    Modifier.draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            settleJob?.cancel()
                            // 位移最多跟到下一首/上一首完全到位为止
                            val maxNext = if (canSwipeToNext) containerWidthPx else 0f
                            val maxPrevious = if (canSwipeToPrevious) containerWidthPx else 0f
                            offsetX = (offsetX + delta).coerceIn(-maxNext, maxPrevious)
                        },
                        onDragStopped = { velocity ->
                            val threshold = containerWidthPx * SwipeConfirmFraction
                            val confirmNext = canSwipeToNext &&
                                (offsetX < -threshold || velocity < -SwipeConfirmVelocityPx)
                            val confirmPrevious = canSwipeToPrevious &&
                                (offsetX > threshold || velocity > SwipeConfirmVelocityPx)
                            if (confirmNext || confirmPrevious) {
                                isTransitioning = true
                            }
                            settleJob = scope.launch {
                                when {
                                    confirmNext -> {
                                        animate(offsetX, -containerWidthPx, velocity, SwipeCoverSpringSpec) { value, _ ->
                                            offsetX = value
                                        }
                                        onConfirmNext()
                                    }
                                    confirmPrevious -> {
                                        animate(offsetX, containerWidthPx, velocity, SwipeCoverSpringSpec) { value, _ ->
                                            offsetX = value
                                        }
                                        onConfirmPrevious()
                                    }
                                    else -> {
                                        animate(offsetX, 0f, velocity, SwipeCoverSpringSpec) { value, _ ->
                                            offsetX = value
                                        }
                                    }
                                }
                            }
                        }
                    )
                } else {
                    Modifier
                }
            )
    ) {
        // 每层用封面 URL（而不是"上一首/当前/下一首"角色）做 key，并且三层必须在同一处
        val previewPreviousUrl = displayedPreviousCoverUrl
        val previewNextUrl = displayedNextCoverUrl
        val layers = buildList {
            if (previewPreviousUrl != null) {
                add(SwipeCoverLayerSpec(previewPreviousUrl) { offsetX - containerWidthPx })
            }
            add(SwipeCoverLayerSpec(coverUrl) { offsetX })
            if (previewNextUrl != null) {
                // 队列只有两首歌时上一首/下一首指向同一首曲目，key 会撞上前一层，加个后缀区分开
                val nextKey = if (previewNextUrl == previewPreviousUrl) "$previewNextUrl-next" else previewNextUrl
                add(SwipeCoverLayerSpec(nextKey, previewNextUrl) { offsetX + containerWidthPx })
            }
        }
        layers.forEach { layer ->
            key(layer.key) {
                SwipeCoverLayer(
                    url = layer.url,
                    contentScale = contentScale,
                    shape = shape,
                    elevation = elevation,
                    contentPadding = contentPadding,
                    translationXProvider = layer.translationXProvider
                )
            }
        }
    }
}

private class SwipeCoverLayerSpec(
    val key: Any,
    val url: String,
    val translationXProvider: () -> Float
) {
    constructor(url: String, translationXProvider: () -> Float) : this(url, url, translationXProvider)
}

@Composable
private fun SwipeCoverLayer(
    url: String,
    contentScale: ContentScale,
    shape: Shape,
    elevation: Dp,
    contentPadding: Dp,
    translationXProvider: () -> Float
) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        // 三张封面在切歌确认前后都是从预览态直接切换过来的，图片基本已经在 Coil 内存缓存里；
        // 不开 crossfade 避免缓存命中时也走一遍淡入动画，看起来像空位图闪了一下
        model = remember(url) {
            ImageRequest.Builder(context)
                .data(url.ifEmpty { null })
                .allowHardware(false)
                .build()
        },
        contentDescription = null,
        contentScale = contentScale,
        loading = { CoverPlaceholder() },
        error = { CoverPlaceholder() },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = translationXProvider() }
            .padding(horizontal = contentPadding)
            .aspectRatio(1f)
            .then(
                if (elevation > 0.dp) Modifier.shadow(elevation = elevation, shape = shape, clip = false) else Modifier
            )
            .clip(shape)
    )
}
