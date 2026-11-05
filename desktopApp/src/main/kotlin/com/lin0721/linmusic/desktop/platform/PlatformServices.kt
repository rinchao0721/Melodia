package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.flow.StateFlow
import java.awt.Point
import java.awt.Window
import java.io.File

// 系统媒体控制（系统播放卡片与媒体键）
interface MediaSessionService {

    val available: StateFlow<Boolean>

    // 成功接管后返回 true，此时媒体键由系统卡片分发，无需再注册全局媒体键
    fun start(): Boolean

    fun setEnabled(value: Boolean)

    // 同步播放状态并响应系统卡片按钮，随调用方协程取消而结束
    suspend fun bind(controller: PlaybackController, playerViewModel: PlayerViewModel)

    fun shutdown()
}

// 任务栏预览小窗：有曲目时显示封面，并嵌入上一首/播放暂停/下一首按钮
interface TaskbarThumbnailService {

    // 须在窗口可显示之后调用；失败时后续调用均无效果
    fun attach(window: Window)

    // 同步播放状态并响应缩略图按钮，随调用方协程取消而结束
    suspend fun bind(controller: PlaybackController)

    fun detach()
}

interface GlobalHotkeyService {

    val isSupported: Boolean

    // 在主线程回调
    var onAction: ((HotkeyAction) -> Unit)?

    // 注册失败（多为被其他程序占用）的自定义快捷键
    val failed: StateFlow<Set<HotkeyAction>>

    fun start()

    fun stop()

    fun apply(custom: Map<HotkeyAction, HotkeyCombo?>, mediaKeys: Boolean)

    // 录制新组合期间暂停，避免按键被已注册的热键拦截
    fun pause()

    fun resume()
}

// isEnabled 与 setEnabled 为阻塞调用，需在 IO 线程执行
interface AutoStartService {

    // 当前无法切换的原因；可切换时为 null
    val unavailableReason: String?

    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean): Boolean
}

enum class WindowEdge { Left, Right, Top, Bottom, TopLeft, TopRight, BottomLeft, BottomRight }

// 无边框主窗口的系统外观，桌面歌词窗口的鼠标穿透，以及无边框窗口的拖动与缩放
interface WindowEffects {

    // 让无边框窗口也有系统的最小化、还原动画
    fun install(window: Window)

    fun uninstall(window: Window)

    // 最大化时取消圆角与边框；borderRgb 为 0xRRGGBB
    fun applyFrame(window: Window, maximized: Boolean, borderRgb: Int)

    // 系统开启“窗口边框显示强调色”时返回 0xRRGGBB，否则为 null；阻塞调用
    fun systemBorderAccent(): Int?

    // 系统应用是否使用浅色主题，读取失败按深色；阻塞调用
    fun systemAppsUseLightTheme(): Boolean

    // 系统切换深浅色时回调，可能在任意线程；返回注销函数
    fun observeSystemTheme(onChanged: () -> Unit): () -> Unit

    val supportsClickThrough: Boolean

    fun setClickThrough(window: Window, enabled: Boolean)

    // 交给系统的移动/缩放循环，跨缩放比例不同的显示器时由系统换算坐标；须在左键按住时调用，未接管返回 false
    fun beginSystemMove(window: Window): Boolean

    fun beginSystemResize(window: Window, edge: WindowEdge): Boolean

    // 光标的物理像素坐标，跨屏连续；不支持时为 null
    fun cursorPhysicalPosition(): Point?
}

// 网页登录需要一个支持远程调试协议的 Chromium 内核浏览器
fun interface BrowserLocator {
    fun findChromiumBrowser(): File?
}
