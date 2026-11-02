package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.PlaylistCreator
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PluginNeteaseApiTest {

    private inner class FakeBackend : PluginNeteaseBackend {
        var uid: Long? = 1L
        val writes = mutableListOf<String>()
        val track = Track(id = 9, name = "晴天", ar = listOf(Artist(1, "周杰伦")), al = Album(2, "叶惠美", "cover"), dt = 269_000)

        override suspend fun uid(): Long? = uid
        override suspend fun songDetails(ids: List<Long>) = Result.success(listOf(track))
        override suspend fun lyrics(id: Long) = Result.success(listOf(LyricLine(timeMs = 1000, text = "第一句")))
        override suspend fun search(keyword: String, type: SearchType, offset: Int, limit: Int) =
            Result.success(SearchPageResult(listOf(SearchResultItem.SongItem(track)), 1, false, 1))
        override suspend fun playlistDetail(id: Long) = Result.success(PlaylistDetail(id = id, name = "歌单", tracks = listOf(track)))
        override suspend fun albumDetail(id: Long) = Result.success(PlaylistDetail(id = id, name = "专辑"))
        override suspend fun artistDetail(id: Long) = Result.success(ArtistDetailInfo(id = id, name = "周杰伦"))
        override suspend fun artistTopSongs(id: Long) = Result.success(listOf(track))
        override suspend fun userPlaylists(uid: Long) = Result.success(
            listOf(
                UserPlaylist(id = 100, name = "我喜欢的音乐", userId = 1, specialType = UserPlaylist.SPECIAL_TYPE_LIKED),
                UserPlaylist(id = 101, name = "自建", userId = 1, creator = PlaylistCreator(userId = 1)),
                UserPlaylist(id = 102, name = "收藏的", userId = 2, creator = PlaylistCreator(userId = 2))
            )
        )
        override fun likedSongIds() = setOf(9L)
        override suspend fun likeSong(id: Long, like: Boolean) = record("like $id $like")
        override suspend fun createPlaylist(name: String, privacy: Int): Result<PlaylistDetail> {
            writes += "create $name"
            return Result.success(PlaylistDetail(id = 5, name = name))
        }
        override suspend fun addTracks(playlistId: Long, trackIds: List<Long>) = record("add $playlistId")
        override suspend fun removeTracks(playlistId: Long, trackIds: List<Long>) = record("remove $playlistId")
        override suspend fun subscribePlaylist(id: Long, subscribe: Boolean) = record("subscribe $id")
        override suspend fun subscribeAlbum(id: Long, subscribe: Boolean) = record("album $id")
        override suspend fun followArtist(id: Long, follow: Boolean) = record("follow $id")
        override suspend fun deletePlaylist(id: Long) = record("delete $id")

        private fun record(text: String): Result<Unit> {
            writes += text
            return Result.success(Unit)
        }
    }

    private lateinit var root: File
    private lateinit var store: PluginStore
    private val backend = FakeBackend()
    private val confirmations = PluginConfirmations()
    private var clock = 0L
    private lateinit var api: PluginNeteaseApi

    private fun manifest(vararg permissions: String): PluginManifest {
        val list = permissions.joinToString(",") { "\"$it\"" }
        val text = """{"format":2,"id":"com.example.net","name":"网易","version":"1","permissions":[$list],"contributes":{"commands":[{"id":"a","title":"甲"}]}}"""
        return (PluginParser.parse(text, platform = PluginPlatform.DESKTOP) as PluginParseResult.Success).manifest
    }

    private val reader get() = manifest("netease.read")
    private val writer get() = manifest("netease.write")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("plugin-netease").toFile()
        store = PluginStore(root)
        api = PluginNeteaseApi(backend, store, confirmations) { clock }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun approved() = store.updateState("com.example.net") { it.copy(neteaseWriteApproved = true) }

    private fun params(id: Long): JsonObject = buildJsonObject { put("id", id) }

    private fun failure(block: suspend () -> Unit): String = runBlocking {
        try {
            block()
            fail("应当被拒绝")
            ""
        } catch (e: PluginCallException) {
            e.message.orEmpty()
        }
    }

    @Test
    fun `响应只包含裁剪后的字段`() = runBlocking {
        val result = api.call(reader, "song.detail", buildJsonObject { putJsonArray("ids") { add(JsonPrimitive(9)) } }) as JsonArray
        val song = result.single().jsonObject
        assertEquals("9", song["songId"]!!.jsonPrimitive.content)
        assertEquals("周杰伦", song["artist"]!!.jsonPrimitive.content)
        assertEquals(setOf("songId", "title", "artist", "artists", "album", "albumId", "cover", "durationMs"), song.keys)
    }

    @Test
    fun `不在方法表里的调用被拒绝`() {
        assertEquals("不支持的网易接口 user.cookie", failure { api.call(reader, "user.cookie", JsonObject(emptyMap())) })
    }

    @Test
    fun `缺少权限时拒绝且读权限不能写`() {
        assertEquals("缺少权限 netease.read", failure { api.call(manifest("storage"), "search", JsonObject(emptyMap())) })
        assertEquals("缺少权限 netease.write", failure { api.call(reader, "song.like", params(1)) })
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `未登录时不能写也不能读用户数据`() {
        backend.uid = null
        approved()
        assertEquals("用户尚未登录", failure { api.call(writer, "song.like", params(1)) })
        assertEquals("用户尚未登录", failure { api.call(reader, "user.playlists", JsonObject(emptyMap())) })
    }

    @Test
    fun `首次写入需要确认并记住`() = runBlocking {
        val like = buildJsonObject {
            put("id", 9)
            put("like", true)
        }
        val call = async(Dispatchers.Default) { api.call(writer, "song.like", like) }
        withTimeout(2_000) { confirmations.pending.filterNotNull().first() }
        confirmations.respond(true)
        call.await()

        assertTrue(store.state("com.example.net").neteaseWriteApproved)
        api.call(writer, "song.like", like)
        assertEquals(2, backend.writes.size)
        assertEquals(null, confirmations.pending.value)
    }

    @Test
    fun `用户拒绝时不写入也不记住`() = runBlocking {
        val like = buildJsonObject {
            put("id", 9)
            put("like", true)
        }
        val call = async(Dispatchers.Default) { runCatching { api.call(writer, "song.like", like) } }
        withTimeout(2_000) { confirmations.pending.filterNotNull().first() }
        confirmations.respond(false)

        assertEquals("用户拒绝了写入", call.await().exceptionOrNull()?.message)
        assertFalse(store.state("com.example.net").neteaseWriteApproved)
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `删除歌单每次都要确认`() = runBlocking {
        approved()
        val call = async(Dispatchers.Default) { api.call(writer, "playlist.delete", params(101)) }
        val request = withTimeout(2_000) { confirmations.pending.filterNotNull().first() }
        assertTrue(request.message.contains("自建"))
        confirmations.respond(true)
        call.await()
        assertEquals(listOf("delete 101"), backend.writes)
    }

    @Test
    fun `只能改自己创建的歌单`() {
        approved()
        val args = buildJsonObject {
            put("id", 102)
            putJsonArray("trackIds") { add(JsonPrimitive(9)) }
        }
        assertEquals("只能操作自己创建的歌单", failure { api.call(writer, "playlist.addTracks", args) })
        assertEquals("只能操作自己创建的歌单", failure { api.call(writer, "playlist.delete", params(102)) })
        assertEquals("不能删除「我喜欢的音乐」", failure { api.call(writer, "playlist.delete", params(100)) })
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `写入按插件限频并在窗口后恢复`() = runBlocking {
        approved()
        val args = buildJsonObject {
            put("id", 1)
            put("follow", true)
        }
        repeat(30) { api.call(writer, "artist.follow", args) }
        assertEquals("写入过于频繁，请稍后再试", failure { api.call(writer, "artist.follow", args) })
        assertEquals(30, backend.writes.size)

        clock += 60_001
        api.call(writer, "artist.follow", args)
        assertEquals(31, backend.writes.size)
    }

    @Test
    fun `参数不合法时不写入`() {
        approved()
        assertEquals("缺少有效的 id", failure { api.call(writer, "song.like", params(-1)) })
        assertEquals("缺少参数 like", failure { api.call(writer, "song.like", params(1)) })
        assertEquals("缺少歌单名称", failure { api.call(writer, "playlist.create", JsonObject(emptyMap())) })
        val tooMany = buildJsonObject { putJsonArray("ids") { repeat(201) { add(JsonPrimitive(it + 1)) } } }
        assertEquals("ids 不能超过 200 个", failure { api.call(reader, "song.detail", tooMany) })
        assertTrue(backend.writes.isEmpty())
    }

    @Test
    fun `音乐库接口需要 library read`() = runBlocking {
        assertEquals("缺少权限 library.read", failure { api.likedSongIds(reader) })
        assertEquals("[9]", api.likedSongIds(manifest("library.read")).toString())
    }
}
