package com.lin0721.linmusic.desktop.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.plugin.ActiveBackdrop
import com.lin0721.linmusic.core.plugin.BackdropExtension
import com.lin0721.linmusic.core.plugin.PluginBackdropEntry
import com.lin0721.linmusic.core.ui.skin.SkinImageCodec
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.core.ui.theme.PaneBackdrop
import com.lin0721.linmusic.core.ui.theme.SkinBackdropHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

private const val TAG = "PluginBackdrop"
private const val RESIZE_DEBOUNCE_MS = 150L
private const val MAX_FRAME_SIDE = 1280
private const val BYTES_PER_PIXEL = 4
private const val NANOS_PER_MILLI = 1_000_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val RETAINED_FRAMES = 2
private const val STILL_FRAME_TIME_MS = 8_000L
private const val RENDER_TIMEOUT_MS = 2_000L

internal fun MelodiaPalette.withPluginBackdrop(entry: PluginBackdropEntry): MelodiaPalette =
    copy(backdrop = PaneBackdrop(dim = entry.dim, paneOpacity = entry.paneOpacity, playerDim = entry.dim))

// 帧大小跟窗口比例走，宽度不超过 maxWidth
internal fun backdropFrameSize(rootWidth: Float, rootHeight: Float, maxWidth: Int): Pair<Int, Int>? {
    if (rootWidth <= 0f || rootHeight <= 0f || maxWidth <= 0) return null
    val width = minOf(rootWidth.roundToInt(), maxWidth).coerceAtLeast(1)
    return width to (width * rootHeight / rootWidth).roundToInt().coerceAtLeast(1)
}

// 逐帧写入 ARGB 再拷进 Skia 位图；插件写入的是不透明像素
internal class BackdropFrameBuffer(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)
    private val bytes = ByteArray(pixels.size * BYTES_PER_PIXEL)
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
    private val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)

    fun toBitmap(): Bitmap {
        buffer.clear()
        buffer.put(pixels)
        val bitmap = Bitmap()
        check(bitmap.installPixels(info, bytes, width * BYTES_PER_PIXEL)) { "帧位图写入失败" }
        bitmap.setImmutable()
        return bitmap
    }
}

// running 为 false（隐藏或最小化）时不出帧；moving 为 false 时只出一帧静止画面
// 换帧与回收都在主线程，和 Compose 绘制同线程，旧帧不会在读取中途被释放
@OptIn(FlowPreview::class)
@Composable
internal fun PluginBackdropAnimator(
    host: SkinBackdropHost,
    backdrop: ActiveBackdrop,
    codec: SkinImageCodec,
    running: Boolean,
    moving: Boolean
) {
    LaunchedEffect(host, backdrop, running, moving) {
        if (!running) return@LaunchedEffect
        val frameMillis = MILLIS_PER_SECOND / backdrop.entry.fps
        try {
            val renderer = backdrop.renderer
            if (renderer != null) {
                val retained = ArrayDeque<Bitmap>()
                try {
                    snapshotFlow { host.rootBounds.size }
                        .filter { it.width > 0f && it.height > 0f }
                        .distinctUntilChanged()
                        .debounce(RESIZE_DEBOUNCE_MS)
                        .collectLatest { size -> renderLoop(size, renderer, frameMillis, moving, host, retained) }
                } finally {
                    host.image = host.staticImage
                    retained.forEach(Bitmap::close)
                }
            } else {
                try {
                    playSequence(backdrop, codec, frameMillis, moving, host)
                } finally {
                    host.image = host.staticImage
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            AppLogger.w(TAG, "动态背景播放失败: ${backdrop.key}", e)
        }
    }
}

private class RenderOutcome(val bitmap: Bitmap?, val stalled: Boolean)

private suspend fun playSequence(
    backdrop: ActiveBackdrop,
    codec: SkinImageCodec,
    frameMillis: Long,
    moving: Boolean,
    host: SkinBackdropHost
) {
    val frames: List<ImageBitmap> = withContext(Dispatchers.IO) { backdrop.frames.mapNotNull { codec.decode(it, MAX_FRAME_SIDE) } }
    if (frames.isEmpty()) {
        AppLogger.w(TAG, "动态背景没有可用的图片: ${backdrop.key}")
        return
    }
    host.image = frames.first()
    if (!moving || frames.size == 1) {
        // 协程结束就会恢复静态图，这里挂起到被取消
        delay(Long.MAX_VALUE)
    }
    var index = 0
    while (coroutineContext.isActive) {
        delay(frameMillis)
        index = (index + 1) % frames.size
        host.image = frames[index]
    }
}

private suspend fun renderLoop(
    size: Size,
    renderer: BackdropExtension,
    frameMillis: Long,
    moving: Boolean,
    host: SkinBackdropHost,
    retained: ArrayDeque<Bitmap>
) {
    val (width, height) = backdropFrameSize(size.width, size.height, renderer.maxWidth) ?: return
    val frame = BackdropFrameBuffer(width, height)
    val publish = { bitmap: Bitmap ->
        host.image = bitmap.asComposeImageBitmap()
        retained.addLast(bitmap)
        while (retained.size > RETAINED_FRAMES) retained.removeFirst().close()
    }
    // 插件可能卡死不返回，放到独立协程里等，界面协程才能按时结束
    suspend fun renderAt(timeMs: Long): RenderOutcome {
        val rendering = CoroutineScope(Dispatchers.Default).async {
            if (renderer.render(width, height, timeMs, frame.pixels)) {
                ensureActive()
                frame.toBitmap()
            } else {
                null
            }
        }
        return try {
            withTimeout(RENDER_TIMEOUT_MS) { RenderOutcome(rendering.await(), stalled = false) }
        } catch (e: TimeoutCancellationException) {
            AppLogger.w(TAG, "动态背景渲染超时，已停止")
            renderer.reportStalled()
            RenderOutcome(null, stalled = true)
        } finally {
            rendering.cancel()
        }
    }
    if (!moving) {
        val still = renderAt(STILL_FRAME_TIME_MS)
        still.bitmap?.let(publish)
        delay(Long.MAX_VALUE)
    }
    val started = System.nanoTime()
    while (coroutineContext.isActive) {
        val frameStart = System.nanoTime()
        val outcome = renderAt((frameStart - started) / NANOS_PER_MILLI)
        // 卡住的调用可能还在写这块缓冲，不能继续渲染
        if (outcome.stalled) return
        outcome.bitmap?.let(publish)
        val spent = (System.nanoTime() - frameStart) / NANOS_PER_MILLI
        delay((frameMillis - spent).coerceAtLeast(1L))
    }
}
