package com.lin0721.linmusic.core.ui.font

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.Serializable

const val FONT_ID_SKIN = "skin"
const val FONT_ID_FOLLOW = "follow"
const val FONT_ID_BUILTIN_DEFAULT = "builtin:default"
const val FONT_ID_BUILTIN_MONOSPACE = "builtin:monospace"
const val FONT_ID_BUILTIN_FIGTREE = "builtin:figtree"

enum class FontSource(val key: String) {
    IMPORTED("imported"),
    SKIN("skin"),
    SYSTEM("system"),
    BUILTIN("builtin");

    companion object {
        fun fromKey(key: String): FontSource? = entries.firstOrNull { it.key == key }
    }
}

enum class FontRole(val label: String) {
    GLOBAL("全局"),
    TITLE("标题"),
    LYRICS("歌词"),
    NUMBERS("数字")
}

@Serializable
data class FontFaceSpec(
    val file: String,
    val weight: Int = 400,
    val italic: Boolean = false
)

@Serializable
data class FontMetadataJson(
    val id: String,
    val name: String,
    val source: String,
    val faces: List<FontFaceSpec> = emptyList(),
    val hasChinese: Boolean = false,
    val totalBytes: Long = 0L,
    val skinId: String? = null,
    val systemFontName: String? = null
)

data class FontEntry(
    val id: String,
    val name: String,
    val source: FontSource,
    val faces: List<FontFaceSpec> = emptyList(),
    val hasChinese: Boolean = false,
    val totalBytes: Long = 0L,
    val skinId: String? = null,
    val systemFontName: String? = null
) {
    fun toJson(): FontMetadataJson = FontMetadataJson(
        id = id,
        name = name,
        source = source.key,
        faces = faces,
        hasChinese = hasChinese,
        totalBytes = totalBytes,
        skinId = skinId,
        systemFontName = systemFontName
    )

    companion object {
        fun fromJson(json: FontMetadataJson): FontEntry? {
            val source = FontSource.fromKey(json.source) ?: return null
            return FontEntry(
                id = json.id,
                name = json.name,
                source = source,
                faces = json.faces,
                hasChinese = json.hasChinese,
                totalBytes = json.totalBytes,
                skinId = json.skinId,
                systemFontName = json.systemFontName
            )
        }
    }
}

@Immutable
data class FontSettings(
    val globalFontId: String = FONT_ID_SKIN,
    val titleFontId: String = FONT_ID_FOLLOW,
    val lyricsFontId: String = FONT_ID_FOLLOW,
    val numbersFontId: String = FONT_ID_FOLLOW
) {
    fun fontIdFor(role: FontRole): String = when (role) {
        FontRole.GLOBAL -> globalFontId
        FontRole.TITLE -> titleFontId
        FontRole.LYRICS -> lyricsFontId
        FontRole.NUMBERS -> numbersFontId
    }
}

@Immutable
data class ResolvedFonts(
    val global: FontFamily? = null,
    val title: FontFamily? = null,
    val lyrics: FontFamily? = null,
    val numbers: FontFamily? = null
)

data class FontFallbackEvent(
    val role: FontRole,
    val fontId: String,
    val fontName: String?,
    val reason: String
)

val LocalMelodiaFonts = staticCompositionLocalOf { ResolvedFonts() }
