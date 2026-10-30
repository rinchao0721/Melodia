package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRegistryTest {

    private class FakeAudioSource(private val url: String?) : AudioSourceExtension {
        var calls = 0

        override suspend fun resolve(query: PluginTrackQuery, quality: String): JsonElement? {
            calls++
            return url?.let(::JsonPrimitive)
        }
    }

    private class FakeLyricsSource(private val lrc: String?) : LyricsSourceExtension {
        override suspend fun fetch(query: PluginTrackQuery): JsonElement? = lrc?.let(::JsonPrimitive)
    }

    private val registry = ExtensionRegistry()

    private fun audio(pluginId: String, name: String, localId: String, order: Int, source: AudioSourceExtension) =
        Extension(ExtensionPoints.AUDIO_SOURCE, pluginId, name, localId, source, order)

    @Test
    fun `按顺序合并的扩展点按插件名称和声明顺序排列`() {
        val source = FakeAudioSource(null)
        registry.register(audio("c.z", "Z", "main", 0, source))
        registry.register(audio("c.a", "A", "second", 1, source))
        registry.register(audio("c.a", "A", "first", 0, source))

        assertEquals(listOf("first", "second", "main"), registry.get(ExtensionPoints.AUDIO_SOURCE).map { it.localId })
    }

    @Test
    fun `同一注册项重复注册时替换旧值`() {
        val old = FakeAudioSource("https://old")
        val new = FakeAudioSource("https://new")
        registry.register(audio("c.a", "A", "main", 0, old))
        registry.register(audio("c.a", "A", "main", 0, new))

        assertEquals(new, registry.get(ExtensionPoints.AUDIO_SOURCE).single().value)
    }

    @Test
    fun `注销只影响指定插件和扩展点`() {
        val source = FakeAudioSource(null)
        registry.register(audio("c.a", "A", "main", 0, source))
        registry.register(audio("c.b", "B", "main", 0, source))
        registry.register(Extension(ExtensionPoints.LYRICS_SOURCE, "c.a", "A", "main", FakeLyricsSource(null)))

        registry.unregister("c.a", setOf(ExtensionPoints.AUDIO_SOURCE))

        assertEquals(listOf("c.b"), registry.get(ExtensionPoints.AUDIO_SOURCE).map { it.pluginId })
        assertEquals(1, registry.get(ExtensionPoints.LYRICS_SOURCE).size)
    }

    @Test
    fun `整点替换不影响其他扩展点`() {
        val source = FakeAudioSource(null)
        registry.register(audio("c.a", "A", "main", 0, source))
        registry.register(Extension(ExtensionPoints.LYRICS_SOURCE, "c.a", "A", "main", FakeLyricsSource(null)))

        registry.replace(ExtensionPoints.AUDIO_SOURCE, emptyList())

        assertTrue(registry.get(ExtensionPoints.AUDIO_SOURCE).isEmpty())
        assertEquals(1, registry.get(ExtensionPoints.LYRICS_SOURCE).size)
    }

    @Test
    fun `音源入口可以使用非脚本实现并按顺序回退`() = runBlocking {
        val empty = FakeAudioSource(null)
        val working = FakeAudioSource("https://example.com/a.mp3")
        val unused = FakeAudioSource("https://example.com/b.mp3")
        registry.register(audio("c.a", "A", "main", 0, empty))
        registry.register(audio("c.b", "B", "main", 0, working))
        registry.register(audio("c.c", "C", "main", 0, unused))

        val result = PluginAudioSourceProvider(registry).resolveUrl("1", "歌", "歌手", "专辑", 1000, "exhigh")

        assertEquals("https://example.com/a.mp3", result?.url)
        assertEquals("c.b", result?.pluginId)
        assertEquals(1, empty.calls)
        assertEquals(0, unused.calls)
    }

    @Test
    fun `没有音源时返回空`() = runBlocking {
        assertNull(PluginAudioSourceProvider(registry).resolveUrl("1", "歌", "歌手", "专辑", 1000, "exhigh"))
    }

    @Test
    fun `歌词入口按名称顺序取第一份非空结果`() = runBlocking {
        val lyrics = PluginLyricsSource(registry)
        assertFalse(lyrics.hasSources())
        registry.register(Extension(ExtensionPoints.LYRICS_SOURCE, "c.b", "B", "main", FakeLyricsSource("[00:01.00]第二")))
        registry.register(Extension(ExtensionPoints.LYRICS_SOURCE, "c.a", "A", "main", FakeLyricsSource(null)))
        registry.register(Extension(ExtensionPoints.LYRICS_SOURCE, "c.c", "C", "main", FakeLyricsSource("[00:01.00]第三")))

        val lines = lyrics.fetch(PluginTrackQuery("1", "歌", "歌手", "专辑", 1000, MusicPlatform.NETEASE))

        assertTrue(lyrics.hasSources())
        assertEquals("第二", lines.single().text)
    }
}
