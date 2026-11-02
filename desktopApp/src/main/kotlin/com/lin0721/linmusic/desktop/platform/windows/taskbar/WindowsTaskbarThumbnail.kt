package com.lin0721.linmusic.desktop.platform.windows.taskbar

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.platform.TaskbarThumbnailService
import com.lin0721.linmusic.desktop.platform.windows.Dwmapi
import com.lin0721.linmusic.desktop.platform.windows.Ole32
import com.lin0721.linmusic.desktop.platform.windows.User32
import com.lin0721.linmusic.desktop.platform.windows.WindowMessageHook
import com.lin0721.linmusic.desktop.platform.windows.WindowsWindowEffects
import com.lin0721.linmusic.desktop.ui.components.sizedCoverUrl
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.Window
import java.awt.image.BufferedImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO

private const val TAG = "TaskbarThumbnail"

private const val THBN_CLICKED = 0x1800
private const val DEFAULT_ICON_SIZE_PX = 16
private const val SHUTDOWN_WAIT_SECONDS = 1L

private data class PlaybackState(val hasTrack: Boolean, val playing: Boolean)

// 任务栏缩略图：有曲目时窗口预览换成封面，并在下方放上一首/播放暂停/下一首；
// 窗口消息在 AWT 工具线程上处理，COM 与按钮操作统一在单独线程执行
internal class WindowsTaskbarThumbnail(private val effects: WindowsWindowEffects) : TaskbarThumbnailService, WindowMessageHook {

    @Volatile private var hwnd: Pointer? = null
    private var executor: ExecutorService? = null

    private val cover = AtomicReference<BufferedImage?>(null)
    private val placeholder: BufferedImage by lazy { placeholderCover(loadAppIcon()) }
    private val buttonCreatedMessage: Int by lazy { User32.INSTANCE.RegisterWindowMessageW("TaskbarButtonCreated") }
    private val commands = MutableSharedFlow<ThumbCommand>(extraBufferCapacity = 8)

    // 以下状态只在单独线程上访问
    private var taskbarList: TaskbarList3? = null
    private var buttonsAdded = false
    private var playbackState = PlaybackState(hasTrack = false, playing = false)
    private var iconic = false
    private var lightTheme = false
    private var icons: Map<ThumbIcon, Pointer> = emptyMap()

