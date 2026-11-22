package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.util.Base64
import javax.crypto.Cipher

class KugouDomainServicesTest {

    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryPreferencesStore
    private lateinit var userPreferences: UserPreferences
    private lateinit var deviceStore: KugouDeviceStore
    private lateinit var testPrivateKey: RSAPrivateKey
    private lateinit var config: KugouConfig
    private lateinit var gatewayClient: KugouGatewayClient
    private lateinit var playlistService: KugouPlaylistService
    private lateinit var searchService: KugouSearchService
    private lateinit var artistService: KugouArtistService
    private lateinit var albumService: KugouAlbumService
    private lateinit var commentService: KugouCommentService
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
                    "token" to "kg_token_xyz"
                )
            ),
            activate = true
        )
        deviceStore = KugouDeviceStore(store)
        deviceStore.getOrCreateDeviceIdentity()
        deviceStore.updateDfid("dfid_domain_test")

        gatewayClient = KugouGatewayClient(
            config = config,
            deviceStore = deviceStore,
            userPreferences = userPreferences,
            nowMs = { clockMs }
        )
        playlistService = KugouPlaylistService(config, gatewayClient, userPreferences, nowMs = { clockMs })
        searchService = KugouSearchService(config, gatewayClient)
        artistService = KugouArtistService(config, deviceStore, gatewayClient, userPreferences, nowMs = { clockMs })
        albumService = KugouAlbumService(gatewayClient)
        commentService = KugouCommentService(config, deviceStore, gatewayClient, userPreferences, nowMs = { clockMs })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `KugouPlaylistService 读取用户歌单与公开及云歌单详情曲目`() = runTest {
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
                            "total_ver": 1002,
                            "info": [
                              {
                                "listid": 2001,
                                "global_collection_id": "collection_3_889900_1_0",
                                "name": "我喜欢",
                                "pic": "http://imge.kugou.com/soft/collection/{size}/a.jpg",
                                "count": 12,
                                "is_def": 1,
                                "is_pri": 0,
                                "type": 0,
                                "list_ver": 45,
                                "total_ver": 1002,
                                "list_create_userid": 889900,
                                "list_create_username": "小酷"
                              },
                              {
                                "listid": 2002,
                                "global_collection_id": "collection_3_555_9_0",
                                "name": "收藏歌单",
                                "count": 5,
                                "is_def": 0,
                                "type": 1,
                                "list_create_userid": 555
                              }
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                    "/v3/get_list_info" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "error_code": 0,
                          "data": [
                            {
                              "listid": 3001,
                              "global_collection_id": "collection_3_555_9_0",
                              "name": "公开精选",
                              "pic": "http://imge.kugou.com/{size}/cover.jpg",
                              "intro": "测试简介",
                              "count": 1,
                              "list_create_userid": 555,
                              "list_create_username": "歌单作者"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                    "/pubsongs/v2/get_other_list_file_nofilt" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "error_code": 0,
                          "data": {
                            "count": 1,
                            "songs": [
                              {
                                "hash": "AABB1122",
                                "name": "周杰伦 - 晴天",
                                "timelen": 269000,
                                "mixsongid": 91001,
                                "album_id": "8801",
                                "albuminfo": {"name": "叶惠美"},
                                "singerinfo": [{"id": 3520, "name": "周杰伦"}],
                                "cover": "http://imge.kugou.com/{size}/album.jpg"
                              }
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
                            "count": 1,
                            "list_ver": 46,
                            "info": [
                              {
                                "hash": "CCDD3344",
                                "fileid": 778899,
                                "name": "林俊杰 - 江南",
                                "timelen": 267000,
                                "mixsongid": 92002,
                                "album_id": 8802,
                                "addtime": 1700001234
                              }
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val userPage = playlistService.getUserPlaylists().getOrThrow()
        assertEquals(2, userPage.playlists.size)
        assertTrue(userPage.playlists[0].isDefaultLike)
        assertEquals("https://imge.kugou.com/soft/collection/400/a.jpg", userPage.playlists[0].coverUrl)
        assertTrue(userPage.playlists[1].isCollected)

        val pubDetail = playlistService.getPublicPlaylistDetail("collection_3_555_9_0").getOrThrow()
        assertEquals("公开精选", pubDetail.name)
        assertEquals("https://imge.kugou.com/400/cover.jpg", pubDetail.coverUrl)

        val pubTracks = playlistService.getPublicPlaylistTracks("collection_3_555_9_0").getOrThrow()
        assertEquals(1, pubTracks.tracks.size)
        assertEquals("aabb1122", pubTracks.tracks.first().hash)
        assertEquals("晴天", pubTracks.tracks.first().name)

        val cloudTracks = playlistService.getCloudPlaylistTracks(2001L).getOrThrow()
        assertEquals(46L, cloudTracks.listVer)
        assertEquals(778899L, cloudTracks.tracks.first().fileId)
        assertEquals(1700001234_000L, cloudTracks.tracks.first().addTimeMs)
        assertEquals(1700001234_000L, cloudTracks.tracks.first().toUnifiedTrack().kugouMeta?.playlistAddTimeMs)
        assertEquals("ccdd3344", cloudTracks.tracks.first().hash)

        assertTrue(requests.any { it.requestUrl?.encodedPath == "/v7/get_all_list" && it.getHeader("x-router") == "cloudlist.service.kugou.com" })
    }

    @Test
    fun `无 global_collection_id 的酷狗公开歌单 DTO 兜底使用 special 前缀而非裸数字 listId`() {
        val searchDto = KugouSearchPlaylistDto(
            globalCollectionId = "",
            specialId = 98765L,
            name = "无GID公开单",
            trackCount = 10
        )
        val unifiedFromSearch = searchDto.toUnifiedPlaylist()
        assertEquals(PlaylistId.kugou("special:98765"), unifiedFromSearch.id)

        val detailDto = KugouPlaylistDetailDto(
            globalCollectionId = "",
            listId = 98765L,
            name = "无GID公开单"
        )
        assertEquals(PlaylistId.kugou("special:98765"), detailDto.toUnifiedPlaylist().id)

        val withGid = KugouSearchPlaylistDto(
            globalCollectionId = "collection_3_555_9_0",
            specialId = 98765L,
            name = "有GID公开单"
        )
        assertEquals(PlaylistId.kugou("collection_3_555_9_0"), withGid.toUnifiedPlaylist().id)
    }

    @Test
    fun `KugouPlaylistService 覆盖增删改排序上传封面与曲目操作加密协议`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/cloudlist.service/v5/add_list" -> {
                        val reqObj = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        val type = reqObj["type"]!!.jsonPrimitive.int
                        MockResponse().setResponseCode(200).setBody(
                            if (type == 0) {
                                """{"status": 1, "error_code": 0, "data": {"listid": 5001, "global_collection_id": "collection_3_889900_5001_0", "name": "${reqObj["name"]!!.jsonPrimitive.content}"}}"""
                            } else {
                                """{"status": 1, "error_code": 0, "data": {"listid": 5002, "list_create_gid": "collection_3_111_5002_0"}}"""
                            }
                        )
                    }
                    "/v2/delete_list" -> {
                        val pObj = json.parseToJsonElement(decryptPkcs1Hex(url.queryParameter("p")!!)).jsonObject
                        val aesKey = pObj["aes"]!!.jsonPrimitive.content
                        assertEquals(889900L, pObj["uid"]!!.jsonPrimitive.long)

                        val decryptedBody = KugouCrypto.playlistAesDecrypt(request.body.readUtf8(), aesKey)
                        val bodyObj = json.parseToJsonElement(decryptedBody).jsonObject
                        assertEquals(5001L, bodyObj["listid"]!!.jsonPrimitive.long)

                        val encryptedResp = KugouCrypto.playlistAesEncrypt("""{"status": 1, "error_code": 0}""", fixedKey = aesKey)
                        MockResponse().setResponseCode(200).setBody(Buffer().write(Base64.getDecoder().decode(encryptedResp.str)))
                    }
                    "/v1/modify_list" -> {
                        assertEquals("cloudlist.service.kugou.com", request.getHeader("x-router"))
                        val pObj = json.parseToJsonElement(decryptPkcs1Hex(url.queryParameter("p")!!)).jsonObject
                        val aesSecret = pObj["aes"]!!.jsonPrimitive.content
                        val aesMd5 = KugouCrypto.md5Hex(aesSecret)
                        val keyBytes = aesMd5.substring(0, 16).toByteArray(Charsets.UTF_8)
                        val ivBytes = aesMd5.substring(16, 32).toByteArray(Charsets.UTF_8)

                        val plainReq = KugouCrypto.aesCbcDecryptBytes(request.body.readByteArray(), keyBytes, ivBytes).toString(Charsets.UTF_8)
                        val reqObj = json.parseToJsonElement(plainReq).jsonObject
                        assertEquals(5001L, reqObj["listid"]!!.jsonPrimitive.long)
                        assertEquals("重命名歌单", reqObj["name"]!!.jsonPrimitive.content)

                        val respCipher = KugouCrypto.aesCbcEncryptBytes("""{"status": 1, "error_code": 0}""".toByteArray(), keyBytes, ivBytes)
                        MockResponse().setResponseCode(200).setBody(Buffer().write(respCipher))
                    }
                    "/v1/modify_song_sort" -> {
                        val pObj = json.parseToJsonElement(decryptPkcs1Hex(url.queryParameter("p")!!)).jsonObject
                        val aesSecret = pObj["aes"]!!.jsonPrimitive.content
                        val aesMd5 = KugouCrypto.md5Hex(aesSecret)
                        val keyBytes = aesMd5.substring(0, 16).toByteArray(Charsets.UTF_8)
                        val ivBytes = aesMd5.substring(16, 32).toByteArray(Charsets.UTF_8)

                        val plainReq = KugouCrypto.aesCbcDecryptBytes(request.body.readByteArray(), keyBytes, ivBytes).toString(Charsets.UTF_8)
                        val reqObj = json.parseToJsonElement(plainReq).jsonObject
                        assertEquals(5001L, reqObj["listid"]!!.jsonPrimitive.long)
                        assertEquals(12L, reqObj["list_ver"]!!.jsonPrimitive.long)
                        assertEquals(2, reqObj["data"]!!.jsonArray.size)

                        val respCipher = KugouCrypto.aesCbcEncryptBytes("""{"status": 1, "error_code": 0}""".toByteArray(), keyBytes, ivBytes)
                        MockResponse().setResponseCode(200).setBody(Buffer().write(respCipher))
                    }
                    "/stream.php" -> {
                        assertEquals("custom", url.queryParameter("type"))
                        assertEquals(".jpg", url.queryParameter("extendName"))
                        assertEquals("1", url.queryParameter("jsonResponse"))
                        assertNotNull(url.queryParameter("md5"))
                        assertEquals(4, request.bodySize.toInt())
                        MockResponse().setResponseCode(200).setBody("""{"IsSuccess": true, "FileName": "20261122_cover.jpg"}""")
                    }
                    "/cloudlist.service/v6/add_song" -> {
                        MockResponse().setResponseCode(200).setBody("""{"status": 1, "error_code": 0, "data": [{"fileid": 99100}]}""")
                    }
                    "/v4/delete_songs" -> {
                        MockResponse().setResponseCode(200).setBody("""{"status": 1, "error_code": 0}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val created = playlistService.createPlaylist("新歌单", isPrivate = true).getOrThrow()
        assertEquals(5001L, created.listId)
        val collected = playlistService.collectPlaylist("他人歌单", 111L, 5002L, "collection_3_111_5002_0").getOrThrow()
        assertEquals("collection_3_111_5002_0", collected.globalCollectionId)

        playlistService.updatePlaylist(listId = 5001L, name = "重命名歌单").getOrThrow()
        playlistService.sortPlaylistTracks(
            listId = 5001L,
            listVer = 12L,
            items = listOf(KugouPlaylistTrackSortInput(101L, 1), KugouPlaylistTrackSortInput(102L, 2))
        ).getOrThrow()

        val cover = playlistService.uploadPlaylistCover(byteArrayOf(1, 2, 3, 4)).getOrThrow()
        assertEquals("custom/20261122_cover.jpg", cover.picValue)

        val fileIds = playlistService.addTracksToPlaylist(
            listId = 5001L,
            tracks = listOf(KugouPlaylistAddTrackInput("晴天", "AABB11", "8801", 91001L))
        ).getOrThrow()
        assertEquals(listOf(99100L), fileIds)

        playlistService.removeTracksFromPlaylist(5001L, listOf(99100L)).getOrThrow()
        playlistService.deletePlaylist(5001L).getOrThrow()
    }

    @Test
    fun `KugouSearchService 正确发送 Lite clientver 与解析各类搜索结果`() = runTest {
        val recordedUrls = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                recordedUrls += url.toString()
                return when (url.encodedPath) {
                    "/v2/search/song" -> {
                        assertEquals("201", url.queryParameter("clientver"))
                        assertEquals("complexsearch.kugou.com", request.getHeader("x-router"))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {
                                "total": 1,
                                "lists": [
                                  {
                                    "FileHash": "ABCD0001",
                                    "SongName": "<em>七里香</em>",
                                    "SingerName": "周杰伦",
                                    "AlbumName": "七里香",
                                    "AlbumID": 10086,
                                    "MixSongID": 777111,
                                    "Duration": 299,
                                    "Image": "http://imge.kugou.com/{size}/qlx.jpg"
                                  }
                                ]
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    "/v6/search/complex" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "error_code": 0,
                          "data": {
                            "lists": [
                              {
                                "type": "song",
                                "lists": [{"hash": "EF0002", "songname": "花海", "singername": "周杰伦", "duration": 264}]
                              },
                              {
                                "type": "album",
                                "lists": [{"albumid": "10087", "albumname": "魔杰座", "singer": "周杰伦"}]
                              }
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                    "/api/v3/search/hot_tab" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "error_code": 0,
                          "data": {
                            "info": [{"keyword": "<em>周杰伦</em>", "reason": "热搜第一"}]
                          }
                        }
                        """.trimIndent()
                    )
                    "/v2/getSearchTip" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "error_code": 0,
                          "data": [
                            {
                              "LableName": "歌手",
                              "RecordDatas": [{"HintInfo": "周杰伦"}]
                            },
                            {
                              "LableName": "热搜",
                              "RecordDatas": [{"HintInfo": "周杰伦新歌"}]
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val songPage = searchService.search("七里香", KugouSearchType.SONG).getOrThrow()
        assertEquals(1, songPage.songs.size)
        assertEquals("七里香", songPage.songs.first().name)
        assertEquals(299_000L, songPage.songs.first().durationMs)
        assertEquals("https://imge.kugou.com/400/qlx.jpg", songPage.songs.first().coverUrl)

        val complex = searchService.complexSearch("周杰伦").getOrThrow()
        assertEquals(1, complex.songs.size)
        assertEquals(1, complex.albums.size)

        val hot = searchService.getHotSearches().getOrThrow()
        assertEquals("周杰伦", hot.first().keyword)

        val suggest = searchService.getSuggestions("周杰").getOrThrow()
        assertEquals(2, suggest.keywords.size)
        assertEquals("周杰伦", suggest.keywords[0].keyword)
        assertEquals("周杰伦新歌", suggest.keywords[1].keyword)
        assertTrue(recordedUrls.isNotEmpty())
    }

    @Test
    fun `KugouArtistService 与 KugouAlbumService 正确发送网关头与加解密参数`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/kmr/v3/author" -> {
                        assertEquals("36", request.getHeader("kg-tid"))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {
                                "author_id": "3520",
                                "author_name": "周杰伦",
                                "sizable_avatar": "http://imge.kugou.com/{size}/jay.jpg",
                                "fans_count": 999999,
                                "album_count": 15,
                                "song_count": 200,
                                "is_follow": 1
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    "/kmr/v1/audio_group/author" -> {
                        assertEquals("220", request.getHeader("kg-tid"))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": [
                                {"audio_name": "稻香", "author_name": "周杰伦", "hash": "1122AABB", "timelength": 223000}
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    "/kmr/v1/author/albums" -> {
                        assertEquals("36", request.getHeader("kg-tid"))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": [
                                {"album_id": 1001, "album_name": "范特西", "author_name": "周杰伦", "publish_date": "2001-09-14"}
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    "/followservice/v3/follow_singer" -> {
                        val reqObj = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        val pObj = json.parseToJsonElement(decryptPkcs1Hex(reqObj["p"]!!.jsonPrimitive.content)).jsonObject
                        val aesKey = pObj["key"]!!.jsonPrimitive.content
                        assertTrue(aesKey.isNotBlank())
                        assertEquals(3520L, reqObj["singerid"]!!.jsonPrimitive.long)
                        MockResponse().setResponseCode(200).setBody("""{"status": 1, "error_code": 0}""")
                    }
                    "/v4/follow_list" -> {
                        val reqObj = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        val pHex = reqObj["p"]!!.jsonPrimitive.content
                        val rawPlain = decryptRawRsaHex(pHex)
                        assertTrue(rawPlain.contains("\"token\":\"kg_token_xyz\""))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {
                                "list": [{"author_id": "3520", "author_name": "周杰伦", "is_follow": 1}]
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    "/kmr/v2/albums" -> {
                        assertEquals("255", request.getHeader("kg-tid"))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": [
                                {
                                  "album_id": 1001,
                                  "album_name": "范特西",
                                  "author_name": "周杰伦",
                                  "publish_date": "2001-09-14",
                                  "sizable_cover": "http://imge.kugou.com/{size}/ftx.jpg",
                                  "intro": "经典专辑"
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    "/v1/album_audio/lite" -> {
                        assertEquals("255", request.getHeader("kg-tid"))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {
                                "total": 1,
                                "songs": [
                                  {
                                    "base": {"audio_name": "爱在西元前", "author_name": "周杰伦"},
                                    "audio_info": {"hash": "9988AABB", "duration": 234000},
                                    "album_info": {"album_id": 1001, "album_name": "范特西"}
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

        val artist = artistService.getArtistDetail("3520").getOrThrow()
        assertEquals("周杰伦", artist.name)
        assertTrue(artist.isFollowed)
        assertEquals(999999L, artist.fansCount)

        val songs = artistService.getArtistAudios("3520").getOrThrow()
        assertEquals("1122aabb", songs.songs.first().hash)

        val albums = artistService.getArtistAlbums("3520").getOrThrow()
        assertEquals("范特西", albums.albums.first().name)
        assertTrue(albums.albums.first().publishTimeMs > 0L)

        artistService.followArtist("3520").getOrThrow()
        val followed = artistService.getUserFollowedArtists().getOrThrow()
        assertEquals(1, followed.size)

        val albumDetail = albumService.getAlbumDetail("1001").getOrThrow()
        assertEquals("范特西", albumDetail.name)
        val albumSongs = albumService.getAlbumSongs("1001").getOrThrow()
        assertEquals("9988aabb", albumSongs.songs.first().hash)
        assertEquals("爱在西元前", albumSongs.songs.first().name)
    }

    @Test
    fun `KugouCommentService 支持查询自动补齐 specialId 以及发评回复删评`() = runTest {
        val capturedActions = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/mcomment/v1/cmtlist" -> {
                        capturedActions += "cmtlist:${url.queryParameter("code")}"
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "err_code": 0,
                              "count": 1,
                              "special_id": "sp_9001",
                              "special_name": "晴天",
                              "list": [
                                {
                                  "id": 7001,
                                  "user_id": 123,
                                  "user_name": "听众A",
                                  "content": "青春回忆",
                                  "addtime": "2024-05-01 12:00:00",
                                  "like": {"count": 88},
                                  "reply_num": 2
                                }
                              ],
                              "weight_list": []
                            }
                            """.trimIndent()
                        )
                    }
                    "/mcomment/v1/hot_replylist" -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "status": 1,
                          "err_code": 0,
                          "count": 1,
                          "list": [{"id": 7002, "user_id": 456, "user_name": "听众B", "content": "同感", "like": {"count": 5}}]
                        }
                        """.trimIndent()
                    )
                    "/index.php" -> {
                        when (url.queryParameter("r")) {
                            "commentsv3/add" -> {
                                assertEquals("sp_9001", url.queryParameter("childrenid"))
                                val bodyObj = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                                assertEquals("真好听", bodyObj["data"]!!.jsonObject["content"]!!.jsonPrimitive.content)
                                assertNotNull(url.queryParameter("key"))
                                MockResponse().setResponseCode(200).setBody(
                                    """{"status": 1, "err_code": 0, "data": {"id": 7003, "content": "真好听", "user_name": "我"}}"""
                                )
                            }
                            "commentsv2/reply" -> {
                                assertEquals("1", url.queryParameter("is_t"))
                                assertEquals("楼中楼回复//@听众A:青春回忆", url.queryParameter("content"))
                                MockResponse().setResponseCode(200).setBody(
                                    """{"status": 1, "err_code": 0, "data": {"id": 7004, "content": "楼中楼回复//@听众A:青春回忆"}}"""
                                )
                            }
                            "commentsv2/delcomment" -> {
                                assertEquals("7003", url.queryParameter("cid"))
                                assertEquals("sp_9001", url.queryParameter("childrenid"))
                                MockResponse().setResponseCode(200).setBody("""{"status": 1, "err_code": 0}""")
                            }
                            else -> MockResponse().setResponseCode(404)
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val page = commentService.getMusicComments(mixSongId = 91001L).getOrThrow()
        assertEquals("sp_9001", page.specialId)
        assertEquals("青春回忆", page.comments.first().content)
        assertEquals(88, page.comments.first().likeCount)

        val floors = commentService.getFloorComments(KugouCommentResourceType.SONG, "sp_9001", "7001").getOrThrow()
        assertEquals("同感", floors.comments.first().content)

        // 不传 specialId 时应先调 cmtlist 自动解析 sp_9001
        val sent = commentService.sendComment(KugouCommentResourceType.SONG, "真好听", mixSongId = 91001L).getOrThrow()
        assertEquals("7003", sent?.id)
        assertFalse(capturedActions.isEmpty())

        val replied = commentService.replyComment(
            resourceType = KugouCommentResourceType.SONG,
            specialId = "sp_9001",
            tid = "7001",
            content = "楼中楼回复",
            replyUserName = "听众A",
            replyContent = "青春回忆"
        ).getOrThrow()
        assertTrue(replied!!.content.contains("//@听众A:青春回忆"))

        commentService.deleteComment(KugouCommentResourceType.SONG, "7003", specialId = "sp_9001").getOrThrow()
    }

    private fun decryptPkcs1Hex(hexCipher: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, testPrivateKey)
        return cipher.doFinal(KugouCrypto.hexToBytes(hexCipher)).toString(Charsets.UTF_8)
    }

    private fun decryptRawRsaHex(hexCipher: String): String {
        val m = BigInteger(hexCipher, 16).modPow(testPrivateKey.privateExponent, testPrivateKey.modulus)
        val raw = m.toByteArray()
        val start = if (raw.isNotEmpty() && raw[0].toInt() == 0) 1 else 0
        var end = raw.size
        while (end > start && raw[end - 1].toInt() == 0) {
            end--
        }
        return String(raw, start, end - start, Charsets.UTF_8)
    }
}
