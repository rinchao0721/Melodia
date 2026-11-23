package com.lin0721.linmusic.core.source

// 音源解析结果
data class SourceResult(
    val url: String,
    val platform: MusicPlatform,
    val quality: String = "",
    val pluginId: String? = null,
    val isTrial: Boolean = false,
    // 命中的音源插件名，非插件来源为空
    val sourceName: String? = null
)

// 换源得到的直链是否为试听/口播提示音，与官方接口 isTrialAudio 的 URL 判定保持一致
fun isTrialPlayUrl(url: String): Boolean = url.contains("musicrep-ts", ignoreCase = true)
