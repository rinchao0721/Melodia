package com.lin0721.linmusic.core.model

// 音质代码转展示文案，被 settings/player 等多个域的音质相关 UI 复用。
fun getQualityDisplayName(quality: String): String {
    return when (quality) {
        "standard" -> "标准"
        "higher" -> "较高"
        "exhigh" -> "极高"
        "lossless" -> "无损 (FLAC)"
        "hires" -> "Hi-Res"
        "jyeffect" -> "高清环绕声"
        "sky" -> "沉浸环绕声"
        "jymaster" -> "超清母带"
        else -> quality
    }
}

// 音质代码转纯英文短标签，用于播放页等空间紧凑处；未知档位返回 null 以便隐藏
fun getQualityBadgeLabel(quality: String): String? {
    return when (quality) {
        "standard" -> "Standard"
        "higher" -> "Higher"
        "exhigh" -> "ExHigh"
        "lossless" -> "Lossless"
        "hires" -> "Hi-Res"
        "jyeffect" -> "Surround"
        "sky" -> "Immersive"
        "jymaster" -> "Master"
        else -> null
    }
}

// 可选下载音质档位，从低到高排列
val DOWNLOAD_QUALITY_LEVELS = listOf(
    "standard", "higher", "exhigh", "lossless", "hires", "jyeffect", "sky", "jymaster"
)

// 音质档位高低排序，未知档位返回 -1
fun qualityRank(level: String?): Int = level?.let(DOWNLOAD_QUALITY_LEVELS::indexOf) ?: -1

// 歌曲列表最高音质徽标；colorArgb 为空表示跟随主题次要文字色
enum class QualityBadge(val label: String, val colorArgb: Long?) {
    Standard("标准", null),
    ExHigh("极高", 0xFF4A90E2),
    Lossless("无损", 0xFF2BAE66),
    HiRes("Hi-Res", 0xFF8E6CEF),
    Master("超清母带", 0xFFC9A227)
}

// 由各档位文件推断最高音质；有 hr 只能说明至少 Hi-Res，母带、杜比等更高档位仍需补查
fun Track.inferredMaxBrLevel(): String? = when {
    hr != null -> "hires"
    sq != null -> "lossless"
    (h?.br ?: 0) >= 320_000 -> "exhigh"
    h != null || m != null || l != null -> "standard"
    else -> null
}

// 推断结果是否已是最终档位，不用再补查
fun isFinalInferredLevel(level: String?): Boolean = level != null && level != "hires"

// 项目播不了的档位降级显示：环绕声、杜比必有无损，不保证有 Hi-Res
fun qualityBadgeOf(maxBrLevel: String?): QualityBadge? = when (maxBrLevel) {
    "jymaster" -> QualityBadge.Master
    "dolby", "sky", "jyeffect" -> QualityBadge.Lossless
    "hires" -> QualityBadge.HiRes
    "lossless" -> QualityBadge.Lossless
    "exhigh" -> QualityBadge.ExHigh
    "higher", "standard" -> QualityBadge.Standard
    else -> null
}
