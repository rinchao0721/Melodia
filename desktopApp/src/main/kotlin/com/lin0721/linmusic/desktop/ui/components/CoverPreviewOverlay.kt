package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.lin0721.linmusic.core.ui.components.CoverPreviewRequest
import com.lin0721.linmusic.core.ui.components.resolveHighResCoverUrl
import com.lin0721.linmusic.core.ui.components.sanitizeCoverFileName
import com.lin0721.linmusic.core.ui.icons.Download
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.MusicNote
import com.lin0721.linmusic.core.ui.theme.styledButtonShape
import com.lin0721.linmusic.desktop.platform.chooseSaveFile
import com.lin0721.linmusic.desktop.ui.share.ShareCardImageIO
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.ImmersiveTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ENTER_ANIM_MS = 320
private const val EXIT_ANIM_MS = 250
private val EnterEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
private val ExitEasing = CubicBezierEasing(0.32f, 0.0f, 0.2f, 1.0f)
private const val ZOOM_ANIM_MS = 120
private const val SCRIM_ALPHA = 0.90f
private const val SCRIM_PROGRESS_END = 0.78f
private const val BUTTON_PROGRESS_START = 0.25f
private const val FALLBACK_START_SCALE = 0.88f
private const val MIN_SCALE = 1f
private const val DOUBLE_TAP_SCALE = 2f
private const val MAX_SCALE = 4f
private const val WHEEL_SCALE_STEP = 0.25f
private const val BASE_COVER_PX = 840
private val ViewportHorizontalPadding = 64.dp
private val ViewportVerticalPadding = 88.dp

