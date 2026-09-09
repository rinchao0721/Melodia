package com.lin0721.linmusic.feature.settings.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.BuildConfig
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.feature.settings.data.UserBindingItem
import com.lin0721.linmusic.feature.settings.data.UserLevelData
import com.lin0721.linmusic.feature.settings.data.VipInfoData
import com.lin0721.linmusic.core.auth.AuthRepository
import com.lin0721.linmusic.feature.settings.data.SettingsRepository
import com.lin0721.linmusic.core.player.AudioCacheManager
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "SettingsViewModel"

@OptIn(FlowPreview::class)
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val settingsPreferences: SettingsPreferences,
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    // DataStore 的偏好流统一以相同策略转为 StateFlow，避免每项重复五行样板
    private fun <T> Flow<T>.asState(initial: T): StateFlow<T> = stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = initial
    )

    // 偏好写入均为即发即忘，无需回传结果
    private fun launchSave(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // ─── 本地偏好设置对外状态流 ───
    val wifiQuality = settingsPreferences.wifiQuality.asState("lossless")

    val mobileQuality = settingsPreferences.mobileQuality.asState("standard")

    val useRealIp = settingsPreferences.useRealIp.asState(false)

    val realIpValue = settingsPreferences.realIpValue.asState("")

    val defaultPlaylistPrivate = settingsPreferences.defaultPlaylistPrivate.asState(false)

    val streamCacheEnabled = settingsPreferences.streamCacheEnabled.asState(true)

    val audioCacheMaxSize = settingsPreferences.audioCacheMaxSize.asState(512 * 1024 * 1024L)

    val autoPlayNext = settingsPreferences.autoPlayNext.asState(true)

    val wifiOnlyPlay = settingsPreferences.wifiOnlyPlay.asState(false)

    val mobileAlert = settingsPreferences.mobileAlert.asState(true)

    val useProxy = settingsPreferences.useProxy.asState(false)

    val showDesktopLrc = settingsPreferences.showDesktopLrc.asState(false)

    val showLockscreen = settingsPreferences.showLockscreen.asState(true)

    val carMode = settingsPreferences.carMode.asState(false)

    val showCreateEntry = settingsPreferences.showCreateEntry.asState(true)

    val lyricTextSize = settingsPreferences.lyricTextSize.asState(14)

    val lyricTextColor = settingsPreferences.lyricTextColor.asState("#FFFFFF")

    val logLevel = settingsPreferences.logLevel.asState(if (BuildConfig.DEBUG) "DEBUG" else "WARN")

    val autoCheckUpdateEnabled = settingsPreferences.autoCheckUpdateEnabled.asState(true)

    val allowPrereleaseChannel = settingsPreferences.allowPrereleaseChannel.asState(false)

    // ─── 服务端拉取数据状态 ───
    private val _userLevel = MutableStateFlow<UserLevelData?>(null)
    val userLevel = _userLevel.asStateFlow()

    private val _vipInfo = MutableStateFlow<VipInfoData?>(null)
    val vipInfo = _vipInfo.asStateFlow()

    private val _userBindings = MutableStateFlow<List<UserBindingItem>>(emptyList())
    val userBindings = _userBindings.asStateFlow()

    // 加载中状态与消息事件
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    // ─── 储存空间分类占用统计 ───
    private val _audioCacheSize = MutableStateFlow(0L)
    val audioCacheSize = _audioCacheSize.asStateFlow()

    private val _imageCacheSize = MutableStateFlow(0L)
    val imageCacheSize = _imageCacheSize.asStateFlow()

    private val _logCacheSize = MutableStateFlow(0L)
    val logCacheSize = _logCacheSize.asStateFlow()

    private val _updatePackageSize = MutableStateFlow(0L)
    val updatePackageSize = _updatePackageSize.asStateFlow()

    private val _otherCacheSize = MutableStateFlow(0L)
    val otherCacheSize = _otherCacheSize.asStateFlow()

    val totalCacheSize = combine(
        _audioCacheSize, _imageCacheSize, _logCacheSize, _updatePackageSize, _otherCacheSize
    ) { audio, image, log, update, other -> audio + image + log + update + other }
        .asState(0L)

    // 昵称重名实时检查防抖状态
    private val _nicknameInput = MutableStateFlow("")
    val nicknameInput = _nicknameInput.asStateFlow()

    private val _isNicknameDuplicated = MutableStateFlow<Boolean?>(null)
    val isNicknameDuplicated = _isNicknameDuplicated.asStateFlow()

    init {
        // 昵称输入实时防抖查重 (500ms 延迟)
        _nicknameInput
            .debounce(500)
            .filter { it.isNotBlank() }
            .distinctUntilChanged()
            .onEach { name ->
                val currentName = userPreferences.userProfile.first()?.nickname
                if (name == currentName) {
                    _isNicknameDuplicated.value = false
                    return@onEach
                }
                settingsRepository.checkNickname(name).collect { result ->
                    result.onSuccess { duplicated ->
                        _isNicknameDuplicated.value = duplicated
                    }.onFailure {
                        _isNicknameDuplicated.value = null
                    }
                }
            }
            .launchIn(viewModelScope)

        // 初始化拉取远端账号数据
        fetchRemoteSettingsData()
    }

    // ─── 核心设置修改方法 ───

    fun updateWifiQuality(quality: String) = launchSave { settingsPreferences.saveWifiQuality(quality) }

    fun updateMobileQuality(quality: String) = launchSave { settingsPreferences.saveMobileQuality(quality) }

    fun updateUseRealIp(enabled: Boolean) = launchSave { settingsPreferences.saveUseRealIp(enabled) }

    fun updateRealIpValue(ip: String) = launchSave { settingsPreferences.saveRealIpValue(ip) }

    fun updateDefaultPlaylistPrivate(private: Boolean) = launchSave { settingsPreferences.saveDefaultPlaylistPrivate(private) }

    fun updateStreamCacheEnabled(enabled: Boolean) = launchSave { settingsPreferences.saveStreamCacheEnabled(enabled) }

    fun updateAudioCacheMaxSize(context: Context, size: Long) {
        viewModelScope.launch {
            settingsPreferences.saveAudioCacheMaxSize(size)
            AudioCacheManager.recreateCache(context, size)
        }
    }

    // 持久化并立即让 AppLogger 生效，无需重启 App
    fun updateLogLevel(level: AppLogger.LogLevel) {
        viewModelScope.launch {
            settingsPreferences.saveLogLevel(level.name)
            AppLogger.setLevel(level)
        }
    }

    fun updateAutoPlayNext(enabled: Boolean) = launchSave { settingsPreferences.saveAutoPlayNext(enabled) }

    fun updateWifiOnlyPlay(enabled: Boolean) = launchSave { settingsPreferences.saveWifiOnlyPlay(enabled) }

    fun updateMobileAlert(enabled: Boolean) = launchSave { settingsPreferences.saveMobileAlert(enabled) }

    fun updateUseProxy(enabled: Boolean) = launchSave { settingsPreferences.saveUseProxy(enabled) }

    fun updateShowDesktopLrc(enabled: Boolean) = launchSave { settingsPreferences.saveShowDesktopLrc(enabled) }

    fun updateShowLockscreen(enabled: Boolean) = launchSave { settingsPreferences.saveShowLockscreen(enabled) }

    fun updateCarMode(enabled: Boolean) = launchSave { settingsPreferences.saveCarMode(enabled) }

    fun updateShowCreateEntry(enabled: Boolean) = launchSave { settingsPreferences.saveShowCreateEntry(enabled) }

    fun updateLyricTextSize(size: Int) = launchSave { settingsPreferences.saveLyricTextSize(size) }

    fun updateLyricTextColor(color: String) = launchSave { settingsPreferences.saveLyricTextColor(color) }

    fun updateAutoCheckUpdateEnabled(enabled: Boolean) = launchSave { settingsPreferences.saveAutoCheckUpdateEnabled(enabled) }

    fun updateAllowPrereleaseChannel(enabled: Boolean) = launchSave { settingsPreferences.saveAllowPrereleaseChannel(enabled) }

    fun onNicknameInputChanged(name: String) {
        _nicknameInput.value = name
        if (name.isBlank()) {
            _isNicknameDuplicated.value = null
        }
    }

    // ─── 服务端接口数据加载 ───

    fun fetchRemoteSettingsData() {
        viewModelScope.launch {
            _isLoading.value = true
            val profile = userPreferences.userProfile.first() ?: return@launch

            // 并发获取用户等级、VIP 信息和绑定第三方账号信息
            launch {
                settingsRepository.getUserLevel().collect { res ->
                    res.onSuccess { _userLevel.value = it }
                }
            }
            launch {
                settingsRepository.getVipInfo().collect { res ->
                    res.onSuccess { _vipInfo.value = it }
                }
            }
            launch {
                settingsRepository.getUserBindings(profile.uid).collect { res ->
                    res.onSuccess { _userBindings.value = it }
                }
            }
            _isLoading.value = false
        }
    }

    // 每日签到
    fun executeDailySignin(type: Int = 0) {
        viewModelScope.launch {
            settingsRepository.dailySignin(type).collect { res ->
                res.onSuccess { point ->
                    if (point > 0) {
                        _toastEvent.emit("签到成功！积分+$point")
                    } else {
                        _toastEvent.emit("今天已经签到过了")
                    }
                }.onFailure {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 更换头像
    fun uploadUserAvatar(file: java.io.File) {
        viewModelScope.launch {
            _isLoading.value = true
            settingsRepository.uploadAvatar(file).collect { res ->
                res.onSuccess { newUrl ->
                    // 更新本地用户元数据
                    val currentProfile = userPreferences.userProfile.first()
                    if (currentProfile != null) {
                        userPreferences.saveUserProfile(currentProfile.copy(avatarUrl = newUrl))
                    }
                    _toastEvent.emit("头像更新成功")
                }.onFailure {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
                _isLoading.value = false
            }
        }
    }

    // 保存资料修改 (修改昵称及个性签名)
    fun saveProfileChanges(nickname: String, signature: String, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            settingsRepository.updateUserProfile(
                nickname = nickname,
                gender = 1,
                birthday = 0L,
                province = 110000,
                city = 110101,
                signature = signature
            ).collect { res ->
                res.onSuccess {
                    // 更新本地 user profile nickname
                    val currentProfile = userPreferences.userProfile.first()
                    if (currentProfile != null) {
                        userPreferences.saveUserProfile(
                            currentProfile.copy(nickname = nickname)
                        )
                    }
                    _toastEvent.emit("资料保存成功")
                    onFinished(true)
                }.onFailure {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                    onFinished(false)
                }
                _isLoading.value = false
            }
        }
    }

    // 退出登录，注销远端会话并清空本地 preferences 缓存
    fun executeLogout(onFinished: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout().collect { }
            userPreferences.clearUserProfile()
            _toastEvent.emit("已成功退出登录")
            onFinished()
        }
    }

    // ─── 深度缓存清理 ───

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearApplicationCache(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                // 0. 清理 ExoPlayer 媒体缓存
                AudioCacheManager.clearCache(context)

                // 1. 清理 Coil 图片缓存
                val imageLoader = coil.Coil.imageLoader(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()

                // 2. 递归清理应用的 cacheDir 临时缓存目录
                val cacheDir = context.cacheDir
                if (cacheDir.exists() && cacheDir.isDirectory) {
                    cacheDir.listFiles()?.forEach { file ->
                        file.deleteRecursively()
                    }
                }

                // 3. 递归清理外部缓存目录 (例如 ExoPlayer 等产生的媒体缓存)
                val extCacheDir = context.externalCacheDir
                if (extCacheDir != null && extCacheDir.exists() && extCacheDir.isDirectory) {
                    extCacheDir.listFiles()?.forEach { file ->
                        file.deleteRecursively()
                    }
                }

                // 4. 清理 App 更新下载的旧安装包
                context.getExternalFilesDir(null)?.let { dir ->
                    File(dir, "updates").listFiles()?.forEach { it.deleteRecursively() }
                }
            }.onFailure { AppLogger.e(TAG, "清理应用缓存失败", it) }
            _audioCacheSize.value = 0L
            _imageCacheSize.value = 0L
            _logCacheSize.value = 0L
            _updatePackageSize.value = 0L
            _otherCacheSize.value = 0L
            _toastEvent.emit("应用临时数据与图片缓存已清理完成")
            _isLoading.value = false
        }
    }

    // 储存空间页首次进入时调用，并发算出各分类占用大小
    fun loadStorageStats(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _audioCacheSize.value = AudioCacheManager.getCacheDirSize(context)
            _imageCacheSize.value = dirSize(File(context.cacheDir, "image_cache"))
            _logCacheSize.value = AppLogger.getLogsSize()
            _updatePackageSize.value = context.getExternalFilesDir(null)?.let { dirSize(File(it, "updates")) } ?: 0L
            _otherCacheSize.value = otherTempFilesSize(context)
        }
    }

    private fun dirSize(dir: File): Long =
        if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L

    // cacheDir/externalCacheDir 下排除已单独统计的 audio_cache/image_cache/logs 后剩余的临时文件大小
    private fun otherTempFilesSize(context: Context): Long {
        val excluded = setOf("audio_cache", "image_cache", "logs")
        val cacheDirTotal = context.cacheDir.listFiles()
            ?.filter { it.name !in excluded }
            ?.sumOf { dirSize(it) } ?: 0L
        val extCacheDirTotal = context.externalCacheDir?.listFiles()
            ?.sumOf { dirSize(it) } ?: 0L
        return cacheDirTotal + extCacheDirTotal
    }

    fun clearAudioCacheOnly(context: Context) {
        viewModelScope.launch {
            runCatching { AudioCacheManager.clearCache(context) }
                .onFailure { AppLogger.e(TAG, "清理音频缓存失败", it) }
            _audioCacheSize.value = 0L
            _toastEvent.emit("音频缓存已清理")
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearImageCacheOnly(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val imageLoader = coil.Coil.imageLoader(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            }.onFailure { AppLogger.e(TAG, "清理图片缓存失败", it) }
            _imageCacheSize.value = 0L
            _toastEvent.emit("图片缓存已清理")
        }
    }

    fun clearLogCacheOnly() {
        viewModelScope.launch {
            AppLogger.clearLogs()
            _logCacheSize.value = 0L
            _toastEvent.emit("日志文件已清理")
        }
    }

    fun clearUpdatePackages(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.getExternalFilesDir(null)?.let { dir ->
                    File(dir, "updates").listFiles()?.forEach { it.deleteRecursively() }
                }
            }.onFailure { AppLogger.e(TAG, "清理更新安装包失败", it) }
            _updatePackageSize.value = 0L
            _toastEvent.emit("更新安装包已清理")
        }
    }

    fun clearOtherTempFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val excluded = setOf("audio_cache", "image_cache", "logs")
            runCatching {
                context.cacheDir.listFiles()?.filter { it.name !in excluded }?.forEach { it.deleteRecursively() }
                context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
            }.onFailure { AppLogger.e(TAG, "清理其他临时文件失败", it) }
            _otherCacheSize.value = 0L
            _toastEvent.emit("其他临时文件已清理")
        }
    }
}
