package com.lin0721.linmusic.core.ui.font

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File

internal actual object FontPlatformLoader {

    // Compose 的 Font(File) 延迟到绘制时才解析，试加载必须直接走 Typeface
    actual fun testLoad(file: File, weight: Int, italic: Boolean): Boolean {
        if (!file.isFile) return false
        return try {
            Typeface.Builder(file).build() != null
        } catch (_: Throwable) {
            false
        }
    }

    actual fun buildFontFamily(entry: FontEntry, directory: File?): FontFamily? {
        return when (entry.source) {
            FontSource.BUILTIN -> when (entry.id) {
                FONT_ID_BUILTIN_MONOSPACE -> FontFamily.Monospace
                else -> buildFromFiles(entry, directory)
            }
            FontSource.SYSTEM -> null
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
                Font(
                    file = file,
                    weight = FontWeight(face.weight.coerceIn(1, 1000)),
                    style = if (face.italic) FontStyle.Italic else FontStyle.Normal
                )
            } catch (_: Throwable) {
                return null
            }
        }
        return if (fonts.isEmpty()) null else FontFamily(fonts)
    }

    actual fun listSystemFontNames(): List<String> = emptyList()

    actual fun isSystemFontAvailable(name: String): Boolean = false
}
