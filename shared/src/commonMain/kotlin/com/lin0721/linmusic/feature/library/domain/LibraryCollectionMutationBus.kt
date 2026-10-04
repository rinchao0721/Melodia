package com.lin0721.linmusic.feature.library.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface LibraryCollectionMutationEvent {
    val id: Long
    val isCollected: Boolean

    data class ArtistChanged(
        override val id: Long,
        override val isCollected: Boolean,
        val name: String,
        val coverUrl: String,
        val updateTime: Long = 0
    ) : LibraryCollectionMutationEvent

    data class AlbumChanged(
        override val id: Long,
        override val isCollected: Boolean,
        val name: String,
        val artistNames: String,
        val coverUrl: String,
        val updateTime: Long = 0
    ) : LibraryCollectionMutationEvent
}

// 歌手/专辑收藏状态跨页面同步。保留每个资源的最新状态，避免音乐库页面未存活时丢失事件。
data class ScopedLibraryCollectionMutation(
    val ownerUid: Long,
    val event: LibraryCollectionMutationEvent
)

private data class MutationSnapshot(
    val ownerUid: Long? = null,
    val events: Map<String, LibraryCollectionMutationEvent> = emptyMap()
)

class LibraryCollectionMutationBus {
    private val _events = MutableSharedFlow<ScopedLibraryCollectionMutation>(extraBufferCapacity = 64)
    val events: SharedFlow<ScopedLibraryCollectionMutation> = _events.asSharedFlow()

    private var snapshot = MutationSnapshot()

    fun latestEvents(ownerUid: Long): Collection<LibraryCollectionMutationEvent> =
        if (snapshot.ownerUid == ownerUid) snapshot.events.values else emptyList()

    fun clear() {
        snapshot = MutationSnapshot()
    }

    suspend fun emit(ownerUid: Long, event: LibraryCollectionMutationEvent) {
        val type = when (event) {
            is LibraryCollectionMutationEvent.ArtistChanged -> "artist"
            is LibraryCollectionMutationEvent.AlbumChanged -> "album"
        }
        val key = "$type:${event.id}"
        val current = if (snapshot.ownerUid == ownerUid) snapshot.events else emptyMap()
        val bounded = LinkedHashMap(current).apply {
            remove(key)
            put(key, event)
            while (size > MAX_RETAINED_MUTATIONS) remove(keys.first())
        }
        snapshot = MutationSnapshot(ownerUid = ownerUid, events = bounded)
        _events.emit(ScopedLibraryCollectionMutation(ownerUid, event))
    }

    private companion object {
        const val MAX_RETAINED_MUTATIONS = 128
    }
}
