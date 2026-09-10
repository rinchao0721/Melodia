package com.lin0721.linmusic.core.update

import android.content.Context
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.update.data.ApkDownloader
import com.lin0721.linmusic.core.update.data.ApkInstaller
import com.lin0721.linmusic.core.update.data.DownloadState
import com.lin0721.linmusic.core.update.data.UpdateNotificationHelper
import com.lin0721.linmusic.core.update.data.UpdateRepository
import com.lin0721.linmusic.core.update.domain.UpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "UpdateManager"

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data class Available(val info: UpdateInfo) : UpdateUiState()
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateUiState()
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateUiState()
    data class DownloadFailed(val info: UpdateInfo, val message: String) : UpdateUiState()
}

// 更新流程的全局状态持有者，Application 启动检查与设置页手动检查、弹窗展示均通过它交互
class UpdateManager(
    private val context: Context,
    private val updateRepository: UpdateRepository,
    private val apkDownloader: ApkDownloader,
    private val apkInstaller: ApkInstaller,
    private val settingsPreferences: SettingsPreferences,
    private val notificationHelper: UpdateNotificationHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _isDialogVisible = MutableStateFlow(false)
    val isDialogVisible: StateFlow<Boolean> = _isDialogVisible.asStateFlow()

    private var downloadJob: Job? = null

    fun checkForUpdate(manual: Boolean) {
        scope.launch {
            val currentState = _uiState.value
            if (currentState is UpdateUiState.Downloading) {
                if (manual) {
                    ToastManager.showToast("正在后台下载中（${currentState.progress}%）")
                    _isDialogVisible.value = true
                }
                return@launch
            }
            if (currentState is UpdateUiState.ReadyToInstall) {
                if (manual) {
                    _isDialogVisible.value = true
                }
                return@launch
            }

            if (!manual && !settingsPreferences.autoCheckUpdateEnabled.first()) return@launch

            val allowPrerelease = settingsPreferences.allowPrereleaseChannel.first()
            updateRepository.fetchLatestUpdate(allowPrerelease)
                .onSuccess { info ->
                    if (info == null) {
                        if (manual) ToastManager.showToast("当前已是最新版本")
                        return@onSuccess
                    }
                    val ignoredTag = settingsPreferences.ignoredUpdateTag.first()
                    if (!manual && info.versionName == ignoredTag) return@onSuccess
                    _uiState.value = UpdateUiState.Available(info)
                    _isDialogVisible.value = true
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "检查更新失败", e)
                    if (manual) ToastManager.showToast("检查更新失败，请稍后重试")
                }
        }
    }

    // 点击更新后关闭弹窗转入后台静默下载，并通过通知栏实时同步下载进度
    fun startDownload() {
        val info = currentInfoOrNull() ?: return
        _isDialogVisible.value = false
        ToastManager.showToast("正在后台下载新版本...")

        downloadJob?.cancel()
        downloadJob = scope.launch {
            apkDownloader.download(info.apkDownloadUrl, info.versionName).collect { state ->
                when (state) {
                    is DownloadState.Downloading -> {
                        _uiState.value = UpdateUiState.Downloading(info, state.progress)
                        notificationHelper.showDownloading(info.versionName, state.progress)
                    }
                    is DownloadState.Success -> {
                        _uiState.value = UpdateUiState.ReadyToInstall(info, state.file)
                        notificationHelper.showDownloadSuccess(
                            info.versionName,
                            apkInstaller.buildInstallIntent(state.file)
                        )
                        _isDialogVisible.value = true
                        tryInstall(state.file)
                    }
                    is DownloadState.Failed -> {
                        _uiState.value = UpdateUiState.DownloadFailed(info, state.message)
                        notificationHelper.showDownloadFailed(state.message)
                        ToastManager.showToast("新版本下载失败，请稍后重试")
                    }
                }
            }
        }
    }

    // 下载完成后自动尝试安装，未授权"安装未知应用"时跳转系统设置，用户返回后点弹窗里的安装按钮重试
    fun retryInstall() {
        val state = _uiState.value
        if (state is UpdateUiState.ReadyToInstall) tryInstall(state.file)
    }

    private fun tryInstall(file: File) {
        if (!apkInstaller.canRequestPackageInstalls()) {
            ToastManager.showToast("请先允许安装未知来源应用")
            context.startActivity(apkInstaller.buildUnknownSourceSettingsIntent())
            return
        }
        apkInstaller.install(file)
    }

    fun ignoreCurrentVersion() {
        val info = currentInfoOrNull() ?: return
        scope.launch { settingsPreferences.saveIgnoredUpdateTag(info.versionName) }
        _isDialogVisible.value = false
        _uiState.value = UpdateUiState.Idle
        notificationHelper.cancelNotification()
    }

    fun dismiss() {
        _isDialogVisible.value = false
        if (_uiState.value !is UpdateUiState.Downloading) {
            _uiState.value = UpdateUiState.Idle
        }
    }

    private fun currentInfoOrNull(): UpdateInfo? = when (val state = _uiState.value) {
        is UpdateUiState.Available -> state.info
        is UpdateUiState.DownloadFailed -> state.info
        is UpdateUiState.Downloading -> state.info
        else -> null
    }
}
