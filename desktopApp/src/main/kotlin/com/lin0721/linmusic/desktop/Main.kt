package com.lin0721.linmusic.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lin0721.linmusic.core.ui.skin.ActiveSkin
import com.lin0721.linmusic.core.ui.skin.SkinImageCodec
import com.lin0721.linmusic.core.ui.skin.SkinRepository
import com.lin0721.linmusic.core.ui.skin.withBackdrop
import com.lin0721.linmusic.core.ui.theme.MelodiaStyle
import com.lin0721.linmusic.core.ui.theme.SkinBackdropHost
import com.lin0721.linmusic.core.ui.theme.ThemeRevealHost
import com.lin0721.linmusic.core.update.UpdateChecker
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.ThemeMode
import com.lin0721.linmusic.core.preferences.ThemeSettings
import com.lin0721.linmusic.core.preferences.UiScale
import com.lin0721.linmusic.desktop.app.AppInfo
import com.lin0721.linmusic.desktop.app.DesktopCacheMigration
import com.lin0721.linmusic.desktop.app.DesktopCacheRoot
import com.lin0721.linmusic.desktop.app.DesktopLogging
import com.lin0721.linmusic.desktop.app.DesktopPaths
import com.lin0721.linmusic.desktop.app.HeapTrim
import com.lin0721.linmusic.desktop.app.RenderBackend
import com.lin0721.linmusic.desktop.app.RenderSessionGuard
import com.lin0721.linmusic.desktop.app.applyRenderBackend
import com.lin0721.linmusic.desktop.app.scaledMinWindowSize
import com.lin0721.linmusic.desktop.app.SingleInstance
import com.lin0721.linmusic.desktop.app.WindowBounds
import com.lin0721.linmusic.desktop.app.currentScreenBounds
import com.lin0721.linmusic.desktop.data.CacheLimits
import com.lin0721.linmusic.desktop.data.CacheRelocator
import com.lin0721.linmusic.desktop.data.CloseAction
import com.lin0721.linmusic.desktop.data.DesktopCacheManager
import com.lin0721.linmusic.desktop.data.DesktopImageLoader
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.data.MiniLyricSettings
import com.lin0721.linmusic.desktop.data.MiniPlayerStyle
import com.lin0721.linmusic.desktop.di.desktopAppDependencies
import com.lin0721.linmusic.desktop.di.desktopPlatformModule
import com.lin0721.linmusic.desktop.di.desktopViewModelModule
import com.lin0721.linmusic.desktop.di.platformModuleFor
import com.lin0721.linmusic.desktop.platform.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.MediaSessionService
import com.lin0721.linmusic.desktop.platform.TaskbarThumbnailService
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.player.DesktopPlaybackController
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.LocalWindowEffects
import com.lin0721.linmusic.desktop.ui.components.TextInputFocus
import com.lin0721.linmusic.desktop.ui.components.UiMotion
import com.lin0721.linmusic.desktop.ui.lyrics.DesktopLyricWindow
import com.lin0721.linmusic.desktop.ui.lyricsview.rememberLyricsViewState
import com.lin0721.linmusic.desktop.ui.miniplayer.MiniPlayerInit
import com.lin0721.linmusic.desktop.ui.miniplayer.MiniPlayerWindow
import com.lin0721.linmusic.desktop.ui.settings.SettingsCategory
import com.lin0721.linmusic.desktop.ui.shell.CanvasClearColorEffect
import com.lin0721.linmusic.desktop.ui.shell.MelodiaDesktopApp
import com.lin0721.linmusic.desktop.ui.shell.TaskbarThumbnailEffect
import com.lin0721.linmusic.desktop.ui.shell.WindowChromeEffect
import com.lin0721.linmusic.desktop.ui.shell.rememberDesktopShellSession
import com.lin0721.linmusic.desktop.ui.shell.rememberFullscreenState
import com.lin0721.linmusic.desktop.ui.theme.MelodiaDesktopTheme
import com.lin0721.linmusic.desktop.ui.theme.SystemDarkTheme
import com.lin0721.linmusic.desktop.ui.theme.rememberDesktopPalette
import com.lin0721.linmusic.desktop.ui.tray.TrayHost
import com.lin0721.linmusic.desktop.ui.tray.TrayMenuEntry
import com.lin0721.linmusic.desktop.ui.tray.isTraySupported
import com.lin0721.linmusic.di.networkModule
import com.lin0721.linmusic.di.repositoryModule
import com.lin0721.linmusic.di.sourceModule
import com.lin0721.linmusic.di.updateModule
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import java.awt.Component
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import kotlin.system.exitProcess

