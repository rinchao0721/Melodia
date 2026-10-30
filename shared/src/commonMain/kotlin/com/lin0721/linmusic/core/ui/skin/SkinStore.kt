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

// 已安装皮肤按 id 各占一个目录；导入先解到 .staging，用户确认后再移入；预设解到 .presets，与已安装互不覆盖
class SkinStore(
    private val root: File,
    private val codec: SkinImageCodec? = null,
    val presets: SkinPresets = SkinPresets(File(root, ".presets"), codec)
) {

    private val stagingRoot = File(root, ".staging")
    private val idPattern = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

    fun stage(input: InputStream): SkinStageResult =
        SkinPackage.stage(input, File(stagingRoot, UUID.randomUUID().toString()), codec)

    fun discard(staged: StagedSkin) {
        staged.directory.deleteRecursively()
    }

    fun isInstalled(id: String): Boolean = directoryOf(id)?.isDirectory == true

    // replace 为 false 且 id 已存在时另存为 id-2、id-3…；与预设同 id 时总是另存
    fun install(staged: StagedSkin, replace: Boolean): Skin? {
        val reserved = presets.isPreset(staged.skin.id)
        val id = if (!reserved && (replace || !isInstalled(staged.skin.id))) staged.skin.id else nextFreeId(staged.skin.id)
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
        if (presets.isPreset(id)) return presets.load(id)
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

    fun delete(id: String): Boolean = if (presets.isPreset(id)) false else directoryOf(id)?.deleteRecursively() ?: false

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

// 当前生效的皮肤与皮肤管理：按当前深浅取对应槽位的皮肤，找不到、解析失败或不支持该模式时为 null 即内置主题
class SkinRepository(
    private val store: SkinStore,
    private val settingsPreferences: SettingsPreferences
) {
    // 覆盖安装同 id 皮肤后 skinId 不变，靠 revision 触发重新读取
    private val revision = MutableStateFlow(0)

    private val _installed = MutableStateFlow<List<Skin>>(emptyList())
    val installed: StateFlow<List<Skin>> = _installed.asStateFlow()

    private val _presets = MutableStateFlow<List<Skin>>(emptyList())
    val presets: StateFlow<List<Skin>> = _presets.asStateFlow()

    // 跟随系统时用于判定当前槽位，由平台层同步系统深浅色
    private val systemDark = MutableStateFlow(true)

    val activeSkin: Flow<Skin?> = combine(
        settingsPreferences.themeSettings,
        systemDark,
        revision
    ) { settings, system, _ ->
        val isDark = settings.mode.isDark(system)
        settings.skinId(isDark) to isDark
    }
        .distinctUntilChanged()
        .map { (id, isDark) -> store.load(id)?.takeIf { it.supports(isDark) } }
        .flowOn(Dispatchers.IO)

    fun updateSystemDark(dark: Boolean) {
        systemDark.value = dark
    }

    fun invalidate() {
        revision.update { it + 1 }
    }

    suspend fun load(id: String, isDark: Boolean): Skin? = withContext(Dispatchers.IO) { store.load(id)?.takeIf { it.supports(isDark) } }

    suspend fun refreshInstalled() {
        withContext(Dispatchers.IO) {
            _presets.value = store.presets.list()
            _installed.value = store.list()
        }
    }

    // 调用方负责打开输入流，这里读完即关闭
    suspend fun stage(input: InputStream): SkinStageResult = withContext(Dispatchers.IO) { input.use { store.stage(it) } }

    suspend fun isInstalled(id: String): Boolean = withContext(Dispatchers.IO) { store.isInstalled(id) }

    suspend fun discard(staged: StagedSkin) = withContext(Dispatchers.IO) { store.discard(staged) }

    // 安装后应用到当前模式，皮肤不支持当前模式时只安装；失败返回 null
    suspend fun installAndApply(staged: StagedSkin, replace: Boolean, isDark: Boolean): Skin? {
        val skin = withContext(Dispatchers.IO) { store.install(staged, replace) } ?: return null
        if (skin.supports(isDark)) settingsPreferences.saveThemeSkin(isDark, skin.id)
        invalidate()
        refreshInstalled()
        return skin
    }

    suspend fun apply(id: String, isDark: Boolean) {
        settingsPreferences.saveThemeSkin(isDark, id)
    }

    fun isPreset(id: String): Boolean = store.presets.isPreset(id)

    // 删除正在使用的皮肤时先退回内置主题；预设不可删除
    suspend fun delete(id: String) {
        if (isPreset(id)) return
        val settings = settingsPreferences.themeSettings.first()
        listOf(true, false).filter { settings.skinId(it) == id }.forEach { settingsPreferences.saveThemeSkin(it, BUILTIN_SKIN_ID) }
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
