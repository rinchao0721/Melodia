package com.lin0721.linmusic.core.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

private const val TAG = "ThemeReveal"
private const val REVEAL_DURATION_MS = 300
// 写入设置后主题迟迟没有变化时撤掉截图，避免画面卡在旧主题
private const val REVEAL_TIMEOUT_MS = 1000L

// 以 origin 为圆心覆盖整个区域所需的半径，即到最远角的距离
fun revealRadius(origin: Offset, size: Size): Float {
    val dx = max(origin.x, size.width - origin.x)
    val dy = max(origin.y, size.height - origin.y)
    return hypot(dx, dy)
}

internal class RevealSnapshot(val image: ImageBitmap, val origin: Offset, val themeKey: Any?)

// 主题切换的圆形扩散：先截下旧主题画面再写设置，新主题生效后在截图上挖圆洞向外扩散
@Stable
class ThemeRevealController internal constructor(private val layer: GraphicsLayer) {
    internal var snapshot by mutableStateOf<RevealSnapshot?>(null)
    internal var enabled = true
    internal var themeKey: Any? = null
    internal var hostOffset = Offset.Zero

    // windowOrigin 为窗口坐标；截图失败或动画关闭时直接应用
    suspend fun switch(windowOrigin: Offset, apply: () -> Unit) {
        if (!enabled || windowOrigin == Offset.Unspecified) {
            apply()
            return
        }
        val image = try {
            layer.toImageBitmap()
        } catch (e: Exception) {
            AppLogger.w(TAG, "截取主题切换画面失败", e)
            null
        }
        if (image != null) snapshot = RevealSnapshot(image, windowOrigin - hostOffset, themeKey)
        apply()
    }

    internal fun clear(target: RevealSnapshot) {
        if (snapshot === target) snapshot = null
    }
}

val LocalThemeReveal = staticCompositionLocalOf<ThemeRevealController?> { null }

// 模式卡片、皮肤卡片的点击入口：主题会变时走圆形扩散，否则直接应用（不变时等不到新主题会卡住截图）
class ThemeSwitcher internal constructor(
    private val scope: CoroutineScope,
    private val controller: ThemeRevealController?
) {
    fun switch(windowOrigin: Offset, changesTheme: Boolean, apply: () -> Unit) {
        if (controller == null || !changesTheme) {
            apply()
            return
        }
        scope.launch { controller.switch(windowOrigin, apply) }
    }
}

@Composable
fun rememberThemeSwitcher(): ThemeSwitcher {
    val scope = rememberCoroutineScope()
    val controller = LocalThemeReveal.current
    return remember(scope, controller) { ThemeSwitcher(scope, controller) }
}

// themeKey 为当前生效主题的完整取值（如色板），变化即视为新主题已生效
@Composable
fun ThemeRevealHost(themeKey: Any, enabled: Boolean, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val controller = remember(layer) { ThemeRevealController(layer) }
    SideEffect {
        controller.enabled = enabled
        controller.themeKey = themeKey
    }
    val progress = remember { Animatable(0f) }
    val snapshot = controller.snapshot
    LaunchedEffect(snapshot, themeKey) {
        val current = snapshot ?: return@LaunchedEffect
        progress.snapTo(0f)
        if (themeKey == current.themeKey) {
            delay(REVEAL_TIMEOUT_MS)
        } else {
            progress.animateTo(1f, tween(REVEAL_DURATION_MS, easing = FastOutSlowInEasing))
        }
        controller.clear(current)
    }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { controller.hostOffset = it.positionInWindow() }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val shot = controller.snapshot ?: return@drawWithContent
                val radius = revealRadius(shot.origin, size) * progress.value
                val hole = Path().apply { addOval(Rect(shot.origin, radius)) }
                clipPath(hole, ClipOp.Difference) { drawImage(shot.image) }
            }
    ) {
        CompositionLocalProvider(LocalThemeReveal provides controller, content = content)
    }
}
