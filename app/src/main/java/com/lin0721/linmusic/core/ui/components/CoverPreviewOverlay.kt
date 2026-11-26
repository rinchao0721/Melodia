package com.lin0721.linmusic.core.ui.components

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.lin0721.linmusic.core.ui.icons.Download
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.feature.player.share.ShareCardImages
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MinPreviewScale = 1f
private const val MaxPreviewScale = 4f
private const val DoubleTapScale = 2.2f
private const val ScrimAlpha = 0.90f
private const val EnterDurationMs = 320
private const val ExitDurationMs = 250
private val EnterEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
private val ExitEasing = CubicBezierEasing(0.32f, 0.0f, 0.2f, 1.0f)

@Stable
class CoverPreviewState {
    var current by mutableStateOf<CoverPreviewRequest?>(null)
        private set

    fun open(url: String?, title: String = "", sourceBounds: CoverPreviewBounds? = null) {
        if (!url.isNullOrBlank()) {
            current = CoverPreviewRequest(url = url, title = title, sourceBounds = sourceBounds)
        }
    }

    fun dismiss() {
        current = null
    }
}

val LocalCoverPreview = staticCompositionLocalOf { CoverPreviewState() }

@Composable
fun CoverPreviewOverlay(
    state: CoverPreviewState,
    modifier: Modifier = Modifier
) {
    var renderedRequest by remember { mutableStateOf<CoverPreviewRequest?>(null) }
    val progress = remember { Animatable(0f) }
    var isClosing by remember { mutableStateOf(false) }
    var openSession by remember { mutableIntStateOf(0) }
    var exitStartScale by remember { mutableFloatStateOf(MinPreviewScale) }
    var exitStartOffset by remember { mutableStateOf(Offset.Zero) }
    var liveScale by remember { mutableFloatStateOf(MinPreviewScale) }
    var liveOffset by remember { mutableStateOf(Offset.Zero) }
    val latestMotionEnabled by rememberUpdatedState(LocalUiMotion.current.enabled)

    val currentRequest = state.current
    val targetOpen = currentRequest != null
    if (currentRequest != null && (renderedRequest == null || isClosing || currentRequest != renderedRequest)) {
        renderedRequest = currentRequest
        isClosing = false
        openSession++
        liveScale = MinPreviewScale
        liveOffset = Offset.Zero
        exitStartScale = MinPreviewScale
        exitStartOffset = Offset.Zero
    } else if (currentRequest == null && renderedRequest != null && !isClosing) {
        isClosing = true
        exitStartScale = liveScale
        exitStartOffset = liveOffset
    }

    LaunchedEffect(targetOpen, openSession) {
        if (targetOpen) {
            if (latestMotionEnabled) {
                progress.animateTo(1f, tween(EnterDurationMs, easing = EnterEasing))
            } else {
                progress.snapTo(1f)
            }
        } else if (renderedRequest != null) {
            if (latestMotionEnabled) {
                progress.animateTo(0f, tween(ExitDurationMs, easing = ExitEasing))
            } else {
                progress.snapTo(0f)
            }
            isClosing = false
            renderedRequest = null
        }
    }

    BackHandler(enabled = state.current != null) {
        state.dismiss()
    }

    val activeRequest = renderedRequest ?: return
    CoverPreviewContent(
        request = activeRequest,
        sessionKey = openSession,
        progressProvider = { progress.value },
        isClosing = isClosing,
        exitStartScale = exitStartScale,
        exitStartOffset = exitStartOffset,
        onTransformChanged = { s, o ->
            liveScale = s
            liveOffset = o
        },
        onDismiss = state::dismiss,
        modifier = modifier
    )
}

