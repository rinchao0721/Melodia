package com.lin0721.linmusic.core.ui.skin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.ThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

private const val TAG = "SkinStore"
private const val STALE_STAGING_MS = 60 * 60 * 1000L

// 已安装皮肤按 id 各占一个目录；导入先解到 .staging，用户确认后再移入
class SkinStore(private val root: File, private val codec: SkinImageCodec? = null) {

    private val stagingRoot = File(root, ".staging")
    private val idPattern = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

    fun stage(input: InputStream): SkinStageResult =
        SkinPackage.stage(input, File(stagingRoot, UUID.randomUUID().toString()), codec)

    fun discard(staged: StagedSkin) {
        staged.directory.deleteRecursively()
    }

    fun isInstalled(id: String): Boolean = directoryOf(id)?.isDirectory == true

    // replace 为 false 且 id 已存在时另存为 id-2、id-3…
    fun install(staged: StagedSkin, replace: Boolean): Skin? {
        val id = if (replace || !isInstalled(staged.skin.id)) staged.skin.id else nextFreeId(staged.skin.id)
        val target = directoryOf(id) ?: return null
        return try {
            if (id != staged.skin.id) {
                val manifest = File(staged.directory, SkinPaths.MANIFEST)
                val root = SkinPackage.readManifest(manifest) ?: return null
                SkinPackage.writeManifest(manifest, SkinPackage.withId(root, id, "${staged.skin.name} ($id)"))
            }
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            if (!staged.directory.renameTo(target)) {
                staged.directory.copyRecursively(target, overwrite = true)
                staged.directory.deleteRecursively()
            }
            load(id)
        } catch (e: IOException) {
            AppLogger.w(TAG, "安装皮肤失败: $id", e)
            null
        }
    }

    fun list(): List<Skin> {
        clearStaleStaging()
        return root.listFiles()
            ?.filter { it.isDirectory && idPattern.matches(it.name) }
            ?.mapNotNull { load(it.name) }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    // 导入中途退出留下的临时目录；只清理一小时前的，避免误删正在预览的
    private fun clearStaleStaging() {
        val threshold = System.currentTimeMillis() - STALE_STAGING_MS
        stagingRoot.listFiles()?.filter { it.lastModified() < threshold }?.forEach { it.deleteRecursively() }
    }

    fun load(id: String): Skin? {
        val directory = directoryOf(id)?.takeIf { it.isDirectory } ?: return null
        val manifest = SkinPackage.readManifest(File(directory, SkinPaths.MANIFEST)) ?: return null
        return when (val result = SkinParser.parse(manifest, directory)) {
            is SkinParseResult.Success -> result.skin
            is SkinParseResult.Failure -> {
                AppLogger.w(TAG, "已安装皮肤无法解析: $id ${result.reason}")
                null
            }
        }
    }

    fun delete(id: String): Boolean = directoryOf(id)?.deleteRecursively() ?: false

    // id 来自偏好设置与皮肤文件，拼路径前先校验，防止越出皮肤目录
    private fun directoryOf(id: String): File? =
        if (id == BUILTIN_SKIN_ID || !idPattern.matches(id)) null else File(root, id)

    private fun nextFreeId(id: String): String {
        var index = 2
        while (true) {
            val candidate = "${id.take(60)}-$index"
            if (!isInstalled(candidate)) return candidate
            index++
        }
    }
}

// 当前生效的皮肤与皮肤管理：跟随设置里的 skinId，找不到或解析失败时为 null 即内置主题
class SkinRepository(
    private val store: SkinStore,
    private val settingsPreferences: SettingsPreferences
) {
    // 覆盖安装同 id 皮肤后 skinId 不变，靠 revision 触发重新读取
    private val revision = MutableStateFlow(0)

    private val _installed = MutableStateFlow<List<Skin>>(emptyList())
    val installed: StateFlow<List<Skin>> = _installed.asStateFlow()

    val activeSkin: Flow<Skin?> = combine(
        settingsPreferences.themeSettings.map { it.skinId }.distinctUntilChanged(),
        revision
    ) { id, _ -> store.load(id) }
        .flowOn(Dispatchers.IO)

    fun invalidate() {
        revision.update { it + 1 }
    }

    suspend fun load(id: String): Skin? = withContext(Dispatchers.IO) { store.load(id) }

    suspend fun refreshInstalled() {
        _installed.value = withContext(Dispatchers.IO) { store.list() }
    }

    // 调用方负责打开输入流，这里读完即关闭
    suspend fun stage(input: InputStream): SkinStageResult = withContext(Dispatchers.IO) { input.use { store.stage(it) } }

    suspend fun isInstalled(id: String): Boolean = withContext(Dispatchers.IO) { store.isInstalled(id) }

    suspend fun discard(staged: StagedSkin) = withContext(Dispatchers.IO) { store.discard(staged) }

    // 安装后立即应用；失败返回 null
    suspend fun installAndApply(staged: StagedSkin, replace: Boolean): Skin? {
        val skin = withContext(Dispatchers.IO) { store.install(staged, replace) } ?: return null
        settingsPreferences.saveThemeSkin(skin.id)
        invalidate()
        refreshInstalled()
        return skin
    }

    suspend fun apply(id: String) {
        settingsPreferences.saveThemeSkin(id)
    }

    // 删除正在使用的皮肤时先退回内置主题
    suspend fun delete(id: String) {
        if (settingsPreferences.themeSettings.first().skinId == id) settingsPreferences.saveThemeSkin(BUILTIN_SKIN_ID)
        withContext(Dispatchers.IO) { store.delete(id) }
        refreshInstalled()
    }

    // 导出当前生效的配色；写入失败返回 false
    suspend fun export(output: OutputStream, settings: ThemeSettings, skin: Skin?): Boolean = withContext(Dispatchers.IO) {
        val dark = resolveSkinPalette(true, settings.accentId(true), settings.customHex(true), skin)
        val light = resolveSkinPalette(false, settings.accentId(false), settings.customHex(false), skin)
        try {
            output.use { SkinPackage.export(it, skin?.id ?: DEFAULT_EXPORT_ID, skin?.name ?: DEFAULT_EXPORT_NAME, dark, light, skin) }
            true
        } catch (e: IOException) {
            AppLogger.w(TAG, "导出主题失败", e)
            false
        }
    }

    companion object {
        const val DEFAULT_EXPORT_ID = "my-theme"
        const val DEFAULT_EXPORT_NAME = "我的主题"
    }
}
