package com.lin0721.linmusic.desktop.data.local

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.desktop.data.download.DesktopDownloadPreferences
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.HotSearch
import com.lin0721.linmusic.feature.search.domain.PlaylistCategoryPage
import com.lin0721.linmusic.feature.search.domain.PlaylistTag
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

class LocalLibraryRepositoryTest {

    private class FakeSearch(private val results: List<Track>) : SearchRepository {
        var calls = 0
        override fun getDefaultSearchKeyword(): Flow<Result<String>> = emptyFlow()
        override fun search(keyword: String, type: SearchType, offset: Int, limit: Int): Flow<Result<SearchPageResult>> {
            calls++
            return flowOf(Result.success(SearchPageResult(results.map { SearchResultItem.SongItem(it) }, results.size, false, results.size)))
        }
        override fun getHotSearches(): Flow<Result<List<HotSearch>>> = emptyFlow()
        override fun getPlaylistTags(): Flow<Result<List<PlaylistTag>>> = emptyFlow()
        override fun getSuggestions(keyword: String): Flow<Result<List<SearchSuggestion>>> = emptyFlow()
        override fun getPlaylistsByCategory(category: String, cursor: Long, limit: Int): Flow<Result<PlaylistCategoryPage>> = emptyFlow()
    }

    private object Online : OnlineStateProvider {
        override val online: StateFlow<Boolean> = MutableStateFlow(true)
    }

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-local-repo").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun silentWav(file: File) {
        file.parentFile.mkdirs()
        val format = AudioFormat(8000f, 16, 1, true, false)
        val frames = 8000L * 70
        AudioInputStream(ByteArrayInputStream(ByteArray((frames * 2).toInt())), format, frames).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
    }

    private fun repository(search: SearchRepository) = LocalLibraryRepository(
        store = LocalLibraryStore(File(dir, "lib.json")),
        scanner = LocalScanner { File(dir, "covers") },
        matcher = LocalMatcher(search),
        downloadPreferences = DesktopDownloadPreferences(PreferencesStores.get(File(dir, "download.preferences_pb"))),
        onlineState = Online,
        scope = scope
    )

    private suspend fun awaitTrack(repo: LocalLibraryRepository, predicate: (LocalTrack) -> Boolean): LocalTrack =
        withTimeout(10_000) {
            var found = repo.data.value.tracks.firstOrNull(predicate)
            while (found == null) {
                delay(50)
                found = repo.data.value.tracks.firstOrNull(predicate)
            }
            found
        }

    @Test
    fun folderPlaylistScansAndAutoMatches() = runBlocking {
        val music = File(dir, "music")
        silentWav(File(music, "周杰伦 - 晴天.wav"))
        val search = FakeSearch(listOf(Track(id = 186016, name = "晴天", ar = listOf(Artist(name = "周杰伦")), dt = 70_000)))
        val repo = repository(search)

        val id = repo.createPlaylist("", music)
        val matched = awaitTrack(repo) { it.matchState == LocalMatchState.AUTO }
        assertEquals(186016L, matched.songId)
        assertEquals("music", repo.summaries.value.single { it.id == id }.name)
        assertEquals(1, repo.tracksOf(id).first().size)
        assertEquals(matched.path, repo.pathForSongId(186016L))
    }

    @Test
    fun manualMatchAndDetachSurviveRescan() = runBlocking {
        val music = File(dir, "music")
        silentWav(File(music, "某人 - 冷门歌.wav"))
        val repo = repository(FakeSearch(emptyList()))
        repo.createPlaylist("测试", music)
        val track = awaitTrack(repo) { it.matchState == LocalMatchState.NOT_FOUND }

        repo.setManualMatch(track.path, 42L)
        awaitTrack(repo) { it.matchState == LocalMatchState.MANUAL && it.songId == 42L }
        repo.rescan()
        delay(500)
        assertEquals(42L, repo.data.value.tracks.single().songId)

        repo.detachMatch(track.path)
        val detached = awaitTrack(repo) { it.matchState == LocalMatchState.DETACHED }
        assertEquals(null, detached.songId)
    }

    @Test
    fun removingPlaylistPrunesItsTracksAndPersists() = runBlocking {
        val music = File(dir, "music")
        silentWav(File(music, "a.wav"))
        val repo = repository(FakeSearch(emptyList()))
        val id = repo.createPlaylist("待删", music)
        awaitTrack(repo) { true }

        repo.removePlaylist(id)
        withTimeout(5_000) {
            while (repo.data.value.tracks.isNotEmpty() || repo.data.value.playlists.isNotEmpty()) delay(50)
        }
        delay(800)
        val saved = LocalLibraryStore(File(dir, "lib.json")).load()
        assertTrue(saved.playlists.isEmpty())
        assertTrue(saved.tracks.isEmpty())
        assertTrue(music.listFiles()!!.any { it.name == "a.wav" })
    }
}
