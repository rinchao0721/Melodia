package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

private const val TAG = "PluginStore"
private const val STALE_STAGING_MS = 60 * 60 * 1000L

// 布局选择里表示「使用内置布局」
const val LAYOUT_CHOICE_DEFAULT = "default"

@Serializable
data class PluginState(
    val enabled: Boolean = false,
    // 用户确认过的权限，清单权限多于这里时插件不运行
    val granted: Set<String> = emptySet(),
    // 连续出错被自动停用时的原因
    val disabledReason: String? = null,
    // 最近一次启用的时间，同一区域有多个插件布局时默认取最近启用的
    val enabledAt: Long = 0L,
    // 用户是否允许过写入网易账号；重新安装后需要再次确认
    val neteaseWriteApproved: Boolean = false,
    // 原生插件 JAR 的 SHA-256，加载时核对
    val nativeHash: String? = null,
    // 导入时选择的文件名
    val source: String? = null
)

data class InstalledPlugin(
    val id: String,
    val manifest: PluginManifest?,
    val state: PluginState,
    // 安装目录被改坏、清单无法解析时的原因；此时只能删除
    val loadError: String? = null,
    // 可以回退到的版本
    val previousVersion: String? = null
) {
    val name: String get() = manifest?.name ?: id

    val missingPermissions: Set<PluginPermission>
        get() = manifest?.permissions.orEmpty().filterTo(LinkedHashSet()) { it.key !in state.granted }

    val canRun: Boolean get() = manifest != null && loadError == null && missingPermissions.isEmpty()
}

internal val PLUGIN_ID_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

// 先写临时文件再整体替换，中途退出不会留下半个文件；File.renameTo 在 Windows 上不能覆盖已有文件
internal fun writeFileAtomically(file: File, text: String) {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, "${file.name}.tmp")
    temp.writeText(text)
    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

// 已安装插件按 id 各占一个目录；导入先解到 .staging，确认后再移入；插件数据放 .data，升级与导出都不碰
// 升级时旧版本连同状态移到 .previous，只保留一份
class PluginStore(private val root: File) {

    private val stagingRoot = File(root, ".staging")
    private val dataRoot = File(root, ".data")
    private val previousRoot = File(root, ".previous")
    private val stateFile = File(root, ".state.json")
    private val layoutFile = File(root, ".layouts.json")
    private val optionsFile = File(root, ".options.json")
    private val catalogFile = File(root, ".catalog.json")
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val stateSerializer = MapSerializer(String.serializer(), PluginState.serializer())
    private val choiceSerializer = MapSerializer(String.serializer(), String.serializer())

    // 原生插件的运行副本与加载标记；运行时只用副本，安装目录随时可以替换
    val nativeRoot: File = File(root, ".native")

    fun stage(input: InputStream, sourceName: String? = null): PluginStageResult =
        PluginPackage.stage(input, File(stagingRoot, UUID.randomUUID().toString()), sourceName)

    fun discard(staged: StagedPlugin) {
        staged.directory.deleteRecursively()
    }

    fun installed(id: String): InstalledPlugin? = if (directoryOf(id)?.isDirectory == true) load(id) else null

    // 同 id 覆盖安装即升级；granted 为用户在导入预览里确认过的权限
    fun install(staged: StagedPlugin, enabled: Boolean): InstalledPlugin? {
        val id = staged.manifest.id
        val target = directoryOf(id) ?: return null
        return try {
            val backup = File(previousRoot, id)
            val backupState = File(previousRoot, "$id.json")
            val oldState = state(id)
            val upgrading = target.isDirectory
            backup.deleteRecursively()
            backupState.delete()
            if (upgrading) {
                previousRoot.mkdirs()
                moveDirectory(target, backup)
            }
            try {
                target.parentFile?.mkdirs()
                moveDirectory(staged.directory, target)
            } catch (e: IOException) {
                target.deleteRecursively()
                if (upgrading) moveDirectory(backup, target)
                throw e
            }
            if (upgrading) backupState.writeText(json.encodeToString(PluginState.serializer(), oldState))
            updateState(id) {
                PluginState(
                    enabled = enabled,
                    granted = staged.manifest.permissions.mapTo(HashSet()) { it.key },
                    enabledAt = if (enabled) System.currentTimeMillis() else 0L,
                    nativeHash = File(target, PluginPaths.JAR).takeIf { staged.manifest.kind == PluginKind.NATIVE }?.let(PluginPackage::sha256),
                    source = staged.sourceName
                )
            }
            load(id)
        } catch (e: IOException) {
            AppLogger.w(TAG, "安装插件失败: $id", e)
            null
        }
    }

