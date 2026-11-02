package com.lin0721.linmusic.core.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.ThemeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "SkinSettings"

sealed interface SkinImportStep {
    data object Idle : SkinImportStep
    data object Working : SkinImportStep

    // conflict 为已安装同 id 皮肤，需用户选择覆盖或另存
    data class Preview(val staged: StagedSkin, val conflict: Boolean) : SkinImportStep
    data class Failed(val message: String) : SkinImportStep
}

// 双端皮肤设置共用的流程：导入先解包预览，确认后安装并应用；取消时清理临时目录
@Stable
class SkinSettingsController(
    private val repository: SkinRepository,
    private val scope: CoroutineScope
) {
    val installed: StateFlow<List<Skin>> = repository.installed
    val presets: StateFlow<List<Skin>> = repository.presets

    var step: SkinImportStep by mutableStateOf(SkinImportStep.Idle)
        private set

    // 导出结果提示；null 表示无提示
    var exportMessage: String? by mutableStateOf(null)
        private set

    fun refresh() {
        scope.launch { repository.refreshInstalled() }
    }

    // open 在 IO 线程调用，返回 null 视为用户取消
    fun import(open: suspend () -> InputStream?) {
        if (step == SkinImportStep.Working) return
        scope.launch {
            step = SkinImportStep.Working
            val input = try {
                withContext(Dispatchers.IO) { open() }
            } catch (e: IOException) {
                AppLogger.w(TAG, "打开皮肤文件失败", e)
                step = SkinImportStep.Failed("无法打开所选文件")
                return@launch
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "没有读取皮肤文件的权限", e)
                step = SkinImportStep.Failed("没有读取所选文件的权限")
                return@launch
            }
            if (input == null) {
                step = SkinImportStep.Idle
                return@launch
            }
            step = when (val result = repository.stage(input)) {
                is SkinStageResult.Failure -> SkinImportStep.Failed(result.reason)
                is SkinStageResult.Success -> SkinImportStep.Preview(result.staged, repository.isInstalled(result.staged.skin.id))
            }
        }
    }

    fun confirmImport(replace: Boolean, isDark: Boolean) {
        val preview = step as? SkinImportStep.Preview ?: return
        scope.launch {
            step = SkinImportStep.Working
            val skin = repository.installAndApply(preview.staged, replace, isDark)
            step = if (skin == null) SkinImportStep.Failed("安装皮肤失败") else SkinImportStep.Idle
        }
    }

    fun cancelImport() {
        val preview = step as? SkinImportStep.Preview
        step = SkinImportStep.Idle
        if (preview != null) scope.launch { repository.discard(preview.staged) }
    }

    fun dismissFailure() {
        if (step is SkinImportStep.Failed) step = SkinImportStep.Idle
    }

    fun apply(id: String, isDark: Boolean) {
        scope.launch { repository.apply(id, isDark) }
    }

    fun delete(id: String) {
        scope.launch { repository.delete(id) }
    }

    // open 在 IO 线程调用，返回 null 视为用户取消
    fun export(settings: ThemeSettings, skin: Skin?, open: suspend () -> OutputStream?) {
        scope.launch {
            val output = try {
                withContext(Dispatchers.IO) { open() }
            } catch (e: IOException) {
                AppLogger.w(TAG, "创建导出文件失败", e)
                null.also { exportMessage = "无法写入所选位置" }
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "没有写入导出文件的权限", e)
                null.also { exportMessage = "没有写入所选位置的权限" }
            } ?: return@launch
            exportMessage = if (repository.export(output, settings, skin)) "已导出当前主题" else "导出失败"
        }
    }

    fun dismissExportMessage() {
        exportMessage = null
    }
}

@Composable
fun rememberSkinSettingsController(repository: SkinRepository): SkinSettingsController {
    val scope = rememberCoroutineScope()
    return remember(repository, scope) { SkinSettingsController(repository, scope) }
}

// 导出文件的默认文件名
fun skinExportFileName(skin: Skin?): String =
    "${skin?.id ?: SkinRepository.DEFAULT_EXPORT_ID}.$SKIN_FILE_EXTENSION"
