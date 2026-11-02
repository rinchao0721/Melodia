package com.lin0721.linmusic.core.player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lin0721.linmusic.MainActivity
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.floatinglyric.ACTION_OPEN_LYRIC_SETTINGS
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricActions
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricFrames
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricOverlay
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricSession
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricStyle
import com.lin0721.linmusic.core.player.floatinglyric.LyricResize
import com.lin0721.linmusic.core.player.floatinglyric.LyricResizeHandle
import com.lin0721.linmusic.core.player.floatinglyric.LyricResizeLimits
import com.lin0721.linmusic.core.player.floatinglyric.LyricResizeState
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.math.roundToInt

private const val TAG = "FloatingLyricService"

private const val DEFAULT_WIDTH_RATIO = 0.92f
private const val MIN_WIDTH_DP = 160
private const val DEFAULT_TOP_DP = 64
private const val MIN_FONT_SP = 12
private const val MAX_FONT_SP = 40

// Android 12 起，叠加层窗口不透明度高于 0.8 时触摸不会穿透到下层应用
private const val LOCKED_WINDOW_ALPHA = 0.8f

class FloatingLyricService : Service() {

    private val playerManager: PlayerManager by inject()
    private val settingsPreferences: SettingsPreferences by inject()
    private val lyricsResolver: LyricsResolver by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private val viewOwner = OverlayViewOwner()
    private val previewFontSize = mutableStateOf<Int?>(null)
    private var resize: ActiveResize? = null

    private val session by lazy {
        FloatingLyricSession(
            controller = playerManager,
            lyricsResolver = lyricsResolver,
            reloadSignal = combine(
                settingsPreferences.amllLyricsEnabled,
                settingsPreferences.qqLyricsEnabled,
                settingsPreferences.neteaseLyricsEnabled
            ) { amll, qq, netease ->
                Triple(amll, qq, netease)
            }.distinctUntilChanged()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        viewOwner.create()
        session.start(serviceScope)
        serviceScope.launch {
            val saved = try {
                settingsPreferences.loadFloatingLyricBounds()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "读取悬浮歌词位置失败，使用默认位置", e)
                null
            }
            addOverlay(saved)
            settingsPreferences.floatingLyricLocked.collect(::applyLocked)
        }
    }

