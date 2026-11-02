package com.lin0721.linmusic.core.player

/**
 * 心动模式对当前播放内容的可用性。
 *
 * 心动模式不是普通播放模式：它会以当前曲目为种子、以「我喜欢的音乐」为推荐池，
 * 重新生成一份队列。因此只有普通网易云在线歌曲能作为种子；播客、云盘、本地文件
 * 与第三方音源都没有可用的在线曲目 id，不能开启。
 *
 * 这里只判断内容与播放上下文；登录态由真正发请求前单独检查并提示。
 */
fun canUseIntelligenceAsSeed(
    item: QueueItem?,
    isPodcast: Boolean,
    playContext: String?
): Boolean = intelligenceSeedBlockReason(item, isPodcast, playContext) == null

/** 返回不能开启心动模式的原因；null 表示当前内容可以作为种子。 */
fun intelligenceSeedBlockReason(
    item: QueueItem?,
    isPodcast: Boolean,
    playContext: String?
): String? {
    if (isPodcast || playContext == PlaybackController.CONTEXT_PODCAST || (item?.radioId ?: 0L) > 0L) {
        return "播客节目不支持心动模式"
    }
    if (playContext == PlaybackController.CONTEXT_CLOUD || item?.cloudDisk == true) {
        return "云盘歌曲不支持心动模式"
    }
    if (item == null) {
        return "请先播放一首歌曲"
    }
    if (item.external != null) {
        return "当前音源不支持心动模式"
    }
    if (item.localUri != null || item.songId <= 0L) {
        return "本地歌曲不支持心动模式"
    }
    return null
}
