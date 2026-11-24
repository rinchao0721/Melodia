package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
import com.lin0721.linmusic.core.player.KugouPlaybackProvider
import com.lin0721.linmusic.core.player.PlaybackTrackMeta
import com.lin0721.linmusic.core.player.PlatformPlaybackRouter
import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Deflater

class KugouPlaybackServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryPreferencesStore
    private lateinit var userPreferences: UserPreferences
    private lateinit var deviceStore: KugouDeviceStore
    private lateinit var config: KugouConfig
    private lateinit var gatewayClient: KugouGatewayClient
    private lateinit var playbackService: KugouPlaybackService
    private lateinit var lyricsCacheDir: java.io.File
    private lateinit var lyricsCache: LyricsCache
    private lateinit var playbackProvider: KugouPlaybackProvider
    private lateinit var router: PlatformPlaybackRouter

    private var clockMs = 1_700_000_123_456L
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        server.start()

        val baseUrl = server.url("/").toString().trimEnd('/')
        config = KugouConfig(
            initialMode = KugouClientMode.LITE,
            gatewayBaseUrl = baseUrl,
            baseUrlRewriter = { baseUrl }
        )
        store = InMemoryPreferencesStore()
        userPreferences = UserPreferences(store, nowMs = { clockMs })
        userPreferences.upsertPlatformAccount(
            StoredAccount(
                uid = 889900L,
                nickname = "酷狗测试用户",
                platform = MusicPlatform.KUGOU,
                platformUid = "889900",
                credentials = mapOf(
                    "userid" to "889900",
                    "token" to "kg_token_abc",
                    "vip_token" to "kg_vip_token",
                    "vip_type" to "6",
                    "auth" to "kg_auth"
                )
            ),
            activate = true
        )
        deviceStore = KugouDeviceStore(store)
        deviceStore.getOrCreateDeviceIdentity()
        deviceStore.updateDfid("dfid_test_123")

        gatewayClient = KugouGatewayClient(
            config = config,
            deviceStore = deviceStore,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
        playbackService = KugouPlaybackService(
            config = config,
            deviceStore = deviceStore,
            gatewayClient = gatewayClient,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
        lyricsCacheDir = Files.createTempDirectory("kugou-lyrics-cache-test").toFile()
        lyricsCache = LyricsCache(lyricsCacheDir, now = { clockMs })
        playbackProvider = KugouPlaybackProvider(playbackService, lyricsCache)
        router = PlatformPlaybackRouter(listOf(playbackProvider))
    }

    @After
    fun tearDown() {
        server.shutdown()
        lyricsCacheDir.deleteRecursively()
    }

    @Test
    fun `getPrivilegeLite 解析真实响应并映射最高音质与各档位 hash`() = runTest {
        val fixture = loadFixture("kugou/privilege_lite.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture))

        val hash = "B5E3CA48FC0D01F01DB0B6BBA570480F"
        val results = playbackService.getPrivilegeLite(
            hashes = listOf(hash),
            albumIds = listOf("215644565")
        ).getOrThrow()

        val recorded = server.takeRequest()
        assertEquals("/v2/get_res_privilege/lite", recorded.requestUrl?.encodedPath)
        assertEquals("media.store.kugou.com", recorded.getHeader("x-router"))
        assertNotNull(recorded.requestUrl?.queryParameter("signature"))

        val reqBody = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val resourceObj = reqBody["resource"]!!.jsonArray.first().jsonObject
        // privilege_lite.js 硬约束：resource 内不能传 album_audio_id，否则酷狗省略 128 ogg
        assertFalse(resourceObj.containsKey("album_audio_id"))
        assertEquals(hash.lowercase(), resourceObj["hash"]!!.jsonPrimitive.content)

        assertEquals(1, results.size)
        val priv = results.first()
        assertEquals(hash.lowercase(), priv.hash)
        assertEquals("215644565", priv.albumId)
        assertEquals(988834222L, priv.albumAudioId)
        assertEquals(1109118629L, priv.mixSongId)
        assertEquals("张赫煊 - 以我", priv.name)
        assertEquals("06678c77a90c17e59367329ee6ca63d8", priv.ogg128Hash)
        assertEquals("10b06d5a2f166b242bee84959a6e2507", priv.ogg320Hash)
        assertTrue(priv.freeListen)
        assertTrue("128" in priv.freeQualities)
        assertTrue("flac" in priv.vipQualities)
        assertTrue("viper_atmos" in priv.superVipQualities)
        assertEquals(UnifiedAudioQuality.MASTER, priv.highestQuality)
        assertEquals("2ae08f884a79494d68a481373278215f", priv.qualityHashes["flac"])
    }

    @Test
    fun `getSongUrlNew 与 getSongUrlLegacy 正确签名并识别试听片段`() = runTest {
        val hash = "B5E3CA48FC0D01F01DB0B6BBA570480F"
        val device = deviceStore.getOrCreateDeviceIdentity()
        val expectedTrackerKey = KugouSigner.signKey(
            hash = hash.lowercase(),
            mid = device.mid,
            userid = 889900L,
            appid = config.effectiveAppId(),
            mode = config.mode
        )

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status": 1,
                  "error_code": 0,
                  "data": {
                    "goods": [
                      {
                        "quality": "320",
                        "hash": "93DB97567735C525B737C3BAA3E70D59",
                        "extName": "mp3",
                        "fileSize": 9191154,
                        "bitRate": 320,
                        "timeLength": 229,
                        "is_free_part": 1,
                        "volume": -11.5,
                        "volume_peak": 0.96,
                        "url": ["https://cdn.kugou.com/preview_trial.mp3", "https://backup.kugou.com/trial.mp3"]
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        val newRes = playbackService.getSongUrlNew(
            hash = hash,
            albumId = "215644565",
            albumAudioId = 988834222L,
            quality = UnifiedAudioQuality.EXHIGH
        ).getOrThrow()

        val reqNew = server.takeRequest()
        assertEquals("/v6/priv_url", reqNew.requestUrl?.encodedPath)
        assertEquals("tracker.kugou.com", reqNew.getHeader("x-router"))
        val newBody = json.parseToJsonElement(reqNew.body.readUtf8()).jsonObject
        val trackerParam = newBody["tracker_param"]!!.jsonObject
        assertEquals(expectedTrackerKey, trackerParam["key"]!!.jsonPrimitive.content)

        assertTrue(newRes.isFreeTrial)
        assertEquals("https://cdn.kugou.com/preview_trial.mp3", newRes.url)
        assertEquals(listOf("https://backup.kugou.com/trial.mp3"), newRes.backupUrls)
        assertEquals(UnifiedAudioQuality.EXHIGH, newRes.unifiedQuality)
        assertEquals(229_000L, newRes.durationMs)
        assertEquals(-11.5, newRes.volume ?: 0.0, 0.001)

        // 测试备选 /v5/url
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status": 1,
                  "extName": "mp3",
                  "fileSize": 3676601,
                  "bitRate": 128,
                  "timeLength": 229,
                  "url": ["https://trackercdn.kugou.com/full_song.mp3"]
                }
                """.trimIndent()
            )
        )
        val legacyRes = playbackService.getSongUrlLegacy(
            hash = hash,
            albumId = "215644565",
            quality = UnifiedAudioQuality.STANDARD
        ).getOrThrow()

        val reqLegacy = server.takeRequest()
        assertEquals("/v5/url", reqLegacy.requestUrl?.encodedPath)
        assertEquals("trackercdn.kugou.com", reqLegacy.getHeader("x-router"))
        assertEquals("11430", reqLegacy.requestUrl?.queryParameter("clientver"))
        // notSignature = true
        // song_url.js 传的 notSign 不被 request.js 识别，参考实现实际会签名
        assertNotNull(reqLegacy.requestUrl?.queryParameter("signature"))
        assertNotNull(reqLegacy.requestUrl?.queryParameter("key"))

        assertFalse(legacyRes.isFreeTrial)
        assertEquals("https://trackercdn.kugou.com/full_song.mp3", legacyRes.url)
        assertEquals(UnifiedAudioQuality.STANDARD, legacyRes.unifiedQuality)
    }

    @Test
    fun `getSongUrlNew 无 album_audio_id 时省略该字段，privilege 专辑 id 为 0 时回落推荐专辑`() = runTest {
        val okBody = """{"status": 1, "error_code": 0, "data": {"goods": [{"quality": "128", "hash": "AA11", "url": ["https://cdn.kugou.com/a.mp3"]}]}}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))

        playbackService.getSongUrlNew(hash = "AA11").getOrThrow()
        val noAa = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        // 传 0 会被服务端判为 unmarshal failed（20010），缺省时必须不带
        assertFalse(noAa["resource"]!!.jsonObject.containsKey("album_audio_id"))

        playbackService.getSongUrlNew(hash = "AA11", albumAudioId = 0L).getOrThrow()
        val zeroAa = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertFalse(zeroAa["resource"]!!.jsonObject.containsKey("album_audio_id"))

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"status": 1, "error_code": 0, "data": [{"hash": "BB22", "album_id": "0", "recommend_album_id": "15104925",
                    "album_audio_id": 130226221, "name": "keshi - 2 soon", "singername": "keshi", "relate_goods": []}]}"""
            )
        )
        val priv = playbackService.getPrivilegeLite(listOf("BB22")).getOrThrow().single()
        server.takeRequest()
        assertEquals("15104925", priv.albumId)
    }

    @Test
    fun `getAudioMetadata searchLyrics downloadLyrics(KRC+LRC) 与 playhistory 接口均正常解析`() = runTest {
        val sampleKrcText = """
            [id:123]
            [1000,1200]<0,600,0>测<600,600,0>试
        """.trimIndent()
        val krcBase64 = encodeKrcBase64(sampleKrcText)
        val lrcBase64 = Base64.getEncoder().encodeToString("[00:01.00]普通LRC".toByteArray(Charsets.UTF_8))

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.requestUrl?.encodedPath) {
                    "/v1/audio/audio" -> {
                        assertEquals("kmr.service.kugou.com", request.getHeader("x-router"))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": [
                                {
                                  "hash": "abc123",
                                  "audio_id": 7788,
                                  "album_id": "200",
                                  "album_audio_id": 9900,
                                  "songname": "以我",
                                  "author_name": "张赫煊",
                                  "album_name": "以我",
                                  "duration": 229,
                                  "filesize": 9000000,
                                  "bitrate": 320,
                                  "extname": "mp3",
                                  "trans_param": {
                                    "union_cover": "http://imge.kugou.com/stdmusic/{size}/cover.jpg"
                                  }
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    "/v1/search" -> {
                        assertNull(request.requestUrl?.queryParameter("signature"))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 200,
                              "candidates": [
                                { "id": "101", "accesskey": "acc_low", "score": 40, "song": "以我", "singer": "张赫煊", "duration": 229000, "fmt": "krc" },
                                { "id": "102", "accesskey": "acc_high", "score": 95, "song": "以我", "singer": "张赫煊", "duration": 229000, "fmt": "krc" }
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    "/download" -> {
                        val fmt = request.requestUrl?.queryParameter("fmt")
                        val payload = if (fmt == "lrc") lrcBase64 else krcBase64
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 200,
                              "contenttype": 0,
                              "fmt": "${fmt ?: "krc"}",
                              "content": "$payload"
                            }
                            """.trimIndent()
                        )
                    }
                    "/playhistory/v1/upload_songs" -> {
                        assertEquals("3", request.requestUrl?.queryParameter("plat"))
                        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        val firstSong = body["songs"]!!.jsonArray.first().jsonObject
                        assertEquals("1109118629", firstSong["mxid"]!!.jsonPrimitive.content)
                        assertEquals("1", firstSong["op"]!!.jsonPrimitive.content)
                        MockResponse().setResponseCode(200).setBody("""{"status":1,"error_code":0}""")
                    }
                    "/playhistory/v1/get_songs" -> {
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {
                                "bp": "next_cursor_2",
                                "has_more": 1,
                                "songs": [
                                  {
                                    "mxid": 1109118629,
                                    "hash": "B5E3CA48FC0D01F01DB0B6BBA570480F",
                                    "name": "以我",
                                    "singername": "张赫煊",
                                    "album_id": "215644565",
                                    "album_name": "以我",
                                    "album_audio_id": 988834222,
                                    "ot": 1700000100,
                                    "pc": 2,
                                    "duration": 229,
                                    "image": "http://imge.kugou.com/stdmusic/{size}/a.jpg"
                                  }
                                ]
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val metas = playbackService.getAudioMetadata(listOf("abc123")).getOrThrow()
        assertEquals(1, metas.size)
        assertEquals(229_000L, metas[0].durationMs)
        assertEquals("https://imge.kugou.com/stdmusic/400/cover.jpg", metas[0].coverUrl)

        val candidates = playbackService.searchLyrics(hash = "abc123", keyword = "张赫煊 - 以我", durationMs = 229_000L).getOrThrow()
        assertEquals(2, candidates.size)
        assertEquals("102", candidates.maxByOrNull { it.score }?.id)

        val krcRes = playbackService.downloadLyrics("102", "acc_high", "krc").getOrThrow()
        assertEquals(sampleKrcText, krcRes.decodedContent)

        val lrcRes = playbackService.downloadLyrics("102", "acc_high", "lrc").getOrThrow()
        assertEquals("[00:01.00]普通LRC", lrcRes.decodedContent)

        assertTrue(playbackService.uploadPlayHistory(1109118629L, 120L).isSuccess)

        val historyPage = playbackService.getUserPlayHistory().getOrThrow()
        assertEquals("next_cursor_2", historyPage.bp)
        assertTrue(historyPage.hasMore)
        assertEquals(1, historyPage.items.size)
        assertEquals(1109118629L, historyPage.items[0].mixSongId)
        assertEquals("https://imge.kugou.com/stdmusic/400/a.jpg", historyPage.items[0].coverUrl)
    }

    @Test
    fun `PlatformPlaybackRouter 与 KugouPlaybackProvider 支持试听回退 歌词缓存与批量音质查询`() = runTest {
        val sampleKrc = "[1000,1000]<0,500,0>你<500,500,0>好"
        val krcB64 = encodeKrcBase64(sampleKrc)
        val fixture = loadFixture("kugou/privilege_lite.json")
        val lyricSearchCalls = AtomicInteger(0)
        val historyCalls = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.requestUrl?.encodedPath) {
                    "/v6/priv_url" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "data": {
                            "quality": "128",
                            "is_free_part": 1,
                            "url": ["https://cdn.kugou.com/trial.mp3"]
                          }
                        }
                        """.trimIndent()
                    )
                    "/v5/url" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "quality": "320",
                          "url": ["https://cdn.kugou.com/full_320.mp3"]
                        }
                        """.trimIndent()
                    )
                    "/v1/search" -> {
                        lyricSearchCalls.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 200,
                              "candidates": [
                                { "id": "555", "accesskey": "k_555", "score": 100, "fmt": "krc" }
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    "/download" -> MockResponse().setResponseCode(200).setBody(
                        """{"status":200,"contenttype":0,"fmt":"krc","content":"$krcB64"}"""
                    )
                    "/playhistory/v1/upload_songs" -> {
                        historyCalls.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody("""{"status":1,"error_code":0}""")
                    }
                    "/v2/get_res_privilege/lite" -> MockResponse().setResponseCode(200).setBody(fixture)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val trackId = TrackId.kugou("B5E3CA48FC0D01F01DB0B6BBA570480F")
        val meta = PlaybackTrackMeta(
            title = "以我",
            artists = "张赫煊",
            durationMs = 229_000L,
            kugouMeta = KugouTrackMeta(
                hash = trackId.rawId,
                mixSongId = 1109118629L,
                albumAudioId = 988834222L,
                albumId = "215644565"
            )
        )

        // 主链路试听时应自动回退 /v5/url 获取非试听完整直链
        val playbackInfo = router.resolvePlayback(trackId, UnifiedAudioQuality.EXHIGH, meta).getOrThrow()
        assertEquals("https://cdn.kugou.com/full_320.mp3", playbackInfo.url)
        assertFalse(playbackInfo.isFreeTrial)
        assertEquals("exhigh", playbackInfo.level)

        // 首次解析歌词走网络并写入 LyricsCache.Namespace.KRC
        val lines1 = router.getLyrics(trackId, meta).getOrThrow()
        assertEquals(1, lines1.size)
        assertEquals("你好", lines1[0].text)
        assertEquals(2, lines1[0].words.size)
        assertEquals(1, lyricSearchCalls.get())

        // 第二次命中磁盘缓存，不再发网络请求
        val lines2 = router.getLyrics(trackId, meta).getOrThrow()
        assertEquals("你好", lines2[0].text)
        assertEquals(1, lyricSearchCalls.get())

        // 上报播放结束触发 playhistory 上传
        assertTrue(router.reportPlayEnd(trackId, playedSeconds = 180L, meta = meta).isSuccess)
        assertEquals(1, historyCalls.get())

        // 批量音质路由查询
        val levels = router.getQualityLevels(listOf(trackId)).getOrThrow()
        assertEquals(UnifiedAudioQuality.MASTER, levels[trackId])
    }

    @Test
    fun `KugouPlaybackProvider 在 isEnabled 为 false 时跳过全部官方网络请求`() = runTest {
        val disabledProvider = KugouPlaybackProvider(
            playbackService = playbackService,
            lyricsCache = lyricsCache,
            isEnabled = { false }
        )
        val disabledRouter = PlatformPlaybackRouter(listOf(disabledProvider))
        val trackId = TrackId.kugou("B5E3CA48FC0D01F01DB0B6BBA570480F")
        val meta = PlaybackTrackMeta(
            title = "以我",
            artists = "张赫煊",
            kugouMeta = KugouTrackMeta(hash = trackId.rawId, mixSongId = 1109118629L)
        )

        assertTrue(disabledRouter.resolvePlayback(trackId, UnifiedAudioQuality.EXHIGH, meta).isFailure)
        assertTrue(disabledRouter.getLyrics(trackId, meta).isFailure)
        assertTrue(disabledRouter.reportPlayEnd(trackId, playedSeconds = 60L, meta = meta).isSuccess)
        assertTrue(disabledRouter.getQualityLevels(listOf(trackId)).getOrThrow().isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `auth 缺失时 getSongUrlNew 按需补齐 verifyPlaybackAuth 并在失败后进入 5 分钟冷却`() = runTest {
        // 移除 setUp 预置的含 auth 账号，重新写入无 auth 凭据的酷狗账号，模拟老数据或短信登录缺少 auth
        userPreferences.removePlatformAccount(AccountId.kugou("889900"))
        userPreferences.upsertPlatformAccount(
            StoredAccount(
                uid = 889900L,
                nickname = "酷狗测试用户",
                platform = MusicPlatform.KUGOU,
                platformUid = "889900",
                credentials = mapOf(
                    "userid" to "889900",
                    "token" to "kg_token_abc",
                    "vip_token" to "kg_vip_token",
                    "vip_type" to "6"
                )
            ),
            activate = true
        )

        val verifyCalls = AtomicInteger(0)
        var verifyShouldSucceed = false
        val serviceWithVerify = KugouPlaybackService(
            config = config,
            deviceStore = deviceStore,
            gatewayClient = gatewayClient,
            userPreferences = userPreferences,
            nowMs = { clockMs },
            verifyPlaybackAuth = {
                if (verifyCalls.incrementAndGet() >= 2 && verifyShouldSucceed) {
                    Result.success("backfilled_auth_999")
                } else {
                    Result.failure(IllegalStateException("临时失败"))
                }
            }
        )

        repeat(2) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"status":1,"data":{"quality":"128","url":["https://cdn.kugou.com/128.mp3"]}}"""
                )
            )
        }

        // 首次失败触发冷却，1 分钟内第二次不重复请求 verifyPlaybackAuth
        serviceWithVerify.getSongUrlNew(hash = "B5E3CA48FC0D01F01DB0B6BBA570480F").getOrThrow()
        assertEquals(1, verifyCalls.get())
        clockMs += 60_000L
        serviceWithVerify.getSongUrlNew(hash = "B5E3CA48FC0D01F01DB0B6BBA570480F").getOrThrow()
        assertEquals(1, verifyCalls.get())

        // 超过 5 分钟冷却后再次触发并成功补齐
        clockMs += 5 * 60_000L
        verifyShouldSucceed = true
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"status":1,"data":{"quality":"128","url":["https://cdn.kugou.com/128.mp3"]}}"""
            )
        )
        serviceWithVerify.getSongUrlNew(hash = "B5E3CA48FC0D01F01DB0B6BBA570480F").getOrThrow()
        assertEquals(2, verifyCalls.get())
    }

    private fun encodeKrcBase64(plainText: String): String {
        val krcKey = byteArrayOf(
            64, 71, 97, 119, 94, 50, 116, 71,
            81, 54, 49, 45, 206.toByte(), 210.toByte(), 110, 105
        )
        val input = plainText.toByteArray(Charsets.UTF_8)
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val deflated = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            deflated.write(buf, 0, n)
        }
        deflater.end()
        val compressed = deflated.toByteArray()

        val result = ByteArray(4 + compressed.size)
        result[0] = 'k'.code.toByte()
        result[1] = 'r'.code.toByte()
        result[2] = 'c'.code.toByte()
        result[3] = '1'.code.toByte()
        for (i in compressed.indices) {
            result[i + 4] = (compressed[i].toInt() xor krcKey[i % krcKey.size].toInt()).toByte()
        }
        return Base64.getEncoder().encodeToString(result)
    }

    private fun loadFixture(path: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(path)
            ?: error("找不到测试资源: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
