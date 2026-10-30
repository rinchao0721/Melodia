package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

private const val TAG = "PluginStore"
private const val STALE_STAGING_MS = 60 * 60 * 1000L

@Serializable
data class PluginState(
    val enabled: Boolean = false,
    // 用户确认过的权限，清单权限多于这里时插件不运行
    val granted: Set<String> = emptySet(),
    // 连续出错被自动停用时的原因
    val disabledReason: String? = null
)

data class InstalledPlugin(
    val id: String,
    val manifest: PluginManifest?,
    val state: PluginState,
    // 安装目录被改坏、清单无法解析时的原因；此时只能删除
    val loadError: String? = null
) {
    val name: String get() = manifest?.name ?: id

    val missingPermissions: Set<PluginPermission>
        get() = manifest?.permissions.orEmpty().filterTo(LinkedHashSet()) { it.key !in state.granted }

    val canRun: Boolean get() = manifest != null && loadError == null && missingPermissions.isEmpty()
}

// 已安装插件按 id 各占一个目录；导入先解到 .staging，确认后再移入；插件数据放 .data，升级与导出都不碰
class PluginStore(private val root: File) {

    private val stagingRoot = File(root, ".staging")
    private val dataRoot = File(root, ".data")
    private val stateFile = File(root, ".state.json")
    private val idPattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val stateSerializer = MapSerializer(String.serializer(), PluginState.serializer())

    fun stage(input: InputStream): PluginStageResult =
        PluginPackage.stage(input, File(stagingRoot, UUID.randomUUID().toString()))

    fun discard(staged: StagedPlugin) {
        staged.directory.deleteRecursively()
    }

    fun installed(id: String): InstalledPlugin? = if (directoryOf(id)?.isDirectory == true) load(id) else null

    // 同 id 覆盖安装即升级；granted 为用户在导入预览里确认过的权限
    fun install(staged: StagedPlugin, enabled: Boolean): InstalledPlugin? {
        val id = staged.manifest.id
        val target = directoryOf(id) ?: return null
        return try {
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            if (!staged.directory.renameTo(target)) {
                staged.directory.copyRecursively(target, overwrite = true)
                staged.directory.deleteRecursively()
            }
            updateState(id) { PluginState(enabled = enabled, granted = staged.manifest.permissions.mapTo(HashSet()) { it.key }) }
            load(id)
        } catch (e: IOException) {
            AppLogger.w(TAG, "安装插件失败: $id", e)
            null
        }
    }

    fun list(): List<InstalledPlugin> {
        clearStaleStaging()
        return root.listFiles()
            ?.filter { it.isDirectory && idPattern.matches(it.name) }
            ?.mapNotNull { load(it.name) }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun load(id: String): InstalledPlugin? {
        val directory = directoryOf(id)?.takeIf { it.isDirectory } ?: return null
        val state = readStates()[id] ?: PluginState()
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
        return directory.deleteRecursively()
    }

    fun export(id: String, output: OutputStream): Boolean {
        val directory = directoryOf(id)?.takeIf { it.isDirectory } ?: return false
        PluginPackage.export(output, directory)
        return true
    }

    // 插件私有数据目录，与安装目录分开，升级不丢失
    fun dataDirectoryOf(id: String): File? = if (idPattern.matches(id)) File(dataRoot, id) else null

    private fun readStates(): Map<String, PluginState> = synchronized(lock) {
        if (!stateFile.isFile) return emptyMap()
        try {
            json.decodeFromString(stateSerializer, stateFile.readText())
        } catch (e: SerializationException) {
            AppLogger.w(TAG, "插件状态文件损坏，按全部停用处理", e)
            emptyMap()
        } catch (e: IllegalArgumentException) {
            AppLogger.w(TAG, "插件状态文件损坏，按全部停用处理", e)
            emptyMap()
        } catch (e: IOException) {
            AppLogger.w(TAG, "读取插件状态失败", e)
            emptyMap()
        }
    }

    // 先写临时文件再替换，避免中途退出写坏状态文件
    private fun writeStates(states: Map<String, PluginState>) {
        try {
            root.mkdirs()
            val temp = File(root, ".state.json.tmp")
            temp.writeText(json.encodeToString(stateSerializer, states))
            if (!temp.renameTo(stateFile)) {
                stateFile.delete()
                if (!temp.renameTo(stateFile)) throw IOException("无法替换状态文件")
            }
        } catch (e: IOException) {
            AppLogger.w(TAG, "保存插件状态失败", e)
        }
    }

    // 导入中途退出留下的临时目录；只清理一小时前的，避免误删正在预览的
    private fun clearStaleStaging() {
        val threshold = System.currentTimeMillis() - STALE_STAGING_MS
        stagingRoot.listFiles()?.filter { it.lastModified() < threshold }?.forEach { it.deleteRecursively() }
    }

    // id 来自状态文件与插件清单，拼路径前先校验，防止越出插件目录
    private fun directoryOf(id: String): File? = if (idPattern.matches(id)) File(root, id) else null
}
