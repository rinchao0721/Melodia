package com.lin0721.linmusic.core.playlistmutation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface PlaylistMutationEvent {
    data class Renamed(val playlistId: Long, val newName: String) : PlaylistMutationEvent
    data class DescriptionUpdated(val playlistId: Long, val newDesc: String) : PlaylistMutationEvent
    data class Deleted(val playlistId: Long) : PlaylistMutationEvent
}

// 跨模块歌单变更事件总线
class PlaylistMutationBus {

    private val _events = MutableSharedFlow<PlaylistMutationEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<PlaylistMutationEvent> = _events.asSharedFlow()

    suspend fun emit(event: PlaylistMutationEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: PlaylistMutationEvent): Boolean {
        return _events.tryEmit(event)
    }
}