@Composable
fun CoverPreviewOverlay(
    request: CoverPreviewRequest?,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var renderedRequest by remember { mutableStateOf<CoverPreviewRequest?>(null) }
    val progress = remember { Animatable(0f) }
    var isClosing by remember { mutableStateOf(false) }
    var openSession by remember { mutableIntStateOf(0) }
    var exitStartScale by remember { mutableFloatStateOf(MIN_SCALE) }
    var exitStartOffset by remember { mutableStateOf(Offset.Zero) }
    var liveScale by remember { mutableFloatStateOf(MIN_SCALE) }
    var liveOffset by remember { mutableStateOf(Offset.Zero) }
    val motionEnabled by rememberUpdatedState(LocalUiMotion.current.enabled)

    val targetOpen = request != null
    if (request != null && (renderedRequest == null || isClosing || request != renderedRequest)) {
        renderedRequest = request
        isClosing = false
        openSession++
        liveScale = MIN_SCALE
        liveOffset = Offset.Zero
        exitStartScale = MIN_SCALE
        exitStartOffset = Offset.Zero
    } else if (request == null && renderedRequest != null && !isClosing) {
        isClosing = true
        exitStartScale = liveScale
        exitStartOffset = liveOffset
    }

    LaunchedEffect(targetOpen, openSession) {
        if (targetOpen) {
            if (motionEnabled) {
                progress.animateTo(1f, tween(ENTER_ANIM_MS, easing = EnterEasing))
            } else {
                progress.snapTo(1f)
            }
        } else if (renderedRequest != null) {
            if (motionEnabled) {
                progress.animateTo(0f, tween(EXIT_ANIM_MS, easing = ExitEasing))
            } else {
                progress.snapTo(0f)
            }
            isClosing = false
            renderedRequest = null
        }
    }

    val activeRequest = renderedRequest ?: return
    // 退场结束前保持占位与沉浸主题，防止插件视图提前盖回
    DesktopOverlays.Track()
    ImmersiveTheme {
        CoverPreviewContent(
            request = activeRequest,
            sessionKey = openSession,
            isClosing = isClosing,
            progressProvider = { progress.value },
            exitStartScale = exitStartScale,
            exitStartOffset = exitStartOffset,
            onTransformChanged = { scale, offset ->
                liveScale = scale
                liveOffset = offset
            },
            onDismiss = onDismiss,
            onMessage = onMessage,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CoverPreviewContent(
    request: CoverPreviewRequest,
    sessionKey: Int,
    isClosing: Boolean,
    progressProvider: () -> Float,
    exitStartScale: Float,
    exitStartOffset: Offset,
    onTransformChanged: (Float, Offset) -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val highResUrl = remember(request.url) { resolveHighResCoverUrl(request.url) }
    val fallbackBaseUrl = remember(request.url) { sizedCoverUrl(request.url, BASE_COVER_PX) ?: request.url }
    val highResRequest = remember(highResUrl) {
        ImageRequest.Builder(PlatformContext.INSTANCE)
            .data(highResUrl)
            .size(CoilSize.ORIGINAL)
            .build()
    }

    var highResLoaded by remember(request.url) { mutableStateOf(false) }
    var highResError by remember(request.url) { mutableStateOf(false) }
    var baseError by remember(request.url) { mutableStateOf(false) }
    var imageAspect by remember(request.url) { mutableFloatStateOf(1f) }
    var saving by remember(request.url, sessionKey) { mutableStateOf(false) }

    var targetScale by remember(request.url, sessionKey) { mutableFloatStateOf(MIN_SCALE) }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = uiTween(ZOOM_ANIM_MS),
        label = "coverPreviewScale"
    )
    var panOffset by remember(request.url, sessionKey) { mutableStateOf(Offset.Zero) }
    var lastTapTimeMs by remember(request.url, sessionKey) { mutableLongStateOf(0L) }
    var lastTapPosition by remember(request.url, sessionKey) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(animatedScale, isClosing) {
        if (!isClosing && animatedScale <= MIN_SCALE) panOffset = Offset.Zero
    }

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentExiting by rememberUpdatedState(isClosing)
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val btnTranslatePx = with(density) { 14.dp.toPx() }
    val scrimColor = DesktopColors.Scrim

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOriginInRoot = it.positionInRoot() }
    ) {
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progressProvider().coerceIn(0f, 1f)
                    alpha = (p / SCRIM_PROGRESS_END).coerceIn(0f, 1f)
                }
                .background(scrimColor.copy(alpha = SCRIM_ALPHA))
        )

        Box(
            Modifier
                .fillMaxSize()
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    event.changes.forEach { it.consume() }
                    if (currentExiting) return@onPointerEvent
                    val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                    if (deltaY == 0f) return@onPointerEvent
                    val next = (targetScale - deltaY * WHEEL_SCALE_STEP).coerceIn(MIN_SCALE, MAX_SCALE)
                    targetScale = next
                    if (next <= MIN_SCALE) panOffset = Offset.Zero
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val isPrimary = currentEvent.buttons.isPrimaryPressed
                        down.consume()
                        if (currentExiting || !isPrimary) return@awaitEachGesture
                        val slop = viewConfiguration.touchSlop
                        var movedTooFar = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                if (!movedTooFar && !currentExiting) currentOnDismiss()
                                break
                            }
                            if ((change.position - down.position).getDistance() > slop) {
                                movedTooFar = true
                            }
                        }
                    }
                }
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ViewportHorizontalPadding, vertical = ViewportVerticalPadding),
                contentAlignment = Alignment.Center
            ) {
                val viewportW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val viewportH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val aspect = imageAspect.coerceIn(0.2f, 5f)
                val fitScale = minOf(viewportW / aspect, viewportH)
                val baseWidthPx = fitScale * aspect
                val baseHeightPx = fitScale
                val clampedOffset = clampPanOffset(panOffset, animatedScale, baseWidthPx, baseHeightPx, viewportW, viewportH)

                LaunchedEffect(animatedScale, clampedOffset, isClosing) {
                    if (!isClosing) onTransformChanged(animatedScale, clampedOffset)
                }

                val activeScale = if (isClosing) exitStartScale else animatedScale
                val activeOffset = if (isClosing) exitStartOffset else clampedOffset

                val currentScale by rememberUpdatedState(animatedScale)
                val currentBaseW by rememberUpdatedState(baseWidthPx)
                val currentBaseH by rememberUpdatedState(baseHeightPx)
                val currentViewW by rememberUpdatedState(viewportW)
                val currentViewH by rememberUpdatedState(viewportH)

                Box(
                    modifier = with(density) { Modifier.size(baseWidthPx.toDp(), baseHeightPx.toDp()) }
                        .graphicsLayer {
                            val p = progressProvider().coerceIn(0f, 1f)
                            val validSource = request.sourceBounds?.takeIf { sb ->
                                sb.isValid &&
                                    rootW > 1f &&
                                    rootH > 1f &&
                                    sb.centerX in overlayOriginInRoot.x..(overlayOriginInRoot.x + rootW) &&
                                    sb.centerY in overlayOriginInRoot.y..(overlayOriginInRoot.y + rootH)
                            }
                            if (validSource != null) {
                                val dstCenterX = overlayOriginInRoot.x + rootW / 2f
                                val dstCenterY = overlayOriginInRoot.y + rootH / 2f
                                val fromScale = (validSource.width / baseWidthPx.coerceAtLeast(1f)).coerceIn(0.1f, 3f)
                                val fromDx = validSource.centerX - dstCenterX
                                val fromDy = validSource.centerY - dstCenterY
                                scaleX = lerp(fromScale, activeScale, p)
                                scaleY = lerp(fromScale, activeScale, p)
                                translationX = lerp(fromDx, activeOffset.x, p)
                                translationY = lerp(fromDy, activeOffset.y, p)
                                alpha = 1f
                            } else {
                                scaleX = lerp(FALLBACK_START_SCALE, activeScale, p)
                                scaleY = lerp(FALLBACK_START_SCALE, activeScale, p)
                                translationX = activeOffset.x * p
                                translationY = activeOffset.y * p
                                alpha = p
                            }
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(request.url, sessionKey) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val isPrimary = currentEvent.buttons.isPrimaryPressed
                                down.consume()
                                if (currentExiting || !isPrimary) return@awaitEachGesture
                                var dragged = false
                                var totalDrag = Offset.Zero
                                val touchSlop = viewConfiguration.touchSlop
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    val delta = change.positionChange()
                                    if (delta != Offset.Zero) {
                                        totalDrag += delta
                                        if (!dragged && totalDrag.getDistance() * currentScale > touchSlop) {
                                            dragged = true
                                        }
                                        if (dragged && currentScale > MIN_SCALE) {
                                            change.consume()
                                            panOffset = clampPanOffset(
                                                panOffset + delta * currentScale,
                                                currentScale,
                                                currentBaseW,
                                                currentBaseH,
                                                currentViewW,
                                                currentViewH
                                            )
                                        }
                                    }
                                }
                                if (!dragged && !currentExiting) {
                                    val now = System.currentTimeMillis()
                                    val withinDoubleTapTime = now - lastTapTimeMs <= viewConfiguration.doubleTapTimeoutMillis
                                    val withinDistance = (down.position - lastTapPosition).getDistance() * currentScale <= touchSlop * 4f
                                    if (lastTapTimeMs != 0L && withinDoubleTapTime && withinDistance) {
                                        lastTapTimeMs = 0L
                                        if (targetScale > MIN_SCALE) {
                                            targetScale = MIN_SCALE
                                            panOffset = Offset.Zero
                                        } else {
                                            targetScale = DOUBLE_TAP_SCALE
                                        }
                                    } else {
                                        lastTapTimeMs = now
                                        lastTapPosition = down.position
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bothFailed = highResError && (fallbackBaseUrl == highResUrl || baseError)
                    if (bothFailed) {
                        Box(
                            Modifier.fillMaxSize().background(DesktopColors.CoverPlaceholder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                MelodiaIcons.MusicNote,
                                contentDescription = null,
                                tint = DesktopColors.TextGray,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    } else {
                        if (fallbackBaseUrl != highResUrl) {
                            AsyncImage(
                                model = fallbackBaseUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                onSuccess = { state ->
                                    baseError = false
                                    if (!highResLoaded) {
                                        val img = state.result.image
                                        if (img.width > 0 && img.height > 0) {
                                            imageAspect = img.width.toFloat() / img.height.toFloat()
                                        }
                                    }
                                },
                                onError = { baseError = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        AsyncImage(
                            model = highResRequest,
                            contentDescription = request.title.ifBlank { "封面大图" },
                            contentScale = ContentScale.Fit,
                            onLoading = {
                                highResLoaded = false
                                highResError = false
                            },
                            onSuccess = { state ->
                                highResLoaded = true
                                highResError = false
                                val img = state.result.image
                                if (img.width > 0 && img.height > 0) {
                                    imageAspect = img.width.toFloat() / img.height.toFloat()
                                }
                            },
                            onError = {
                                highResLoaded = false
                                highResError = true
                            },
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                alpha = if (highResLoaded) 1f else 0f
                            }
                        )
                    }
                }
            }

            SaveCoverButton(
                saving = saving,
                enabled = !saving && !isClosing,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .graphicsLayer {
                        val p = progressProvider().coerceIn(0f, 1f)
                        val btnP = ((p - BUTTON_PROGRESS_START) / (1f - BUTTON_PROGRESS_START)).coerceIn(0f, 1f)
                        alpha = btnP
                        translationY = btnTranslatePx * (1f - btnP)
                    },
                onClick = {
                    if (saving || isClosing) return@SaveCoverButton
                    saving = true
                    scope.launch {
                        try {
                            val defaultName = "${sanitizeCoverFileName(request.title)}.png"
                            val file = chooseSaveFile("保存封面图片", defaultName, "png") ?: return@launch
                            val bitmap = loadOriginalCoverBitmap(highResUrl, request.url)
                            if (bitmap != null && ShareCardImageIO.savePng(bitmap, file)) {
                                onMessage("已保存封面到 ${file.name}")
                            } else {
                                onMessage("保存失败，请稍后重试")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            onMessage("保存失败，请稍后重试")
                        } finally {
                            saving = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SaveCoverButton(
    saving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonShape = styledButtonShape(RoundedCornerShape(16.dp))
    Row(
        modifier = modifier
            .clip(buttonShape)
            .background(DesktopColors.TextPrimary.copy(alpha = if (!saving) 1f else 0.5f))
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = DesktopColors.InverseText,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                MelodiaIcons.Download,
                contentDescription = null,
                tint = DesktopColors.InverseText,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = if (saving) "保存中..." else "保存图片",
            color = DesktopColors.InverseText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun clampPanOffset(
    offset: Offset,
    scale: Float,
    baseWidthPx: Float,
    baseHeightPx: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float
): Offset {
    if (scale <= MIN_SCALE) return Offset.Zero
    val maxX = ((baseWidthPx * scale - viewportWidthPx) / 2f).coerceAtLeast(0f)
    val maxY = ((baseHeightPx * scale - viewportHeightPx) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

private suspend fun loadOriginalCoverBitmap(highResUrl: String, fallbackUrl: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val context = PlatformContext.INSTANCE
        val loader = SingletonImageLoader.get(context)
        val urls = listOf(highResUrl, fallbackUrl).distinct().filter { it.isNotBlank() }
        for (targetUrl in urls) {
            val result = try {
                val request = ImageRequest.Builder(context)
                    .data(targetUrl)
                    .size(CoilSize.ORIGINAL)
                    .build()
                loader.execute(request).image?.toBitmap()?.asComposeImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (result != null) return@withContext result
        }
        null
    }
