package com.lin0721.linmusic.core.download.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedFileMatcherTest {

    private fun target(id: Long, title: String, artist: String, durationMs: Long = 200_000L) =
        RecognitionTarget(id, title, listOf(artist), durationMs)

    private fun entry(
        uri: String,
        title: String,
        artist: String,
        durationMs: Long = 200_000L,
        quality: String = "exhigh",
        songIdTag: Long? = null
    ) = LocalAudioEntry(uri, title, artist, durationMs, 1000L, quality, songIdTag)

    @Test
    fun songIdTagMatchesExactlyEvenWhenNamesDiffer() {
        val result = DownloadedFileMatcher.match(
            listOf(target(1, "晴天", "周杰伦")),
            listOf(entry("a", "别的名字", "别人", songIdTag = 1))
        )
        assertEquals("a", result[1]?.uri)
    }

    @Test
    fun fileTaggedWithOtherSongIsNotFuzzyMatched() {
        val result = DownloadedFileMatcher.match(
            listOf(target(1, "晴天", "周杰伦")),
            listOf(entry("a", "晴天", "周杰伦", songIdTag = 2))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun fuzzyMatchNormalizesTitleAndArtists() {
        val result = DownloadedFileMatcher.match(
            listOf(RecognitionTarget(1, "Hello, World", listOf("A/B"), 200_000L)),
            listOf(entry("a", "ＨＥＬＬＯ　ｗｏｒｌｄ", "B feat. C"))
        )
        assertEquals("a", result[1]?.uri)
    }

    @Test
    fun durationBeyondToleranceDoesNotMatch() {
        val result = DownloadedFileMatcher.match(
            listOf(target(1, "晴天", "周杰伦", 200_000L)),
            listOf(entry("a", "晴天", "周杰伦", 204_000L))
        )
        assertNull(result[1])
    }

    @Test
    fun differentArtistDoesNotMatch() {
        val result = DownloadedFileMatcher.match(
            listOf(target(1, "晴天", "周杰伦")),
            listOf(entry("a", "晴天", "翻唱歌手"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun picksHighestQualityWhenSongHasSeveralFiles() {
        val result = DownloadedFileMatcher.match(
            listOf(target(1, "晴天", "周杰伦")),
            listOf(
                entry("mp3", "晴天", "周杰伦", quality = "exhigh"),
                entry("flac", "晴天", "周杰伦", 201_000L, quality = "lossless")
            )
        )
        assertEquals("flac", result[1]?.uri)
    }

    @Test
    fun fileGoesToSongWithClosestDuration() {
        val result = DownloadedFileMatcher.match(
            listOf(target(1, "晴天", "周杰伦", 200_000L), target(2, "晴天", "周杰伦", 202_500L)),
            listOf(entry("a", "晴天", "周杰伦", 202_000L))
        )
        assertEquals(mapOf(2L to "a"), result.mapValues { it.value.uri })
    }

    @Test
    fun claimedFilesAreSkipped() {
        val result = DownloadedFileMatcher.match(
            listOf(target(1, "晴天", "周杰伦")),
            listOf(entry("a", "晴天", "周杰伦")),
            claimedUris = setOf("a")
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun unknownDurationSkipsDurationCheck() {
        val result = DownloadedFileMatcher.match(
            listOf(target(1, "晴天", "周杰伦", 0)),
            listOf(entry("a", "晴天", "周杰伦", 999_000L))
        )
        assertEquals("a", result[1]?.uri)
    }
}
