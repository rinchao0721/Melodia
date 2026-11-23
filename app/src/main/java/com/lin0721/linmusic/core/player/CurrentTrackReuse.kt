package com.lin0721.linmusic.core.player

// 点击的曲目与播放器已加载的曲目是同一首时可直接复用，不重新取链、不重置进度。
// loadedItem 为切换队列前的当前队列项，用于按「平台 + 曲目标识」区分同数字 ID 的不同平台歌曲
internal fun canReuseLoadedTrack(
    loadedMediaId: String?,
    loadedItem: QueueItem?,
    isLoaded: Boolean,
    isPendingSwitch: Boolean,
    target: QueueItem?,
    playContext: String?,
    startPositionMs: Long
): Boolean {
    if (!isLoaded || isPendingSwitch || target == null || loadedItem == null) return false
    if (playContext == PlaybackController.CONTEXT_PERSONAL_FM) return false
    if (startPositionMs > 0L) return false
    if (loadedMediaId == null || loadedMediaId != target.songId.toString()) return false
    return loadedItem.songId.toString() == loadedMediaId &&
        loadedItem.effectiveTrackId == target.effectiveTrackId
}
