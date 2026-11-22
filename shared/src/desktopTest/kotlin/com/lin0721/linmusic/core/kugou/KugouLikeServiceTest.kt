package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class KugouLikeServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryPreferencesStore
    private lateinit var userPreferences: UserPreferences
    private lateinit var deviceStore: KugouDeviceStore
    private lateinit var gatewayClient: KugouGatewayClient
    private lateinit var likeService: KugouLikeService
    private val json = Json { ignoreUnknownKeys = true }
    private var clockMs = 1_700_000_000_000L

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString().trimEnd('/')
        val config = KugouConfig(
            initialMode = KugouClientMode.LITE,
            gatewayBaseUrl = baseUrl,
            baseUrlRewriter = { baseUrl }
        )
        store = InMemoryPreferencesStore()
        userPreferences = UserPreferences(store, nowMs = { clockMs })
        userPreferences.upsertPlatformAccount(
            StoredAccount(
                uid = 889900L,
                nickname = "酷狗红心测试",
                platform = MusicPlatform.KUGOU,
                platformUid = "889900",
                credentials = mapOf(
                    "userid" to "889900",
                    "token" to "kg_token_123"
                )
            ),
            activate = true
        )
        deviceStore = KugouDeviceStore(store)
        deviceStore.getOrCreateDeviceIdentity()
        deviceStore.updateDfid("dfid_like_test")
        gatewayClient = KugouGatewayClient(
            config = config,
            deviceStore = deviceStore,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
        likeService = KugouLikeService(
            gatewayClient = gatewayClient,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchLikedTrackMap 解析我喜欢歌单并拉取 hash 到 fileid 映射`() = runTest {
        val requests = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return when (request.requestUrl?.encodedPath) {
                    "/v7/get_all_list" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "error_code": 0,
                          "data": {
                            "info": [
                              {"listid": 1001, "name": "自建歌单", "is_def": 0, "list_create_userid": 889900},
                              {"listid": 2002, "name": "我喜欢", "is_def": 1, "list_create_userid": 889900}
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                    "/v4/get_list_all_file_v3" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "error_code": 0,
                          "data": {
                            "info": [
                              {"hash": "ABCDEF1234", "fileid": 99001},
                              {"hash": "998877AABB", "file_id": 99002}
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val map = likeService.fetchLikedTrackMap().getOrThrow()
        assertEquals(mapOf("abcdef1234" to 99001L, "998877aabb" to 99002L), map)

        val listReq = requests.first { it.requestUrl?.encodedPath == "/v7/get_all_list" }
        assertEquals("cloudlist.service.kugou.com", listReq.getHeader("x-router"))
        val tracksReq = requests.first { it.requestUrl?.encodedPath == "/v4/get_list_all_file_v3" }
        val tracksBody = json.parseToJsonElement(tracksReq.body.readUtf8()).jsonObject
        assertEquals(2002L, tracksBody["listid"]!!.jsonPrimitive.long)
    }

    @Test
    fun `likeTrack 缓存 fileid 后 unlikeTrack 直接删除且冷缓存时先刷新再删`() = runTest {
        val listAllCalls = AtomicInteger(0)
        val deletedFileIds = mutableListOf<Long>()
        val addedBodyRef = mutableListOf<String>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.requestUrl?.encodedPath) {
                    "/v7/get_all_list" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "data": { "info": [{"listid": 2002, "name": "我喜欢", "is_def": 1}] }
                        }
                        """.trimIndent()
                    )
                    "/cloudlist.service/v6/add_song" -> {
                        addedBodyRef += request.body.readUtf8()
                        MockResponse().setResponseCode(200).setBody(
                            """{"status": 1, "error_code": 0, "data": [{"fileid": 778899}]}"""
                        )
                    }
                    "/v4/get_list_all_file_v3" -> {
                        listAllCalls.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "data": { "info": [{"hash": "AABBCCDD", "fileid": 665544}] }
                            }
                            """.trimIndent()
                        )
                    }
                    "/v4/delete_songs" -> {
                        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        val fileId = body["data"]!!.jsonArray.first().jsonObject["fileid"]!!.jsonPrimitive.long
                        deletedFileIds += fileId
                        MockResponse().setResponseCode(200).setBody("""{"status": 1, "error_code": 0}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        // 1. likeTrack 返回并缓存 fileid，紧接着 unlikeTrack 不需要拉取全量列表
        likeService.likeTrack(
            trackName = "测试歌曲",
            hash = "AABBCCDD",
            albumId = "54321",
            mixSongId = 123456L
        ).getOrThrow()

        val addObj = json.parseToJsonElement(addedBodyRef.single()).jsonObject
        val songItem = addObj["data"]!!.jsonArray.first().jsonObject
        assertEquals("aabbccdd", songItem["hash"]!!.jsonPrimitive.content)
        assertEquals(54321L, songItem["album_id"]!!.jsonPrimitive.long)
        assertEquals(123456L, songItem["mixsongid"]!!.jsonPrimitive.long)

        likeService.unlikeTrack("AABBCCDD").getOrThrow()
        assertEquals(listOf(778899L), deletedFileIds)
        assertEquals(0, listAllCalls.get())

        // 2. 清空缓存后再次 unlikeTrack，会先刷新一次我喜欢列表获取 fileid
        likeService.clearCache()
        likeService.unlikeTrack("AABBCCDD").getOrThrow()
        assertTrue(listAllCalls.get() == 1)
        assertEquals(listOf(778899L, 665544L), deletedFileIds)
    }
}