private const val VOLUME_STEP = 5
private const val SEEK_STEP_MS = 5_000L
private const val EXIT_ANIMATION_MS = 250L
private const val WINDOW_SAVE_DEBOUNCE_MS = 400L
private const val BACKGROUND_RELEASE_DELAY_MS = 120_000L
private const val MAIN_WINDOW_DISPOSE_DELAY_MS = 600_000L
private const val WINDOW_DISPOSE_SETTLE_MS = 1_000L

// 判断桌面歌词上次位置是否仍在屏幕内时假定的窗口高度
private const val LYRIC_REACH_HEIGHT = 100
// 判断迷你窗上次位置是否仍在屏幕内时假定的窗口宽度，取各样式中最窄的
private const val MINI_PLAYER_REACH_WIDTH = 240
private val DEFAULT_WINDOW_SIZE = DpSize(1280.dp, 800.dp)

private data class WindowSnapshot(
    val placement: WindowPlacement,
    val minimized: Boolean,
    val size: DpSize,
    val position: WindowPosition,
    val awtBounds: java.awt.Rectangle
)

@OptIn(FlowPreview::class)
fun main() {
    DesktopLogging.install()
    DesktopImageLoader.install()
    // 已有实例则唤起它并退出
    val activationRequests = Channel<Unit>(Channel.CONFLATED)
    val quitRequests = Channel<Unit>(Channel.CONFLATED)
    val singleInstance = SingleInstance(DesktopPaths.dataDir)
    if (!singleInstance.acquire(
            onActivate = { activationRequests.trySend(Unit) },
            onQuit = { quitRequests.trySend(Unit) }
        )
    ) {
        exitProcess(0)
    }
    // 须先于任何缓存目录的读取与缓存对象创建
    DesktopCacheRoot.configure(DesktopPreferences(PreferencesStores.get(DesktopPaths.preferencesFile(DesktopPreferences.STORE_NAME))))
    DesktopCacheMigration.migrateAll()
    val koin = startKoin {
        modules(
            platformModuleFor(System.getProperty("os.name").orEmpty()),
            desktopPlatformModule, networkModule, repositoryModule, sourceModule, desktopViewModelModule,
            updateModule(AppInfo.versionCode)
        )
    }.koin
    val controller = koin.get<DesktopPlaybackController>()
    // 进程内常驻记录播客收听进度
    PodcastProgressTracker(controller, koin.get()).start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    // 延迟检查更新，避开启动关键路径；进程生命周期内只自动检查这一次
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        delay(3000)
        koin.get<UpdateChecker>().checkForUpdate(manual = false, showDialogOnAutoFound = true)
    }
    val settingsPreferences = koin.get<SettingsPreferences>()
    // 切换缓存目录后清掉旧目录；旧缓存超出当前上限时补一次淘汰
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        koin.get<CacheRelocator>().cleanupPending()
        try {
            koin.get<DesktopCacheManager>().enforce(CacheLimits.normalize(settingsPreferences.audioCacheMaxSize.first()))
        } catch (e: Exception) {
            AppLogger.w("Main", "启动时淘汰缓存失败", e)
        }
    }
    val playerViewModel = koin.get<PlayerViewModel>()
    val appDependencies = koin.desktopAppDependencies()
    val desktopPreferences = koin.get<DesktopPreferences>()
    val hotkeys = koin.get<GlobalHotkeyService>()
    val mediaSession = koin.get<MediaSessionService>()
    val windowEffects = koin.get<WindowEffects>()
    val taskbarThumbnail = koin.get<TaskbarThumbnailService>()
    val searchFocusRequests = Channel<Unit>(Channel.CONFLATED)
    val settingsRequests = Channel<String>(Channel.CONFLATED)
    val loginRequests = Channel<Unit>(Channel.CONFLATED)
    // 系统媒体卡片可用时由它接管媒体键，否则回退全局热键
    val mediaSessionActive = mediaSession.start()
    val savedWindow = runBlocking { desktopPreferences.loadWindow() }
    applyRenderBackend(
        RenderSessionGuard.begin(
            runCatching { runBlocking { desktopPreferences.renderBackend.first() } }.getOrDefault(RenderBackend.AUTO)
        ) { fallback -> runCatching { runBlocking { desktopPreferences.saveRenderBackend(fallback) } } }
    )
    // 首帧即按已存缩放渲染，避免先以 100% 绘制再跳变
    val initialUiScalePercent = runCatching {
        runBlocking { settingsPreferences.uiScalePercent.first() }
    }.getOrDefault(UiScale.DEFAULT_PERCENT)
    val initialUiAnimationsEnabled = runCatching {
        runBlocking { settingsPreferences.uiAnimationsEnabled.first() }
    }.getOrDefault(true)
    // 首帧即按已存主题渲染；跟随系统时先同步读一次系统深浅色
    val initialThemeSettings = runCatching {
        runBlocking { settingsPreferences.themeSettings.first() }
    }.getOrDefault(ThemeSettings())
    if (initialThemeSettings.mode == ThemeMode.SYSTEM) SystemDarkTheme.refreshBlocking(windowEffects)
    val skinRepository = koin.get<SkinRepository>()
    val skinImageCodec = koin.get<SkinImageCodec>()
    val initialDark = initialThemeSettings.mode.isDark(SystemDarkTheme.isDark.value)
    skinRepository.updateSystemDark(SystemDarkTheme.isDark.value)
    val initialSkin = runCatching { runBlocking { skinRepository.load(initialThemeSettings.skinId(initialDark), initialDark) } }.getOrNull()
    val restoredBounds = savedWindow.bounds
    val restoredPosition = restoredBounds?.takeIf { it.isReachableOn(currentScreenBounds()) }
    // 附属窗口每次打开都取最新保存的状态；显示器布局变化后够不着的位置交给默认值
    fun loadLyricBounds() = runCatching { runBlocking { desktopPreferences.loadDesktopLyricBounds() } }.getOrNull()
        ?.takeIf { WindowBounds(it.x, it.y, it.width, LYRIC_REACH_HEIGHT).isReachableOn(currentScreenBounds()) }
    fun loadLyricStyle() = runCatching { runBlocking { desktopPreferences.desktopLyricStyle.first() } }
        .getOrDefault(DesktopPreferences.DEFAULT_DESKTOP_LYRIC_STYLE)
    fun loadMiniPlayerInit() = MiniPlayerInit(
        position = runCatching { runBlocking { desktopPreferences.loadMiniPlayerPosition() } }.getOrNull()
            ?.takeIf { WindowBounds(it.x, it.y, MINI_PLAYER_REACH_WIDTH, 0).isReachableOn(currentScreenBounds()) },
        style = runCatching { runBlocking { desktopPreferences.miniPlayerStyle.first() } }.getOrDefault(MiniPlayerStyle.DEFAULT),
        sizes = runCatching { runBlocking { desktopPreferences.miniPlayerSizes.first() } }
            .getOrDefault(MiniPlayerStyle.entries.associateWith { it.defaultSize }),
        alwaysOnTop = runCatching { runBlocking { desktopPreferences.miniPlayerAlwaysOnTop.first() } }.getOrDefault(true),
        lyricSettings = runCatching { runBlocking { desktopPreferences.miniLyricSettings.first() } }.getOrDefault(MiniLyricSettings())
    )

    application {
        val scope = rememberCoroutineScope()
        var isMainVisible by remember { mutableStateOf(true) }
        // 迷你模式下主窗口隐藏，由迷你窗取代
        var isMiniMode by remember { mutableStateOf(false) }
        // 已可见但被遮挡或最小化时，靠计数触发再次置前
        var bringToFrontRequest by remember { mutableStateOf(0) }
        // 在托盘里放久了会销毁主窗口，任何唤起入口都先重建
        var isMainAlive by remember { mutableStateOf(true) }
        val showMainWindow: () -> Unit = {
            isMainAlive = true
            isMiniMode = false
            isMainVisible = true
            bringToFrontRequest++
        }
        val enterMiniPlayer: () -> Unit = {
            isMiniMode = true
            isMainVisible = false
        }
        val isLyricLocked by desktopPreferences.desktopLyricLocked.collectAsState(initial = false)
        val setLyricLocked: (Boolean) -> Unit = { locked ->
            scope.launch { desktopPreferences.saveDesktopLyricLocked(locked) }
        }
        val showDesktopLyric by settingsPreferences.showDesktopLrc.collectAsState(initial = false)
        val uiScalePercent by settingsPreferences.uiScalePercent.collectAsState(initial = initialUiScalePercent)
        val uiScaleFactor = UiScale.toFactor(uiScalePercent)
        val uiAnimationsEnabled by settingsPreferences.uiAnimationsEnabled.collectAsState(initial = initialUiAnimationsEnabled)
        val uiMotion = remember { UiMotion(initialUiAnimationsEnabled) }
        val themeSettings by settingsPreferences.themeSettings.collectAsState(initial = initialThemeSettings)
        val systemDark by SystemDarkTheme.isDark.collectAsState()
        LaunchedEffect(systemDark) { skinRepository.updateSystemDark(systemDark) }
        val activeSkinFlow = remember { skinRepository.activeSkin.withBackdrop(skinImageCodec) }
        val activeSkin by activeSkinFlow.collectAsState(initial = ActiveSkin(initialSkin, null))
        val skin = activeSkin.skin
        val basePalette = rememberDesktopPalette(themeSettings, skin, windowEffects)
        val backdropHost = remember(activeSkin) { activeSkin.backdrop?.let(::SkinBackdropHost) }
        val palette = remember(basePalette, backdropHost, skin) {
            if (backdropHost == null) basePalette else basePalette.withBackdrop(skin?.background)
        }
        LaunchedEffect(uiAnimationsEnabled) { uiMotion.enabled = uiAnimationsEnabled }
        val closeAction by desktopPreferences.closeAction.collectAsState(initial = CloseAction.TRAY)
        val isPlaying by controller.playWhenReady.collectAsState()
        val nowPlaying by controller.nowPlaying.collectAsState()
        val userProfile by appDependencies.homeViewModel.userProfile.collectAsState()

        val setDesktopLyric: (Boolean) -> Unit = { enabled ->
            scope.launch { settingsPreferences.saveShowDesktopLrc(enabled) }
        }
        // 先隐藏窗口等消失动画播完，再补报当前曲目播放时长、销毁 mpv 句柄并退出
        var isExiting by remember { mutableStateOf(false) }
        val shutdownAndExit = {
            mediaSession.shutdown()
            controller.release()
            singleInstance.close()
            // 正常退出才算渲染后端验证通过，清掉标记，下次不会自动降级
            RenderSessionGuard.end()
        }
        val exit: () -> Unit = {
            if (!isExiting) {
                isExiting = true
                isMainVisible = false
                scope.launch {
                    delay(EXIT_ANIMATION_MS)
                    shutdownAndExit()
                    exitApplication()
                }
            }
        }

        // 主窗口关闭按钮与 Alt+F4 统一按设置处理；系统不支持托盘时隐藏后无法找回，只能退出
        val closeMainWindow = {
            if (closeAction == CloseAction.EXIT || !isTraySupported) exit() else isMainVisible = false
        }

        LaunchedEffect(Unit) {
            for (request in activationRequests) showMainWindow()
        }
        LaunchedEffect(Unit) {
            for (request in quitRequests) {
                if (!isExiting) {
                    isExiting = true
                    isMainVisible = false
                    shutdownAndExit()
                    exitProcess(0)
                }
            }
        }

        // 占位播放器没有音量，返回 false 让方向键留给页面
        val adjustVolume: (Int) -> Boolean = { delta ->
            val available = controller.unavailableReason == null
            if (available) controller.setVolume(controller.volume.value + delta)
            available
        }

        hotkeys.onAction = { action ->
            when (action) {
                HotkeyAction.PlayPause -> controller.togglePlayPause()
                HotkeyAction.Previous -> controller.skipToPrevious()
                HotkeyAction.Next -> controller.playNext()
                HotkeyAction.VolumeUp -> adjustVolume(VOLUME_STEP)
                HotkeyAction.VolumeDown -> adjustVolume(-VOLUME_STEP)
                HotkeyAction.ToggleDesktopLyric -> setDesktopLyric(!showDesktopLyric)
                HotkeyAction.ToggleDesktopLyricLock -> setLyricLocked(!isLyricLocked)
            }
        }
        DisposableEffect(Unit) {
            hotkeys.start()
            onDispose { hotkeys.stop() }
        }
        LaunchedEffect(Unit) {
            combine(desktopPreferences.hotkeys, desktopPreferences.mediaKeysEnabled, ::Pair)
                .collect { (custom, mediaKeys) ->
                    mediaSession.setEnabled(mediaKeys)
                    hotkeys.apply(custom, mediaKeys && !mediaSessionActive)
                }
        }
        LaunchedEffect(Unit) {
            mediaSession.bind(controller, playerViewModel)
        }

        val appIcon = remember { loadAppIcon() }
        val trackText = nowPlaying?.let { "${it.title} - ${it.artist}" }
        TrayHost(
            tooltip = trackText?.let { "Melodia - $it" } ?: "Melodia",
            header = trackText?.let { "正在播放：$it" },
            entries = listOfNotNull(
                TrayMenuEntry.Action("显示主窗口", showMainWindow),
                TrayMenuEntry.Action("迷你播放器", enterMiniPlayer),
                TrayMenuEntry.Divider,
                TrayMenuEntry.Action(if (isPlaying) "暂停" else "播放", controller::togglePlayPause),
                TrayMenuEntry.Action("上一首", controller::skipToPrevious),
                TrayMenuEntry.Action("下一首", controller::playNext),
                TrayMenuEntry.Divider,
                TrayMenuEntry.Toggle("桌面歌词", showDesktopLyric, setDesktopLyric),
                TrayMenuEntry.Toggle("锁定桌面歌词", isLyricLocked, setLyricLocked)
                    .takeIf { windowEffects.supportsClickThrough },
                TrayMenuEntry.Divider,
                TrayMenuEntry.Action("退出", exit)
            ),
            onOpenMain = showMainWindow
        )

        // 预览阶段先于获得焦点的按钮处理，空格才不会再触发刚点过的按钮；输入框占用键盘时让给输入框
        val handleShortcut: (KeyEvent) -> Boolean = { event ->
            when {
                event.type != KeyEventType.KeyDown -> false
                event.isCtrlPressed && event.key == Key.F -> {
                    searchFocusRequests.trySend(Unit)
                    true
                }
                event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed -> false
                else -> when (event.key) {
                    Key.Spacebar -> {
                        controller.togglePlayPause()
                        true
                    }
                    Key.DirectionLeft -> {
                        controller.seekTo((controller.currentPosition.value - SEEK_STEP_MS).coerceAtLeast(0L))
                        true
                    }
                    Key.DirectionRight -> {
                        val target = controller.currentPosition.value + SEEK_STEP_MS
                        val duration = controller.duration.value
                        controller.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                        true
                    }
                    Key.DirectionUp -> adjustVolume(VOLUME_STEP)
                    Key.DirectionDown -> adjustVolume(-VOLUME_STEP)
                    else -> false
                }
            }
        }

        val windowState = rememberWindowState(
            placement = if (savedWindow.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            size = restoredBounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DEFAULT_WINDOW_SIZE,
            position = restoredPosition?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition(Alignment.Center)
        )
        // 主窗口在托盘或最小化持续一段时间后释放图片内存；迷你窗仍在显示封面，不算后台
        LaunchedEffect(windowState) {
            snapshotFlow { (!isMainVisible && !isMiniMode) || (isMainVisible && windowState.isMinimized) }
                .collectLatest { inBackground ->
                    if (!inBackground) {
                        HeapTrim.restore()
                        return@collectLatest
                    }
                    delay(BACKGROUND_RELEASE_DELAY_MS)
                    HeapTrim.shrink()
                    withContext(Dispatchers.Default) { DesktopImageLoader.releaseMemory() }
                }
        }
        val fullscreen = rememberFullscreenState(windowState)
        val lyricsView = rememberLyricsViewState(fullscreen)
        val shellSession = rememberDesktopShellSession(appDependencies, lyricsView)
        // 关到托盘久了销毁主窗口，连同 GPU 上下文与整棵界面树一起释放；页面现场留在 shellSession，重建后原样接回
        LaunchedEffect(Unit) {
            snapshotFlow { !isMainVisible && !isMiniMode }
                .collectLatest { inTray ->
                    if (!inTray) return@collectLatest
                    delay(MAIN_WINDOW_DISPOSE_DELAY_MS)
                    isMainAlive = false
                    // 等窗口真正销毁后再清引用并回收
                    delay(WINDOW_DISPOSE_SETTLE_MS)
                    clearStaleFocusedWindow()
                    withContext(Dispatchers.Default) { DesktopImageLoader.releaseMemory() }
                }
        }
        if (isMainAlive) Window(
            onCloseRequest = closeMainWindow,
            visible = isMainVisible,
            state = windowState,
            title = "Melodia",
            icon = appIcon,
            undecorated = true,
            onPreviewKeyEvent = { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    // 先收起全屏歌词；其后才轮到退出窗口全屏
                    when {
                        lyricsView.isOpen -> {
                            lyricsView.close()
                            true
                        }
                        fullscreen.isFullscreen -> {
                            fullscreen.exit()
                            true
                        }
                        else -> false
                    }
                } else if (TextInputFocus.isActive) {
                    false
                } else {
                    handleShortcut(event)
                }
            }
        ) {
            LaunchedEffect(uiScaleFactor) {
                val screen = window.graphicsConfiguration?.bounds
                val (minWidth, minHeight) = scaledMinWindowSize(uiScaleFactor, screen?.width ?: 0, screen?.height ?: 0)
                window.minimumSize = Dimension(minWidth, minHeight)
            }
            // 全屏不记；最小化时系统会把窗口挪到屏外，同样不记
            LaunchedEffect(windowState) {
                // 读 size/position 只为订阅它们的变化，实际取值用 AWT 窗口
                snapshotFlow {
                    WindowSnapshot(windowState.placement, windowState.isMinimized, windowState.size, windowState.position, window.bounds)
                }
                    .debounce(WINDOW_SAVE_DEBOUNCE_MS)
                    .collect { snapshot ->
                        if (snapshot.minimized) return@collect
                        val bounds = snapshot.awtBounds
                        when (snapshot.placement) {
                            WindowPlacement.Floating ->
                                desktopPreferences.saveWindow(WindowBounds(bounds.x, bounds.y, bounds.width, bounds.height), false)
                            WindowPlacement.Maximized -> desktopPreferences.saveWindow(null, true)
                            WindowPlacement.Fullscreen -> Unit
                        }
                    }
            }
            LaunchedEffect(isMainVisible, bringToFrontRequest) {
                if (isMainVisible) {
                    windowState.isMinimized = false
                    window.toFront()
                }
            }
            // 全屏与最大化一样不要圆角和边框线
            WindowChromeEffect(windowEffects, maximized = windowState.placement != WindowPlacement.Floating, palette = palette)
            CanvasClearColorEffect(palette.windowBackground)
            TaskbarThumbnailEffect(taskbarThumbnail, controller)
            CompositionLocalProvider(LocalUiMotion provides uiMotion, LocalWindowEffects provides windowEffects) {
                MelodiaDesktopTheme(
                    uiScale = uiScaleFactor,
                    palette = palette,
                    style = skin?.style ?: MelodiaStyle.Default,
                    backdrop = backdropHost,
                    skin = skin
                ) {
                    ThemeRevealHost(themeKey = palette to backdropHost, enabled = uiMotion.enabled) {
                        shellSession.Provide {
                            MelodiaDesktopApp(
                                deps = appDependencies,
                                session = shellSession,
                                windowState = windowState,
                                fullscreen = fullscreen,
                                lyricsView = lyricsView,
                                searchFocusRequests = searchFocusRequests.receiveAsFlow(),
                                settingsRequests = settingsRequests.receiveAsFlow(),
                                loginRequests = loginRequests.receiveAsFlow(),
                                onEnterMiniPlayer = enterMiniPlayer,
                                onClose = closeMainWindow
                            )
                        }
                    }
                }
            }
        }

        // 隐藏的窗口不出帧，其 Recomposer 会一直攒着全局状态变更、连带引住主窗口已离开的页面，所以不显示就不创建
        if (showDesktopLyric && !isExiting) {
            DesktopLyricWindow(
                locked = isLyricLocked,
                initialBounds = remember { loadLyricBounds() },
                initialStyle = remember { loadLyricStyle() },
                playerViewModel = playerViewModel,
                controller = controller,
                desktopPreferences = desktopPreferences,
                windowEffects = windowEffects,
                persistScope = scope,
                onLockedChange = setLyricLocked,
                onOpenSettings = {
                    showMainWindow()
                    settingsRequests.trySend(SettingsCategory.Interface.name)
                },
                onHide = { setDesktopLyric(false) }
            )
        }

        if (isMiniMode && !isExiting) {
            MiniPlayerWindow(
                init = remember { loadMiniPlayerInit() },
                uiScale = uiScaleFactor,
                palette = basePalette,
                skin = skin,
                uiMotion = uiMotion,
                windowEffects = windowEffects,
                icon = appIcon,
                isLoggedIn = userProfile != null,
                playerViewModel = playerViewModel,
                controller = controller,
                desktopPreferences = desktopPreferences,
                persistScope = scope,
                onRestore = showMainWindow,
                onOpenLyricSettings = {
                    showMainWindow()
                    settingsRequests.trySend(SettingsCategory.Interface.name)
                },
                onRequireLogin = {
                    showMainWindow()
                    loginRequests.trySend(Unit)
                }
            )
        }
    }
}

