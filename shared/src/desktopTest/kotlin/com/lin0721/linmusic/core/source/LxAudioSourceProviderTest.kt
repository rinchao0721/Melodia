package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LxAudioSourceProviderTest {

    // 记录每次解析请求的（平台, 歌曲标识），只有 urls 里配置过的组合才返回直链
    private class RecordingRuntime(
        private val sources: List<String>,
        private val urls: Map<Pair<String, String>, String>
    ) : LxPluginRuntime {
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun downloadScript(url: String) = Result.success("")
        override suspend fun loadAndVerifyScript(scriptText: String) = Result.success(LxPluginInfo())
        override suspend fun activatePlugin(item: LxPluginItem) = true
        override fun deactivatePlugin(pluginId: String) = Unit
        override suspend fun syncActivePlugins(items: List<LxPluginItem>) = Unit
        override fun getSupportedSources(pluginId: String) = sources
        override suspend fun resolveMusicUrl(
            pluginId: String,
            source: String,
            songId: String,
            songName: String,
            singer: String,
            albumName: String,
            durationMs: Long,
            quality: String,
            timeoutMs: Long
        ): String? {
            calls += source to songId
            return urls[source to songId]
        }

        override fun reset() = Unit
    }

    private class FakeSearch(override val platform: MusicPlatform, private val results: List<ExternalTrack>) : PlatformSearchProvider {
        var searched = 0
        override suspend fun search(keyword: String, offset: Int, limit: Int): List<ExternalTrack> {
            searched++
            return results
        }
    }

    private val prefs = SourcePreferences(InMemoryPreferencesStore())

    @Before
    fun setUp() = runBlocking {
        prefs.saveLxPluginEnabled(true)
        prefs.saveLxPlugins(listOf(LxPluginItem(id = "p1", name = "插件", sources = listOf("wy", "kw", "kg", "tx", "mg"))))
    }

    private fun track(platform: MusicPlatform, id: String, name: String = "七里香", artist: String = "周杰伦", durationMs: Long = 299_000) =
        ExternalTrack(id = id, name = name, artists = artist, durationMs = durationMs, platform = platform, hash = if (platform == MusicPlatform.KUGOU) id else null)

    private suspend fun resolve(
        provider: LxAudioSourceProvider,
        songId: String,
        origin: MusicPlatform = MusicPlatform.NETEASE
    ) = provider.resolveUrl(
        songId = songId,
        songName = "七里香",
        artists = "周杰伦",
        albumName = "七里香",
        durationMs = 299_000,
        quality = "lossless",
        origin = origin
    )

    @Test
    fun `网易云歌曲优先用网易云 id 直接解析`() = runBlocking {
        val runtime = RecordingRuntime(listOf("wy", "kw"), mapOf(("wy" to "100") to "http://wy/100"))
        val provider = LxAudioSourceProvider(runtime, prefs, listOf(FakeSearch(MusicPlatform.KUWO, emptyList())))

        assertEquals("http://wy/100", resolve(provider, "100")?.url)
        assertEquals(listOf("wy" to "100"), runtime.calls)
    }

    @Test
    fun `插件返回试听链接时视为未命中并继续尝试其他平台`() = runBlocking {
        val runtime = RecordingRuntime(
            listOf("wy", "kw"),
            mapOf(
                ("wy" to "100") to "http://m701.music.126.net/musicrep-ts/trial.mp3",
                ("kw" to "KW9") to "http://kw/9"
            )
        )
        val kuwo = FakeSearch(MusicPlatform.KUWO, listOf(track(MusicPlatform.KUWO, "KW9")))
        val provider = LxAudioSourceProvider(runtime, prefs, listOf(kuwo))

        assertEquals("http://kw/9", resolve(provider, "100")?.url)
        assertEquals(listOf("wy" to "100", "kw" to "KW9"), runtime.calls)
    }

    @Test
    fun `网易云解析失败后换平台时用匹配出的标识而不是网易云 id`() = runBlocking {
        val runtime = RecordingRuntime(listOf("wy", "kw"), mapOf(("kw" to "KW9") to "http://kw/9"))
        val kuwo = FakeSearch(MusicPlatform.KUWO, listOf(track(MusicPlatform.KUWO, "KW9")))
        val provider = LxAudioSourceProvider(runtime, prefs, listOf(kuwo))

        assertEquals("http://kw/9", resolve(provider, "100")?.url)
        assertEquals(listOf("wy" to "100", "kw" to "KW9"), runtime.calls)
    }

    @Test
    fun `搜索不到同一首歌时不尝试该平台`() = runBlocking {
        val runtime = RecordingRuntime(listOf("kw"), mapOf(("kw" to "100") to "http://wrong"))
        val kuwo = FakeSearch(MusicPlatform.KUWO, listOf(track(MusicPlatform.KUWO, "KW1", name = "晴天"), track(MusicPlatform.KUWO, "KW2", artist = "别的歌手")))
        val provider = LxAudioSourceProvider(runtime, prefs, listOf(kuwo))

        assertNull(resolve(provider, "100"))
        assertTrue("不应拿网易云 id 去其他平台试", runtime.calls.isEmpty())
    }

    @Test
    fun `没有对应搜索源的平台只在歌曲属于它时才尝试`() = runBlocking {
        val runtime = RecordingRuntime(listOf("mg", "wy"), mapOf(("mg" to "100") to "http://mg/wrong"))
        val provider = LxAudioSourceProvider(runtime, prefs, emptyList())

        // 网易云歌曲：wy 没有直链，mg 无法匹配，不能用网易云 id 去试
        assertNull(resolve(provider, "100"))
        assertEquals(listOf("wy" to "100"), runtime.calls)
    }

    @Test
    fun `外部平台歌曲先用自己的标识，且不再向网易云平台尝试`() = runBlocking {
        val runtime = RecordingRuntime(listOf("wy", "kw", "tx"), mapOf(("tx" to "QQMID") to "http://tx/ok"))
        val kuwo = FakeSearch(MusicPlatform.KUWO, listOf(track(MusicPlatform.KUWO, "KW9")))
        val provider = LxAudioSourceProvider(runtime, prefs, listOf(kuwo, FakeSearch(MusicPlatform.QQ, emptyList())))

        assertEquals("http://tx/ok", resolve(provider, "QQMID", MusicPlatform.QQ)?.url)
        assertEquals(listOf("tx" to "QQMID"), runtime.calls)
    }

    @Test
    fun `外部平台直链失败时转到其他平台并用匹配标识`() = runBlocking {
        val runtime = RecordingRuntime(listOf("wy", "kw", "tx"), mapOf(("kw" to "KW9") to "http://kw/9"))
        val kuwo = FakeSearch(MusicPlatform.KUWO, listOf(track(MusicPlatform.KUWO, "KW9")))
        val provider = LxAudioSourceProvider(runtime, prefs, listOf(kuwo, FakeSearch(MusicPlatform.QQ, emptyList())))

        assertEquals("http://kw/9", resolve(provider, "QQMID", MusicPlatform.QQ)?.url)
        assertEquals(listOf("tx" to "QQMID", "kw" to "KW9"), runtime.calls)
        assertTrue("wy 需要网易云 id，外部歌曲不应尝试", runtime.calls.none { it.first == "wy" })
    }

    @Test
    fun `酷狗匹配结果使用 hash`() = runBlocking {
        val hash = "b3a52a7a958bf0aed0ebfba2e9a818b7"
        val runtime = RecordingRuntime(listOf("kg"), mapOf(("kg" to hash) to "http://kg/ok"))
        val kugou = FakeSearch(MusicPlatform.KUGOU, listOf(track(MusicPlatform.KUGOU, hash)))
        val provider = LxAudioSourceProvider(runtime, prefs, listOf(kugou))

        assertNotNull(resolve(provider, "100"))
        assertEquals(listOf("kg" to hash), runtime.calls)
    }

    @Test
    fun `多个插件共用同一次匹配结果`() = runBlocking {
        prefs.saveLxPlugins(
            listOf(
                LxPluginItem(id = "p1", name = "A", sources = listOf("kw")),
                LxPluginItem(id = "p2", name = "B", sources = listOf("kw"))
            )
        )
        val runtime = RecordingRuntime(listOf("kw"), emptyMap())
        val kuwo = FakeSearch(MusicPlatform.KUWO, listOf(track(MusicPlatform.KUWO, "KW9")))
        val provider = LxAudioSourceProvider(runtime, prefs, listOf(kuwo))

        assertNull(resolve(provider, "100"))
        assertEquals(1, kuwo.searched)
        assertEquals(2, runtime.calls.size)
    }

    @Test
    fun `酷狗歌曲信息里带 hash 字段而其他平台没有`() {
        val kg = LxScriptSupport.buildMusicInfoJson("kg", "H1", "歌", "手", "专辑", 1000)
        val kw = LxScriptSupport.buildMusicInfoJson("kw", "123", "歌", "手", "专辑", 1000)
        assertTrue(kg.contains("\"hash\":\"H1\""))
        assertTrue(!kw.contains("\"hash\""))
    }
}
