package com.lin0721.linmusic.desktop.data.cloud

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.feature.cloud.data.CloudActionResponse
import com.lin0721.linmusic.feature.cloud.data.CloudInfoRequest
import com.lin0721.linmusic.feature.cloud.data.CloudInfoResponse
import com.lin0721.linmusic.feature.cloud.data.CloudPage
import com.lin0721.linmusic.feature.cloud.data.CloudPublishRequest
import com.lin0721.linmusic.feature.cloud.data.CloudRepository
import com.lin0721.linmusic.feature.cloud.data.CloudUploadApi
import com.lin0721.linmusic.feature.cloud.data.CloudUploadCheckRequest
import com.lin0721.linmusic.feature.cloud.data.CloudUploadCheckResponse
import com.lin0721.linmusic.feature.cloud.data.NosTokenRequest
import com.lin0721.linmusic.feature.cloud.data.NosTokenResponse
import com.lin0721.linmusic.feature.cloud.domain.CloudQuota
import com.lin0721.linmusic.feature.cloud.domain.CloudSong
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadManager
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadRunner
import com.lin0721.linmusic.feature.cloud.upload.NosTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Files

class CloudLibraryTest {

    private class FakeRepository(private val all: List<CloudSong>, private val pageSize: Int) : CloudRepository {
        var matched: Triple<Long, Long, Long>? = null
        override fun getCloudSongs(limit: Int, offset: Int): Flow<Result<CloudPage>> {
            val page = all.drop(offset).take(pageSize)
            val hasMore = offset + page.size < all.size
            return flowOf(Result.success(CloudPage(page, CloudQuota(usedBytes = 300, maxBytes = 1000, totalCount = all.size), hasMore)))
        }
        override fun deleteCloudSong(songId: Long): Flow<Result<Unit>> = flowOf(Result.success(Unit))
        override fun matchCloudSong(userId: Long, songId: Long, adjustSongId: Long): Flow<Result<Unit>> {
            matched = Triple(userId, songId, adjustSongId)
            return flowOf(Result.success(Unit))
        }
    }

    private object NoopApi : CloudUploadApi {
        override suspend fun checkUpload(body: CloudUploadCheckRequest) = CloudUploadCheckResponse()
        override suspend fun allocNosToken(body: NosTokenRequest) = NosTokenResponse()
        override suspend fun registerCloudInfo(body: CloudInfoRequest) = CloudInfoResponse()
        override suspend fun publishCloud(body: CloudPublishRequest) = CloudActionResponse()
    }

    private object NoopNos : NosTransport {
        override suspend fun fetchUploadHosts(bucket: String) = emptyList<String>()
        override suspend fun uploadBytes(
            host: String, bucket: String, objectKey: String, token: String, md5: String?,
            mimeType: String, contentLength: Long, openStream: () -> InputStream, onProgress: (Float) -> Unit
        ) = Unit
    }

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: UserPreferences

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-cloud").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        prefs = UserPreferences(PreferencesStores.get(File(dir, "user.preferences_pb")))
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun song(id: Long, size: Long = 100) = CloudSong(
        songId = id, name = "歌$id", artist = "某人", album = "", bitrate = 320, fileSizeBytes = size,
        fileSizeText = "", addedAtText = "", coverUrl = null, isUnmatched = true
    )

    private suspend fun awaitUntil(predicate: () -> Boolean) = withTimeout(5_000) {
        while (!predicate()) delay(20)
    }

    private fun library(repo: CloudRepository, manager: CloudUploadManager = CloudUploadManager()) =
        CloudLibrary(repo, prefs, manager, CloudUploadRunner(manager, NoopApi, NoopNos, scope), scope)

    @Test
    fun loadsAllPagesAfterLogin() = runBlocking {
        val lib = library(FakeRepository((1L..5L).map { song(it) }, pageSize = 2))
        lib.ensureLoaded()
        prefs.saveUserProfile(UserProfile(7, "u", ""))
        awaitUntil { lib.state.value.complete }
        assertEquals((1L..5L).toList(), lib.state.value.songs.map { it.songId })
        assertFalse(lib.state.value.loading)
    }

    @Test
    fun deleteAndMatchUpdateLocalState() = runBlocking {
        prefs.saveUserProfile(UserProfile(7, "u", ""))
        val repo = FakeRepository(listOf(song(1, size = 100), song(2)), pageSize = 10)
        val lib = library(repo)
        awaitUntil { lib.ensureLoaded(); lib.state.value.complete }

        assertTrue(lib.delete(lib.state.value.songs.first()).isSuccess)
        assertEquals(listOf(2L), lib.state.value.songs.map { it.songId })
        assertEquals(200L, lib.state.value.quota?.usedBytes)
        assertEquals(1, lib.state.value.quota?.totalCount)

        val target = Track(id = 99, name = "正版", ar = listOf(Artist(name = "歌手")), al = Album(name = "专辑", picUrl = "http://c"))
        assertTrue(lib.match(lib.state.value.songs.single(), target).isSuccess)
        assertEquals(Triple(7L, 2L, 99L), repo.matched)
        val updated = lib.state.value.songs.single()
        assertEquals("正版", updated.name)
        assertEquals("专辑", updated.album)
        assertFalse(updated.isUnmatched)
        assertEquals(99L, updated.playSongId)
        assertEquals(2L, updated.songId)
    }

    @Test
    fun logoutClearsState() = runBlocking {
        prefs.saveUserProfile(UserProfile(7, "u", ""))
        val lib = library(FakeRepository(listOf(song(1)), pageSize = 10))
        awaitUntil { lib.ensureLoaded(); lib.state.value.complete }
        prefs.clearUserProfile()
        awaitUntil { !lib.state.value.loaded }
        assertTrue(lib.state.value.songs.isEmpty())
    }
}
