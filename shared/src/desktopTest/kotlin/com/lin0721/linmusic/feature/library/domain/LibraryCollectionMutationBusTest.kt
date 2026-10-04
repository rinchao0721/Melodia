package com.lin0721.linmusic.feature.library.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCollectionMutationBusTest {

    @Test
    fun `mutation emitted before collector starts is retained`() = runBlocking {
        val bus = LibraryCollectionMutationBus()
        val event = LibraryCollectionMutationEvent.AlbumChanged(
            id = 7,
            isCollected = true,
            name = "Album",
            artistNames = "Artist",
            coverUrl = "cover"
        )

        bus.emit(ownerUid = 1, event = event)

        assertEquals(listOf(event), bus.latestEvents(ownerUid = 1).toList())
    }

    @Test
    fun `new mutation replaces old state for the same resource`() = runBlocking {
        val bus = LibraryCollectionMutationBus()
        bus.emit(1, LibraryCollectionMutationEvent.ArtistChanged(9, true, "Artist", "cover"))
        bus.emit(1, LibraryCollectionMutationEvent.ArtistChanged(9, false, "Artist", "cover"))

        val latest = bus.latestEvents(1).single()
        assertTrue(latest is LibraryCollectionMutationEvent.ArtistChanged)
        assertEquals(false, latest.isCollected)
    }

    @Test
    fun `mutations are isolated between accounts`() = runBlocking {
        val bus = LibraryCollectionMutationBus()
        bus.emit(1, LibraryCollectionMutationEvent.ArtistChanged(9, false, "Artist", "cover"))

        assertTrue(bus.latestEvents(2).isEmpty())

        bus.emit(2, LibraryCollectionMutationEvent.AlbumChanged(7, true, "Album", "Artist", "cover"))
        assertTrue(bus.latestEvents(1).isEmpty())
        assertEquals(7L, bus.latestEvents(2).single().id)
    }

    @Test
    fun `retained mutations are bounded`() = runBlocking {
        val bus = LibraryCollectionMutationBus()
        repeat(160) { id ->
            bus.emit(1, LibraryCollectionMutationEvent.ArtistChanged(id.toLong(), true, "Artist", "cover"))
        }

        assertEquals(128, bus.latestEvents(1).size)
    }
}