// AWT 在窗口销毁后仍把它记作焦点/激活窗口，连带引住整棵界面树；托盘期间没有新窗口接替，只能手动清掉
// 这些设置方法受保护，依赖启动参数 --add-opens java.desktop/java.awt
private val StaleFocusSetters = listOf(
    "setGlobalFocusOwner" to Component::class.java,
    "setGlobalPermanentFocusOwner" to Component::class.java,
    "setGlobalFocusedWindow" to java.awt.Window::class.java,
    "setGlobalActiveWindow" to java.awt.Window::class.java
)

private fun clearStaleFocusedWindow() {
    val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    for ((name, type) in StaleFocusSetters) {
        try {
            KeyboardFocusManager::class.java.getDeclaredMethod(name, type).apply { isAccessible = true }.invoke(manager, null)
        } catch (e: ReflectiveOperationException) {
            AppLogger.w("Main", "清理过期焦点引用失败：$name", e)
        } catch (e: RuntimeException) {
            AppLogger.w("Main", "清理过期焦点引用失败：$name", e)
        }
    }
}

// 窗口与托盘共用的应用图标；资源缺失时退回空白图标而不中断启动
internal fun loadAppIcon(): Painter {
    val bytes = Thread.currentThread().contextClassLoader?.getResourceAsStream("melodia.png")?.use { it.readBytes() }
        ?: return BitmapPainter(ImageBitmap(32, 32))
    return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}
