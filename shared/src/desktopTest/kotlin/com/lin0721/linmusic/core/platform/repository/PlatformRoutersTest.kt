package com.lin0721.linmusic.core.platform.repository

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.kugou.KugouAlbumService
import com.lin0721.linmusic.core.kugou.KugouArtistService
import com.lin0721.linmusic.core.kugou.KugouClientMode
import com.lin0721.linmusic.core.kugou.KugouCommentService
import com.lin0721.linmusic.core.kugou.KugouConfig
import com.lin0721.linmusic.core.kugou.KugouDeviceStore
import com.lin0721.linmusic.core.kugou.KugouGatewayClient
import com.lin0721.linmusic.core.kugou.KugouPlaybackService
import com.lin0721.linmusic.core.kugou.KugouPlaylistService
import com.lin0721.linmusic.core.kugou.KugouSearchService
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedComment
import com.lin0721.linmusic.core.platform.UnifiedCreator
import com.lin0721.linmusic.core.platform.UnifiedPlaylist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.QQCommentApi
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PlatformRoutersTest {

    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryPreferencesStore
    private lateinit var userPreferences: UserPreferences
    private lateinit var deviceStore: KugouDeviceStore
    private lateinit var config: KugouConfig
    private lateinit var gatewayClient: KugouGatewayClient
    private lateinit var externalStore: PlaylistExternalTrackStore
    private var kugouEnabled = true
    private val json = Json { ignoreUnknownKeys = true }
    private val clockMs = 1_700_000_000_000L

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
                nickname = "酷狗路由测试",
                platform = MusicPlatform.KUGOU,
                platformUid = "889900",
                credentials = mapOf("userid" to "889900", "token" to "kg_tok")
            ),
            activate = true
        )
        deviceStore = KugouDeviceStore(store)
        deviceStore.getOrCreateDeviceIdentity()
        deviceStore.updateDfid("dfid_router_test")
        gatewayClient = KugouGatewayClient(config, deviceStore, userPreferences, nowMs = { clockMs })
        externalStore = PlaylistExternalTrackStore(store, nowMs = { clockMs })
        kugouEnabled = true
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `PlatformLibraryRouter 按平台分发并校验 kugouPlatformEnabled 开关`() = runTest {
        val neteaseCalls = AtomicInteger(0)
        val fakeNetease = object : PlatformLibraryRepository {
            override fun getUserPlaylists(accountId: AccountId, limit: Int): Flow<Result<List<UnifiedUserPlaylist>>> {
                neteaseCalls.incrementAndGet()
                return flowOf(Result.success(listOf(UnifiedUserPlaylist(PlaylistId.netease(101L), "网易歌单"))))
            }
            override fun getFavoriteArtists(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedArtist>()))
            override fun getCollectedAlbums(platform: MusicPlatform, limit: Int) = flowOf(Result.success(emptyList<UnifiedCollectedAlbum>()))
            override fun getUserRecord(accountId: AccountId, type: Int) = flowOf(Result.success(emptyList<UnifiedUserRecordTrack>()))
            override fun getUserSubcount(platform: MusicPlatform) = flowOf(Result.success(UnifiedUserSubcount()))
            override fun updatePlaylistOrder(platform: MusicPlatform, playlistIds: List<PlaylistId>) = flowOf(Result.success(Unit))
            override fun setPlaylistTop(playlistId: PlaylistId, top: Boolean) = flowOf(Result.success(Unit))
        }

        val kugouHttpCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                kugouHttpCalls.incrementAndGet()
                return when (request.requestUrl?.encodedPath) {
                    "/v7/get_all_list" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": {"info": [{"listid": 2001, "global_collection_id": "collection_3_889900_2001_0", "name": "酷狗自建", "count": 3, "type": 0}]}}"""
                    )
                    "/v4/follow_list" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": {"list": [{"author_id": "3520", "author_name": "周杰伦"}]}}"""
                    )
                    "/playhistory/v1/get_songs" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": {"list": [{"hash": "AABBCC", "songname": "青花瓷", "singername": "周杰伦", "timelen": 239000, "play_count": 7}]}}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val playlistSvc = KugouPlaylistService(config, gatewayClient, userPreferences)
        val artistSvc = KugouArtistService(config, deviceStore, gatewayClient, userPreferences)
        val playbackSvc = KugouPlaybackService(config, deviceStore, gatewayClient, userPreferences)
        val kugouRepo = KugouPlatformLibraryRepository(playlistSvc, artistSvc, playbackSvc) { kugouEnabled }
        val router = PlatformLibraryRouter(fakeNetease, kugouRepo) { kugouEnabled }

        val nList = router.getUserPlaylists(AccountId.netease(1001L)).first().getOrThrow()
        assertEquals("网易歌单", nList.single().name)
        assertEquals(1, neteaseCalls.get())

        val kList = router.getUserPlaylists(AccountId.kugou(889900L)).first().getOrThrow()
        assertEquals("酷狗自建", kList.single().name)
        assertEquals(1, kugouHttpCalls.get())

        val kSub = router.getUserSubcount(MusicPlatform.KUGOU).first().getOrThrow()
        assertEquals(1, kSub.artistCount)
        assertEquals(1, kSub.createdPlaylistCount)

        // 关闭酷狗总开关后零 HTTP 请求并立即失败
        kugouEnabled = false
        val beforeBlocked = kugouHttpCalls.get()
        val blockedRes = router.getUserPlaylists(AccountId.kugou(889900L)).first()
        assertTrue(blockedRes.isFailure)
        assertEquals(beforeBlocked, kugouHttpCalls.get())
    }

    @Test
    fun `PlatformPlaylistRouter 支持酷狗双路径读取与跨平台持久化混排`() = runTest {
        val neteaseAddedIds = mutableListOf<Long>()
        val neteaseRemovedIds = mutableListOf<Long>()
        val fakeNetease = object : PlatformPlaylistRepository {
            override fun getPlaylistDetail(playlistId: PlaylistId): Flow<Result<UnifiedPlaylist>> =
                flowOf(
                    Result.success(
                        UnifiedPlaylist(
                            id = playlistId,
                            name = "网易测试歌单",
                            tracks = listOf(UnifiedTrack(TrackId.netease(100L), "网易原曲", listOf(UnifiedArtist(name = "歌手N")))),
                            trackCount = 1,
                            trackIds = listOf(TrackId.netease(100L))
                        )
                    )
                )
            override fun loadMoreTracks(trackIds: List<TrackId>) = flowOf(Result.success(emptyList<UnifiedTrack>()))
            override fun loadAllTracks(detail: UnifiedPlaylist): Flow<Result<List<UnifiedTrack>>> =
                flowOf(Result.success(detail.tracks))
            override fun createPlaylist(platform: MusicPlatform, name: String, isPrivate: Boolean) =
                flowOf(Result.success(UnifiedPlaylist(PlaylistId.netease(99L), name)))
            override fun subscribePlaylist(playlistId: PlaylistId, subscribe: Boolean) = flowOf(Result.success(Unit))
            override fun addTracksToPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>> {
                neteaseAddedIds += tracks.mapNotNull { it.id.neteaseIdOrNull() }
                return flowOf(Result.success(Unit))
            }
            override fun removeTracksFromPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>> {
                neteaseRemovedIds += tracks.mapNotNull { it.id.neteaseIdOrNull() }
                return flowOf(Result.success(Unit))
            }
            override fun renamePlaylist(playlistId: PlaylistId, name: String) = flowOf(Result.success(Unit))
            override fun updateDescription(playlistId: PlaylistId, description: String) = flowOf(Result.success(Unit))
            override fun deletePlaylist(playlistId: PlaylistId) = flowOf(Result.success(Unit))
            override fun updatePlaylistCover(playlistId: PlaylistId, imageBytes: ByteArray) = flowOf(Result.success("url"))
            override fun sortPlaylistTracks(playlistId: PlaylistId, orderedTracks: List<UnifiedTrack>, listVer: Long) = flowOf(Result.success(Unit))
        }

        val kugouRemoteAdded = mutableListOf<String>()
        val kugouRemoteDeleted = mutableListOf<Long>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.requestUrl?.encodedPath) {
                    "/v7/get_all_list" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": {"info": [{"listid": 3001, "name": "酷狗云歌单", "count": 1, "list_ver": 9, "total_ver": 100}]}}"""
                    )
                    "/v4/get_list_all_file_v3" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": {"count": 1, "list_ver": 9, "info": [{"hash": "KG_CLOUD_1", "fileid": 5566, "name": "张三 - 云曲目", "timelen": 200000}]}}"""
                    )
                    "/v3/get_list_info" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": [{"listid": 4001, "global_collection_id": "collection_3_555_4001_0", "name": "公开歌单", "count": 1}]}"""
                    )
                    "/pubsongs/v2/get_other_list_file_nofilt" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": {"count": 1, "songs": [{"hash": "KG_PUB_1", "name": "李四 - 公开曲", "timelen": 180000}]}}"""
                    )
                    "/cloudlist.service/v6/add_song" -> {
                        val req = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        kugouRemoteAdded += req["data"]!!.jsonArray.first().jsonObject["hash"]!!.jsonPrimitive.content
                        MockResponse().setResponseCode(200).setBody("""{"status": 1, "data": [{"fileid": 7788}]}""")
                    }
                    "/v4/delete_songs" -> {
                        val req = json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        kugouRemoteDeleted += req["data"]!!.jsonArray.first().jsonObject["fileid"]!!.jsonPrimitive.long
                        MockResponse().setResponseCode(200).setBody("""{"status": 1}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val kugouRepo = KugouPlatformPlaylistRepository(KugouPlaylistService(config, gatewayClient, userPreferences)) { kugouEnabled }
        val router = PlatformPlaylistRouter(fakeNetease, kugouRepo, externalStore) { kugouEnabled }

        // 1. 酷狗双路径：云歌单 listid 保留 fileid；公开歌单 collection_3_... 走 pubsongs
        val cloudDetail = router.getPlaylistDetail(PlaylistId.kugou("3001")).first().getOrThrow()
        assertEquals("酷狗云歌单", cloudDetail.name)
        assertEquals(5566L, cloudDetail.tracks.single().kugouMeta?.playlistFileId)

        val pubDetail = router.getPlaylistDetail(PlaylistId.kugou("collection_3_555_4001_0")).first().getOrThrow()
        assertEquals("公开歌单", pubDetail.name)
        assertEquals("kugou:kg_pub_1", pubDetail.tracks.single().id.canonical)

        // 2. 跨平台混排：向网易歌单同时加入「网易歌曲 + 酷狗歌曲」
        val neteasePid = PlaylistId.netease(10L)
        val kgMixed = UnifiedTrack(TrackId.kugou("KG_MIX_99"), "跨平台酷狗歌", kugouMeta = KugouTrackMeta(hash = "KG_MIX_99", mixSongId = 123L))
        val neSong = UnifiedTrack(TrackId.netease(200L), "网易同源歌")
        router.addTracksToPlaylist(neteasePid, listOf(neSong, kgMixed)).first().getOrThrow()
        assertEquals(listOf(200L), neteaseAddedIds)
        assertTrue(externalStore.contains(neteasePid, kgMixed.id))

        val mergedNetease = router.getPlaylistDetail(neteasePid).first().getOrThrow()
        assertEquals(listOf("kugou:kg_mix_99", "netease:100"), mergedNetease.tracks.map { it.id.canonical })

        // 删歌分流：酷狗曲目从本地混排移除，网易曲目走远程删歌
        router.removeTracksFromPlaylist(neteasePid, listOf(kgMixed, neSong)).first().getOrThrow()
        assertFalse(externalStore.contains(neteasePid, kgMixed.id))
        assertEquals(listOf(200L), neteaseRemovedIds)

        // 3. 向酷狗云歌单同时加入「酷狗曲目 + 网易曲目」
        val kugouPid = PlaylistId.kugou("3001")
        val kgSame = UnifiedTrack(TrackId.kugou("KG_NEW_1"), "酷狗新歌")
        router.addTracksToPlaylist(kugouPid, listOf(kgSame, neSong)).first().getOrThrow()
        assertEquals(listOf("kg_new_1"), kugouRemoteAdded)
        assertTrue(externalStore.contains(kugouPid, neSong.id))

        val mergedKugou = router.getPlaylistDetail(kugouPid).first().getOrThrow()
        assertEquals(listOf("netease:200", "kugou:kg_cloud_1"), mergedKugou.tracks.map { it.id.canonical })

        router.removeTracksFromPlaylist(kugouPid, listOf(cloudDetail.tracks.single(), neSong)).first().getOrThrow()
        assertEquals(listOf(5566L), kugouRemoteDeleted)
        assertFalse(externalStore.contains(kugouPid, neSong.id))
    }

    @Test
    fun `Search Artist Album Router 与 Comment Router 正确路由与兜底`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.requestUrl?.encodedPath) {
                    "/v6/search/complex" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": {"lists": [{"type": "song", "lists": [{"hash": "S1", "songname": "简单爱", "singername": "周杰伦", "duration": 270}]}]}}"""
                    )
                    "/kmr/v3/author" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": {"author_id": "3520", "author_name": "周杰伦", "fans_count": 100}}"""
                    )
                    "/kmr/v2/albums" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": [{"album_id": 1001, "album_name": "范特西", "author_name": "周杰伦"}]}"""
                    )
                    "/v1/album_audio/lite" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": {"total": 1, "songs": [{"base": {"audio_name": "双截棍", "author_name": "周杰伦"}, "audio_info": {"hash": "H1", "duration": 200000}}]}}"""
                    )
                    "/v2/get_res_privilege/lite" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "data": [{"hash": "AUTO_MIX", "mixsongid": 888999}]}"""
                    )
                    "/mcomment/v1/cmtlist" -> MockResponse().setResponseCode(200).setBody(
                        """{"status": 1, "err_code": 0, "count": 1, "special_id": "sp_auto", "list": [{"id": 11, "user_name": "用户1", "content": "酷狗评论"}]}"""
                    )
                    "/fcgi-bin/music_global_comment.fcg" -> MockResponse().setResponseCode(200).setBody(
                        """{"code": 0, "comment": {"commenttotal": 1, "commentlist": [{"commentid": "qq_1", "nick": "Q友", "rootcommentcontent": "QQ评论", "time": 1700000000, "praisenum": 9}]}}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val searchRouter = PlatformSearchRouter(
            netease = object : PlatformSearchRepository {
                override fun search(platform: MusicPlatform, keyword: String, type: UnifiedSearchType, offset: Int, limit: Int) =
                    flowOf(Result.success(UnifiedSearchPageResult(MusicPlatform.NETEASE, emptyList(), 0, false, 0)))
                override fun getHotSearches(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedHotSearch>()))
                override fun getSuggestions(platform: MusicPlatform, keyword: String) = flowOf(Result.success(emptyList<UnifiedSearchSuggestion>()))
                override fun getDefaultSearchKeyword(platform: MusicPlatform) = flowOf(Result.success(""))
            },
            kugou = KugouPlatformSearchRepository(KugouSearchService(config, gatewayClient)) { kugouEnabled },
            isKugouEnabled = { kugouEnabled }
        )
        val searchRes = searchRouter.search(MusicPlatform.KUGOU, "周杰伦", UnifiedSearchType.COMPLEX).first().getOrThrow()
        assertEquals(1, searchRes.items.size)
        assertTrue(searchRouter.search(MusicPlatform.KUGOU, "x", UnifiedSearchType.RADIO).first().isFailure)

        val artistRouter = PlatformArtistRouter(
            netease = object : PlatformArtistRepository {
                override fun getArtistDetail(artistId: ArtistId) = flowOf(Result.failure<UnifiedArtistDetail>(IllegalStateException()))
                override fun getArtistTopSongs(artistId: ArtistId) = flowOf(Result.success(emptyList<UnifiedTrack>()))
                override fun getArtistAllSongs(artistId: ArtistId, offset: Int, limit: Int, order: String) =
                    flowOf(Result.success(UnifiedArtistSongsPage(emptyList(), false)))
                override fun getArtistAlbums(artistId: ArtistId, offset: Int, limit: Int, order: String) =
                    flowOf(Result.success(UnifiedArtistAlbumsPage(emptyList(), false)))
                override fun subscribeArtist(artistId: ArtistId, subscribe: Boolean) = flowOf(Result.success(Unit))
                override fun checkArtistFollowed(artistId: ArtistId) = flowOf(Result.success(false))
                override fun getArtistFansCount(artistId: ArtistId) = flowOf(Result.success(0L))
                override fun getSimilarArtists(artistId: ArtistId) = flowOf(Result.success(emptyList<UnifiedArtist>()))
            },
            kugou = KugouPlatformArtistRepository(KugouArtistService(config, deviceStore, gatewayClient, userPreferences)) { kugouEnabled },
            isKugouEnabled = { kugouEnabled }
        )
        assertEquals("周杰伦", artistRouter.getArtistDetail(ArtistId.kugou("3520")).first().getOrThrow().name)

        val albumRouter = PlatformAlbumRouter(
            netease = object : PlatformAlbumRepository {
                override fun getAlbumDetail(albumId: AlbumId) = flowOf(Result.failure<UnifiedAlbumDetail>(IllegalStateException()))
                override fun getAlbumSongs(albumId: AlbumId, offset: Int, limit: Int) = flowOf(Result.success(emptyList<UnifiedTrack>()))
                override fun subscribeAlbum(albumId: AlbumId, subscribe: Boolean) = flowOf(Result.success(Unit))
            },
            kugou = KugouPlatformAlbumRepository(KugouAlbumService(gatewayClient)) { kugouEnabled },
            isKugouEnabled = { kugouEnabled }
        )
        val albumDetail = albumRouter.getAlbumDetail(AlbumId.kugou("1001")).first().getOrThrow()
        assertEquals("范特西", albumDetail.name)
        assertEquals("双截棍", albumDetail.tracks.single().name)

        // QQCommentApi 测试指向 MockWebServer
        val qqApi = QQCommentApi(
            baseUrl = server.url("/fcgi-bin/music_global_comment.fcg").toString()
        )
        val commentRouter = PlatformCommentRouter(
            netease = object : PlatformCommentRepository {
                override fun getComments(target: CommentTarget, pageNo: Int, pageSize: Int, cursor: String, sortType: CommentSortType) =
                    flowOf(Result.success(UnifiedCommentsPage(emptyList())))
                override fun getFloorComments(target: CommentTarget, parentCommentId: String, cursorOrTime: Long, pageNo: Int, limit: Int, kugouSpecialId: String?) =
                    flowOf(Result.success(UnifiedFloorCommentsPage(emptyList())))
                override fun addComment(target: CommentTarget, content: String) = flowOf(Result.success<UnifiedComment?>(null))
                override fun replyComment(target: CommentTarget, commentId: String, content: String, pid: String, replyUserName: String?, replyContent: String?, kugouSpecialId: String?) =
                    flowOf(Result.success<UnifiedComment?>(null))
                override fun deleteComment(target: CommentTarget, commentId: String, tid: String?, kugouSpecialId: String?) = flowOf(Result.success(Unit))
                override fun likeComment(target: CommentTarget, commentId: String, like: Boolean) = flowOf(Result.success(Unit))
            },
            kugou = KugouPlatformCommentRepository(
                KugouCommentService(config, deviceStore, gatewayClient, userPreferences),
                KugouPlaybackService(config, deviceStore, gatewayClient, userPreferences)
            ) { kugouEnabled },
            qq = QQPlatformCommentRepository(qqApi),
            isKugouEnabled = { kugouEnabled }
        )

        // 酷狗曲目不传 mixSongId 时自动调 privilege/lite 补全并查到评论
        val kgComments = commentRouter.getComments(CommentTarget.TrackTarget(TrackId.kugou("AUTO_MIX"))).first().getOrThrow()
        assertEquals("酷狗评论", kgComments.comments.single().content)

        // QQ 曲目走 QQ 只读评论，写操作拒绝
        val qqTarget = CommentTarget.TrackTarget(TrackId.qq("00123"), qqSongId = 555666L)
        val qqComments = commentRouter.getComments(qqTarget).first().getOrThrow()
        assertEquals("QQ评论", qqComments.comments.single().content)
        assertTrue(commentRouter.addComment(qqTarget, "hi").first().isFailure)

        // 本地曲目不支持评论
        assertTrue(commentRouter.getComments(CommentTarget.TrackTarget(TrackId.local("/a.mp3"))).first().isFailure)

        // 总开关关闭后酷狗所有 Router 均被拦截
        kugouEnabled = false
        assertTrue(searchRouter.getHotSearches(MusicPlatform.KUGOU).first().isFailure)
        assertTrue(artistRouter.getArtistDetail(ArtistId.kugou("3520")).first().isFailure)
        assertTrue(albumRouter.getAlbumDetail(AlbumId.kugou("1001")).first().isFailure)
        assertTrue(commentRouter.getComments(CommentTarget.TrackTarget(TrackId.kugou("AUTO_MIX"))).first().isFailure)
    }
}
