package com.lin0721.linmusic.core.download.recognition

import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.download.skipsDownloadAt
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionHelpersTest {

    @Test
    fun lossyQualityFollowsBitrate() {
        assertEquals("standard", guessAudioQuality("mp3", 128))
        assertEquals("higher", guessAudioQuality("MP3", 192))
        assertEquals("exhigh", guessAudioQuality(".mp3", 320))
        assertEquals("standard", guessAudioQuality("mp3", 0))
    }

    @Test
    fun losslessQualityFollowsSampleRateAndBitDepth() {
        assertEquals("lossless", guessAudioQuality("flac", 900, 44_100, 16))
        assertEquals("hires", guessAudioQuality("flac", 900, 96_000, 16))
        assertEquals("hires", guessAudioQuality("flac", 900, 48_000, 24))
        assertEquals("hires", guessAudioQuality("flac", 2500))
        assertEquals("lossless", guessAudioQuality("flac", 1000))
    }

    @Test
    fun bitrateEstimatedFromSizeAndDuration() {
        assertEquals(320, estimateBitrateKbps(8_000_000L, 200_000L))
        assertEquals(0, estimateBitrateKbps(0, 200_000L))
        assertEquals(0, estimateBitrateKbps(1000, 0))
    }

    @Test
    fun parsesDownloadFileName() {
        assertEquals("晴天" to "周杰伦", parseDownloadFileName("周杰伦 - 晴天.flac"))
        assertEquals("A - B" to "X", parseDownloadFileName("X - A - B.mp3"))
        assertNull(parseDownloadFileName("晴天.mp3"))
        assertNull(parseDownloadFileName(" - 晴天.mp3"))
    }

    @Test
    fun recognizedRecordSkipsAnyLevel() {
        val downloaded = DownloadRecord(1, "a", "exhigh", 0, 0)
        assertFalse(downloaded.skipsDownloadAt("lossless"))
        assertTrue(downloaded.skipsDownloadAt("higher"))
        assertTrue(downloaded.copy(recognized = true).skipsDownloadAt("lossless"))
    }

    @Test
    fun parsesSongIdTag() {
        assertEquals(123L, parseSongIdTag(" 123 "))
        assertNull(parseSongIdTag("0"))
        assertNull(parseSongIdTag("abc"))
        assertNull(parseSongIdTag(null))
    }

    @Test
    fun recognizerRecordsMatchesAndDropsDeadFiles() = runBlocking {
        val dir = Files.createTempDirectory("melodia-recognition").toFile()
        try {
            val store = LocalAudioIndexStore(dir.resolve("index.json"))
            store.replaceRoot(
                "root",
                listOf(
                    LocalAudioEntry("alive", "晴天", "周杰伦", 200_000L, 10L, "lossless"),
                    LocalAudioEntry("dead", "七里香", "周杰伦", 200_000L, 10L, "exhigh")
                )
            )
            val existing = listOf(DownloadRecord(9, "other", "exhigh", 0, 0))
            val saved = mutableListOf<DownloadRecord>()
            val recognizer = DownloadRecognizer(
                indexStore = store,
                records = { existing + saved },
                addRecords = { saved += it },
                isAlive = { it != "dead" },
                now = { 42L }
            )
            val added = recognizer.recognize(
                listOf(
                    RecognitionTarget(1, "晴天", listOf("周杰伦"), 200_000L),
                    RecognitionTarget(2, "七里香", listOf("周杰伦"), 200_000L),
                    RecognitionTarget(9, "已下载", listOf("某人"), 200_000L)
                )
            )
            assertEquals(listOf(1L), added.map { it.songId })
            assertEquals("lossless", added.single().quality)
            assertEquals(42L, added.single().downloadedAt)
            assertTrue(added.single().recognized)
            assertEquals(added, saved)
            assertEquals(listOf("alive"), LocalAudioIndexStore(dir.resolve("index.json")).entries().map { it.uri })
            assertTrue(recognizer.recognize(listOf(RecognitionTarget(1, "晴天", listOf("周杰伦")))).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
