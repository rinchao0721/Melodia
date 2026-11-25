package com.lin0721.linmusic.core.ui.font

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.Locale

private const val TAG = "FontStore"
private const val METADATA_FILE = "font.json"
const val MAX_FONT_FILE_BYTES = 30L * 1024 * 1024

data class FontImportFailure(
    val fileName: String,
    val reason: String
)

data class FontImportResult(
    val imported: List<FontEntry>,
    val failures: List<FontImportFailure>
)

// 字体库本地存储：fonts/<id>/ 下存放字体文件与 font.json
class FontStore(
    private val root: File,
    private val fileValidator: (File, Int, Boolean) -> Boolean = FontPlatformLoader::testLoad
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val idPattern = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

    fun directoryOf(id: String): File? =
        if (idPattern.matches(id)) File(root, id) else null

    fun list(): List<FontEntry> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && idPattern.matches(it.name) }
            ?.mapNotNull { readEntry(it) }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            .orEmpty()
    }

    fun get(id: String): FontEntry? {
        val dir = directoryOf(id)?.takeIf { it.isDirectory } ?: return null
        return readEntry(dir)
    }

    fun delete(id: String): Boolean {
        val dir = directoryOf(id) ?: return false
        if (!dir.exists()) return false
        return dir.deleteRecursively()
    }

    fun importFiles(files: List<File>): FontImportResult {
        if (files.isEmpty()) return FontImportResult(emptyList(), emptyList())
        if (!root.exists() && !root.mkdirs()) {
            return FontImportResult(
                emptyList(),
                files.map { FontImportFailure(it.name, "无法创建字体存储目录") }
            )
        }

        val failures = mutableListOf<FontImportFailure>()
        val validated = mutableListOf<ValidatedFace>()

        for (file in files) {
            if (!file.isFile) {
                failures += FontImportFailure(file.name, "文件不存在")
                continue
            }
            val size = file.length()
            if (size <= 0L) {
                failures += FontImportFailure(file.name, "文件为空")
                continue
            }
            if (size > MAX_FONT_FILE_BYTES) {
                failures += FontImportFailure(file.name, "单个字体文件不能超过 30MB")
                continue
            }
            val parsed = when (val result = SfntReader.read(file)) {
                is SfntParseResult.Failure -> {
                    failures += FontImportFailure(file.name, result.reason)
                    continue
                }
                is SfntParseResult.Success -> result.metadata
            }
            if (!fileValidator(file, parsed.weight, parsed.isItalic)) {
                failures += FontImportFailure(file.name, "字体文件无法被系统加载")
                continue
            }
            validated += ValidatedFace(file = file, size = size, metadata = parsed)
        }

        if (validated.isEmpty()) {
            return FontImportResult(emptyList(), failures)
        }

        // 按家族名归组（忽略首尾空白与大小写差异）
        val groups = validated.groupBy { it.metadata.familyName.trim().lowercase(Locale.ROOT) }
        val imported = mutableListOf<FontEntry>()

        for ((_, groupFaces) in groups) {
            val displayName = groupFaces.first().metadata.familyName.trim().ifBlank { "Custom Font" }
            val id = nextFreeId(slugify(displayName))
            val targetDir = directoryOf(id) ?: continue
            if (!targetDir.mkdirs()) {
                groupFaces.forEach { failures += FontImportFailure(it.file.name, "无法创建字体目录") }
                continue
            }

            val savedFaces = mutableListOf<FontFaceSpec>()
            var totalBytes = 0L
            var hasChinese = false

            for (item in groupFaces) {
                val safeFileName = uniqueFileName(targetDir, sanitizeFileName(item.file.name))
                val dest = File(targetDir, safeFileName)
                try {
                    item.file.copyTo(dest, overwrite = true)
                    savedFaces += FontFaceSpec(
                        file = safeFileName,
                        weight = item.metadata.weight,
                        italic = item.metadata.isItalic
                    )
                    totalBytes += item.size
                    if (item.metadata.hasChinese) hasChinese = true
                } catch (e: IOException) {
                    dest.delete()
                    failures += FontImportFailure(item.file.name, "复制字体文件失败：${e.message}")
                }
            }

            if (savedFaces.isEmpty()) {
                targetDir.deleteRecursively()
                continue
            }

            val entry = FontEntry(
                id = id,
                name = displayName,
                source = FontSource.IMPORTED,
                faces = savedFaces,
                hasChinese = hasChinese,
                totalBytes = totalBytes
            )
            if (writeEntry(targetDir, entry)) {
                imported += entry
            } else {
                targetDir.deleteRecursively()
                groupFaces.forEach { failures += FontImportFailure(it.file.name, "写入字体元数据失败") }
            }
        }

        return FontImportResult(imported, failures)
    }

    // 登记皮肤内嵌字体；同 skinId 先清理旧记录再写入
    fun registerSkinFont(
        skinId: String,
        skinName: String,
        faces: List<FontFaceSpec>,
        skinDirectory: File
    ): FontEntry? {
        if (faces.isEmpty()) return null
        removeSkinFonts(skinId)
        if (!root.exists() && !root.mkdirs()) return null

        val id = skinFontId(skinId)
        val targetDir = directoryOf(id) ?: return null
        if (!targetDir.mkdirs()) return null

        val savedFaces = mutableListOf<FontFaceSpec>()
        var totalBytes = 0L
        var hasChinese = false

        for (face in faces) {
            val sourceFile = File(skinDirectory, face.file)
            if (!sourceFile.isFile) continue
            val size = sourceFile.length()
            if (size <= 0L || size > MAX_FONT_FILE_BYTES) continue
            val sfnt = SfntReader.read(sourceFile)
            if (sfnt is SfntParseResult.Failure) continue
            if (!fileValidator(sourceFile, face.weight, face.italic)) continue

            val safeName = uniqueFileName(targetDir, sanitizeFileName(sourceFile.name))
            val dest = File(targetDir, safeName)
            try {
                sourceFile.copyTo(dest, overwrite = true)
                savedFaces += FontFaceSpec(
                    file = safeName,
                    weight = face.weight,
                    italic = face.italic
                )
                totalBytes += size
                if (sfnt is SfntParseResult.Success && sfnt.metadata.hasChinese) {
                    hasChinese = true
                }
            } catch (e: IOException) {
                dest.delete()
                AppLogger.w(TAG, "复制皮肤字体失败: ${face.file}", e)
            }
        }

        if (savedFaces.isEmpty()) {
            targetDir.deleteRecursively()
            return null
        }

        val entry = FontEntry(
            id = id,
            name = "$skinName 随包字体",
            source = FontSource.SKIN,
            faces = savedFaces,
            hasChinese = hasChinese,
            totalBytes = totalBytes,
            skinId = skinId
        )
        return if (writeEntry(targetDir, entry)) entry else {
            targetDir.deleteRecursively()
            null
        }
    }

    fun removeSkinFonts(skinId: String): Boolean {
        val id = skinFontId(skinId)
        var removed = delete(id)
        list().filter { it.source == FontSource.SKIN && it.skinId == skinId }.forEach {
            if (delete(it.id)) removed = true
        }
        return removed
    }

    fun skinFontId(skinId: String): String {
        val clean = skinId.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]"), "-").trim('-')
        val base = if (clean.isEmpty()) "skin" else "skin-$clean"
        return base.take(64)
    }

    private fun readEntry(dir: File): FontEntry? {
        val file = File(dir, METADATA_FILE)
        if (!file.isFile) return null
        return try {
            val raw = file.readText()
            val decoded = json.decodeFromString(FontMetadataJson.serializer(), raw)
            FontEntry.fromJson(decoded)
        } catch (e: IOException) {
            AppLogger.w(TAG, "读取字体元数据失败: ${dir.name}", e)
            null
        } catch (e: SerializationException) {
            AppLogger.w(TAG, "解析字体元数据失败: ${dir.name}", e)
            null
        }
    }

    private fun writeEntry(dir: File, entry: FontEntry): Boolean = try {
        val encoded = json.encodeToString(FontMetadataJson.serializer(), entry.toJson())
        File(dir, METADATA_FILE).writeText(encoded)
        true
    } catch (e: IOException) {
        AppLogger.w(TAG, "写入字体元数据失败: ${entry.id}", e)
        false
    }

    private fun slugify(name: String): String {
        val ascii = name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return if (ascii.isEmpty()) "font" else ascii.take(48)
    }

    private fun nextFreeId(base: String): String {
        if (directoryOf(base)?.exists() != true) return base
        var index = 2
        while (true) {
            val candidate = "${base.take(56)}-$index"
            if (directoryOf(candidate)?.exists() != true) return candidate
            index++
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.ifBlank { "font.ttf" }
    }

    private fun uniqueFileName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = "$stem-$index$ext"
            if (!File(dir, candidate).exists()) return candidate
            index++
        }
    }

    private data class ValidatedFace(
        val file: File,
        val size: Long,
        val metadata: SfntMetadata
    )
}
