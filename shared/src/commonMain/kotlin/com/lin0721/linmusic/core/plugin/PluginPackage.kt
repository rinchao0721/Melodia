package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.archive.SafeZip
import com.lin0721.linmusic.core.archive.ZipExtractResult
import com.lin0721.linmusic.core.archive.ZipLimitExceeded
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

data class StagedPlugin(
    val manifest: PluginManifest,
    val warnings: List<String>,
    val directory: File
)

sealed interface PluginStageResult {
    data class Success(val staged: StagedPlugin) : PluginStageResult
    data class Failure(val reason: String) : PluginStageResult
}

// 插件包读写：解包按白名单落盘并限制体积，再核对清单引用的脚本与界面文件都在包里
object PluginPackage {

    private const val MAX_ENTRIES = 64
    private const val MAX_TOTAL_BYTES = 10L * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 256L * 1024
    private const val MAX_SCRIPT_BYTES = 2L * 1024 * 1024
    private const val MAX_UI_BYTES = 256L * 1024
    private const val MAX_ASSET_BYTES = 2L * 1024 * 1024

    fun stage(input: InputStream, directory: File): PluginStageResult {
        directory.deleteRecursively()
        if (!directory.mkdirs()) return PluginStageResult.Failure("无法创建临时目录")
        val result = try {
            unpack(input, directory)
        } catch (e: ZipLimitExceeded) {
            PluginStageResult.Failure(e.message ?: "插件包超出大小限制")
        } catch (e: ZipException) {
            PluginStageResult.Failure("不是有效的插件包")
        } catch (e: IllegalArgumentException) {
            PluginStageResult.Failure("不是有效的插件包")
        } catch (e: IOException) {
            PluginStageResult.Failure("读取插件包失败：${e.message}")
        }
        if (result is PluginStageResult.Failure) directory.deleteRecursively()
        return result
    }

    private fun unpack(input: InputStream, directory: File): PluginStageResult {
        val extracted = SafeZip.extract(input, directory, "插件包", MAX_ENTRIES, MAX_TOTAL_BYTES) { name ->
            when {
                name == PluginPaths.MANIFEST -> MAX_MANIFEST_BYTES
                name == PluginPaths.SCRIPT -> MAX_SCRIPT_BYTES
                PluginPaths.isUi(name) -> MAX_UI_BYTES
                PluginPaths.isAsset(name) -> MAX_ASSET_BYTES
                else -> null
            }
        }
        val warnings = when (extracted) {
            is ZipExtractResult.Failure -> return PluginStageResult.Failure(extracted.reason)
            is ZipExtractResult.Success -> extracted.warnings
        }
        val manifestFile = File(directory, PluginPaths.MANIFEST)
        if (!manifestFile.isFile) return PluginStageResult.Failure("插件包里缺少 ${PluginPaths.MANIFEST}")
        val root = readManifest(manifestFile) ?: return PluginStageResult.Failure("${PluginPaths.MANIFEST} 不是有效的 JSON 对象")
        val parsed = when (val result = PluginParser.parse(root, directory)) {
            is PluginParseResult.Failure -> return PluginStageResult.Failure(result.reason)
            is PluginParseResult.Success -> result
        }
        val manifest = parsed.manifest
        checkFiles(manifest, directory)?.let { return PluginStageResult.Failure(it) }
        val unusedScript = !manifest.contributes.needsScript && File(directory, PluginPaths.SCRIPT).isFile
        val extra = mutableListOf<String>()
        if (unusedScript) extra += "插件只提供布局，${PluginPaths.SCRIPT} 不会运行"
        manifest.contributes.playerLayouts.keys.mapNotNull(PlayerLayoutArea::fromKey).forEach { area ->
            when (val layout = PlayerLayoutParser.load(manifest, area)) {
                is PlayerLayoutParseResult.Failure -> extra += "${area.label}布局无效（${layout.reason}），将使用默认布局"
                is PlayerLayoutParseResult.Success -> layout.warnings.forEach { extra += "${area.label}布局：$it" }
            }
        }
        return PluginStageResult.Success(StagedPlugin(manifest, warnings + parsed.warnings + extra, directory))
    }

    // 已安装的插件文件可能被外部改动，加载时同样调用
    fun checkFiles(manifest: PluginManifest, directory: File): String? {
        if (manifest.contributes.needsScript && !File(directory, PluginPaths.SCRIPT).isFile) {
            return "插件提供了页面、音源、歌词源或事件处理，但包里缺少 ${PluginPaths.SCRIPT}"
        }
        val missing = manifest.contributes.uiFiles.firstOrNull { !File(directory, it).isFile }
        return missing?.let { "插件包里缺少界面描述 $it" }
    }

    fun readManifest(file: File): JsonObject? = try {
        if (file.length() > MAX_MANIFEST_BYTES) null else Json.parseToJsonElement(file.readText()) as? JsonObject
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IOException) {
        null
    }

    // 只打包白名单内的安装文件，插件自己存的数据不在安装目录里
    fun export(output: OutputStream, directory: File) {
        val files = directory.walkTopDown()
            .filter { it.isFile }
            .map { it to it.relativeTo(directory).invariantSeparatorsPath }
            .filter { (_, path) -> PluginPaths.isAllowed(path) }
            .sortedBy { (_, path) -> path }
            .toList()
        ZipOutputStream(output).use { zip ->
            files.forEach { (file, path) ->
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
