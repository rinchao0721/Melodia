package com.lin0721.linmusic.core.ui.skin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

private const val TAG = "SkinStore"

// 已安装皮肤按 id 各占一个目录；导入先解到 .staging，用户确认后再移入
class SkinStore(private val root: File) {

    private val stagingRoot = File(root, ".staging")
    private val idPattern = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

    fun stage(input: InputStream): SkinStageResult =
        SkinPackage.stage(input, File(stagingRoot, UUID.randomUUID().toString()))

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

    fun list(): List<Skin> = root.listFiles()
        ?.filter { it.isDirectory && idPattern.matches(it.name) }
        ?.mapNotNull { load(it.name) }
        ?.sortedBy { it.name }
        .orEmpty()

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

// 当前生效的皮肤：跟随设置里的 skinId，找不到或解析失败时为 null 即内置主题
class SkinRepository(
    private val store: SkinStore,
    settingsPreferences: SettingsPreferences
) {
    // 覆盖安装同 id 皮肤后 skinId 不变，靠 revision 触发重新读取
    private val revision = MutableStateFlow(0)

    val activeSkin: Flow<Skin?> = combine(
        settingsPreferences.themeSettings.map { it.skinId }.distinctUntilChanged(),
        revision
    ) { id, _ -> store.load(id) }
        .flowOn(Dispatchers.IO)

    fun invalidate() {
        revision.update { it + 1 }
    }

    suspend fun load(id: String): Skin? = withContext(Dispatchers.IO) { store.load(id) }
}
