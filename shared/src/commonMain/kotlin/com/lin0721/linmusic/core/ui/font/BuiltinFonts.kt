package com.lin0721.linmusic.core.ui.font

import com.lin0721.linmusic.core.log.AppLogger
import java.io.File
import java.io.IOException
import java.io.InputStream

private const val TAG = "BuiltinFonts"
private const val RESOURCE_DIR = "/fonts/figtree"
private const val VERSION_FILE = ".version"
private const val FIGTREE_VERSION = "1"

private val FIGTREE_FACES = listOf(
    FontFaceSpec(file = "Figtree-Regular.ttf", weight = 400, italic = false),
    FontFaceSpec(file = "Figtree-Medium.ttf", weight = 500, italic = false),
    FontFaceSpec(file = "Figtree-SemiBold.ttf", weight = 600, italic = false),
    FontFaceSpec(file = "Figtree-Bold.ttf", weight = 700, italic = false)
)

data class BuiltinFontAsset(
    val entry: FontEntry,
    val directory: File
)

// 随应用分发的内置字体：首次使用时将资源解到 <应用数据>/fonts/builtin/figtree/，版本变化时重新解压
class BuiltinFonts(
    private val root: File,
    private val version: String = FIGTREE_VERSION,
    private val source: (String) -> InputStream? = { name ->
        BuiltinFonts::class.java.getResourceAsStream("$RESOURCE_DIR/$name")
    }
) {
    private val figtreeEntry = FontEntry(
        id = FONT_ID_BUILTIN_FIGTREE,
        name = "Figtree",
        source = FontSource.BUILTIN,
        faces = FIGTREE_FACES
    )

    @Synchronized
    fun figtree(): BuiltinFontAsset? {
        val directory = File(root, "figtree")
        val versionFile = File(directory, VERSION_FILE)
        val unpacked = runCatching { versionFile.readText() }.getOrNull() == version &&
            FIGTREE_FACES.all { face ->
                val file = File(directory, face.file)
                file.isFile && file.length() > 0L
            }
        if (unpacked) {
            return BuiltinFontAsset(entry = figtreeEntry, directory = directory)
        }

        if (!directory.exists() && !directory.mkdirs()) {
            AppLogger.w(TAG, "无法创建内置字体目录: ${directory.path}")
            return null
        }

        for (face in FIGTREE_FACES) {
            val input = try {
                source(face.file)
            } catch (e: IOException) {
                AppLogger.w(TAG, "打开内置字体资源失败: ${face.file}", e)
                null
            } ?: run {
                AppLogger.w(TAG, "缺少内置字体资源: ${face.file}")
                versionFile.delete()
                return null
            }

            val target = File(directory, face.file)
            try {
                input.use { stream ->
                    target.outputStream().use { out ->
                        stream.copyTo(out)
                    }
                }
                if (target.length() <= 0L) {
                    AppLogger.w(TAG, "内置字体资源为空: ${face.file}")
                    target.delete()
                    versionFile.delete()
                    return null
                }
            } catch (e: IOException) {
                AppLogger.w(TAG, "解压内置字体失败: ${face.file}", e)
                target.delete()
                versionFile.delete()
                return null
            }
        }

        try {
            versionFile.writeText(version)
        } catch (e: IOException) {
            AppLogger.w(TAG, "写入内置字体版本失败", e)
            return null
        }

        return BuiltinFontAsset(entry = figtreeEntry, directory = directory)
    }
}
