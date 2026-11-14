package com.lin0721.linmusic.desktop.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.lin0721.linmusic.core.share.ui.ShareCardArt
import com.lin0721.linmusic.desktop.loadAppIcon
import com.lin0721.linmusic.desktop.ui.palette.FallbackBase
import com.lin0721.linmusic.desktop.ui.palette.extractShareColors
import com.lin0721.linmusic.desktop.ui.palette.shareColorsFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

private const val PALETTE_SIZE = 128
private const val PALETTE_CACHE_MAX = 64

private val DefaultPalette: List<Color> = shareColorsFrom(FallbackBase)
private val AppLogoPainter: Painter by lazy { loadAppIcon() }

// 聊天列表滚动复用同一封面时避免反复跑中位切分取色
private val PaletteCache: MutableMap<String, List<Color>> = Collections.synchronizedMap(
    object : LinkedHashMap<String, List<Color>>(PALETTE_CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Color>>?): Boolean =
            size > PALETTE_CACHE_MAX
    }
)

@Composable
internal fun rememberShareCardArt(
    coverUrl: String?,
    coverSize: Int
): ShareCardArt {
    val cleanUrl = coverUrl?.trim().orEmpty()
    val cachedPalette = remember(cleanUrl) { if (cleanUrl.isEmpty()) DefaultPalette else PaletteCache[cleanUrl] }
    var coverImage by remember(cleanUrl, coverSize) { mutableStateOf<ImageBitmap?>(null) }
    var palette by remember(cleanUrl) { mutableStateOf(cachedPalette ?: DefaultPalette) }

    LaunchedEffect(cleanUrl, coverSize) {
        if (cleanUrl.isEmpty()) return@LaunchedEffect
        launch {
            coverImage = ShareCardImageIO.loadImage(cleanUrl, coverSize)
        }
        if (cachedPalette == null) {
            launch {
                val small = ShareCardImageIO.loadImage(cleanUrl, PALETTE_SIZE) ?: return@launch
                if (small.width <= 0 || small.height <= 0) return@launch
                val pixels = IntArray(small.width * small.height)
                val extracted = runCatching {
                    small.readPixels(pixels)
                    withContext(Dispatchers.Default) { extractShareColors(pixels) }
                }.getOrNull()
                if (extracted != null) {
                    PaletteCache[cleanUrl] = extracted
                    palette = extracted
                }
            }
        }
    }

    val cover = remember(coverImage) { coverImage?.let { BitmapPainter(it) } }
    return remember(cover, palette) { ShareCardArt(cover, AppLogoPainter, palette) }
}
