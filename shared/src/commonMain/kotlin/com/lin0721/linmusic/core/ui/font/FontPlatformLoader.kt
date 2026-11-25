package com.lin0721.linmusic.core.ui.font

import androidx.compose.ui.text.font.FontFamily
import java.io.File

internal expect object FontPlatformLoader {
    fun testLoad(file: File, weight: Int, italic: Boolean): Boolean
    fun buildFontFamily(entry: FontEntry, directory: File?): FontFamily?
    fun listSystemFontNames(): List<String>
    fun isSystemFontAvailable(name: String): Boolean
}
