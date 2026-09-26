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
