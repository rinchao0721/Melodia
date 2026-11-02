package com.lin0721.linmusic.core.update

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.update.data.UpdateRepository
import com.lin0721.linmusic.core.update.domain.UpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "UpdateChecker"

// 更新检查的全局状态持有者；只负责发现新版本，下载交给发布页
// currentVersionCode 为 null 表示开发构建，不参与版本比较
class UpdateChecker(
    private val updateRepository: UpdateRepository,
    private val settingsPreferences: SettingsPreferences,
    private val currentVersionCode: Int?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val availableUpdate: StateFlow<UpdateInfo?> = _availableUpdate.asStateFlow()

    private val _isDialogVisible = MutableStateFlow(false)
    val isDialogVisible: StateFlow<Boolean> = _isDialogVisible.asStateFlow()

    // 自动检查只展示横幅，点击横幅再弹详情
    private val _isBannerVisible = MutableStateFlow(false)
    val isBannerVisible: StateFlow<Boolean> = _isBannerVisible.asStateFlow()

    // 一次性提示文案，由各端以 toast/snackbar 展示
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: Flow<String> = _messages.asSharedFlow()

    private var checkJob: Job? = null

    // 手动检查发现新版本直接弹窗；自动检查默认只亮横幅，showDialogOnAutoFound 为 true 时也弹窗
    fun checkForUpdate(manual: Boolean, showDialogOnAutoFound: Boolean = false) {
        if (checkJob?.isActive == true) {
            if (manual) _messages.tryEmit("正在检查更新…")
            return
        }
        checkJob = scope.launch {
            val versionCode = currentVersionCode
            if (versionCode == null) {
                if (manual) _messages.tryEmit("开发版本不支持检查更新")
                return@launch
            }
            if (!manual && !settingsPreferences.autoCheckUpdateEnabled.first()) return@launch

            val allowPrerelease = settingsPreferences.allowPrereleaseChannel.first()
            updateRepository.fetchLatestUpdate(versionCode, allowPrerelease)
                .onSuccess { info ->
                    if (info == null) {
                        _availableUpdate.value = null
                        _isBannerVisible.value = false
                        if (manual) _messages.tryEmit("当前已是最新版本")
                        return@onSuccess
                    }
                    val ignoredTag = settingsPreferences.ignoredUpdateTag.first()
                    if (!manual && info.versionName == ignoredTag) return@onSuccess
                    _availableUpdate.value = info
                    _isBannerVisible.value = true
                    if (manual || showDialogOnAutoFound) _isDialogVisible.value = true
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "检查更新失败", e)
                    if (manual) _messages.tryEmit("检查更新失败，请稍后重试")
                }
        }
    }

    fun ignoreCurrentVersion() {
        val info = _availableUpdate.value ?: return
        scope.launch { settingsPreferences.saveIgnoredUpdateTag(info.versionName) }
        _isDialogVisible.value = false
        _isBannerVisible.value = false
        _availableUpdate.value = null
    }

    // "稍后"：收起弹窗与横幅，保留可用更新供设置页与小红点提示
    fun dismiss() {
        _isDialogVisible.value = false
        _isBannerVisible.value = false
    }

    fun showDialog() {
        if (_availableUpdate.value != null) _isDialogVisible.value = true
    }

    // 仅收起弹窗，保留横幅
    fun hideDialog() {
        _isDialogVisible.value = false
    }
}
