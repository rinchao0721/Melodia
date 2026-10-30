package com.lin0721.linmusic.core.ui.skin

import com.lin0721.linmusic.core.log.AppLogger
import java.io.File
import java.io.IOException
import java.io.InputStream

private const val TAG = "SkinPresets"
private const val RESOURCE_DIR = "/skins"
private const val INDEX_FILE = "index.txt"
private const val VERSION_FILE = ".version"

const val PRESET_SKIN_ID_PREFIX = "melodia-"

// 随应用分发的预设皮肤：包放在 resources/skins，背景图要落盘才能解码，首次使用时解到 root/<id>
class SkinPresets(
    private val root: File,
    private val codec: SkinImageCodec? = null,
    private val source: (String) -> InputStream? = { name -> SkinPresets::class.java.getResourceAsStream("$RESOURCE_DIR/$name") }
) {
    private data class Entry(val id: String, val version: String)

    // index.txt 每行“id 版本”；版本变化时重新解压，应用升级后预设随之更新
    private val entries: List<Entry> by lazy { readIndex() }

    val ids: List<String> get() = entries.map { it.id }

    fun isPreset(id: String): Boolean = entries.any { it.id == id }

    fun list(): List<Skin> = entries.mapNotNull { load(it.id) }

    fun load(id: String): Skin? {
        val entry = entries.firstOrNull { it.id == id } ?: return null
        val directory = ensureUnpacked(entry) ?: return null
        val manifest = SkinPackage.readManifest(File(directory, SkinPaths.MANIFEST)) ?: return null
        return when (val result = SkinParser.parse(manifest, directory)) {
            is SkinParseResult.Success -> result.skin
            is SkinParseResult.Failure -> {
                AppLogger.w(TAG, "预设皮肤无法解析: $id ${result.reason}")
                null
            }
        }
    }

    private fun readIndex(): List<Entry> = try {
        source(INDEX_FILE)?.bufferedReader()?.use { reader ->
            reader.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val parts = line.split(Regex("\\s+"))
                    val id = parts.getOrNull(0) ?: return@mapNotNull null
                    if (!id.startsWith(PRESET_SKIN_ID_PREFIX)) {
                        AppLogger.w(TAG, "预设皮肤 id 缺少前缀: $id")
                        return@mapNotNull null
                    }
                    Entry(id, parts.getOrNull(1).orEmpty())
                }
                .distinctBy { it.id }
        }.orEmpty()
    } catch (e: IOException) {
        AppLogger.w(TAG, "读取预设皮肤列表失败", e)
        emptyList()
    }

    // 当前皮肤与设置页列表可能同时读取，解压需串行
    @Synchronized
    private fun ensureUnpacked(entry: Entry): File? {
        val directory = File(root, entry.id)
        val versionFile = File(directory, VERSION_FILE)
        val unpacked = File(directory, SkinPaths.MANIFEST).isFile &&
            runCatching { versionFile.readText() }.getOrNull() == entry.version
        if (unpacked) return directory
        val input = try {
            source("${entry.id}.$SKIN_FILE_EXTENSION")
        } catch (e: IOException) {
            AppLogger.w(TAG, "打开预设皮肤失败: ${entry.id}", e)
            null
        } ?: run {
            AppLogger.w(TAG, "缺少预设皮肤包: ${entry.id}")
            return null
        }
        return when (val result = input.use { SkinPackage.stage(it, directory, codec) }) {
            is SkinStageResult.Failure -> {
                AppLogger.w(TAG, "预设皮肤解压失败: ${entry.id} ${result.reason}")
                null
            }
            is SkinStageResult.Success -> {
                if (result.staged.skin.id != entry.id) {
                    AppLogger.w(TAG, "预设皮肤 id 与列表不一致: ${entry.id} ${result.staged.skin.id}")
                    directory.deleteRecursively()
                    return null
                }
                try {
                    versionFile.writeText(entry.version)
                } catch (e: IOException) {
                    AppLogger.w(TAG, "写入预设皮肤版本失败: ${entry.id}", e)
                }
                directory
            }
        }
    }
}
