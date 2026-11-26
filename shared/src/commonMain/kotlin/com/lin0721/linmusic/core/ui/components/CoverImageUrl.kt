package com.lin0721.linmusic.core.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot

private const val KUGOU_TARGET_SIZE = "1080"
private const val QQ_TARGET_SIZE = "T002R800x800M000"
private const val DEFAULT_COVER_FILE_NAME = "Melodia_cover"
private const val MAX_COVER_FILE_NAME_LENGTH = 40

private val KUGOU_STDMUSIC_SIZE_REGEX = Regex("/stdmusic/(?:150|240|400)/")
private val QQ_COVER_SIZE_REGEX = Regex("T002R(\\d+)x(\\d+)M000")
private val INVALID_FILE_NAME_CHARS_REGEX = Regex("""[\\/:*?"<>|\s]+""")

@Immutable
data class CoverPreviewBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val isValid: Boolean get() = width > 1f && height > 1f
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

@Immutable
data class CoverPreviewRequest(
    val url: String,
    val title: String = "",
    val sourceBounds: CoverPreviewBounds? = null
)

fun LayoutCoordinates.toCoverPreviewBounds(): CoverPreviewBounds? {
    if (!isAttached) return null
    val rect = boundsInRoot()
    return if (rect.width > 1f && rect.height > 1f) {
        CoverPreviewBounds(rect.left, rect.top, rect.width, rect.height)
    } else {
        null
    }
}

// 去掉尺寸限制并提升酷狗/QQ 封面到高清规格
fun resolveHighResCoverUrl(url: String?): String {
    if (url.isNullOrBlank()) return ""
    val trimmed = url.trim()
    if (!isHttpUrl(trimmed)) return trimmed

    var resolved = stripParamQuery(trimmed)
    if (extractHost(resolved).contains("kugou.com")) {
        resolved = resolved
            .replace("{size}", KUGOU_TARGET_SIZE)
            .replace(KUGOU_STDMUSIC_SIZE_REGEX, "/stdmusic/$KUGOU_TARGET_SIZE/")
    }
    if (resolved.contains("T002R")) {
        resolved = QQ_COVER_SIZE_REGEX.replace(resolved) { match ->
            val width = match.groupValues[1].toIntOrNull() ?: 0
            val height = match.groupValues[2].toIntOrNull() ?: 0
            if (width < 800 || height < 800) QQ_TARGET_SIZE else match.value
        }
    }
    return resolved
}

fun sanitizeCoverFileName(title: String?): String {
    if (title.isNullOrBlank()) return DEFAULT_COVER_FILE_NAME
    val sanitized = title
        .replace(INVALID_FILE_NAME_CHARS_REGEX, "_")
        .trim('_')
        .take(MAX_COVER_FILE_NAME_LENGTH)
    return sanitized.ifEmpty { DEFAULT_COVER_FILE_NAME }
}

private fun isHttpUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)

private fun stripParamQuery(url: String): String {
    val fragmentIndex = url.indexOf('#')
    val withoutFragment = if (fragmentIndex >= 0) url.substring(0, fragmentIndex) else url
    val fragment = if (fragmentIndex >= 0) url.substring(fragmentIndex) else ""

    val queryIndex = withoutFragment.indexOf('?')
    if (queryIndex < 0) return url

    val base = withoutFragment.substring(0, queryIndex)
    val rawQuery = withoutFragment.substring(queryIndex + 1)
    val keptParams = rawQuery
        .split('&')
        .filter { it.isNotEmpty() && !it.startsWith("param=") }

    val rebuilt = if (keptParams.isEmpty()) base else "$base?${keptParams.joinToString("&")}"
    return rebuilt + fragment
}

private fun extractHost(url: String): String {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return ""
    val afterScheme = url.substring(schemeEnd + 3)
    val endIndex = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = if (endIndex >= 0) afterScheme.substring(0, endIndex) else afterScheme
    return authority.substringAfterLast('@').substringBefore(':').lowercase()
}
