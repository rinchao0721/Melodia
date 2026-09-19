package com.lin0721.linmusic.core.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.SwipeCoverSpringSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 拖动超过容器宽度这个比例，或松手时甩动速度超过阈值，判定为确认切歌
private const val SwipeConfirmFraction = 0.32f
private const val SwipeConfirmVelocityPx = 1200f

// 点击上一首/下一首按钮、或播完自动切歌等非拖拽路径触发切歌时，判断应该模拟哪个方向的滑入动画
enum class SwipeDirection { NEXT, PREVIOUS }

// 手势捕获区域跟实际跟手滑动的视觉区域尺寸不一致时使用（比如迷你条：整条悬浮栏都能拖出切歌手势，
// 但只有封面+歌名歌手那一行跟着滑动）。containerWidthPx 由调用方在渲染滑动内容时上报自己的宽度，
// 阈值和归位动画都按它算，跟手势检测区域无关
@Stable
class SwipeToSkipCoverState {
    var offsetX by mutableFloatStateOf(0f)
        internal set
    var containerWidthPx by mutableFloatStateOf(0f)
    internal var settleJob: Job? = null
    internal var isDragActive by mutableStateOf(false)
    private var syncedKey: Any? = null
    private var lastPreviousKey: Any? = null
    private var lastNextKey: Any? = null
    // 拖拽确认已经自己在跑滑出动画，跟外部（按钮/自动切歌）触发的过渡区分开，
    // 避免归零时误判成外部触发又补一次动画
    private var isDragConfirmed = false

    // 预览内容不跟着刷新，等 syncCurrentKey 探测到曲目真正切换完成后再放开
    var isTransitioning by mutableStateOf(false)
        private set

    fun beginTransition() {
        isTransitioning = true
        isDragConfirmed = true
    }

    // 撤销成功：这次过渡等同于没发生过，立刻解冻
    fun cancelTransition() {
        isTransitioning = false
        isDragConfirmed = false
    }

    // 撤销来不及：保持"过渡中"冻结，但不再压制新歌数据落地时该补的滑入动画
    fun markCancelTooLate() {
        isDragConfirmed = false
    }