    // 换回上一版本和当时的状态，替换下来的版本不保留；没有可回退的版本或替换失败时返回 null
    fun rollback(id: String): InstalledPlugin? {
        val target = directoryOf(id) ?: return null
        val backup = File(previousRoot, id)
        val backupState = File(previousRoot, "$id.json")
        val oldState = previousState(id) ?: return null
        if (!backup.isDirectory) return null
        val aside = File(stagingRoot, UUID.randomUUID().toString())
        return try {
            stagingRoot.mkdirs()
            if (target.isDirectory) moveDirectory(target, aside)
            try {
                moveDirectory(backup, target)
            } catch (e: IOException) {
                target.deleteRecursively()
                if (aside.isDirectory) moveDirectory(aside, target)
                throw e
            }
            aside.deleteRecursively()
            backupState.delete()
            updateState(id) { oldState }
            load(id)
        } catch (e: IOException) {
            AppLogger.w(TAG, "回退插件失败: $id", e)
            null
        }
    }

    fun list(): List<InstalledPlugin> {
        clearStaleStaging()
        val states = readStates()
        return root.listFiles()
            ?.filter { it.isDirectory && PLUGIN_ID_PATTERN.matches(it.name) }
            ?.mapNotNull { load(it.name, states) }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun load(id: String): InstalledPlugin? = load(id, readStates())

    private fun load(id: String, states: Map<String, PluginState>): InstalledPlugin? =
        loadCurrent(id, states[id] ?: PluginState())?.copy(previousVersion = previousVersion(id))

    private fun loadCurrent(id: String, state: PluginState): InstalledPlugin? {
        val directory = directoryOf(id)?.takeIf { it.isDirectory } ?: return null
        val root = PluginPackage.readManifest(File(directory, PluginPaths.MANIFEST))
            ?: return InstalledPlugin(id, null, state, "${PluginPaths.MANIFEST} 缺失或无效")
        return when (val result = PluginParser.parse(root, directory)) {
            is PluginParseResult.Failure -> InstalledPlugin(id, null, state, result.reason)
            is PluginParseResult.Success -> {
                val manifest = result.manifest
                when {
                    manifest.id != id -> InstalledPlugin(id, null, state, "清单 id 与安装目录不一致")
                    else -> InstalledPlugin(id, manifest, state, PluginPackage.checkFiles(manifest, directory))
                }
            }
        }
    }

    fun state(id: String): PluginState = synchronized(lock) { readStates()[id] ?: PluginState() }

    fun updateState(id: String, transform: (PluginState) -> PluginState) {
        synchronized(lock) {
            val states = readStates().toMutableMap()
            states[id] = transform(states[id] ?: PluginState())
            writeStates(states)
        }
    }

    fun delete(id: String): Boolean {
        val directory = directoryOf(id) ?: return false
        synchronized(lock) {
            val states = readStates()
            if (id in states) writeStates(states - id)
        }
        dataDirectoryOf(id)?.deleteRecursively()
        File(previousRoot, id).deleteRecursively()
        File(previousRoot, "$id.json").delete()
        return directory.deleteRecursively()
    }

    fun export(id: String, output: OutputStream): Boolean {
        val directory = directoryOf(id)?.takeIf { it.isDirectory } ?: return false
        PluginPackage.export(output, directory)
        return true
    }

    // 区域 -> 选定的插件 id 或 LAYOUT_CHOICE_DEFAULT；没有记录的区域按最近启用的插件
    fun layoutChoices(): Map<String, String> = synchronized(lock) { readJson(layoutFile, choiceSerializer) }

    fun setLayoutChoice(area: String, choice: String?) {
        synchronized(lock) {
            val choices = layoutChoices().toMutableMap()
            if (choice == null) choices.remove(area) else choices[area] = choice
            writeJson(layoutFile, choiceSerializer, choices)
        }
    }

    fun nativeAllowed(): Boolean = synchronized(lock) { readJson(optionsFile, choiceSerializer)[OPTION_ALLOW_NATIVE] == "true" }

    fun setNativeAllowed(allowed: Boolean) {
        synchronized(lock) {
            writeJson(optionsFile, choiceSerializer, readJson(optionsFile, choiceSerializer) + (OPTION_ALLOW_NATIVE to allowed.toString()))
        }
    }

    // null 表示使用默认目录
    fun catalogUrl(): String? = synchronized(lock) { readJson(optionsFile, choiceSerializer)[OPTION_CATALOG_URL] }

    fun setCatalogUrl(url: String?) {
        synchronized(lock) {
            val options = readJson(optionsFile, choiceSerializer)
            writeJson(optionsFile, choiceSerializer, if (url == null) options - OPTION_CATALOG_URL else options + (OPTION_CATALOG_URL to url))
        }
    }

    // 上次成功读到的目录原文，读不到网络时用
    fun catalogCache(): String? = try {
        catalogFile.takeIf { it.isFile }?.readText()
    } catch (e: IOException) {
        AppLogger.w(TAG, "读取目录缓存失败", e)
        null
    }

    fun catalogCheckedAt(): Long = catalogFile.lastModified()

    fun writeCatalogCache(text: String?) {
        try {
            if (text == null) {
                catalogFile.delete()
            } else {
                root.mkdirs()
                catalogFile.writeText(text)
            }
        } catch (e: IOException) {
            AppLogger.w(TAG, "保存目录缓存失败", e)
        }
    }

    // 下载到这里，stage 后由调用方删除；中途退出的残留按过期临时文件清理
    fun downloadFile(): File {
        stagingRoot.mkdirs()
        return File(stagingRoot, "${UUID.randomUUID()}.download")
    }

    // 插件私有数据目录，与安装目录分开，升级不丢失
    fun dataDirectoryOf(id: String): File? = if (PLUGIN_ID_PATTERN.matches(id)) File(dataRoot, id) else null

    private fun previousVersion(id: String): String? {
        if (!File(previousRoot, "$id.json").isFile) return null
        val manifest = PluginPackage.readManifest(File(File(previousRoot, id), PluginPaths.MANIFEST)) ?: return null
        return (manifest["version"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private fun previousState(id: String): PluginState? {
        val file = File(previousRoot, "$id.json")
        if (!file.isFile) return null
        return try {
            json.decodeFromString(PluginState.serializer(), file.readText())
        } catch (e: SerializationException) {
            AppLogger.w(TAG, "${file.name} 损坏，已忽略", e)
            null
        } catch (e: IllegalArgumentException) {
            AppLogger.w(TAG, "${file.name} 损坏，已忽略", e)
            null
        } catch (e: IOException) {
            AppLogger.w(TAG, "读取 ${file.name} 失败", e)
            null
        }
    }

    // 跨盘时 renameTo 失败，退回复制
    private fun moveDirectory(from: File, to: File) {
        if (from.renameTo(to)) return
        from.copyRecursively(to, overwrite = true)
        from.deleteRecursively()
    }

    private fun readStates(): Map<String, PluginState> = synchronized(lock) { readJson(stateFile, stateSerializer) }

    private fun writeStates(states: Map<String, PluginState>) = writeJson(stateFile, stateSerializer, states)

    // 文件损坏时按空处理：状态全部停用、布局选择回到默认
    private fun <T> readJson(file: File, serializer: KSerializer<Map<String, T>>): Map<String, T> {
        if (!file.isFile) return emptyMap()
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: SerializationException) {
            AppLogger.w(TAG, "${file.name} 损坏，已忽略", e)
            emptyMap()
        } catch (e: IllegalArgumentException) {
            AppLogger.w(TAG, "${file.name} 损坏，已忽略", e)
            emptyMap()
        } catch (e: IOException) {
            AppLogger.w(TAG, "读取 ${file.name} 失败", e)
            emptyMap()
        }
    }

    private fun <T> writeJson(file: File, serializer: KSerializer<Map<String, T>>, value: Map<String, T>) {
        try {
            writeFileAtomically(file, json.encodeToString(serializer, value))
        } catch (e: IOException) {
            AppLogger.w(TAG, "保存 ${file.name} 失败", e)
        }
    }

    // 导入中途退出留下的临时目录；只清理一小时前的，避免误删正在预览的
    private fun clearStaleStaging() {
        val threshold = System.currentTimeMillis() - STALE_STAGING_MS
        stagingRoot.listFiles()?.filter { it.lastModified() < threshold }?.forEach { it.deleteRecursively() }
    }

    // id 来自状态文件与插件清单，拼路径前先校验，防止越出插件目录
    private fun directoryOf(id: String): File? = if (PLUGIN_ID_PATTERN.matches(id)) File(root, id) else null

    private companion object {
        const val OPTION_ALLOW_NATIVE = "allowNative"
        const val OPTION_CATALOG_URL = "catalogUrl"
    }
}
