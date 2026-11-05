package com.lin0721.linmusic.core.plugin

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// 宿主动作；清单里的取值，改名会让已发布的插件失效
enum class HostAction(val key: String) {
    TOGGLE_PLAY("togglePlay"),
    NEXT("next"),
    PREVIOUS("previous"),
    SEEK_FORWARD("seekForward"),
    SEEK_BACK("seekBack"),
    TOGGLE_LIKE("toggleLike"),
    CYCLE_PLAY_MODE("cyclePlayMode"),
    PAGER_NEXT("pagerNext"),
    PAGER_PREVIOUS("pagerPrevious"),
    OPEN_LYRICS("openLyrics"),
    OPEN_QUEUE("openQueue"),
    OPEN_COMMENTS("openComments"),
    OPEN_SLEEP_TIMER("openSleepTimer"),
    OPEN_MORE("openMore"),
    COLLAPSE("collapse"),
    TOGGLE_NOW_PLAYING("toggleNowPlaying"),
    TOGGLE_LYRICS_VIEW("toggleLyricsView"),
    CLOSE_LYRICS("closeLyrics"),
    OPEN_LYRICS_SETTINGS("openLyricsSettings");

    companion object {
        fun fromKey(key: String): HostAction? = entries.firstOrNull { it.key == key }

        // 所有播放器区域都能用的动作
        val COMMON = setOf(TOGGLE_PLAY, NEXT, PREVIOUS, SEEK_FORWARD, SEEK_BACK, TOGGLE_LIKE, CYCLE_PLAY_MODE, PAGER_NEXT, PAGER_PREVIOUS)
        val ANDROID_PLAYER = COMMON + setOf(OPEN_LYRICS, OPEN_QUEUE, OPEN_COMMENTS, OPEN_SLEEP_TIMER, OPEN_MORE)
        val DESKTOP = COMMON + setOf(TOGGLE_NOW_PLAYING, TOGGLE_LYRICS_VIEW)
        val ANDROID_LYRICS = COMMON + setOf(OPEN_QUEUE, OPEN_COMMENTS, OPEN_SLEEP_TIMER, OPEN_MORE, CLOSE_LYRICS, OPEN_LYRICS_SETTINGS)
        val DESKTOP_LYRICS = COMMON + setOf(CLOSE_LYRICS, OPEN_LYRICS_SETTINGS)
    }
}

sealed interface LayoutAction {
    data class Host(val action: HostAction) : LayoutAction
    // 插件脚本里用 melodia.register.action 注册的动作
    data class Script(val name: String) : LayoutAction
}

// 播放器布局的运行环境：动效读取播放状态，手势把动作交给宿主
data class LayoutEnvironment(
    val playing: Boolean = false,
    val songKey: Any? = null,
    val motionEnabled: Boolean = true,
    // 宿主自动收起控件时为 false，autoHide 节点随之淡出
    val controlsVisible: Boolean = true,
    val onAction: (LayoutAction) -> Unit = {}
)

val LocalLayoutEnvironment = compositionLocalOf { LayoutEnvironment() }

// 最近一层分页器的翻页入口
internal class LayoutPagerControl(val next: () -> Unit, val previous: () -> Unit)

internal val LocalLayoutPagerControl = compositionLocalOf<LayoutPagerControl?> { null }

internal object LayoutGestureParser {
    const val SCRIPT_PREFIX = "script:"
    private val SCRIPT_NAME = Regex("^[A-Za-z0-9_:-]{1,40}$")

    fun parse(value: String?, key: String, allowed: Set<HostAction>, allowScript: Boolean, warnings: MutableList<String>): LayoutAction? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.startsWith(SCRIPT_PREFIX)) {
            val name = text.removePrefix(SCRIPT_PREFIX)
            return when {
                !allowScript -> null.also { warnings += "$key 不能用脚本动作，已忽略" }
                !SCRIPT_NAME.matches(name) -> null.also { warnings += "$key 的脚本动作名 $name 无效，已忽略" }
                else -> LayoutAction.Script(name)
            }
        }
        val action = HostAction.fromKey(text) ?: return null.also { warnings += "$key 的动作 $text 无效，已忽略" }
        return if (action in allowed) LayoutAction.Host(action) else null.also { warnings += "这个区域不支持动作 $text，已忽略" }
    }
}

fun PlayerLayoutNode.usesScript(): Boolean =
    listOf(onTap, onDoubleTap, onLongPress).any { it is LayoutAction.Script } || children.any { it.usesScript() }

fun PlayerLayoutNode.withoutScripts(): PlayerLayoutNode = copy(
    onTap = onTap.takeUnless { it is LayoutAction.Script },
    onDoubleTap = onDoubleTap.takeUnless { it is LayoutAction.Script },
    onLongPress = onLongPress.takeUnless { it is LayoutAction.Script },
    children = children.map { it.withoutScripts() }
)

// 节点手势；自带点击的组件先处理自己的点击。桌面上右键等同长按
@Composable
internal fun Modifier.layoutGestures(node: PlayerLayoutNode, onTapAnimation: (() -> Unit)?): Modifier {
    val tap = node.onTap
    val doubleTap = node.onDoubleTap
    val longPress = node.onLongPress
    if (tap == null && doubleTap == null && longPress == null && onTapAnimation == null) return this
    // 环境与回调每次重组都会变，手势检测不随之重启
    val env by rememberUpdatedState(LocalLayoutEnvironment.current)
    val pager by rememberUpdatedState(LocalLayoutPagerControl.current)
    val animate by rememberUpdatedState(onTapAnimation)
    fun run(action: LayoutAction?) {
        when {
            action == null -> Unit
            action == LayoutAction.Host(HostAction.PAGER_NEXT) -> pager?.next?.invoke()
            action == LayoutAction.Host(HostAction.PAGER_PREVIOUS) -> pager?.previous?.invoke()
            else -> env.onAction(action)
        }
    }
    val hasTap = tap != null || onTapAnimation != null
    var result = pointerInput(tap, doubleTap, longPress, hasTap) {
        detectTapGestures(
            onTap = if (hasTap) {
                { animate?.invoke(); run(tap) }
            } else null,
            onDoubleTap = doubleTap?.let { action -> { _ -> run(action) } },
            onLongPress = longPress?.let { action -> { _ -> run(action) } }
        )
    }
    // 右键不算点击：在 Initial 阶段消费掉，外层的点击检测就收不到；有长按动作时执行长按
    result = result.pointerInput(longPress) {
        awaitEachGesture {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                event.changes.forEach { it.consume() }
                run(longPress)
            }
        }
    }
    return result
}

fun PlayerBindingState.toJson(): JsonObject = JsonObject(
    PlayerBinding.FIELDS.associateWith { field ->
        when (field) {
            "playing", "liked", "isPodcast", "hasLyrics" -> JsonPrimitive(truthy(field))
            else -> JsonPrimitive(text(field))
        }
    }
)

// 脚本动作发给提供布局的插件，参数为当前播放状态；在后台执行，失败不提示
@Composable
fun rememberLayoutScriptRunner(pluginId: String, binding: PlayerBindingState): (String) -> Unit {
    val host = LocalPluginCards.current?.host
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(binding)
    return remember(host, scope, pluginId) {
        { name -> if (host != null && pluginId.isNotEmpty()) scope.launch { host.action(pluginId, name, current.toJson()) } }
    }
}
