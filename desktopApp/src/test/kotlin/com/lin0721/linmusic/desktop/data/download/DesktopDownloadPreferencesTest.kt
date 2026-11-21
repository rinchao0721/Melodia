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
}
