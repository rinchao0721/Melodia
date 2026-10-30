package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.source.ExternalTrackResolver
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class PluginContentTest {

    private class FakeSource(
        override val searchable: Boolean = true,
        private val url: String? = "https://example.com/a.mp3"
    ) : ContentSourceExtension {
        val resolved = mutableListOf<String>()

        private fun tracks() = buildJsonArray {
            add(buildJsonObject {
                put("id", "s/1")
                put("title", "晴天")
                put("artist", "周杰伦")
                put("durationMs", 1000)
            })
            add(buildJsonObject { put("title", "没有 id") })
            add(JsonPrimitive("无效"))
        }

        override suspend fun search(keyword: String, offset: Int, limit: Int): JsonElement = tracks()

        override suspend fun playlist(id: String): JsonElement = buildJsonObject {
            put("name", "歌单 $id")
            put("cover", "https://example.com/c.jpg")
            put("tracks", tracks())
        }

        override suspend fun resolve(trackId: String, quality: String): JsonElement? {
            resolved += trackId
            return url?.let(::JsonPrimitive)
        }
    }

    private val registry = ExtensionRegistry()
    private val content = PluginContent(registry)

    private fun register(source: ContentSourceExtension, pluginId: String = "c.a", name: String = "插件") =
        registry.register(Extension(ExtensionPoints.CONTENT_SOURCE, pluginId, name, "main", source))

    @Test
    fun `搜索结果转成外部歌曲并跳过无效项`() = runBlocking {
        register(FakeSource())
        val track = content.search("晴天", 0, 30).single()
        assertEquals("晴天", track.name)
        assertEquals(MusicPlatform.LX, track.platform)
        assertTrue(track.queueSongId < 0)
        assertEquals(track, content.externalTrack(track.queueSongId))
    }

    @Test
    fun `不支持搜索的内容源不参与搜索`() = runBlocking {
        register(FakeSource(searchable = false))
        assertFalse(content.hasSearch())
        assertTrue(content.search("晴天", 0, 30).isEmpty())
    }

    @Test
    fun `合成 id 不与保留 id 和网易 id 冲突且可重复计算`() {
        val id = content.collectionId("c.a", "main", "1")
        assertTrue(id <= -1000L)
        assertEquals(id, content.collectionId("c.a", "main", "1"))
        assertTrue(content.ownsCollection(id))
        assertFalse(content.ownsCollection(123L))
    }

    @Test
    fun `详情转成统一歌单模型`() = runBlocking {
        register(FakeSource())
        val id = content.collectionId("c.a", "main", "p1")
        val detail = content.detail(id).getOrThrow()
        assertEquals("歌单 p1", detail.name)
        assertEquals(id, detail.id)
        assertEquals("插件", detail.creator?.nickname)
        assertEquals(1, detail.tracks.size)
        assertNotNull(content.externalTrack(detail.tracks.single().id))
    }

    @Test
    fun `插件停用后详情返回失败`() = runBlocking {
        val id = content.collectionId("c.gone", "main", "p1")
        assertEquals("提供这个内容的插件已停用", content.detail(id).exceptionOrNull()?.message)
        assertEquals("插件内容已失效", content.detail(-5000L).exceptionOrNull()?.message)
    }

    @Test
    fun `播放地址由提供歌曲的内容源解析`() = runBlocking {
        val source = FakeSource()
        register(source)
        val track = content.search("晴天", 0, 30).single()
        assertTrue(content.owns(track))

        val url = ExternalTrackResolver(emptyList(), null, directResolver = content).resolve(track)

        assertEquals("https://example.com/a.mp3", url)
        assertEquals(listOf("s/1"), source.resolved)
    }

    @Test
    fun `试听链接不能播放`() = runBlocking {
        register(FakeSource(url = "https://musicrep-ts.example.com/a.mp3"))
        val track = content.search("晴天", 0, 30).single()
        assertNull(content.resolve(track, "exhigh"))
    }

    @Test
    fun `插件停用后歌曲不再走换源`() = runBlocking {
        register(FakeSource())
        val track = content.search("晴天", 0, 30).single()
        registry.unregister("c.a", setOf(ExtensionPoints.CONTENT_SOURCE))

        val resolver = ExternalTrackResolver(emptyList(), null, directResolver = content)

        assertNull(resolver.resolve(track))
        assertTrue(resolver.explainFailure(track).contains("插件已停用"))
    }

    @Test
    fun `歌单仓储只拦截插件 id`() = runBlocking {
        register(FakeSource())
        val delegate = Proxy.newProxyInstance(
            PlaylistRepository::class.java.classLoader,
            arrayOf(PlaylistRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getPlaylistDetail" -> flowOf(Result.success(PlaylistDetail(id = args[0] as Long, name = "网易")))
                else -> error("不应调用 ${method.name}")
            }
        } as PlaylistRepository
        val repository = PluginAwarePlaylistRepository(delegate, content)

        val id = content.collectionId("c.a", "main", "p1")
        val detail = repository.getPlaylistDetail(id).first().getOrThrow()
        assertEquals("歌单 p1", detail.name)
        assertEquals(detail.tracks, repository.loadAllTracks(detail).first().getOrThrow())
        assertEquals("网易", repository.getPlaylistDetail(7L).first().getOrThrow().name)
        assertEquals("网易", repository.getPlaylistDetail(-99L).first().getOrThrow().name)
    }

    @Test
    fun `插件搜索平台的可用状态跟随注册表`() = runBlocking {
        val provider = PluginPlatformSearchProvider(content)
        assertEquals(false, provider.available.first())
        register(FakeSource())
        assertEquals(true, provider.available.first())
    }

    @Test
    fun `打开内容时发出导航事件`() = runBlocking {
        val opened = async(Dispatchers.Unconfined) { withTimeout(2_000) { content.opens.first() } }

        content.open("c.a", "main", "p1", "我的歌单")

        val event = opened.await()
        assertEquals("我的歌单", event.title)
        assertEquals(content.collectionId("c.a", "main", "p1"), event.id)
        assertTrue(content.ownsCollection(event.id))
    }
}
