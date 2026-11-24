package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.util.Base64
import javax.crypto.Cipher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KugouDiscoverAndCloudServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryPreferencesStore
    private lateinit var userPreferences: UserPreferences
    private lateinit var deviceStore: KugouDeviceStore
    private lateinit var testPrivateKey: RSAPrivateKey
    private lateinit var config: KugouConfig
    private lateinit var gatewayClient: KugouGatewayClient
    private lateinit var discoverService: KugouDiscoverService
    private lateinit var cloudService: KugouCloudService
    private val json = Json { ignoreUnknownKeys = true }
    private var clockMs = 1_700_000_000_000L

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        server.start()

        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        testPrivateKey = keyPair.private as RSAPrivateKey
        val publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getEncoder().encodeToString(keyPair.public.encoded) +
            "\n-----END PUBLIC KEY-----"

        val baseUrl = server.url("/").toString().trimEnd('/')
        config = KugouConfig(
            initialMode = KugouClientMode.LITE,
            gatewayBaseUrl = baseUrl,
            baseUrlRewriter = { baseUrl },
            overridePublicKeyPem = publicKeyPem
        )
        store = InMemoryPreferencesStore()
        userPreferences = UserPreferences(store, nowMs = { clockMs })
        userPreferences.upsertPlatformAccount(
            StoredAccount(
                uid = 889900L,
                nickname = "酷狗测试账号",
                platform = MusicPlatform.KUGOU,
                platformUid = "889900",
                credentials = mapOf(
                    "userid" to "889900",
                    "token" to "kg_token_xyz",
                    "vip_type" to "6"
                )
            ),
            activate = true
        )
        deviceStore = KugouDeviceStore(store)
        deviceStore.getOrCreateDeviceIdentity()
        deviceStore.updateDfid("dfid_discover_cloud_test")

        gatewayClient = KugouGatewayClient(
            config = config,
            deviceStore = deviceStore,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
        discoverService = KugouDiscoverService(config, deviceStore, gatewayClient, userPreferences, nowMs = { clockMs })
        cloudService = KugouCloudService(config, deviceStore, gatewayClient, userPreferences, nowMs = { clockMs })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `KugouDiscoverService 解析真实抓包契约并验证请求头与 clientver 覆盖`() = runTest {
        val homeDiscoverFixture = loadResource("kugou/home_discover.json")
        val topPlaylistFixture = loadResource("kugou/top_playlist.json")
        val rankListFixture = loadResource("kugou/rank_list.json")

        val recorded = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded += request
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/everyday_song_recommend" -> {
                        assertEquals("POST", request.method)
                        assertEquals("everydayrec.service.kugou.com", request.getHeader("x-router"))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {
                                "date": "2026-11-23",
                                "song_count": 1,
                                "song_list": [
                                  {
                                    "hash": "DAILYHASH1",
                                    "songname": "周杰伦 - 晴天",
                                    "timelen": 269000,
                                    "mixsongid": 91001,
                                    "album_id": "8801",
                                    "album_name": "叶惠美",
                                    "sizable_cover": "http://imge.kugou.com/stdmusic/{size}/daily.jpg"
                                  }
                                ]
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    "/v2/personal_recommend" -> {
                        assertEquals("POST", request.method)
                        assertEquals("persnfm.service.kugou.com", request.getHeader("x-router"))
                        val bodyObj = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        val expectedKey = KugouSigner.signParamsKey(clockMs.toString(), mode = KugouClientMode.LITE)
                        assertEquals(expectedKey, bodyObj["key"]?.jsonPrimitive?.content)
                        if (bodyObj["action"]?.jsonPrimitive?.content == "dislike") {
                            assertEquals("fmhash1", bodyObj["hash"]?.jsonPrimitive?.content)
                            assertEquals(50L, bodyObj["playtime"]?.jsonPrimitive?.long)
                            MockResponse().setResponseCode(200).setBody("""{"status": 1, "error_code": 0, "data": {}}""")
                        } else {
                            MockResponse().setResponseCode(200).setBody(
                                """
                                {
                                  "status": 1,
                                  "error_code": 0,
                                  "data": {
                                    "song_pool_id": 77,
                                    "cur_mark": "mark_1",
                                    "song_list": [
                                      {
                                        "hash": "FMHASH1",
                                        "songname": "夜曲",
                                        "author_name": "周杰伦",
                                        "timelen": 226000,
                                        "mixsongid": 92001
                                      }
                                    ]
                                  }
                                }
                                """.trimIndent()
                            )
                        }
                    }
                    "/homediscoverrec/v1/client/home_discover_rec" -> {
                        assertEquals("POST", request.method)
                        assertEquals("20809", url.queryParameter("clientver"))
                        MockResponse().setResponseCode(200).setBody(homeDiscoverFixture)
                    }
                    "/v2/special_recommend" -> {
                        assertEquals("POST", request.method)
                        assertEquals("specialrec.service.kugou.com", request.getHeader("x-router"))
                        val bodyObj = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        val expectedKey = KugouSigner.signParamsKey((clockMs / 1000L).toString(), mode = KugouClientMode.LITE)
                        assertEquals(expectedKey, bodyObj["key"]?.jsonPrimitive?.content)
                        MockResponse().setResponseCode(200).setBody(topPlaylistFixture)
                    }
                    "/ocean/v6/rank/list" -> {
                        assertEquals("GET", request.method)
                        MockResponse().setResponseCode(200).setBody(rankListFixture)
                    }
                    "/openapi/kmr/v2/rank/audio" -> {
                        assertEquals("POST", request.method)
                        assertEquals("369", request.getHeader("kg-tid"))
                        val bodyObj = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        assertEquals(6666L, bodyObj["rank_id"]?.jsonPrimitive?.long)
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {
                                "total": 1,
                                "songlist": [
                                  {
                                    "hash": "RANKHASH1",
                                    "songname": "稻香",
                                    "author_name": "周杰伦",
                                    "timelen": 223000,
                                    "mixsongid": 93001
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

        val daily = discoverService.getDailyRecommendSongs().getOrThrow()
        assertEquals("2026-11-23", daily.date)
        assertEquals("dailyhash1", daily.tracks.single().hash)
        assertEquals("晴天", daily.tracks.single().name)
        assertEquals("https://imge.kugou.com/stdmusic/400/daily.jpg", daily.tracks.single().coverUrl)

        val fm = discoverService.getPersonalFm().getOrThrow()
        assertEquals(77L, fm.songPoolId)
        assertEquals("fmhash1", fm.tracks.single().hash)
        discoverService.getPersonalFm(action = "dislike", hash = "FMHASH1", playTime = 50L).getOrThrow()

        // 即使 LITE 模式默认 clientver=11440，home_discover_rec 也必须显式覆盖为 20809
        val discover = discoverService.getHomeDiscover(pageSize = 2).getOrThrow()
        assertEquals(2, discover.size)
        assertEquals(2810L, discover[0].startTimeMs)
        assertEquals("琵琶曲 (DJ筱轩版)", discover[0].track.name)
        assertEquals(UnifiedAudioQuality.LOSSLESS, discover[0].track.maxQuality)
        assertTrue(discover[0].track.coverUrl.startsWith("https://imge.kugou.com/"))

        val playlists = discoverService.getRecommendedPlaylists(page = 1, pageSize = 2).getOrThrow()
        assertTrue(playlists.hasMore)
        assertEquals(2, playlists.playlists.size)
        assertEquals(PlaylistId.kugou("collection_3_1312812254_37_0"), playlists.playlists[0].preferredPlaylistId())
        assertEquals(listOf("流行", "舞曲", "韩语"), playlists.playlists[0].tags)

        val ranks = discoverService.getRankList().getOrThrow()
        assertTrue(ranks.isNotEmpty())
        assertEquals("TOP500", ranks.first().name)
        assertTrue(ranks.first().topSongs.isNotEmpty())

        val rankTracks = discoverService.getRankTracks(rankId = 6666L).getOrThrow()
        assertEquals("rankhash1", rankTracks.tracks.single().hash)
        assertEquals("稻香", rankTracks.tracks.single().name)
    }

    @Test
    fun `KugouCloudService 完成 mcloudservice AES+RSA 加密查询删除及云盘播放直链签名`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/v1/get_list" -> {
                        val pHex = url.queryParameter("p")!!
                        val portrait = json.parseToJsonElement(decryptPkcs1Hex(pHex)).jsonObject
                        val aesKey = portrait["aes"]!!.jsonPrimitive.content
                        assertEquals(889900L, portrait["uid"]!!.jsonPrimitive.long)
                        assertEquals("kg_token_xyz", portrait["token"]!!.jsonPrimitive.content)

                        val bodyBytes = request.body.readByteArray()
                        val bodyJson = json.parseToJsonElement(
                            KugouCrypto.playlistAesDecrypt(Base64.getEncoder().encodeToString(bodyBytes), aesKey)
                        ).jsonObject
                        assertEquals(1, bodyJson["page"]!!.jsonPrimitive.int)
                        assertEquals(1, bodyJson["getkmr"]!!.jsonPrimitive.int)

                        val respJson = """
                        {
                          "status": 1,
                          "error_code": 0,
                          "data": {
                            "count": 2,
                            "used_size": 10485760,
                            "total_size": 1073741824,
                            "list": [
                              {
                                "kv_id": 5001,
                                "fileid": 5001,
                                "hash": "CLOUDHASH1",
                                "name": "陈奕迅 - 富士山下",
                                "album_name": "What's Going On...?",
                                "album_audio_id": 880011,
                                "audio_id": 770011,
                                "filesize": 5242880,
                                "timelen": 259000,
                                "bitrate": 320,
                                "extname": "mp3",
                                "addtime": "2024-06-01 10:00:00"
                              }
                            ]
                          }
                        }
                        """.trimIndent()
                        val encryptedResp = Base64.getDecoder().decode(KugouCrypto.playlistAesEncrypt(respJson, aesKey).str)
                        MockResponse().setResponseCode(200).setBody(Buffer().write(encryptedResp))
                    }
                    "/bsstrackercdngz/v2/query_musicclound_url" -> {
                        val hash = url.queryParameter("hash")!!
                        assertEquals("cloudhash1", hash)
                        assertEquals("20026", url.queryParameter("pid"))
                        assertEquals(KugouSigner.signCloudKey(hash, "20026"), url.queryParameter("key"))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {
                                "url": "https://cdn.kugou.com/cloud/fujisan.mp3",
                                "backup_url": ["https://cdn2.kugou.com/cloud/fujisan.mp3"],
                                "extname": "mp3",
                                "filesize": 5242880,
                                "bitrate": 320,
                                "timelen": 259000
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    "/v1/del_files" -> {
                        val pHex = url.queryParameter("p")!!
                        val portrait = json.parseToJsonElement(decryptPkcs1Hex(pHex)).jsonObject
                        val aesKey = portrait["aes"]!!.jsonPrimitive.content
                        val bodyBytes = request.body.readByteArray()
                        val bodyJson = json.parseToJsonElement(
                            KugouCrypto.playlistAesDecrypt(Base64.getEncoder().encodeToString(bodyBytes), aesKey)
                        ).jsonObject
                        val firstItem = bodyJson["data"]!!.jsonArray.single().jsonObject
                        assertEquals(5001L, firstItem["kv_id"]!!.jsonPrimitive.long)
                        assertEquals(880011L, firstItem["album_audio_id"]!!.jsonPrimitive.long)

                        val encryptedResp = Base64.getDecoder().decode(
                            KugouCrypto.playlistAesEncrypt("""{"status": 1, "error_code": 0}""", aesKey).str
                        )
                        MockResponse().setResponseCode(200).setBody(Buffer().write(encryptedResp))
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val page = cloudService.getCloudList(page = 1, pageSize = 30).getOrThrow()
        assertEquals(1, page.items.size)
        val item = page.items.single()
        assertEquals(5001L, item.kvId)
        assertEquals("富士山下", item.name)
        assertEquals("陈奕迅", item.singerName)
        assertEquals(1073741824L, page.quota.maxBytes)

        val unified = item.toUnifiedTrack()
        assertTrue(unified.kugouMeta?.isCloudTrack == true)
        assertEquals(770011L, unified.kugouMeta?.cloudAudioId)

        val urlRes = cloudService.getCloudPlayUrl(
            hash = item.hash,
            albumAudioId = item.albumAudioId,
            audioId = item.audioId,
            name = item.name
        ).getOrThrow()
        assertEquals("https://cdn.kugou.com/cloud/fujisan.mp3", urlRes.url)
        assertEquals(listOf("https://cdn2.kugou.com/cloud/fujisan.mp3"), urlRes.backupUrls)

        cloudService.deleteCloudFiles(listOf(KugouCloudDeleteItem(kvId = item.kvId, albumAudioId = item.albumAudioId))).getOrThrow()
    }

    @Test
    fun `getNewSongs getNewAlbums getStyleRecommend 解析脱敏样本并校验路径`() = runTest {
        val requests = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return when (request.requestUrl?.encodedPath) {
                    "/musicadservice/container/v1/newsong_publish" ->
                        MockResponse().setResponseCode(200).setBody(loadResource("kugou/kugou_top_song.json"))
                    "/musicadservice/v1/mobile_newalbum_sp" ->
                        MockResponse().setResponseCode(200).setBody(loadResource("kugou/kugou_top_album.json"))
                    "/everydayrec.service/everyday_style_recommend" ->
                        MockResponse().setResponseCode(200).setBody(loadResource("kugou/kugou_style_rec.json"))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val newSongs = discoverService.getNewSongs().getOrThrow()
        assertTrue(newSongs.isNotEmpty())
        assertEquals("沉默交响", newSongs.first().name)
        assertTrue(newSongs.first().coverUrl.isNotBlank() && !newSongs.first().coverUrl.contains("{size}"))

        val newAlbums = discoverService.getNewAlbums().getOrThrow()
        assertTrue(newAlbums.isNotEmpty())
        assertTrue(newAlbums.first().albumId > 0L)
        assertTrue(newAlbums.first().coverUrl.isNotBlank() && !newAlbums.first().coverUrl.contains("{size}"))

        val styleRec = discoverService.getStyleRecommend().getOrThrow()
        assertEquals("华语流行", styleRec.title)
        assertTrue(styleRec.tracks.isNotEmpty())
        assertEquals("千里之外", styleRec.tracks.first().name)

        assertEquals(3, requests.size)
        assertTrue(requests.all { it.method == "POST" })
    }

    private fun loadResource(path: String): String {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "缺少测试资源: $path" }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun decryptPkcs1Hex(hexCipher: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, testPrivateKey)
        return cipher.doFinal(KugouCrypto.hexToBytes(hexCipher)).toString(Charsets.UTF_8)
    }
}
