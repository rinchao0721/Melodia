package com.lin0721.linmusic.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow

// 一键收藏成功后直接打开页面自己的加入歌单小弹窗
@Composable
fun CollectPromptEffect(events: Flow<CollectPromptEvent>, onCollected: (Long) -> Unit) {
    val latestOnCollected by rememberUpdatedState(onCollected)
    LaunchedEffect(events) {
        events.collect { event -> latestOnCollected(event.songId) }
    }
}
