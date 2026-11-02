package com.lin0721.linmusic.desktop.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricStyle
import com.lin0721.linmusic.desktop.app.DesktopPaths
import com.lin0721.linmusic.desktop.app.RenderBackend
import com.lin0721.linmusic.desktop.data.CacheDirState
import com.lin0721.linmusic.desktop.data.CacheRelocator
import com.lin0721.linmusic.desktop.data.CacheUsage
import com.lin0721.linmusic.desktop.data.CloseAction
import com.lin0721.linmusic.desktop.data.DesktopCacheManager
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.data.MiniLyricSettings
import com.lin0721.linmusic.desktop.data.MiniPlayerStyle
import com.lin0721.linmusic.desktop.data.RelocationProgress
import com.lin0721.linmusic.desktop.data.RelocationResult
import com.lin0721.linmusic.desktop.platform.AutoStartService
import com.lin0721.linmusic.desktop.platform.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import com.lin0721.linmusic.desktop.platform.MediaSessionService
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 日志由全局对象管理，抽成参数便于测试替换
class LocalStorageOps(
    val logsSize: () -> Long = AppLogger::getLogsSize,
    val clearLogs: () -> Unit = { AppLogger.clearLogs() }
)

// 桌面专属设置：窗口、开机自启、快捷键与本地存储
class DesktopSettingsViewModel(
    private val desktopPreferences: DesktopPreferences,
    private val hotkeys: GlobalHotkeyService,
    mediaSession: MediaSessionService,
    private val autoStart: AutoStartService,
    private val cacheManager: DesktopCacheManager,
    private val relocator: CacheRelocator,
    private val storage: LocalStorageOps = LocalStorageOps(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private fun <T> Flow<T>.asState(initial: T): StateFlow<T> = stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = initial
    )

    val closeAction = desktopPreferences.closeAction.asState(CloseAction.TRAY)
    val mediaKeysEnabled = desktopPreferences.mediaKeysEnabled.asState(true)
    val hotkeyMap = desktopPreferences.hotkeys.asState(HotkeyCombo.defaults)
    val renderBackend = desktopPreferences.renderBackend.asState(RenderBackend.AUTO)
    val desktopLyricStyle = desktopPreferences.desktopLyricStyle.asState(DesktopPreferences.DEFAULT_DESKTOP_LYRIC_STYLE)
    val desktopLyricLocked = desktopPreferences.desktopLyricLocked.asState(false)
    val miniPlayerStyle = desktopPreferences.miniPlayerStyle.asState(MiniPlayerStyle.DEFAULT)
    val miniPlayerAlwaysOnTop = desktopPreferences.miniPlayerAlwaysOnTop.asState(true)
    val miniLyricSettings = desktopPreferences.miniLyricSettings.asState(MiniLyricSettings())

    val hotkeysSupported: Boolean = hotkeys.isSupported
    val failedHotkeys: StateFlow<Set<HotkeyAction>> = hotkeys.failed
    val mediaSessionAvailable: StateFlow<Boolean> = mediaSession.available

    val autoStartUnavailableReason: String? = autoStart.unavailableReason

    // null 表示尚未读到系统中的自启状态
    private val _autoStartEnabled = MutableStateFlow<Boolean?>(null)
    val autoStartEnabled: StateFlow<Boolean?> = _autoStartEnabled.asStateFlow()

    private val _cacheUsage = MutableStateFlow(CacheUsage(0L, 0L, 0L, 0L))
    val cacheUsage: StateFlow<CacheUsage> = _cacheUsage.asStateFlow()

    val cacheDir: StateFlow<CacheDirState> = relocator.state.asState(relocator.stateFor(null))

    // 非空表示正在迁移缓存
    private val _relocation = MutableStateFlow<RelocationProgress?>(null)
    val relocation: StateFlow<RelocationProgress?> = _relocation.asStateFlow()
    private var relocationJob: Job? = null

    private val _logBytes = MutableStateFlow(0L)
    val logBytes: StateFlow<Long> = _logBytes.asStateFlow()

    val logDir: File = DesktopPaths.logDir
    val defaultDownloadDir: File = DesktopPaths.defaultDownloadDir

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // 进入设置页时刷新一次；这些值都要读磁盘或系统
    fun refresh() {
        viewModelScope.launch {
            _autoStartEnabled.value = withContext(ioDispatcher) { autoStart.isEnabled() }
        }
        viewModelScope.launch {
            withContext(ioDispatcher) {
                _cacheUsage.value = cacheManager.usage()
                _logBytes.value = storage.logsSize()
            }
        }
    }

    fun updateCloseAction(action: CloseAction) {
        viewModelScope.launch { desktopPreferences.saveCloseAction(action) }
    }

    fun updateRenderBackend(backend: RenderBackend) {
        if (backend == renderBackend.value) return
        viewModelScope.launch {
            desktopPreferences.saveRenderBackend(backend)
            _messages.tryEmit("渲染方式已更改，重启后生效")
        }
    }

    fun updateDesktopLyricStyle(transform: (FloatingLyricStyle) -> FloatingLyricStyle) {
        viewModelScope.launch { desktopPreferences.saveDesktopLyricStyle(transform(desktopPreferences.desktopLyricStyle.first())) }
    }

    fun updateDesktopLyricLocked(locked: Boolean) {
        viewModelScope.launch { desktopPreferences.saveDesktopLyricLocked(locked) }
    }

    fun updateMiniPlayerStyle(style: MiniPlayerStyle) {
        viewModelScope.launch { desktopPreferences.saveMiniPlayerStyle(style) }
    }

    fun updateMiniPlayerAlwaysOnTop(onTop: Boolean) {
        viewModelScope.launch { desktopPreferences.saveMiniPlayerAlwaysOnTop(onTop) }
    }

    fun updateMiniLyricSettings(transform: (MiniLyricSettings) -> MiniLyricSettings) {
        viewModelScope.launch { desktopPreferences.saveMiniLyricSettings(transform(desktopPreferences.miniLyricSettings.first())) }
    }

    fun updateMediaKeysEnabled(enabled: Boolean) {
        viewModelScope.launch { desktopPreferences.saveMediaKeysEnabled(enabled) }
    }

    fun updateHotkeys(map: Map<HotkeyAction, HotkeyCombo?>) {
        viewModelScope.launch { desktopPreferences.saveHotkeys(map) }
    }

    // 录制新组合期间暂停，避免按键被已注册的热键拦截
    fun pauseHotkeys() = hotkeys.pause()

    fun resumeHotkeys() = hotkeys.resume()

    fun updateAutoStart(enabled: Boolean) {
        if (autoStartUnavailableReason != null) return
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { autoStart.setEnabled(enabled) }
            if (ok) _autoStartEnabled.value = enabled else _messages.tryEmit("修改开机启动失败")
        }
    }

    // 容量上限调低后立即按新上限淘汰
    fun enforceCacheLimit(maxBytes: Long) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                cacheManager.enforce(maxBytes)
                _cacheUsage.value = cacheManager.usage()
            }
        }
    }

    // selected 为 null 表示恢复默认位置；新目录重启后才生效
    fun changeCacheDir(selected: File?) {
        if (relocationJob?.isActive == true) return
        relocationJob = viewModelScope.launch {
            _relocation.value = RelocationProgress(0L, 0L)
            try {
                when (val result = relocator.switchTo(selected) { _relocation.value = it }) {
                    RelocationResult.Unchanged -> _messages.tryEmit("缓存目录已是当前使用的位置")
                    RelocationResult.Switched -> _messages.tryEmit("缓存目录已更改，重启后生效")
                    is RelocationResult.Failed -> _messages.tryEmit("更改缓存目录失败：${result.reason}")
                }
            } catch (e: CancellationException) {
                _messages.tryEmit("已取消迁移")
                throw e
            } finally {
                _relocation.value = null
            }
        }
    }

    fun cancelRelocation() {
        relocationJob?.cancel()
    }

    fun clearCaches() {
        viewModelScope.launch {
            val freed = withContext(ioDispatcher) {
                val before = cacheManager.usage().total
                cacheManager.clear()
                val after = cacheManager.usage()
                _cacheUsage.value = after
                before - after.total
            }
            _messages.tryEmit(if (freed > 0) "已清除缓存，释放 ${formatFileSize(freed)}" else "没有可清除的缓存")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                storage.clearLogs()
                _logBytes.value = storage.logsSize()
            }
        }
    }
}