    // 每次重组都要调用。队列 currentIndex 往往先于 currentTrack 更新：一旦发现活的
    // previousKey/nextKey 已经等于还没让位的当前 key，说明外部（按钮/自动切歌）触发的切歌
    // 已经在路上了，提前进入"过渡中"并把 lastNext/PreviousKey 锁定在这一帧之前的值，
    // 不能被过渡期里的新值覆盖，否则等 key 真的变化时会拿错比对对象、判断不出方向
    fun syncCurrentKey(key: Any?, previousKey: Any? = null, nextKey: Any? = null): SwipeDirection? {
        // 拖拽或收尾动画正握着 offsetX，这次同步先跳过，等手势彻底结束再处理
        if (isDragActive || settleJob?.isActive == true) return null
        if (!isTransitioning && syncedKey == key && (previousKey == key || nextKey == key)) {
            isTransitioning = true
        }
        var direction: SwipeDirection? = null
        if (syncedKey != key) {
            direction = when {
                isDragConfirmed -> null
                key == lastNextKey -> SwipeDirection.NEXT
                key == lastPreviousKey -> SwipeDirection.PREVIOUS
                else -> null
            }
            offsetX = when (direction) {
                SwipeDirection.NEXT -> containerWidthPx
                SwipeDirection.PREVIOUS -> -containerWidthPx
                null -> 0f
            }
            syncedKey = key
            isTransitioning = false
            isDragConfirmed = false
            lastPreviousKey = previousKey
            lastNextKey = nextKey
        } else if (!isTransitioning) {
            // 稳定态才刷新，过渡期内保持冻结
            lastPreviousKey = previousKey
            lastNextKey = nextKey
        }
        return direction
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
    onCancelPending: () -> Boolean,
    scope: CoroutineScope
) {
    val containerWidthPx = state.containerWidthPx
    val threshold = containerWidthPx * SwipeConfirmFraction
    val confirmNext = canSwipeToNext &&
        (state.offsetX < -threshold || velocity < -SwipeConfirmVelocityPx)
    val confirmPrevious = canSwipeToPrevious &&
        (state.offsetX > threshold || velocity > SwipeConfirmVelocityPx)
    // 这次手势没有再次确认方向，但之前已经有一次确认过的切歌还没落地，说明用户是在快速滑回去取消
    val wasTransitioning = state.isTransitioning
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
                if (wasTransitioning) {
                    if (onCancelPending()) state.cancelTransition() else state.markCancelTooLate()
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
    onConfirmNext: () -> Unit,
    onCancelPending: () -> Boolean = { false }
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
        onDragStarted = { state.isDragActive = true },
        onDragStopped = { velocity ->
            state.isDragActive = false
            settleSwipe(state, velocity, canSwipeToPrevious, canSwipeToNext, onConfirmPrevious, onConfirmNext, onCancelPending, scope)
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
    previousKey: Any? = null,
    nextKey: Any? = null,
    contentPadding: Dp = 0.dp,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RectangleShape,
    elevation: Dp = 0.dp,
    // 快速滑回中间时尝试撤销上一次已确认但还没落地的切歌；返回 true 表示撤销成功
    onCancelPending: () -> Boolean = { false }
) {
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // 手指正按在屏幕上拖拽期间，offsetX 完全交给手势本身掌控，下面的 key 同步逻辑不能插手，
    // 否则网络请求在拖拽过程中落地会跟手势的实时位移打架，出现封面/文字硬跳的画面撕裂
    var isDragActive by remember { mutableStateOf(false) }

    // 切歌确认后到 currentKey 真正追上来之前冻结预览内容，避免队列 currentIndex 先于
    // currentTrack 更新时，画面被下下首的数据提前顶掉
    var isTransitioning by remember { mutableStateOf(false) }
    // 区分这次过渡是拖拽确认触发的（自己已经在跑滑出动画）还是外部（按钮/自动切歌）触发的，
    // 只有后者需要在下面的同步点补一次程序化的滑入动画
    var isDragConfirmed by remember { mutableStateOf(false) }
    var displayedPreviousCoverUrl by remember { mutableStateOf(previousCoverUrl) }
    var displayedNextCoverUrl by remember { mutableStateOf(nextCoverUrl) }
    var displayedPreviousKey by remember { mutableStateOf(previousKey) }
    var displayedNextKey by remember { mutableStateOf(nextKey) }
    // 当前位置渲染用的封面/Key 同样要冻结：过渡期间网络请求随时可能落地，
    // 直接用实时 coverUrl/currentKey 会让"当前"这一层的画面在手势进行中被悄悄换成新歌
    var displayedCoverUrl by remember { mutableStateOf(coverUrl) }
    var displayedCurrentKey by remember { mutableStateOf(currentKey) }
    if (!isTransitioning) {
        val currentKeyStr = currentKey.toString()
        val prevKeyStr = previousKey?.toString()
        val nextKeyStr = nextKey?.toString()
        // 点击上一首/下一首切歌时外部队列索引先变，若与当前封面或Key重合则冻结预览，避免被提前顶掉
        if ((previousCoverUrl != null && previousCoverUrl == coverUrl) ||
            (nextCoverUrl != null && nextCoverUrl == coverUrl) ||
            (prevKeyStr != null && prevKeyStr == currentKeyStr) ||
            (nextKeyStr != null && nextKeyStr == currentKeyStr)
        ) {
            isTransitioning = true
        } else {
            displayedPreviousCoverUrl = previousCoverUrl
            displayedNextCoverUrl = nextCoverUrl
            displayedPreviousKey = previousKey
            displayedNextKey = nextKey
            displayedCoverUrl = coverUrl
            displayedCurrentKey = currentKey
        }
    }
    val canSwipeToPrevious = displayedPreviousCoverUrl != null
    val canSwipeToNext = displayedNextCoverUrl != null

    var syncedKey by remember { mutableStateOf(currentKey) }
    var pendingSlideInKey by remember { mutableStateOf<Any?>(null) }
    if (!isDragActive && settleJob?.isActive != true && syncedKey != currentKey) {
        // 拖拽确认路径已经自己在跑滑出动画，这里只需要收尾归零；外部（按钮/自动切歌）触发的
        // 变化如果能判断出新的当前曲目就是刚才冻结住的"下一首/上一首"，就把位移摆到对应的
        // 滑入起点，交给下面的 LaunchedEffect 播一次归位动画，跟拖拽切歌的观感保持一致
        val matchesNext = (displayedNextKey != null && displayedNextKey.toString() == currentKey.toString()) ||
            (displayedNextCoverUrl != null && displayedNextCoverUrl == coverUrl)
        val matchesPrevious = (displayedPreviousKey != null && displayedPreviousKey.toString() == currentKey.toString()) ||
            (displayedPreviousCoverUrl != null && displayedPreviousCoverUrl == coverUrl)
        val direction = if (isDragConfirmed) {
            null
        } else when {
            matchesNext -> SwipeDirection.NEXT
            matchesPrevious -> SwipeDirection.PREVIOUS
            else -> null
        }
        offsetX = when (direction) {
            SwipeDirection.NEXT -> containerWidthPx
            SwipeDirection.PREVIOUS -> -containerWidthPx
            null -> 0f
        }
        syncedKey = currentKey
        isTransitioning = false
        isDragConfirmed = false
        if (direction != null) {
            pendingSlideInKey = currentKey
        }
    }
    LaunchedEffect(pendingSlideInKey) {
        if (pendingSlideInKey != null) {
            animate(offsetX, 0f, 0f, SwipeCoverSpringSpec) { value, _ ->
                offsetX = value
            }
        }
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
                        onDragStarted = { isDragActive = true },
                        onDragStopped = { velocity ->
                            isDragActive = false
                            val threshold = containerWidthPx * SwipeConfirmFraction
                            val confirmNext = canSwipeToNext &&
                                (offsetX < -threshold || velocity < -SwipeConfirmVelocityPx)
                            val confirmPrevious = canSwipeToPrevious &&
                                (offsetX > threshold || velocity > SwipeConfirmVelocityPx)
                            // 这次没有再次确认方向，但之前已经有一次确认过的切歌还没落地，说明是在快速滑回取消
                            val wasTransitioning = isTransitioning
                            if (confirmNext || confirmPrevious) {
                                isTransitioning = true
                                isDragConfirmed = true
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
                                        if (wasTransitioning) {
                                            if (onCancelPending()) {
                                                isTransitioning = false
                                                isDragConfirmed = false
                                            } else {
                                                isDragConfirmed = false
                                            }
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
        val previewPreviousUrl = displayedPreviousCoverUrl
        val previewNextUrl = displayedNextCoverUrl
        val previewPrevKey = displayedPreviousKey
        val previewNxtKey = displayedNextKey
        val currentKeyStr = displayedCurrentKey.toString()

        val layers = buildList {
            if (previewPreviousUrl != null) {
                val prevKeyCalculated = when {
                    previewPrevKey != null -> {
                        val k = previewPrevKey.toString()
                        if (k == currentKeyStr) "$k-prev" else k
                    }
                    previewPreviousUrl == displayedCoverUrl -> "$previewPreviousUrl-prev"
                    else -> previewPreviousUrl
                }
                add(SwipeCoverLayerSpec(prevKeyCalculated, previewPreviousUrl) { offsetX - containerWidthPx })
            }
            add(SwipeCoverLayerSpec(currentKeyStr, displayedCoverUrl) { offsetX })
            if (previewNextUrl != null) {
                val nextKeyCalculated = when {
                    previewNxtKey != null -> {
                        val k = previewNxtKey.toString()
                        val prevK = previewPrevKey?.toString()
                        if (k == prevK || k == currentKeyStr) "$k-next" else k
                    }
                    previewNextUrl == previewPreviousUrl || previewNextUrl == displayedCoverUrl -> "$previewNextUrl-next"
                    else -> previewNextUrl
                }
                add(SwipeCoverLayerSpec(nextKeyCalculated, previewNextUrl) { offsetX + containerWidthPx })
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
    var isTransparentCover by remember(url) {
        mutableStateOf(CoverContourShadowHelper.isTransparent(url) == true)
    }
    val scope = rememberCoroutineScope()

    AntiFlickerCoverImage(
        url = url,
        contentScale = contentScale,
        onImageLoaded = { bitmap ->
            scope.launch(Dispatchers.Default) {
                val isTrans = CoverContourShadowHelper.detectIsTransparentCover(bitmap)
                CoverContourShadowHelper.markTransparency(url, isTrans)
                if (isTrans != isTransparentCover) {
                    withContext(Dispatchers.Main) {
                        isTransparentCover = isTrans
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = translationXProvider() }
            .padding(horizontal = contentPadding)
            .aspectRatio(1f)
            .then(
                if (!isTransparentCover && elevation > 0.dp) {
                    Modifier.shadow(elevation = elevation, shape = shape, clip = false)
                } else {
                    Modifier
                }
            )
            .then(
                if (!isTransparentCover) {
                    Modifier.clip(shape)
                } else {
                    Modifier
                }
            )
    )
}
