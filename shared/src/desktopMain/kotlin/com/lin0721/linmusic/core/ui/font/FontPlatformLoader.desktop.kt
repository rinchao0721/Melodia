package com.lin0721.linmusic.core.ui.font

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.platform.SystemFont
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import java.awt.GraphicsEnvironment
import java.io.File

internal actual object FontPlatformLoader {

    // Compose 的 Font(ByteArray) 延迟到绘制时才解析，试加载必须直接交给 Skia
    actual fun testLoad(file: File, weight: Int, italic: Boolean): Boolean {
        if (!file.isFile) return false
        return try {
            val data = Data.makeFromBytes(file.readBytes())
            try {
                val typeface = FontMgr.default.makeFromData(data)
                typeface?.close()
                typeface != null
            } finally {
                data.close()
            }
        } catch (_: Throwable) {
            false
        }
    }

    @OptIn(ExperimentalTextApi::class)
    actual fun buildFontFamily(entry: FontEntry, directory: File?): FontFamily? {
        return when (entry.source) {
            FontSource.BUILTIN -> when (entry.id) {
                FONT_ID_BUILTIN_MONOSPACE -> FontFamily.Monospace
                else -> buildFromFiles(entry, directory)
            }
            FontSource.SYSTEM -> {
                val name = entry.systemFontName ?: return null
                if (!isSystemFontAvailable(name)) return null
                try {
                    FontFamily(SystemFont(name))
                } catch (_: Throwable) {
                    null
                }
            }
            FontSource.IMPORTED, FontSource.SKIN -> buildFromFiles(entry, directory)
        }
    }

    private fun buildFromFiles(entry: FontEntry, directory: File?): FontFamily? {
        val dir = directory?.takeIf { it.isDirectory } ?: return null
        if (entry.faces.isEmpty()) return null
        val fonts = entry.faces.mapNotNull { face ->
            val file = File(dir, face.file)
            if (!file.isFile) return null
            try {
                // Skia 按 identity 缓存字形，文件被覆盖后需换 identity
                Font(
                    identity = "${entry.id}:${face.file}:${face.weight}:${face.italic}:${file.length()}:${file.lastModified()}",
                    data = file.readBytes(),
                    weight = FontWeight(face.weight.coerceIn(1, 1000)),
                    style = if (face.italic) FontStyle.Italic else FontStyle.Normal
                )
            } catch (_: Throwable) {
                return null
            }
        }
        return if (fonts.isEmpty()) null else FontFamily(fonts)
    }

    actual fun listSystemFontNames(): List<String> = try {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .availableFontFamilyNames
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    } catch (_: Throwable) {
        emptyList()
    }

    actual fun isSystemFontAvailable(name: String): Boolean {
        if (name.isBlank()) return false
        return listSystemFontNames().any { it.equals(name, ignoreCase = false) }
    }
}
