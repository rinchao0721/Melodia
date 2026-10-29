package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max

const val SKIN_FILE_EXTENSION = "melodiaskin"

data class StagedSkin(
    val skin: Skin,
    val warnings: List<String>,
    val fixes: List<SkinContrastFix>,
    val directory: File
)

sealed interface SkinStageResult {
    data class Success(val staged: StagedSkin) : SkinStageResult
    data class Failure(val reason: String) : SkinStageResult
}

// 皮肤包读写：解包时按白名单落盘并限制体积，不信任 zip 头里声明的大小
object SkinPackage {

    private const val MAX_ENTRIES = 32
    private const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
    private const val MAX_ASSET_BYTES = 10L * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 256L * 1024
    private const val BUFFER_SIZE = 8 * 1024

    private val json = Json { prettyPrint = true }

    private class LimitExceeded(message: String) : Exception(message)

    // codec 为 null 时跳过背景图尺寸检查与按背景图修正对比度
    fun stage(input: InputStream, directory: File, codec: SkinImageCodec? = null): SkinStageResult {
        directory.deleteRecursively()
        if (!directory.mkdirs()) return SkinStageResult.Failure("无法创建临时目录")
        val result = try {
            unpack(input, directory, codec)
        } catch (e: LimitExceeded) {
            SkinStageResult.Failure(e.message ?: "皮肤包超出大小限制")
        } catch (e: ZipException) {
            SkinStageResult.Failure("不是有效的皮肤包")
        } catch (e: IllegalArgumentException) {
            SkinStageResult.Failure("不是有效的皮肤包")
        } catch (e: IOException) {
            SkinStageResult.Failure("读取皮肤包失败：${e.message}")
        }
        if (result is SkinStageResult.Failure) directory.deleteRecursively()
        return result
    }

    private fun unpack(input: InputStream, directory: File, codec: SkinImageCodec?): SkinStageResult {
        val warnings = mutableListOf<String>()
        val seen = HashSet<String>()
        var totalBytes = 0L
        var entryCount = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ENTRIES) throw LimitExceeded("皮肤包里的文件超过 $MAX_ENTRIES 个")
                val name = entry.name
                if (name.contains('\\') || name.startsWith("/") || name.contains(':') || name.split('/').any { it == ".." }) {
                    return SkinStageResult.Failure("皮肤包含有非法路径：$name")
                }
                if (entry.isDirectory) continue
                val limit = when {
                    name == SkinPaths.MANIFEST -> MAX_MANIFEST_BYTES
                    SkinPaths.isAsset(name) -> MAX_ASSET_BYTES
                    else -> {
                        warnings += "已忽略不支持的文件：$name"
                        continue
                    }
                }
                if (!seen.add(name)) return SkinStageResult.Failure("皮肤包里有重复文件：$name")
                val target = File(directory, name)
                target.parentFile?.mkdirs()
                val written = target.outputStream().use { out -> copyLimited(zip, out, limit, MAX_TOTAL_BYTES - totalBytes, name) }
                totalBytes += written
            }
        }
        val manifest = File(directory, SkinPaths.MANIFEST)
        if (!manifest.isFile) return SkinStageResult.Failure("皮肤包里缺少 ${SkinPaths.MANIFEST}")
        val root = readManifest(manifest) ?: return SkinStageResult.Failure("${SkinPaths.MANIFEST} 不是有效的 JSON 对象")
        val parsed = when (val result = SkinParser.parse(root, directory)) {
            is SkinParseResult.Failure -> return SkinStageResult.Failure(result.reason)
            is SkinParseResult.Success -> result
        }
        val background = parsed.skin.background
        val average = if (background != null && codec != null) {
            val file = File(directory, background.image)
            val size = codec.readSize(file) ?: return SkinStageResult.Failure("无法识别背景图 ${background.image}")
            if (max(size.width, size.height) > SkinImages.MAX_IMAGE_SIDE) {
                return SkinStageResult.Failure("背景图最长边超过 ${SkinImages.MAX_IMAGE_SIDE}px")
            }
            SkinImages.averageColor(file, codec) ?: return SkinStageResult.Failure("无法解码背景图 ${background.image}")
        } else {
            null
        }
        val (fixedSkin, fixes) = SkinContrast.fix(parsed.skin, average)
        if (fixes.isNotEmpty()) writeManifest(manifest, withColors(root, fixedSkin.darkColors, fixedSkin.lightColors))
        return SkinStageResult.Success(StagedSkin(fixedSkin, warnings + parsed.warnings, fixes, directory))
    }

    private fun copyLimited(input: InputStream, output: OutputStream, entryLimit: Long, totalRemaining: Long, name: String): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            copied += read
            if (copied > entryLimit) throw LimitExceeded("$name 超过 ${entryLimit / 1024 / 1024}MB")
            if (copied > totalRemaining) throw LimitExceeded("皮肤包解压后超过 ${MAX_TOTAL_BYTES / 1024 / 1024}MB")
            output.write(buffer, 0, read)
        }
        return copied
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

    fun writeManifest(file: File, root: JsonObject) {
        file.writeText(json.encodeToString(JsonObject.serializer(), root))
    }

    fun withColors(root: JsonObject, dark: Map<PaletteToken, Color>, light: Map<PaletteToken, Color>): JsonObject {
        val colors = buildJsonObject {
            listOf(SkinMode.DARK to dark, SkinMode.LIGHT to light).forEach { (mode, map) ->
                if (map.isEmpty()) return@forEach
                put(mode.key, buildJsonObject { map.forEach { (token, color) -> put(token.key, JsonPrimitive(SkinParser.encodeColor(color))) } })
            }
        }
        return JsonObject(root + ("colors" to colors))
    }

    fun withId(root: JsonObject, id: String, name: String? = null): JsonObject {
        val patched = root + ("id" to JsonPrimitive(id))
        return JsonObject(if (name == null) patched else patched + ("name" to JsonPrimitive(name)))
    }

    // 导出当前主题：两种模式写完整色板；正在使用皮肤时沿用其背景、样式、布局与资源
    fun export(
        output: OutputStream,
        id: String,
        name: String,
        dark: MelodiaPalette,
        light: MelodiaPalette,
        source: Skin?
    ) {
        val sourceRoot = source?.directory?.let { readManifest(File(it, SkinPaths.MANIFEST)) }
        val base = sourceRoot ?: buildJsonObject {
            put("format", JsonPrimitive(SKIN_FORMAT_VERSION))
        }
        val modes = source?.modes ?: SkinMode.entries.toSet()
        val darkColors = if (SkinMode.DARK in modes) dark.toTokenMap() else emptyMap()
        val lightColors = if (SkinMode.LIGHT in modes) light.toTokenMap() else emptyMap()
        val modeArray = buildJsonArray { modes.forEach { add(JsonPrimitive(it.key)) } }
        val root = JsonObject(
            withColors(withId(base, id, name), darkColors, lightColors) +
                ("format" to JsonPrimitive(SKIN_FORMAT_VERSION)) +
                ("modes" to modeArray)
        )
        val assets = source?.directory?.let { File(it, SkinPaths.ASSETS_PREFIX) }?.listFiles()
            ?.filter { it.isFile && SkinPaths.isAsset(SkinPaths.ASSETS_PREFIX + it.name) }
            .orEmpty()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(SkinPaths.MANIFEST))
            zip.write(json.encodeToString(JsonObject.serializer(), root).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            assets.forEach { file ->
                zip.putNextEntry(ZipEntry(SkinPaths.ASSETS_PREFIX + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
