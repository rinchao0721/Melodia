package com.lin0721.linmusic.core.plugin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs

enum class MotionPreset(val key: String) {
    ROTATE_WHILE_PLAYING("rotateWhilePlaying"), SCALE_ON_PAUSE("scaleOnPause"), PULSE("pulse"), SONG_CHANGE("songChange");

    companion object {
        fun fromKey(key: String): MotionPreset? = entries.firstOrNull { it.key == key }
    }
}

enum class SongChangeEffect(val key: String) {
    FADE("fade"), SLIDE_UP("slideUp"), ZOOM("zoom");

    companion object {
        fun fromKey(key: String): SongChangeEffect? = entries.firstOrNull { it.key == key }
    }
}

enum class MotionEasing(val key: String, val easing: Easing) {
    LINEAR("linear", LinearEasing),
    EASE("ease", FastOutSlowInEasing),
    EASE_IN("easeIn", FastOutLinearInEasing),
    EASE_OUT("easeOut", LinearOutSlowInEasing);

    companion object {
        fun fromKey(key: String): MotionEasing? = entries.firstOrNull { it.key == key }
    }
}

enum class MotionRepeat(val key: String) {
    ONCE("once"), LOOP("loop"), REVERSE("reverse");

    companion object {
        fun fromKey(key: String): MotionRepeat? = entries.firstOrNull { it.key == key }
    }
}

enum class MotionTrigger(val key: String) {
    ALWAYS("always"), PLAYING("playing"), SONG_CHANGE("songChange"), TAP("tap");

    companion object {
        fun fromKey(key: String): MotionTrigger? = entries.firstOrNull { it.key == key }
    }
}

// 关键帧的属性已按「没写沿用上一帧」补全
data class MotionFrame(
    val at: Float,
    val scale: Float = 1f,
    val rotate: Float = 0f,
    val alpha: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f
)

sealed interface LayoutAnimation {
    data class Preset(
        val preset: MotionPreset,
        val period: Float,
        val scale: Float,
        val effect: SongChangeEffect = SongChangeEffect.FADE
    ) : LayoutAnimation

    data class Keyframes(
        val frames: List<MotionFrame>,
        val durationMs: Int,
        val easing: MotionEasing,
        val repeat: MotionRepeat,
        val trigger: MotionTrigger
    ) : LayoutAnimation
}

internal object LayoutMotionParser {

    private const val MAX_FRAMES = 8
    private const val MIN_DURATION = 100
    private const val MAX_DURATION = 60_000
    private const val MAX_OFFSET = 200f

    fun parse(value: JsonElement, warnings: MutableList<String>): LayoutAnimation? {
        (value as? JsonPrimitive)?.takeIf { it.isString }?.let { primitive ->
            return MotionPreset.fromKey(primitive.content)?.let { preset(it, JsonObject(emptyMap()), warnings) }
                ?: null.also { warnings += "animation 预设 ${primitive.content} 无效，已忽略" }
        }
        val obj = value as? JsonObject ?: return null.also { warnings += "animation 取值无效，已忽略" }
        obj.text("preset")?.let { key ->
            return MotionPreset.fromKey(key)?.let { preset(it, obj, warnings) }
                ?: null.also { warnings += "animation 预设 $key 无效，已忽略" }
        }
        return keyframes(obj, warnings)
    }

    private fun preset(preset: MotionPreset, obj: JsonObject, warnings: MutableList<String>): LayoutAnimation.Preset = when (preset) {
        MotionPreset.ROTATE_WHILE_PLAYING -> LayoutAnimation.Preset(preset, obj.number("period")?.coerceIn(2f, 60f) ?: 20f, 1f)
        MotionPreset.SCALE_ON_PAUSE -> LayoutAnimation.Preset(preset, 0f, obj.number("scale")?.coerceIn(0.5f, 1f) ?: 0.85f)
        MotionPreset.PULSE -> LayoutAnimation.Preset(
            preset,
            obj.number("period")?.coerceIn(0.5f, 10f) ?: 2f,
            obj.number("scale")?.coerceIn(1f, 1.2f) ?: 1.04f
        )
        MotionPreset.SONG_CHANGE -> LayoutAnimation.Preset(
            preset, 0f, 1f,
            obj.text("effect")?.let { key ->
                SongChangeEffect.fromKey(key) ?: null.also { warnings += "animation 的 effect 取值 $key 无效，已忽略" }
            } ?: SongChangeEffect.FADE
        )
    }

