package com.lin0721.linmusic.core.source

import kotlinx.coroutines.flow.first
import java.util.UUID

// LX 插件的导入、启停、卸载编排，两端设置页共用
class LxPluginManager(
    private val runtime: LxPluginRuntime,
    private val sourcePreferences: SourcePreferences
) {

    sealed interface ImportCheckResult {
        data class Success(val item: LxPluginItem) : ImportCheckResult
        data class Duplicate(val existing: LxPluginItem, val newInfo: LxPluginInfo, val rawScript: String) : ImportCheckResult
        data class Failure(val message: String) : ImportCheckResult
    }

    // 预热已启用的插件沙盒
    suspend fun warmUp() {
        if (sourcePreferences.lxPluginEnabled.first()) syncEnabledPlugins()
    }

    suspend fun setEnabled(enabled: Boolean) {
        sourcePreferences.saveLxPluginEnabled(enabled)
        if (enabled) syncEnabledPlugins() else runtime.reset()
    }

    suspend fun toggle(id: String) {
        sourcePreferences.toggleLxPlugin(id)
        if (sourcePreferences.lxPluginEnabled.first()) syncEnabledPlugins()
    }

    suspend fun remove(id: String) {
        sourcePreferences.removeLxPlugin(id)
        runtime.deactivatePlugin(id)
        runtime.clearData(id)
    }

    suspend fun clearAll() {
        sourcePreferences.clearLxPlugin()
        runtime.reset()
    }

    // 批量导入的汇总：成功入库的、需要确认的重名项、失败原因
    data class BatchImportResult(
        val imported: List<LxPluginItem>,
        val duplicates: List<ImportCheckResult.Duplicate>,
        val failures: List<String>
    ) {
        fun summary(): String {
            val parts = mutableListOf("导入 ${imported.size} 个")
            if (duplicates.isNotEmpty()) parts += "重名 ${duplicates.size} 个待确认"
            if (failures.isNotEmpty()) parts += "失败 ${failures.size} 个"
            return parts.joinToString("，")
        }
    }

    // 逐个校验并导入压缩包里选中的脚本
    suspend fun importScripts(scripts: List<ArchiveScript>): BatchImportResult {
        val imported = mutableListOf<LxPluginItem>()
        val duplicates = mutableListOf<ImportCheckResult.Duplicate>()
        val failures = mutableListOf<String>()
        for (script in scripts) {
            when (val result = precheckAndImport(script.content)) {
                is ImportCheckResult.Success -> imported += result.item
                is ImportCheckResult.Duplicate -> duplicates += result
                is ImportCheckResult.Failure -> failures += "${script.path}：${result.message}"
            }
        }
        return BatchImportResult(imported, duplicates, failures)
    }

    // 输入为链接时下载脚本，否则当作脚本文本
    suspend fun resolveScriptContent(input: String): Result<String> {
        val trimmed = input.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            runtime.downloadScript(trimmed)
        } else {
            Result.success(trimmed)
        }
    }

    // 校验脚本；无重名直接入库，重名交给调用方确认
    suspend fun precheckAndImport(scriptText: String): ImportCheckResult {
        val verifyRes = runtime.loadAndVerifyScript(scriptText)
        if (verifyRes.isFailure) {
            return ImportCheckResult.Failure(verifyRes.exceptionOrNull()?.message ?: "脚本解析失败，请检查脚本语法")
        }
        val info = verifyRes.getOrThrow()
        val existing = sourcePreferences.lxPlugins.first().firstOrNull { it.name.trim() == info.name.trim() }
        if (existing != null) {
            return ImportCheckResult.Duplicate(existing = existing, newInfo = info, rawScript = scriptText)
        }
        return ImportCheckResult.Success(confirmImport(info, scriptText, overwriteExistingId = null))
    }

    // 确认导入：overwriteExistingId 非空为覆盖更新，否则新增
    suspend fun confirmImport(info: LxPluginInfo, script: String, overwriteExistingId: String? = null): LxPluginItem {
        val item = LxPluginItem(
            id = overwriteExistingId ?: UUID.randomUUID().toString(),
            name = info.name,
            version = info.version,
            author = info.author,
            description = info.description,
            sources = info.sources,
            rawScript = script,
            isEnabled = true
        )
        sourcePreferences.addOrUpdateLxPlugin(item, overwriteId = overwriteExistingId)
        if (sourcePreferences.lxPluginEnabled.first()) runtime.activatePlugin(item)
        return item
    }

    private suspend fun syncEnabledPlugins() {
        runtime.syncActivePlugins(sourcePreferences.lxPlugins.first().filter { it.isEnabled })
    }
}
