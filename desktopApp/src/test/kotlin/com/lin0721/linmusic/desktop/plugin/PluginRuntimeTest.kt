package com.lin0721.linmusic.desktop.plugin

import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.plugin.PluginConfirmations
import com.lin0721.linmusic.core.plugin.PluginNeteaseApi
import com.lin0721.linmusic.core.plugin.PluginNeteaseBackend
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.flow.filterNotNull
import com.lin0721.linmusic.core.plugin.InstalledPlugin
import com.lin0721.linmusic.core.plugin.PluginAudioSourceProvider
import com.lin0721.linmusic.core.plugin.PluginCommands
import com.lin0721.linmusic.core.plugin.PluginMenuLocation
import com.lin0721.linmusic.core.plugin.PluginNotices
import com.lin0721.linmusic.core.plugin.PluginPageHost
import com.lin0721.linmusic.core.plugin.PluginLyricsSource
import com.lin0721.linmusic.core.plugin.PluginTrackQuery
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.plugin.PluginHostApi
import com.lin0721.linmusic.core.plugin.PluginHttpResponse
import com.lin0721.linmusic.core.plugin.PluginPaths
import com.lin0721.linmusic.core.plugin.PluginPrelude
import com.lin0721.linmusic.core.plugin.PluginRepository
import com.lin0721.linmusic.core.plugin.PluginRuntime
import com.lin0721.linmusic.core.plugin.PluginStageResult
import com.lin0721.linmusic.core.plugin.PluginStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginRuntimeTest {

    private lateinit var root: File
    private lateinit var store: PluginStore
    private lateinit var runtime: PluginRuntime
    private lateinit var repository: PluginRepository
    private val requestedUrls = mutableListOf<String>()
    private val notices = PluginNotices()
    private lateinit var pageHost: PluginPageHost

    @Before
    fun setUp() {
        root = Files.createTempDirectory("plugin-runtime").toFile()
        store = PluginStore(root)
        val api = PluginHostApi(
            store = store,
            http = { request ->
                requestedUrls += request.url
                PluginHttpResponse(200, emptyMap(), "{\"title\":\"晴天\"}")
            },
            mainDispatcher = Dispatchers.Unconfined,
            notices = notices
        )
        runtime = PluginRuntime(GraalScriptSandboxFactory(), api)
        repository = PluginRepository(store, runtime, api)
        pageHost = PluginPageHost(runtime, api)
    }

    @After
    fun tearDown() {
        runBlocking { repository.installed.value.forEach { runtime.stop(it.id) } }
        root.deleteRecursively()
    }

    private fun install(
        script: String,
        permissions: String = "\"playback.read\",\"storage\",\"network\"",
        contributes: String = "\"events\":[\"songChanged\"],\"pages\":[{\"id\":\"main\",\"title\":\"主页\",\"ui\":{\"type\":\"column\"}}]"
    ) = runBlocking {
        val manifest = """
            {"format":1,"id":"com.example.demo","name":"示例","version":"1.0.0","permissions":[$permissions],
             "network":{"hosts":["api.example.com"]},
             "contributes":{$contributes}}
        """.trimIndent()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(PluginPaths.MANIFEST))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(PluginPaths.SCRIPT))
            zip.write(script.toByteArray())
            zip.closeEntry()
        }
        val staged = (repository.stage(ByteArrayInputStream(out.toByteArray())) as PluginStageResult.Success).staged
        repository.install(staged)
    }

    private fun plugin(): InstalledPlugin = repository.installed.value.single()

    private suspend fun waitFor(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    private fun invokeAction(name: String, args: JsonObject = JsonObject(emptyMap())) = runBlocking {
        runtime.invoke("com.example.demo", PluginPrelude.KIND_ACTION, name, args, 3_000)
    }

    private fun installV2(script: String, permissions: String, contributes: String) = runBlocking {
        val manifest = """{"format":2,"id":"com.example.v2","name":"二代","version":"1.0.0","permissions":[$permissions],"contributes":{$contributes}}"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(PluginPaths.MANIFEST))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(PluginPaths.SCRIPT))
            zip.write(script.toByteArray())
            zip.closeEntry()
        }
        repository.install((repository.stage(ByteArrayInputStream(out.toByteArray())) as PluginStageResult.Success).staged)
    }

    @Test
    fun `命令经菜单入口执行并发出提示`() = runBlocking {
        installV2(
            """
            melodia.register.command('share', async function(song) {
                const mode = await melodia.settings.get('mode');
                await melodia.ui.toast('已处理 ' + song.title + ' ' + mode);
            });
            """.trimIndent(),
            "\"ui.notify\"",
            """"commands":[{"id":"share","title":"分享"}],"menus":[{"command":"share","location":"song"}],
               "settings":[{"id":"mode","type":"select","title":"模式","options":[{"value":"fast"},{"value":"slow"}],"default":"slow"}]"""
        )
        val commands = PluginCommands(runtime.registry, notices)
        waitFor { commands.itemsNow(PluginMenuLocation.SONG).isNotEmpty() }
        val message = async(Dispatchers.Unconfined) { withTimeout(5_000) { notices.messages.first() } }

        commands.run(commands.itemsNow(PluginMenuLocation.SONG).single(), buildJsonObject { put("title", "晴天") })

        assertEquals("二代：已处理 晴天 slow", message.await())
    }

    private class LikeOnlyBackend : PluginNeteaseBackend {
        val liked = mutableListOf<Long>()

        override suspend fun uid(): Long = 1L
        override suspend fun likeSong(id: Long, like: Boolean): Result<Unit> {
            liked += id
            return Result.success(Unit)
        }
        override suspend fun songDetails(ids: List<Long>) = unused<List<Track>>()
        override suspend fun lyrics(id: Long) = unused<List<LyricLine>>()
        override suspend fun search(keyword: String, type: SearchType, offset: Int, limit: Int) = unused<SearchPageResult>()
        override suspend fun playlistDetail(id: Long) = unused<PlaylistDetail>()
        override suspend fun albumDetail(id: Long) = unused<PlaylistDetail>()
        override suspend fun artistDetail(id: Long) = unused<ArtistDetailInfo>()
        override suspend fun artistTopSongs(id: Long) = unused<List<Track>>()
        override suspend fun userPlaylists(uid: Long) = unused<List<UserPlaylist>>()
        override fun likedSongIds() = emptySet<Long>()
        override suspend fun createPlaylist(name: String, privacy: Int) = unused<PlaylistDetail>()
        override suspend fun addTracks(playlistId: Long, trackIds: List<Long>) = unused<Unit>()
        override suspend fun removeTracks(playlistId: Long, trackIds: List<Long>) = unused<Unit>()
        override suspend fun subscribePlaylist(id: Long, subscribe: Boolean) = unused<Unit>()
        override suspend fun subscribeAlbum(id: Long, subscribe: Boolean) = unused<Unit>()
        override suspend fun followArtist(id: Long, follow: Boolean) = unused<Unit>()
        override suspend fun deletePlaylist(id: Long) = unused<Unit>()

        private fun <T> unused(): Result<T> = Result.failure(IllegalStateException("未使用"))
    }

    @Test
    fun `脚本等用户确认写入时不按命令超时处理`() = runBlocking {
        val backend = LikeOnlyBackend()
        val confirmations = PluginConfirmations()
        val api = PluginHostApi(
            store = store,
            mainDispatcher = Dispatchers.Unconfined,
            netease = PluginNeteaseApi(backend, store, confirmations)
        )
        runtime = PluginRuntime(GraalScriptSandboxFactory(), api)
        repository = PluginRepository(store, runtime, api)
        installV2(
            "melodia.register.command('like', function() { return melodia.netease.call('song.like', { id: 9, like: true }); });",
            "\"netease.write\"",
            """"commands":[{"id":"like","title":"红心"}]"""
        )
        waitFor { repository.status.value["com.example.v2"]?.registrations?.contains("command:like") == true }

        val call = async(Dispatchers.Default) { runtime.invoke("com.example.v2", PluginPrelude.KIND_COMMAND, "like", JsonNull, 200) }
        withTimeout(3_000) { confirmations.pending.filterNotNull().first() }
        delay(500)
        confirmations.respond(true)

        assertTrue(call.await() != null)
        assertEquals(listOf(9L), backend.liked)
        assertNull(repository.status.value["com.example.v2"]?.lastError)
    }

    @Test
    fun `没有申请提示权限时不能发提示`() = runBlocking {
        installV2(
            """
            melodia.register.command('toast', async function() {
                try { await melodia.ui.toast('hi'); return 'sent'; } catch (e) { return e.message; }
            });
            """.trimIndent(),
            "",
            """"commands":[{"id":"toast","title":"提示"}]"""
        )
        waitFor { repository.status.value["com.example.v2"]?.registrations?.contains("command:toast") == true }
        val result = runtime.invoke("com.example.v2", PluginPrelude.KIND_COMMAND, "toast", JsonNull, 3_000)
        assertEquals("缺少权限 ui.notify", result!!.jsonPrimitive.content)
    }

    @Test
    fun `设置值写入后脚本能读到`() = runBlocking {
        installV2(
            "melodia.register.action('read', function() { return melodia.settings.all(); });",
            "",
            """"pages":[{"id":"main","title":"主页","ui":{"type":"column"}}],"settings":[{"id":"level","type":"number","title":"等级","min":0,"max":10}]"""
        )
        waitFor { repository.status.value["com.example.v2"]?.registrations?.contains("action:read") == true }
        val manifest = repository.installed.value.single().manifest!!
        pageHost.setSetting(manifest, "level", JsonPrimitive(7))
        val result = runtime.invoke("com.example.v2", PluginPrelude.KIND_ACTION, "read", JsonNull, 3_000) as JsonObject
        assertEquals(7.0, result["level"]!!.jsonPrimitive.content.toDouble(), 0.0)
    }

    @Test
    fun `启用后运行脚本并登记注册的能力`() {
        install("melodia.register.action('ping', function(args) { return { pong: args.n + 1 }; });")
        assertTrue(runtime.isRunning("com.example.demo"))
        runBlocking { waitFor { repository.status.value["com.example.demo"]?.registrations?.contains("action:ping") == true } }
        val result = invokeAction("ping", buildJsonObject { put("n", 41) }) as JsonObject
        assertEquals(42, result["pong"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `脚本经宿主 API 读写数据与访问网络`() {
        install(
            """
            melodia.register.action('run', async function() {
                await melodia.storage.set('visits', 2);
                const visits = await melodia.storage.get('visits');
                const resp = await melodia.http.request({ url: 'https://api.example.com/song' });
                const song = JSON.parse(resp.body);
                let denied = '';
                try { await melodia.http.request({ url: 'https://evil.com/' }); } catch (e) { denied = e.message; }
                return { visits: visits, title: song.title, denied: denied };
            });
            """.trimIndent()
        )
        val result = invokeAction("run") as JsonObject
        assertEquals("2", result["visits"]!!.jsonPrimitive.content)
        assertEquals("晴天", result["title"]!!.jsonPrimitive.content)
        assertTrue(result["denied"]!!.jsonPrimitive.content.contains("evil.com"))
        assertEquals(listOf("https://api.example.com/song"), requestedUrls)
    }

    @Test
    fun `未申请的权限在脚本里调用会被拒绝`() {
        install(
            """
            melodia.register.action('run', async function() {
                try { await melodia.player.pause(); return 'ok'; } catch (e) { return e.message; }
            });
            """.trimIndent()
        )
        assertTrue((invokeAction("run") as JsonPrimitive).content.contains("playback.control"))
    }

    @Test
    fun `脚本加载出错时自动停用并记录原因`() {
        install("this is not javascript")
        runBlocking { waitFor { !plugin().state.enabled } }
        assertFalse(runtime.isRunning("com.example.demo"))
        assertTrue(plugin().state.disabledReason.orEmpty().startsWith("脚本加载失败"))
    }

    @Test
    fun `连续出错达到上限后自动停用，重新启用后恢复`() {
        install("melodia.register.action('boom', function() { throw new Error('坏了'); });")
        repeat(PluginRuntime.MAX_CONSECUTIVE_ERRORS) { assertNull(invokeAction("boom")) }
        runBlocking { waitFor { !plugin().state.enabled } }
        assertTrue(plugin().state.disabledReason.orEmpty().contains("坏了"))
        assertFalse(runtime.isRunning("com.example.demo"))

        runBlocking { repository.setEnabled("com.example.demo", true) }
        assertTrue(plugin().state.enabled)
        assertNull(plugin().state.disabledReason)
        assertTrue(runtime.isRunning("com.example.demo"))
    }

    @Test
    fun `成功调用会清零连续出错计数`() {
        install(
            """
            melodia.register.action('maybe', function(args) { if (args.fail) throw new Error('x'); return 1; });
            """.trimIndent()
        )
        repeat(10) { index ->
            invokeAction("maybe", buildJsonObject { put("fail", index % 2 == 0) })
        }
        assertTrue(plugin().state.enabled)
        assertTrue(runtime.isRunning("com.example.demo"))
    }

    @Test
    fun `调用超时按失败处理`() {
        install("melodia.register.action('slow', function() { return new Promise(function() {}); });")
        val result = runBlocking { runtime.invoke("com.example.demo", PluginPrelude.KIND_ACTION, "slow", JsonNull, 300) }
        assertNull(result)
        assertTrue(repository.status.value["com.example.demo"]?.lastError.orEmpty().contains("没有返回"))
    }

    @Test
    fun `停用与删除插件会关闭沙盒并清理数据`() {
        install("melodia.register.action('save', function() { return melodia.storage.set('a', 1); });")
        invokeAction("save")
        runBlocking { repository.setEnabled("com.example.demo", false) }
        assertFalse(runtime.isRunning("com.example.demo"))
        runBlocking { repository.delete("com.example.demo") }
        assertTrue(repository.installed.value.isEmpty())
        assertFalse(store.dataDirectoryOf("com.example.demo")!!.exists())
    }

    @Test
    fun `沙盒内无法访问宿主类与文件`() {
        install(
            """
            melodia.register.action('probe', function() {
                var result = [];
                try { Java.type('java.io.File'); result.push('java'); } catch (e) {}
                try { if (typeof require === 'function') { require('fs'); result.push('fs'); } } catch (e) {}
                return result.join(',');
            });
            """.trimIndent()
        )
        assertEquals("", (invokeAction("probe") as JsonPrimitive).content)
    }

    private fun installSources(script: String) = install(
        script,
        permissions = "\"audioSource\",\"lyricsSource\"",
        contributes = "\"audioSources\":[{\"id\":\"main\"}],\"lyricsSources\":[{\"id\":\"lrc\"}]"
    )

    private suspend fun waitForRegistration(name: String) =
        waitFor { repository.status.value["com.example.demo"]?.registrations?.contains(name) == true }

    @Test
    fun `插件音源按歌曲信息返回直链`() = runBlocking {
        installSources(
            """
            melodia.register.audioSource('main', {
                resolve: function(p) { return { url: 'https://cdn.example.com/' + p.songId + '-' + p.quality + '.mp3' }; }
            });
            """.trimIndent()
        )
        waitForRegistration("audioSource:main")
        val result = PluginAudioSourceProvider(runtime.registry).resolveUrl("186016", "晴天", "周杰伦", "叶惠美", 269_000, "exhigh")
        assertEquals("https://cdn.example.com/186016-exhigh.mp3", result?.url)
        assertEquals("com.example.demo", result?.pluginId)
    }

    @Test
    fun `插件音源返回试听或非 http 链接时视为没有结果`() = runBlocking {
        installSources(
            """
            melodia.register.audioSource('main', {
                resolve: function(p) { return p.songId === '1' ? 'https://m.example.com/musicrep-ts/x.mp3' : 'file:///etc/passwd'; }
            });
            melodia.register.audioSource('other', { resolve: function() { return 'https://ok.example.com/a.mp3'; } });
            """.trimIndent()
        )
        waitForRegistration("audioSource:other")
        val provider = PluginAudioSourceProvider(runtime.registry)
        assertNull(provider.resolveUrl("1", "a", "b", null, 0, "standard"))
        assertNull(provider.resolveUrl("2", "a", "b", null, 0, "standard"))
    }

    @Test
    fun `插件歌词源返回 LRC 并解析成歌词行`() = runBlocking {
        installSources(
            """
            melodia.register.lyricsSource('lrc', {
                fetch: function(p) { return '[00:01.00]' + p.title + '\n[00:03.50]第二句'; }
            });
            """.trimIndent()
        )
        waitForRegistration("lyricsSource:lrc")
        val lyrics = PluginLyricsSource(runtime.registry)
        assertTrue(lyrics.hasSources())
        val lines = lyrics.fetch(PluginTrackQuery("1", "晴天", "周杰伦", "", 0, MusicPlatform.NETEASE))
        assertEquals(listOf("晴天", "第二句"), lines.map { it.text })
        assertEquals(3_500L, lines[1].timeMs)
    }
}