@Composable
private fun CoverPreviewContent(
    request: CoverPreviewRequest,
    sessionKey: Int,
    progressProvider: () -> Float,
    isClosing: Boolean,
    exitStartScale: Float,
    exitStartOffset: Offset,
    onTransformChanged: (Float, Offset) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val highResUrl = remember(request.url) { resolveHighResCoverUrl(request.url) }
    val needDualLayer = remember(request.url, highResUrl) {
        highResUrl.isNotBlank() && highResUrl != request.url
    }
    var highResLoaded by remember(request.url) { mutableStateOf(false) }
    var highResError by remember(request.url) { mutableStateOf(false) }
    var baseError by remember(request.url) { mutableStateOf(false) }
    var imageAspectRatio by remember(request.url) { mutableFloatStateOf(1f) }

    var scale by remember(request.url, sessionKey) { mutableFloatStateOf(MinPreviewScale) }
    var offset by remember(request.url, sessionKey) { mutableStateOf(Offset.Zero) }
    var animJob by remember(request.url, sessionKey) { mutableStateOf<Job?>(null) }
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    var isSaving by remember(request.url, sessionKey) { mutableStateOf(false) }
    val latestRequest by rememberUpdatedState(request)
    val latestHighResUrl by rememberUpdatedState(highResUrl)
    val latestIsClosing by rememberUpdatedState(isClosing)
    val latestProgressProvider by rememberUpdatedState(progressProvider)
    val latestOnTransformChanged by rememberUpdatedState(onTransformChanged)

    fun updateTransform(newScale: Float, newOffset: Offset) {
        scale = newScale
        offset = newOffset
        latestOnTransformChanged(newScale, newOffset)
    }

    fun performSave() {
        if (latestIsClosing || isSaving) return
        isSaving = true
        val currentReq = latestRequest
        val currentHighRes = latestHighResUrl
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeCoverBitmap(context, currentHighRes)
                        ?: if (currentHighRes != currentReq.url) decodeCoverBitmap(context, currentReq.url) else null
                }
                if (bitmap == null) {
                    ToastManager.showToast("保存失败，请稍后重试")
                    return@launch
                }
                val saved = ShareCardImages.saveToGallery(
                    context = context,
                    bitmap = bitmap,
                    baseName = sanitizeCoverFileName(currentReq.title)
                )
                ToastManager.showToast(if (saved) "已保存到相册" else "保存失败，请稍后重试")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ToastManager.showToast("保存失败，请稍后重试")
            } finally {
                isSaving = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            performSave()
        } else {
            ToastManager.showToast("需要存储权限才能保存图片")
        }
    }

    val scrimColor = MelodiaColors.Scrim
    val btnTranslatePx = with(density) { 14.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOriginInRoot = it.positionInRoot() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progressProvider().coerceIn(0f, 1f)
                    alpha = (p / 0.78f).coerceIn(0f, 1f)
                }
                .background(scrimColor.copy(alpha = ScrimAlpha))
        )

        // 按视口 constraints 首帧同步算出目标宽高，避免动画首帧尺寸跳闪
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val horizontalPadPx = with(density) { (MelodiaSpacing.lg * 2).toPx() }
            val verticalReservePx = with(density) { 160.dp.toPx() }
            val minCoverPx = with(density) { 120.dp.toPx() }
            val viewportW = constraints.maxWidth.toFloat()
            val viewportH = constraints.maxHeight.toFloat()
            val maxAvailW = (viewportW - horizontalPadPx).coerceAtLeast(minCoverPx)
            val maxAvailH = (viewportH - verticalReservePx).coerceAtLeast(minCoverPx)
            val ratio = imageAspectRatio.coerceIn(0.35f, 2.8f)
            val widthFromHeight = maxAvailH * ratio
            val targetWidthPx = if (widthFromHeight < maxAvailW) widthFromHeight else maxAvailW
            val targetHeightPx = targetWidthPx / ratio
            val targetWidth = with(density) { targetWidthPx.toDp() }
            val targetHeight = with(density) { targetHeightPx.toDp() }
            val coverShape = RoundedCornerShape(RadiusCompact)

            fun clampOffset(candidate: Offset, targetScale: Float): Offset {
                if (targetScale <= MinPreviewScale || viewportW <= 0f || viewportH <= 0f) {
                    return Offset.Zero
                }
                val maxX = ((targetWidthPx * targetScale - viewportW) / 2f).coerceAtLeast(0f)
                val maxY = ((targetHeightPx * targetScale - viewportH) / 2f).coerceAtLeast(0f)
                return Offset(
                    x = candidate.x.coerceIn(-maxX, maxX),
                    y = candidate.y.coerceIn(-maxY, maxY)
                )
            }

            fun isInsideImage(position: Offset): Boolean {
                if (viewportW <= 0f || viewportH <= 0f) return false
                val centerX = viewportW / 2f + offset.x
                val centerY = viewportH / 2f + offset.y
                val halfW = (targetWidthPx * scale) / 2f
                val halfH = (targetHeightPx * scale) / 2f
                return abs(position.x - centerX) <= halfW && abs(position.y - centerY) <= halfH
            }

            fun animateTransform(targetScale: Float, targetOffset: Offset) {
                animJob?.cancel()
                val startScale = scale
                val startOffset = offset
                val clampedTarget = clampOffset(targetOffset, targetScale)
                animJob = scope.launch {
                    launch {
                        animate(startScale, targetScale, animationSpec = tween(220)) { value, _ ->
                            updateTransform(value, offset)
                        }
                    }
                    launch {
                        animate(
                            typeConverter = Offset.VectorConverter,
                            initialValue = startOffset,
                            targetValue = clampedTarget,
                            animationSpec = tween(220)
                        ) { value, _ ->
                            updateTransform(scale, value)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(request.url, sessionKey, targetWidthPx, targetHeightPx) {
                        detectTapGestures(
                            onTap = { pos ->
                                if (!latestIsClosing && !isInsideImage(pos)) {
                                    onDismiss()
                                }
                            },
                            onDoubleTap = { pos ->
                                if (latestIsClosing || latestProgressProvider() < 0.99f || !isInsideImage(pos)) {
                                    return@detectTapGestures
                                }
                                if (scale > 1.05f) {
                                    animateTransform(MinPreviewScale, Offset.Zero)
                                } else {
                                    val viewportCenter = Offset(viewportW / 2f, viewportH / 2f)
                                    val focusOffset = (viewportCenter - pos) * (DoubleTapScale - 1f)
                                    animateTransform(DoubleTapScale, focusOffset)
                                }
                            }
                        )
                    }
                    .pointerInput(request.url, sessionKey, targetWidthPx, targetHeightPx) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val touchSlop = viewConfiguration.touchSlop
                            var totalPan = Offset.Zero
                            var dragSlopExceeded = false
                            do {
                                val event = awaitPointerEvent()
                                val canceled = event.changes.any { it.isConsumed }
                                if (canceled) break
                                val pressedCount = event.changes.count { it.pressed }
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                totalPan += panChange
                                if (!dragSlopExceeded && totalPan.getDistance() > touchSlop) {
                                    dragSlopExceeded = true
                                }
                                if (latestIsClosing || latestProgressProvider() < 0.99f) {
                                    // 动画期间超 slop 的拖动也要吞掉，防止穿透到底层页面
                                    if (pressedCount > 1 || dragSlopExceeded) {
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    }
                                    continue
                                }
                                val shouldConsume = pressedCount > 1 ||
                                    zoomChange != 1f ||
                                    dragSlopExceeded
                                if (shouldConsume) {
                                    if (zoomChange != 1f || (scale > MinPreviewScale && panChange != Offset.Zero)) {
                                        animJob?.cancel()
                                        val nextScale = (scale * zoomChange).coerceIn(MinPreviewScale, MaxPreviewScale)
                                        val nextOffset = if (nextScale > MinPreviewScale) {
                                            clampOffset(offset + panChange, nextScale)
                                        } else {
                                            Offset.Zero
                                        }
                                        updateTransform(nextScale, nextOffset)
                                    }
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (!latestIsClosing &&
                                latestProgressProvider() >= 0.99f &&
                                scale <= 1.02f &&
                                (scale != MinPreviewScale || offset != Offset.Zero)
                            ) {
                                animateTransform(MinPreviewScale, Offset.Zero)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = targetWidth, height = targetHeight)
                        .graphicsLayer {
                            val p = progressProvider().coerceIn(0f, 1f)
                            val activeScale = if (isClosing) exitStartScale else scale
                            val activeOffset = if (isClosing) exitStartOffset else offset
                            val sb = request.sourceBounds
                            val useBounds = sb != null &&
                                sb.isValid &&
                                viewportW > 0f &&
                                viewportH > 0f &&
                                targetWidthPx > 1f &&
                                sb.centerX in overlayOriginInRoot.x..(overlayOriginInRoot.x + viewportW) &&
                                sb.centerY in overlayOriginInRoot.y..(overlayOriginInRoot.y + viewportH)
                            if (useBounds) {
                                val dstCenterX = overlayOriginInRoot.x + viewportW / 2f
                                val dstCenterY = overlayOriginInRoot.y + viewportH / 2f
                                val fromScale = (sb.width / targetWidthPx).coerceIn(0.1f, 3f)
                                val fromDx = sb.centerX - dstCenterX
                                val fromDy = sb.centerY - dstCenterY
                                scaleX = lerp(fromScale, activeScale, p)
                                scaleY = lerp(fromScale, activeScale, p)
                                translationX = lerp(fromDx, activeOffset.x, p)
                                translationY = lerp(fromDy, activeOffset.y, p)
                                alpha = 1f
                            } else {
                                scaleX = lerp(0.88f, activeScale, p)
                                scaleY = lerp(0.88f, activeScale, p)
                                translationX = lerp(0f, activeOffset.x, p)
                                translationY = lerp(0f, activeOffset.y, p)
                                alpha = p
                            }
                        }
                        .clip(coverShape)
                ) {
                    if (baseError && (!needDualLayer || highResError)) {
                        CoverPlaceholder()
                    }

                    // 地址未升级时只按原图尺寸请求一次，避免重复加载
                    AsyncImage(
                        model = remember(request.url, needDualLayer) {
                            ImageRequest.Builder(context)
                                .data(request.url)
                                .apply { if (!needDualLayer) size(CoilSize.ORIGINAL) }
                                .build()
                        },
                        contentDescription = request.title.ifBlank { "封面预览" },
                        contentScale = ContentScale.Crop,
                        onState = { painterState ->
                            when (painterState) {
                                is AsyncImagePainter.State.Success -> {
                                    baseError = false
                                    val img = painterState.result.image
                                    if (img.width > 0 && img.height > 0 && !highResLoaded) {
                                        imageAspectRatio = img.width.toFloat() / img.height.toFloat()
                                    }
                                }
                                is AsyncImagePainter.State.Error -> {
                                    baseError = true
                                }
                                else -> Unit
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 上层叠放高清原图，解码成功后无缝覆盖，失败则自动隐藏回退底层图
                    if (needDualLayer && !highResError) {
                        AsyncImage(
                            model = remember(highResUrl) {
                                ImageRequest.Builder(context)
                                    .data(highResUrl)
                                    .size(CoilSize.ORIGINAL)
                                    .build()
                            },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onState = { painterState ->
                                when (painterState) {
                                    is AsyncImagePainter.State.Success -> {
                                        highResLoaded = true
                                        highResError = false
                                        val img = painterState.result.image
                                        if (img.width > 0 && img.height > 0) {
                                            imageAspectRatio = img.width.toFloat() / img.height.toFloat()
                                        }
                                    }
                                    is AsyncImagePainter.State.Error -> {
                                        highResLoaded = false
                                        highResError = true
                                    }
                                    else -> Unit
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = if (highResLoaded) 1f else 0f
                                }
                        )
                    }
                }
            }
        }

        MelodiaButton(
            onClick = {
                if (latestIsClosing || isSaving) return@MelodiaButton
                if (ShareCardImages.needsLegacyPermission(context)) {
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    performSave()
                }
            },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.6f),
                disabledContentColor = Color.Black.copy(alpha = 0.6f)
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.lg)
                .graphicsLayer {
                    val p = progressProvider().coerceIn(0f, 1f)
                    val btnP = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    alpha = btnP
                    translationY = btnTranslatePx * (1f - btnP)
                }
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存中...", fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    imageVector = MelodiaIcons.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存图片", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private suspend fun decodeCoverBitmap(context: Context, url: String): Bitmap? {
    if (url.isBlank()) return null
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(CoilSize.ORIGINAL)
            .allowHardware(false)
            .build()
        val raw = SingletonImageLoader.get(context).execute(request).image?.toBitmap() ?: return null
        // 硬件位图无法直接 compress 写入相册
        if (raw.config == Bitmap.Config.HARDWARE) raw.copy(Bitmap.Config.ARGB_8888, false) else raw
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
