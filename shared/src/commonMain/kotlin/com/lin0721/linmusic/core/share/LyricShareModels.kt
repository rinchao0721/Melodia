package com.lin0721.linmusic.core.share

enum class ShareCardKind(val label: String) {
    SONG("歌曲"),
    LYRIC("歌词")
}

enum class ShareCardStyle(val kind: ShareCardKind, val label: String) {
    COVER(ShareCardKind.SONG, "封面"),
    VINYL(ShareCardKind.SONG, "黑胶"),
    CLASSIC(ShareCardKind.LYRIC, "经典"),
    AURORA(ShareCardKind.LYRIC, "流光"),
    POSTER(ShareCardKind.LYRIC, "巨字海报"),
    SNAPSHOT(ShareCardKind.LYRIC, "播放快照"),
    SCROLL(ShareCardKind.LYRIC, "竖排书笺");

    companion object {
        fun of(kind: ShareCardKind): List<ShareCardStyle> = entries.filter { it.kind == kind }
    }
}

enum class ShareCardRatio(val label: String, val width: Int, val height: Int) {
    PORTRAIT("4:5", 4, 5),
    SQUARE("1:1", 1, 1),
    STORY("9:16", 9, 16);

    val aspect: Float get() = width.toFloat() / height
}

sealed interface ShareCardBackground {
    // 跟随样式自带的配色（多数取封面主色）
    data object Auto : ShareCardBackground

    // 封面取色结果中的第 index 个
    data class Palette(val index: Int) : ShareCardBackground

    data class Solid(val argb: Long, val label: String) : ShareCardBackground

    data object BlurCover : ShareCardBackground

    companion object {
        const val PALETTE_SIZE = 3

        val presets: List<Solid> = listOf(
            Solid(0xFF121212, "黑色"),
            Solid(0xFFC62F2F, "网易红"),
            Solid(0xFF1B2A41, "深蓝"),
            Solid(0xFFF3EEE3, "米白")
        )
    }
}

data class ShareLyricLine(val text: String, val translation: String? = null)

data class ShareCardContent(
    val title: String,
    val artist: String,
    val lines: List<ShareLyricLine> = emptyList(),
    // 播放快照用：选中段前后各一句
    val lineBefore: String? = null,
    val lineAfter: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0
)