    private fun keyframes(obj: JsonObject, warnings: MutableList<String>): LayoutAnimation.Keyframes? {
        val raw = obj["keyframes"] as? JsonArray ?: return null.also { warnings += "animation 缺少 keyframes，已忽略" }
        if (raw.size !in 2..MAX_FRAMES) return null.also { warnings += "keyframes 需要 2–$MAX_FRAMES 帧，已忽略" }
        var previous = MotionFrame(0f)
        val frames = mutableListOf<MotionFrame>()
        for (item in raw) {
            val frame = item as? JsonObject ?: return null.also { warnings += "keyframes 里有无效的帧，已忽略" }
            val at = frame.number("at")?.takeIf { it in 0f..1f } ?: return null.also { warnings += "keyframes 的 at 需要是 0–1，已忽略" }
            if (frames.isNotEmpty() && at <= previous.at) return null.also { warnings += "keyframes 的 at 需要递增，已忽略" }
            previous = MotionFrame(
                at = at,
                scale = frame.number("scale")?.coerceIn(0f, 3f) ?: previous.scale,
                rotate = frame.number("rotate") ?: previous.rotate,
                alpha = frame.number("alpha")?.coerceIn(0f, 1f) ?: previous.alpha,
                x = frame.number("x")?.coerceIn(-MAX_OFFSET, MAX_OFFSET) ?: previous.x,
                y = frame.number("y")?.coerceIn(-MAX_OFFSET, MAX_OFFSET) ?: previous.y
            )
            frames += previous
        }
        fun <T> option(key: String, from: (String) -> T?, default: T): T =
            obj.text(key)?.let { text -> from(text) ?: null.also { warnings += "animation 的 $key 取值 $text 无效，已忽略" } } ?: default
        return LayoutAnimation.Keyframes(
            frames = frames,
            durationMs = (obj["duration"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.coerceIn(MIN_DURATION, MAX_DURATION) ?: 1000,
            easing = option("easing", MotionEasing::fromKey, MotionEasing.EASE),
            repeat = option("repeat", MotionRepeat::fromKey, MotionRepeat.ONCE),
            trigger = option("trigger", MotionTrigger::fromKey, MotionTrigger.ALWAYS)
        )
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.number(key: String): Float? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }
}

private val LayoutAnimation.Keyframes.triggeredOnce: Boolean
    get() = trigger == MotionTrigger.SONG_CHANGE || trigger == MotionTrigger.TAP

// 静止时停留的进度：一次性的停在最后一帧，循环的停在第一帧
internal val LayoutAnimation.Keyframes.restProgress: Float
    get() = if (triggeredOnce || repeat == MotionRepeat.ONCE) 1f else 0f

// 节点静止时可能停留的帧；跟随播放状态的在播放与暂停时各停一帧
internal val LayoutAnimation.Keyframes.restFrames: List<MotionFrame>
    get() = when {
        trigger == MotionTrigger.PLAYING -> listOf(frames.first(), frames.last())
        restProgress == 1f -> listOf(frames.last())
        else -> listOf(frames.first())
    }

// 关键帧之间线性插值，t 为 0–1
internal fun List<MotionFrame>.sample(t: Float): MotionFrame {
    if (t <= first().at) return first()
    if (t >= last().at) return last()
    val end = indexOfFirst { it.at >= t }
    val a = this[end - 1]
    val b = this[end]
    val f = (t - a.at) / (b.at - a.at)
    fun lerp(x: Float, y: Float) = x + (y - x) * f
    return MotionFrame(t, lerp(a.scale, b.scale), lerp(a.rotate, b.rotate), lerp(a.alpha, b.alpha), lerp(a.x, b.x), lerp(a.y, b.y))
}

private const val SONG_CHANGE_MS = 360
private const val SLIDE_DISTANCE_DP = 24f
private val SongChangeEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// 节点动效：只改绘制层，不影响布局；关闭界面动画时停在静止状态
@Composable
internal fun Modifier.layoutAnimation(animation: LayoutAnimation, tapCount: Int): Modifier {
    val env = LocalLayoutEnvironment.current
    val density = LocalDensity.current
    return when (animation) {
        is LayoutAnimation.Preset -> when (animation.preset) {
            MotionPreset.ROTATE_WHILE_PLAYING -> {
                val angle = remember { Animatable(0f) }
                LaunchedEffect(env.playing, env.motionEnabled, animation.period) {
                    if (!env.playing || !env.motionEnabled) return@LaunchedEffect
                    val full = (animation.period * 1000).toInt()
                    while (isActive) {
                        val remaining = (360f - angle.value % 360f)
                        angle.animateTo(angle.value + remaining, tween((full * remaining / 360f).toInt().coerceAtLeast(1), easing = LinearEasing))
                        angle.snapTo(angle.value % 360f)
                    }
                }
                graphicsLayer { rotationZ = angle.value }
            }
            MotionPreset.SCALE_ON_PAUSE -> {
                val scale = remember { Animatable(1f) }
                val target = if (env.playing) 1f else animation.scale
                LaunchedEffect(target, env.motionEnabled) {
                    if (env.motionEnabled) scale.animateTo(target, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) else scale.snapTo(target)
                }
                graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            }
            MotionPreset.PULSE -> {
                val scale = remember { Animatable(1f) }
                LaunchedEffect(env.playing, env.motionEnabled, animation.period, animation.scale) {
                    if (!env.playing || !env.motionEnabled) {
                        scale.snapTo(1f)
                        return@LaunchedEffect
                    }
                    val half = (animation.period * 500).toInt()
                    while (isActive) {
                        scale.animateTo(animation.scale, tween(half, easing = FastOutSlowInEasing))
                        scale.animateTo(1f, tween(half, easing = FastOutSlowInEasing))
                    }
                }
                graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            }
            MotionPreset.SONG_CHANGE -> {
                val progress = remember { Animatable(1f) }
                val firstKey = remember { arrayOf(env.songKey) }
                LaunchedEffect(env.songKey) {
                    // 首次进入不播放，只在切歌时播放
                    if (firstKey[0] == env.songKey || !env.motionEnabled) return@LaunchedEffect
                    firstKey[0] = null
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(SONG_CHANGE_MS, easing = SongChangeEasing))
                }
                graphicsLayer {
                    val p = progress.value
                    alpha = p
                    when (animation.effect) {
                        SongChangeEffect.FADE -> Unit
                        SongChangeEffect.SLIDE_UP -> translationY = (1f - p) * SLIDE_DISTANCE_DP * density.density
                        SongChangeEffect.ZOOM -> {
                            scaleX = 0.9f + 0.1f * p
                            scaleY = 0.9f + 0.1f * p
                        }
                    }
                }
            }
        }
        is LayoutAnimation.Keyframes -> {
            val restAt = animation.restProgress
            // 切歌、点击触发的在触发前就停在静止帧，节点照常显示
            val progress = remember { Animatable(if (animation.triggeredOnce) restAt else 0f) }
            val duration = animation.durationMs
            val easing = animation.easing.easing
            suspend fun run(from: Float, to: Float) {
                progress.snapTo(from)
                progress.animateTo(to, tween((duration * abs(to - from)).toInt().coerceAtLeast(1), easing = easing))
            }
            suspend fun playAll() {
                when (animation.repeat) {
                    MotionRepeat.ONCE -> run(0f, 1f)
                    MotionRepeat.LOOP -> while (true) run(0f, 1f)
                    MotionRepeat.REVERSE -> while (true) {
                        run(0f, 1f)
                        run(1f, 0f)
                    }
                }
            }
            when (animation.trigger) {
                MotionTrigger.ALWAYS -> LaunchedEffect(env.motionEnabled) {
                    if (env.motionEnabled) playAll() else progress.snapTo(restAt)
                }
                MotionTrigger.PLAYING -> LaunchedEffect(env.playing, env.motionEnabled) {
                    val target = if (env.playing) 1f else 0f
                    when {
                        !env.motionEnabled -> progress.snapTo(target)
                        // 只播一次的按播放状态正放、倒放；循环的在播放时循环，暂停时回到第一帧
                        animation.repeat == MotionRepeat.ONCE || !env.playing -> run(progress.value, target)
                        else -> {
                            run(progress.value, 0f)
                            playAll()
                        }
                    }
                }
                MotionTrigger.SONG_CHANGE -> {
                    val firstKey = remember { arrayOf(env.songKey) }
                    LaunchedEffect(env.songKey) {
                        if (firstKey[0] == env.songKey || !env.motionEnabled) return@LaunchedEffect
                        firstKey[0] = null
                        run(0f, 1f)
                    }
                }
                MotionTrigger.TAP -> LaunchedEffect(tapCount) {
                    if (tapCount > 0 && env.motionEnabled) run(0f, 1f)
                }
            }
            graphicsLayer {
                val frame = animation.frames.sample(progress.value)
                scaleX = frame.scale
                scaleY = frame.scale
                rotationZ = frame.rotate
                alpha = frame.alpha
                translationX = frame.x * density.density
                translationY = frame.y * density.density
            }
        }
    }
}
