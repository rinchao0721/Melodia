package com.lin0721.linmusic.core.playlistmutation

import com.lin0721.linmusic.core.platform.PlaylistId
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface PlaylistMutationEvent {
    val entityId: PlaylistId?
    val resolvedPlaylistId: PlaylistId

    data class Created(val playlistId: Long, override val entityId: PlaylistId? = null) : PlaylistMutationEvent {
        override val resolvedPlaylistId: PlaylistId get() = entityId ?: PlaylistId.netease(playlistId)
    }
    data class Subscribed(val playlistId: Long, val isSubscribed: Boolean, override val entityId: PlaylistId? = null) : PlaylistMutationEvent {
        override val resolvedPlaylistId: PlaylistId get() = entityId ?: PlaylistId.netease(playlistId)
    }
    data class Renamed(val playlistId: Long, val newName: String, override val entityId: PlaylistId? = null) : PlaylistMutationEvent {
        override val resolvedPlaylistId: PlaylistId get() = entityId ?: PlaylistId.netease(playlistId)
    }
    data class DescriptionUpdated(val playlistId: Long, val newDesc: String, override val entityId: PlaylistId? = null) : PlaylistMutationEvent {
        override val resolvedPlaylistId: PlaylistId get() = entityId ?: PlaylistId.netease(playlistId)
    }
    data class Deleted(val playlistId: Long, override val entityId: PlaylistId? = null) : PlaylistMutationEvent {
        override val resolvedPlaylistId: PlaylistId get() = entityId ?: PlaylistId.netease(playlistId)
    }
    data class CoverUpdated(val playlistId: Long, val newCoverUrl: String, override val entityId: PlaylistId? = null) : PlaylistMutationEvent {
        override val resolvedPlaylistId: PlaylistId get() = entityId ?: PlaylistId.netease(playlistId)
    }
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
