package com.lin0721.linmusic.desktop.data.local

import com.lin0721.linmusic.core.download.DownloadRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LibraryViewTest {

    private lateinit var dir: File
    private lateinit var scanner: LocalScanner

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-local-view").toFile()
        scanner = LocalScanner { File(dir, "covers") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun path(vararg parts: String) = normalizePath(File(dir, parts.joinToString(File.separator)).path)

    private fun track(path: String, title: String = File(path).nameWithoutExtension, durationMs: Long = 180_000L) =
        LocalTrack(path = path, title = title, artist = "歌手", durationMs = durationMs)

    @Test
    fun folderPlaylistAppliesRemovedExcludedAndDurationFilters() {
        val music = path("music")
        val kept = track(path("music", "a.mp3"))
        val removed = track(path("music", "b.mp3"))
        val excluded = track(path("music", "skip", "c.mp3"))
        val short = track(path("music", "d.mp3"), durationMs = 5_000L)
        val unknownDuration = track(path("music", "e.mp3"), durationMs = 0L)
        val outside = track(path("other", "f.mp3"))
        val data = LocalLibraryData(
            playlists = listOf(LocalPlaylist(id = 10, name = "列表", createdAt = 1, folders = listOf(music), removed = listOf(removed.path))),
            tracks = listOf(kept, removed, excluded, short, unknownDuration, outside),
            filter = LocalScanFilter(minDurationSec = 30, excludedFolders = listOf(path("music", "skip")))
        )
        val view = LibraryView(data, emptyList(), scanner)
        assertEquals(listOf(kept.path, unknownDuration.path), view.tracksOf(10).map { it.path })
    }

    @Test
    fun explicitFilesAreAppendedWithoutDuplicatingFolderTracks() {
        val music = path("music")
        val inFolder = track(path("music", "a.mp3"))
        val single = track(path("single", "z.mp3"))
        val data = LocalLibraryData(
            playlists = listOf(
                LocalPlaylist(id = 10, name = "混合", createdAt = 1, folders = listOf(music), files = listOf(single.path, inFolder.path))
            ),
            tracks = listOf(inFolder, single)
        )
        val view = LibraryView(data, emptyList(), scanner)
        assertEquals(listOf(inFolder.path, single.path), view.tracksOf(10).map { it.path })
        assertEquals(2, view.summaries.single { it.id == 10L }.trackCount)
    }

    @Test
    fun downloadsUseRecordSongIdAndSkipMissingFiles() {
        val existing = File(dir, "dl.mp3").apply { writeBytes(byteArrayOf(1)) }
        val records = listOf(
            DownloadRecord(songId = 42, mediaStoreUri = existing.path, quality = "", downloadedAt = 2, fileSize = 1, songName = "下载歌", artistName = "某人"),
            DownloadRecord(songId = 43, mediaStoreUri = File(dir, "gone.mp3").path, quality = "", downloadedAt = 1, fileSize = 1)
        )
        val view = LibraryView(LocalLibraryData(), records, scanner)
        val downloads = view.tracksOf(LOCAL_DOWNLOADS_ID)
        assertEquals(1, downloads.size)
        assertEquals(42L, downloads[0].queueSongId)
        assertEquals("下载歌", downloads[0].title)
        assertEquals(normalizePath(existing.path), view.pathIndex[42L]?.let(::normalizePath))
    }

    @Test
    fun allTracksMergesPlaylistsAndDownloadsWithoutDuplicates() {
        val shared = track(path("music", "a.mp3"))
        val data = LocalLibraryData(
            playlists = listOf(
                LocalPlaylist(id = 1, name = "一", createdAt = 1, files = listOf(shared.path)),
                LocalPlaylist(id = 2, name = "二", createdAt = 2, files = listOf(shared.path))
            ),
            tracks = listOf(shared)
        )
        val view = LibraryView(data, emptyList(), scanner)
        assertEquals(1, view.tracksOf(LOCAL_ALL_TRACKS_ID).size)
        assertTrue(view.pathIndex.containsKey(shared.placeholderId))
        assertTrue(shared.placeholderId < -1_000_000L)
    }
}
