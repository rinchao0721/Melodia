package com.lin0721.linmusic.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lin0721.linmusic.core.ui.skin.LocalImmersiveBasePalette
import kotlin.math.floor
import kotlin.math.roundToInt

// 皮肤背景图与根布局在窗口中的位置；各处按窗口坐标取同一张图的对应区域，拼起来无缝
@Stable
class SkinBackdropHost(val image: ImageBitmap) {
    var rootBounds by mutableStateOf(Rect.Zero)
}

// 色板带 backdrop 且背景图已解码时才提供
val LocalSkinBackdrop = staticCompositionLocalOf<SkinBackdropHost?> { null }

// 根布局铺底：登记根位置，画背景图并叠画布遮罩；没有背景图时铺 fallback
@Composable
fun Modifier.skinBackdropRoot(fallback: Color): Modifier {
    val host = LocalSkinBackdrop.current
    val backdrop = LocalMelodiaPalette.current.backdrop
    if (host == null || backdrop == null) return background(fallback)
    val overlay = LocalMelodiaPalette.current.windowBackground.copy(alpha = backdrop.dim)
    return onGloballyPositioned { host.rootBounds = it.boundsInWindow() }
        .drawBehind {
            drawAlignedImage(host, host.rootBounds.topLeft)
            drawRect(overlay)
        }
}

// 盖住其他面板的整块画布区域（如全屏歌词外沿），看起来与根画布一致
@Composable
fun Modifier.canvasBackdrop(fallback: Color): Modifier = alignedBackdrop(fallback, withShell = false, forPlayer = false, alpha = null)

// 需要挡住下方滚动内容的吸顶栏、面板内浮层：画出与外壳相同的底，看起来透明但不透出内容
@Composable
fun Modifier.paneBackdrop(fallback: Color): Modifier = alignedBackdrop(fallback, withShell = true, forPlayer = false, alpha = null)

// 随滚动渐显的顶栏：与 paneBackdrop 相同，整体按 alpha 淡入
@Composable
fun Modifier.paneBackdrop(fallback: Color, alpha: () -> Float): Modifier =
    alignedBackdrop(fallback, withShell = true, forPlayer = false, alpha = alpha)

// 播放区透出背景图：沉浸区文字直接压在背景图上，遮罩按皮肤的播放区遮罩
@Composable
fun Modifier.playerBackdrop(fallback: Color): Modifier = alignedBackdrop(fallback, withShell = false, forPlayer = true, alpha = null)

@Composable
fun Modifier.playerBackdrop(fallback: Color, alpha: () -> Float): Modifier =
    alignedBackdrop(fallback, withShell = false, forPlayer = true, alpha = alpha)

// alpha 为 null 时不透明铺满；否则整体按 alpha 淡入，没有背景图时同样按 alpha 铺 fallback
@Composable
private fun Modifier.alignedBackdrop(fallback: Color, withShell: Boolean, forPlayer: Boolean, alpha: (() -> Float)?): Modifier {
    val host = LocalSkinBackdrop.current
    val palette = LocalMelodiaPalette.current
    val backdrop = palette.backdrop
    if (host == null || backdrop == null) {
        if (alpha == null) return background(fallback)
        return drawBehind {
            val a = alpha().coerceIn(0f, 1f)
            if (a > 0f) drawRect(fallback.copy(alpha = fallback.alpha * a))
        }
    }
    // 播放区固定按沉浸区深色配色压遮罩，与其中的白字配套
    val overlayBase = if (forPlayer) LocalImmersiveBasePalette.current.windowBackground else palette.windowBackground
    val overlay = overlayBase.copy(alpha = if (forPlayer) backdrop.playerDim else backdrop.dim)
    val shell = palette.shellFill
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            val a = alpha?.invoke()?.coerceIn(0f, 1f) ?: 1f
            if (a <= 0f) return@drawBehind
            fun layers() {
                drawAlignedImage(host, origin)
                drawRect(overlay)
                if (withShell) drawRect(shell)
            }
            if (a >= 1f) {
                layers()
            } else {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = a })
                    layers()
                    canvas.restore()
                }
            }
        }
}

// 背景图按根布局等比铺满居中裁切，再取本元素所在窗口区域对应的部分
private fun DrawScope.drawAlignedImage(host: SkinBackdropHost, origin: Offset) {
    val image = host.image
    val root = host.rootBounds.takeIf { it.width > 0f && it.height > 0f } ?: Rect(origin, size)
    if (size.width <= 0f || size.height <= 0f || image.width <= 0 || image.height <= 0) return
    val scale = maxOf(root.width / image.width, root.height / image.height)
    val cropLeft = (image.width - root.width / scale) / 2f
    val cropTop = (image.height - root.height / scale) / 2f
    val srcLeft = (cropLeft + (origin.x - root.left) / scale).coerceIn(0f, image.width - 1f)
    val srcTop = (cropTop + (origin.y - root.top) / scale).coerceIn(0f, image.height - 1f)
    val srcWidth = (size.width / scale).coerceIn(1f, image.width - srcLeft)
    val srcHeight = (size.height / scale).coerceIn(1f, image.height - srcTop)
    drawImage(
        image = image,
        srcOffset = IntOffset(floor(srcLeft).toInt(), floor(srcTop).toInt()),
        srcSize = IntSize(srcWidth.roundToInt().coerceAtLeast(1), srcHeight.roundToInt().coerceAtLeast(1)),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.Medium
    )
}
