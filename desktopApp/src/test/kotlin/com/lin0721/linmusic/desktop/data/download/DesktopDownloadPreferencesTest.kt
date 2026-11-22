package com.lin0721.linmusic.desktop.data.download

import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.preferences.PreferencesStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopDownloadPreferencesTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-download-prefs").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun downloadedQualitiesMapsSongIdToRecordQuality() = runTest {
        val prefs = DesktopDownloadPreferences(PreferencesStores.get(File(dir, "download_test.preferences_pb")))
        assertEquals(emptyMap<Long, String>(), prefs.downloadedQualities.first())

        prefs.addRecords(
            listOf(
                DownloadRecord(
                    songId = 101L,
                    mediaStoreUri = File(dir, "a.flac").path,
                    quality = "lossless",
                    downloadedAt = 1L,
                    fileSize = 1000L,
                    songName = "A",
                    artistName = "Artist"
                ),
                DownloadRecord(
                    songId = 202L,
                    mediaStoreUri = File(dir, "b.mp3").path,
                    quality = "exhigh",
                    downloadedAt = 2L,
                    fileSize = 500L,
                    songName = "B",
                    artistName = "Artist"
                )
            )
        )

        assertEquals(
            mapOf(101L to "lossless", 202L to "exhigh"),
            prefs.downloadedQualities.first()
        )
    }

    @Test
    fun distinctTrackIdsWithSameNegativeSongIdDoNotOverwriteOrPurgeEachOther() = runTest {
        val prefs = DesktopDownloadPreferences(PreferencesStores.get(File(dir, "download_kugou_test.preferences_pb")))
        val file1 = File(dir, "kg1.mp3").apply { writeText("audio1") }
        val file2 = File(dir, "kg2.mp3").apply { writeText("audio2") }

        val t1 = com.lin0721.linmusic.core.platform.TrackId.kugou("hash_001")
        val t2 = com.lin0721.linmusic.core.platform.TrackId.kugou("hash_002")

        prefs.addRecord(
            DownloadRecord(
                songId = -1L,
                mediaStoreUri = file1.absolutePath,
                quality = "exhigh",
                downloadedAt = 10L,
                fileSize = 6L,
                songName = "K1",
                artistName = "A1",
                trackId = t1
            )
        )
        prefs.addRecord(
            DownloadRecord(
                songId = -1L,
                mediaStoreUri = file2.absolutePath,
                quality = "lossless",
                downloadedAt = 20L,
                fileSize = 6L,
                songName = "K2",
                artistName = "A2",
                trackId = t2
            )
        )

        val all = prefs.records.first()
        assertEquals(2, all.size)
        assertEquals(file1.absolutePath, prefs.findVerifiedRecord(t1)?.mediaStoreUri)
        assertEquals(file2.absolutePath, prefs.findVerifiedRecord(t2)?.mediaStoreUri)

        // 删除 t1 对应文件并触发校验清理，t2 不应受影响
        file1.delete()
        val verified = prefs.findVerifiedRecordsForTracks(listOf(t1, t2))
        assertEquals(listOf(t2), verified.map { it.effectiveTrackId })
        assertEquals(1, prefs.records.first().size)
    }
}