    override fun attach(window: Window) {
        if (hwnd != null) return
        val handle = windowHandle(window) ?: return
        // 缩略图消息经由主窗口过程送达，先确保已子类化（重复调用无副作用）
        effects.install(window)
        val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "taskbar-thumbnail").apply { isDaemon = true } }
        executor = worker
        hwnd = handle
        effects.addMessageHook(this)
        post {
            // 悬停缩略图时不再弹出整窗放大预览
            setWindowFlag(handle, Dwmapi.DWMWA_DISALLOW_PEEK, true)
            setWindowFlag(handle, Dwmapi.DWMWA_EXCLUDED_FROM_PEEK, true)
            Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
            taskbarList = TaskbarList3.create()
            syncThemeAndButtons()
        }
    }

    override suspend fun bind(controller: PlaybackController): Unit = coroutineScope {
        if (hwnd == null) return@coroutineScope

        launch {
            combine(controller.nowPlaying, controller.playWhenReady) { track, playing ->
                PlaybackState(hasTrack = track != null, playing = playing)
            }.distinctUntilChanged().collect { state -> post { applyState(state) } }
        }

        // 切歌时先沿用上一张封面，新封面解码完成后再刷新
        launch {
            controller.nowPlaying
                .map { sizedCoverUrl(it?.artworkUri, TASKBAR_COVER_SIZE_PX) }
                .distinctUntilChanged()
                .collectLatest { url ->
                    cover.set(url?.let { TaskbarCoverLoader.load(it) })
                    post { invalidateThumbnails() }
                }
        }

        launch {
            commands.collect { command -> handle(controller, command) }
        }
    }

    override fun detach() {
        val handle = hwnd ?: return
        val worker = executor ?: return
        effects.removeMessageHook(this)
        hwnd = null
        post {
            setIconic(handle, false)
            setWindowFlag(handle, Dwmapi.DWMWA_DISALLOW_PEEK, false)
            setWindowFlag(handle, Dwmapi.DWMWA_EXCLUDED_FROM_PEEK, false)
            icons.values.forEach { GdiBitmaps.destroyIcon(it) }
            icons = emptyMap()
            taskbarList?.release()
            taskbarList = null
            Ole32.INSTANCE.CoUninitialize()
        }
        worker.shutdown()
        runCatching { worker.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS) }
        executor = null
    }

    override fun onMessage(hwnd: Pointer, msg: Int, wParam: Long, lParam: Long): Long? {
        if (hwnd != this.hwnd) return null
        return when (msg) {
            User32.WM_DWMSENDICONICTHUMBNAIL -> {
                respondThumbnail(hwnd, lParam)
                0L
            }
            User32.WM_DWMSENDICONICLIVEPREVIEWBITMAP -> {
                respondLivePreview(hwnd)
                0L
            }
            User32.WM_COMMAND -> onCommand(wParam)
            User32.WM_SETTINGCHANGE -> {
                if (isImmersiveColorSetChange(wParam, lParam)) {
                    post { syncThemeAndButtons() }
                }
                null
            }
            else -> {
                // 任务栏按钮重建（如窗口隐藏到托盘后再显示）后，之前添加的按钮会丢失
                if (msg == buttonCreatedMessage) {
                    post {
                        buttonsAdded = false
                        syncThemeAndButtons()
                    }
                }
                null
            }
        }
    }

    private fun onCommand(wParam: Long): Long? {
        val notification = ((wParam shr 16) and 0xFFFF).toInt()
        if (notification != THBN_CLICKED) return null
        val command = commandForButton((wParam and 0xFFFF).toInt()) ?: return null
        commands.tryEmit(command)
        return 0L
    }

    private fun handle(controller: PlaybackController, command: ThumbCommand) {
        when (command) {
            ThumbCommand.PREVIOUS -> controller.skipToPrevious()
            ThumbCommand.NEXT -> controller.playNext()
            ThumbCommand.TOGGLE -> if (controller.playWhenReady.value) controller.pause() else controller.resume()
        }
    }

    private fun currentCover(): BufferedImage = cover.get() ?: placeholder

    private fun respondThumbnail(hwnd: Pointer, lParam: Long) {
        // 高位字是系统允许的最大宽度，低位字是最大高度
        val maxWidth = ((lParam shr 16) and 0xFFFF).toInt()
        val maxHeight = (lParam and 0xFFFF).toInt()
        val size = squareSizeWithin(maxWidth, maxHeight)
        val pixels = opaqueArgbPixels(scaleToSquare(currentCover(), size))
        val bitmap = GdiBitmaps.createDib(size, size, pixels) ?: return
        try {
            val hr = Dwmapi.INSTANCE.DwmSetIconicThumbnail(hwnd, bitmap, 0)
            if (hr != 0) AppLogger.d(TAG, "DwmSetIconicThumbnail 未生效 hr=0x${Integer.toHexString(hr)}")
        } finally {
            GdiBitmaps.delete(bitmap)
        }
    }

    private fun respondLivePreview(hwnd: Pointer) {
        val bitmap = GdiBitmaps.createEmptyDib() ?: return
        try {
            val hr = Dwmapi.INSTANCE.DwmSetIconicLivePreviewBitmap(hwnd, bitmap, null, 0)
            if (hr != 0) AppLogger.d(TAG, "DwmSetIconicLivePreviewBitmap 未生效 hr=0x${Integer.toHexString(hr)}")
        } finally {
            GdiBitmaps.delete(bitmap)
        }
    }

    private fun applyState(state: PlaybackState) {
        playbackState = state
        hwnd?.let { setIconic(it, state.hasTrack) }
        refreshButtons()
    }

    private fun setIconic(handle: Pointer, on: Boolean) {
        if (on == iconic) return
        iconic = on
        // 先声明提供位图，再强制使用位图；关闭时顺序相反
        val order = if (on) {
            listOf(Dwmapi.DWMWA_HAS_ICONIC_BITMAP, Dwmapi.DWMWA_FORCE_ICONIC_REPRESENTATION)
        } else {
            listOf(Dwmapi.DWMWA_FORCE_ICONIC_REPRESENTATION, Dwmapi.DWMWA_HAS_ICONIC_BITMAP)
        }
        for (attribute in order) setWindowFlag(handle, attribute, on)
        if (on) {
            setWindowFlag(handle, Dwmapi.DWMWA_DISALLOW_PEEK, true)
            setWindowFlag(handle, Dwmapi.DWMWA_EXCLUDED_FROM_PEEK, true)
            Dwmapi.INSTANCE.DwmInvalidateIconicBitmaps(handle)
        }
    }

    private fun setWindowFlag(handle: Pointer, attribute: Int, on: Boolean) {
        val hr = Dwmapi.INSTANCE.DwmSetWindowAttribute(handle, attribute, IntByReference(if (on) 1 else 0), Int.SIZE_BYTES)
        if (hr != 0) AppLogger.d(TAG, "DwmSetWindowAttribute($attribute) 未生效 hr=0x${Integer.toHexString(hr)}")
    }

    private fun invalidateThumbnails() {
        val handle = hwnd ?: return
        if (iconic) Dwmapi.INSTANCE.DwmInvalidateIconicBitmaps(handle)
    }

    // 主题变化时重绘图标；其余情况沿用现有图标
    private fun syncThemeAndButtons() {
        val light = SystemTheme.taskbarUsesLightTheme()
        if (light != lightTheme || icons.isEmpty()) {
            lightTheme = light
            val previous = icons
            icons = buildIcons(light)
            refreshButtons()
            previous.values.forEach { GdiBitmaps.destroyIcon(it) }
        } else {
            refreshButtons()
        }
    }

    private fun refreshButtons() {
        val list = taskbarList ?: return
        val handle = hwnd ?: return
        val buttons = thumbButtonSpecs(playbackState.hasTrack, playbackState.playing).map { ThumbButtonData(it, icons[it.icon]) }
        val hr = if (buttonsAdded) list.updateButtons(handle, buttons) else list.addButtons(handle, buttons)
        if (hr == 0) {
            buttonsAdded = true
        } else {
            AppLogger.d(TAG, "缩略图按钮${if (buttonsAdded) "更新" else "添加"}未生效 hr=0x${Integer.toHexString(hr)}")
        }
    }

    private fun buildIcons(light: Boolean): Map<ThumbIcon, Pointer> {
        val size = iconSizePx()
        val result = HashMap<ThumbIcon, Pointer>()
        for (icon in ThumbIcon.entries) {
            val handle = GdiBitmaps.createIcon(size, renderThumbIcon(icon, size, light))
            if (handle != null) result[icon] = handle
        }
        return result
    }

    private fun iconSizePx(): Int {
        val handle = hwnd ?: return DEFAULT_ICON_SIZE_PX
        return try {
            val user32 = User32.INSTANCE
            user32.GetSystemMetricsForDpi(User32.SM_CXSMICON, user32.GetDpiForWindow(handle)).coerceAtLeast(DEFAULT_ICON_SIZE_PX)
        } catch (e: LinkageError) {
            DEFAULT_ICON_SIZE_PX
        }
    }

    private fun windowHandle(window: Window): Pointer? {
        if (!window.isDisplayable) return null
        return try {
            Native.getWindowPointer(window)
        } catch (e: RuntimeException) {
            AppLogger.w(TAG, "获取窗口句柄失败", e)
            null
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "获取窗口句柄失败", e)
            null
        }
    }

    private fun loadAppIcon(): BufferedImage? = try {
        Thread.currentThread().contextClassLoader?.getResourceAsStream("melodia.png")?.use { ImageIO.read(it) }
    } catch (e: Exception) {
        AppLogger.d(TAG, "读取应用图标失败", e)
        null
    }

    private fun post(block: () -> Unit) {
        val worker = executor ?: return
        runCatching {
            worker.execute {
                runCatching(block).onFailure { AppLogger.w(TAG, "任务栏缩略图操作失败", it) }
            }
        }
    }
}
