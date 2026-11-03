package com.lin0721.linmusic.core.player

// 播放来源的标题与名称；名称为空时只显示标题
fun playSourceText(playContext: String?, podcastRadioName: String): Pair<String, String?> = when (playContext) {
    null -> "NOW PLAYING" to null
    "搜索" -> "播放自" to "搜索"
    "每日推荐" -> "播放自" to "每日推荐"
    "历史日推" -> "播放自" to "历史日推"
    "intelligence" -> "播放自" to "心动模式"
    PlaybackController.CONTEXT_PODCAST -> "播放自播客" to podcastRadioName.ifBlank { "播客" }
    else -> "播放自歌单" to playContext
}