    private fun addOverlay(saved: Triple<Int, Int, Int>?) {
        val wm = windowManager ?: return
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val minWidth = (MIN_WIDTH_DP * metrics.density).roundToInt()
        val width = (saved?.third ?: (screenWidth * DEFAULT_WIDTH_RATIO).roundToInt()).coerceIn(minWidth, screenWidth)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val layoutParams = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 旋转或换屏后旧坐标可能越界，拉回屏幕内
            x = (saved?.first ?: ((screenWidth - width) / 2)).coerceIn(0, (screenWidth - width).coerceAtLeast(0))
            y = (saved?.second ?: (DEFAULT_TOP_DP * metrics.density).roundToInt()).coerceIn(0, (screenHeight - minWidth / 2).coerceAtLeast(0))
        }
        params = layoutParams

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(viewOwner)
            setViewTreeSavedStateRegistryOwner(viewOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { OverlayContent() }
        }
        try {
            wm.addView(view, layoutParams)
            composeView = view
            viewOwner.resume()
        } catch (e: Exception) {
            // 悬浮窗权限在运行中被收回时 addView 会抛 BadTokenException / SecurityException
            AppLogger.e(TAG, "添加悬浮歌词窗口失败", e)
            stopSelf()
        }
    }

    @Composable
    private fun OverlayContent() {
        val savedStyle by settingsPreferences.floatingLyricStyle.collectAsState(FloatingLyricStyle(fontSizeSp = 14, backgroundAlpha = 70))
        // 右下角缩放过程中先用预览字号，写盘后等存储值追上再清掉，避免回跳一帧
        val preview = previewFontSize.value
        LaunchedEffect(savedStyle.fontSizeSp) { if (previewFontSize.value == savedStyle.fontSizeSp) previewFontSize.value = null }
        val style = preview?.let { savedStyle.copy(fontSizeSp = it) } ?: savedStyle
        val locked by settingsPreferences.floatingLyricLocked.collectAsState(false)
        val nowPlaying by playerManager.nowPlaying.collectAsState()
        val isPlaying by playerManager.isPlaying.collectAsState()
        val lines by session.lines.collectAsState()
        val index by session.primaryIndex.collectAsState()
        val frame = remember(lines, index, nowPlaying, style.secondary) {
            FloatingLyricFrames.of(lines, index, nowPlaying, style.secondary)
        }
        val actions = remember(locked, style.fontSizeSp) {
            FloatingLyricActions(
                onPrevious = playerManager::playPrevious,
                onTogglePlay = playerManager::togglePlayPause,
                onNext = playerManager::playNext,
                onToggleLock = { saveLocked(!locked) },
                onOpenSettings = ::openSettings,
                onClose = ::closeOverlay,
                onMoveBy = ::moveBy,
                onResizeStart = { handle -> startResize(handle, style.fontSizeSp) },
                onResizeBy = ::resizeBy,
                onGestureEnd = ::finishGesture
            )
        }
        FloatingLyricOverlay(
            frame = frame,
            style = style,
            locked = locked,
            isPlaying = isPlaying,
            positionProvider = session::positionNow,
            actions = actions
        )
    }

    private fun applyLocked(locked: Boolean) {
        val lp = params ?: return
        val view = composeView ?: return
        lp.flags = if (locked) {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        lp.alpha = if (locked) LOCKED_WINDOW_ALPHA else 1f
        updateLayout(view, lp)
    }

    private fun moveBy(dx: Float, dy: Float) {
        val lp = params ?: return
        val view = composeView ?: return
        val metrics = resources.displayMetrics
        lp.x = (lp.x + dx.roundToInt()).coerceIn(0, (metrics.widthPixels - lp.width).coerceAtLeast(0))
        lp.y = (lp.y + dy.roundToInt()).coerceIn(0, (metrics.heightPixels - view.height).coerceAtLeast(0))
        updateLayout(view, lp)
    }

    private fun startResize(handle: LyricResizeHandle, fontSizeSp: Int) {
        val lp = params ?: return
        val metrics = resources.displayMetrics
        // 左侧拖动以右边缘为锚，不能拖出屏幕左侧；右侧与角落不能超出屏幕右侧
        val maxWidth = if (handle == LyricResizeHandle.LEFT) lp.x + lp.width else metrics.widthPixels - lp.x
        resize = ActiveResize(
            handle = handle,
            start = LyricResizeState(lp.x.toFloat(), lp.width.toFloat(), fontSizeSp),
            limits = LyricResizeLimits(MIN_WIDTH_DP * metrics.density, maxWidth.toFloat(), MIN_FONT_SP, MAX_FONT_SP)
        )
    }

    private fun resizeBy(handle: LyricResizeHandle, dx: Float) {
        val active = resize?.takeIf { it.handle == handle } ?: return
        val lp = params ?: return
        val view = composeView ?: return
        active.totalDx += dx
        val next = LyricResize.apply(handle, active.start, active.totalDx, active.limits)
        lp.x = next.x.roundToInt()
        lp.width = next.width.roundToInt()
        if (handle == LyricResizeHandle.CORNER) previewFontSize.value = next.fontSizeSp
        updateLayout(view, lp)
    }

    private fun finishGesture() {
        val active = resize
        resize = null
        saveBounds()
        val fontSize = previewFontSize.value
        if (active?.handle == LyricResizeHandle.CORNER && fontSize != null) {
            serviceScope.launch {
                try {
                    settingsPreferences.saveFloatingLyricStyle(settingsPreferences.floatingLyricStyle.first().copy(fontSizeSp = fontSize))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "保存悬浮歌词字号失败", e)
                }
            }
        }
    }

    private fun updateLayout(view: ComposeView, lp: WindowManager.LayoutParams) {
        try {
            windowManager?.updateViewLayout(view, lp)
        } catch (e: IllegalArgumentException) {
            AppLogger.w(TAG, "悬浮歌词窗口已移除，忽略布局更新", e)
        }
    }

    private fun saveBounds() {
        val lp = params ?: return
        val (x, y, width) = Triple(lp.x, lp.y, lp.width)
        serviceScope.launch { settingsPreferences.saveFloatingLyricBounds(x, y, width) }
    }

    private fun saveLocked(locked: Boolean) {
        serviceScope.launch { settingsPreferences.saveFloatingLyricLocked(locked) }
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_LYRIC_SETTINGS
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            AppLogger.w(TAG, "从悬浮歌词打开设置页失败", e)
        }
    }

    // MainActivity 可能已不在，开关写回后自行停止
    private fun closeOverlay() {
        serviceScope.launch {
            settingsPreferences.saveShowDesktopLrc(false)
            stopSelf()
        }
    }

    override fun onDestroy() {
        viewOwner.destroy()
        serviceScope.cancel()
        composeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: IllegalArgumentException) {
                AppLogger.w(TAG, "悬浮歌词窗口未挂载，跳过移除", e)
            }
        }
        composeView = null
        super.onDestroy()
    }
}

private class ActiveResize(val handle: LyricResizeHandle, val start: LyricResizeState, val limits: LyricResizeLimits) {
    var totalDx = 0f
}

// Service 没有 Activity 的生命周期与状态保存，ComposeView 挂到 WindowManager 前需要手动提供
private class OverlayViewOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun create() {
        savedStateController.performRestore(Bundle())
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
