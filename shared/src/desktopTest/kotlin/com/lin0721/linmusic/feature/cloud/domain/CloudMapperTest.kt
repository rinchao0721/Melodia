package com.lin0721.linmusic.feature.cloud.domain

import com.lin0721.linmusic.feature.cloud.data.CloudSimpleSong
import com.lin0721.linmusic.feature.cloud.data.CloudSongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudMapperTest {

    @Test
    fun matchedSongPlaysOfficialId() {
        val item = CloudSongItem(songId = 900, matchType = "matched", simpleSong = CloudSimpleSong(id = 186016))
        val song = item.toDomain(0)
        assertEquals(900L, song.songId)
        assertEquals(186016L, song.playSongId)
    }

    @Test
    fun unmatchedOrMissingIdFallsBackToCloudId() {
        assertEquals(900L, CloudSongItem(songId = 900, matchType = "unmatched", simpleSong = CloudSimpleSong(id = 5)).toDomain(0).playSongId)
        assertEquals(900L, CloudSongItem(songId = 900, matchType = "matched").toDomain(0).playSongId)
    }
}
