package com.lin0721.linmusic.feature.kugou

import com.lin0721.linmusic.core.auth.AccountSwitcher
import com.lin0721.linmusic.core.auth.FakeAuthRepository
import com.lin0721.linmusic.core.auth.PlatformSessionRegistry
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.domain.CommentsSectionController
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.kugou.KugouPlaylistService
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.CommentTarget
import com.lin0721.linmusic.core.platform.repository.PlatformCommentRepository
import com.lin0721.linmusic.core.platform.repository.PlatformCommentRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedCommentsPage
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRepository
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRouter
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRepository
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedCollectedAlbum
import com.lin0721.linmusic.core.platform.repository.UnifiedUserPlaylist
import com.lin0721.linmusic.core.platform.repository.UnifiedUserRecordTrack
import com.lin0721.linmusic.core.platform.repository.UnifiedUserSubcount
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.feature.playlist.domain.CreatePlaylistAndAddSongUseCase
import com.lin0721.linmusic.feature.playlist.domain.FakeCreateRepository
import com.lin0721.linmusic.feature.playlist.domain.FakePlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.FakeSongLikeRepository
import com.lin0721.linmusic.feature.playlist.domain.FakeUserPlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KugouParitySharedTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        testScope.cancel()
        SongCollectDelegate.clearCache()
    }

    @Test
    fun `加入歌单弹窗只列出当前平台歌单`() = runBlocking {
        SongCollectDelegate.clearCache()
        val store = InMemoryPreferencesStore()
        val userPreferences = UserPreferences(store)
        userPreferences.saveUserProfile(
            UserProfile(uid = 1001L, nickname = "网易用户", avatarUrl = "")
        )
        userPreferences.upsertPlatformAccount(
            StoredAccount(
                platform = MusicPlatform.KUGOU,
                uid = 2002L,
                nickname = "酷狗用户",
                avatarUrl = ""
            )
        )

        val likeRepo = FakeSongLikeRepository()
        val playlistRepo = java.lang.reflect.Proxy.newProxyInstance(
            com.lin0721.linmusic.feature.playlist.data.PlaylistRepository::class.java.classLoader,
            arrayOf(com.lin0721.linmusic.feature.playlist.data.PlaylistRepository::class.java)
        ) { _, _, _ -> flowOf(Result.failure<Any>(UnsupportedOperationException())) } as com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
        val externalStore = PlaylistExternalTrackStore(store)
        val kugouLibRepo = java.lang.reflect.Proxy.newProxyInstance(
            PlatformLibraryRepository::class.java.classLoader,
            arrayOf(PlatformLibraryRepository::class.java)
        ) { _, method, args ->
            if (method.name == "getUserPlaylists") {
                val accountId = args[0] as AccountId
                flowOf(
                    Result.success(
                        listOf(
                            UnifiedUserPlaylist(
                                id = PlaylistId.kugou("list:10"),
                                name = "酷狗歌单",
                                coverUrl = "https://kg/cover.jpg",
                                trackCount = 3,
                                ownerId = accountId
                            )
                        )
                    )
                )
            } else {
                flowOf(Result.success(emptyList<Any>()))
            }
        } as PlatformLibraryRepository
        val dummyLibRepo = java.lang.reflect.Proxy.newProxyInstance(
            PlatformLibraryRepository::class.java.classLoader,
            arrayOf(PlatformLibraryRepository::class.java)
        ) { _, _, _ -> flowOf(Result.success(emptyList<Any>())) } as PlatformLibraryRepository
        val libraryRouter = PlatformLibraryRouter(
            netease = dummyLibRepo,
            kugou = kugouLibRepo
        )
        val dummyPlaylistRepo = java.lang.reflect.Proxy.newProxyInstance(
            PlatformPlaylistRepository::class.java.classLoader,
            arrayOf(PlatformPlaylistRepository::class.java)
        ) { _, _, _ -> flowOf(Result.failure<Any>(UnsupportedOperationException())) } as PlatformPlaylistRepository
        val playlistRouter = PlatformPlaylistRouter(
            netease = dummyPlaylistRepo,
            kugou = dummyPlaylistRepo,
            externalStore = externalStore
        )

        val delegate = SongCollectDelegate(
            userPlaylistRepository = FakeUserPlaylistRepository(
                listOf(
                    UserPlaylist(
                        id = 1L,
                        name = "网易歌单",
                        coverImgUrl = "https://ne/cover.jpg",
                        trackCount = 5,
                        userId = 1001L
                    )
                )
            ),
            playlistRepository = playlistRepo,
            songLikeRepository = likeRepo,
            createPlaylistAndAddSongUseCase = CreatePlaylistAndAddSongUseCase(
                FakeCreateRepository(),
                playlistRepo,
                PlaylistMutationBus()
            ),
            userPreferences = userPreferences,
            resourceProvider = testResourceProvider,
            externalStore = externalStore,
            externalSongLikes = ExternalSongLikes(externalStore, userPreferences) { emptyList<QueueItem>() },
            platformLibraryRouter = libraryRouter,
            platformPlaylistRouter = playlistRouter
        )

        // 当前平台为酷狗时只列酷狗歌单
        userPreferences.setCurrentPlatform(MusicPlatform.KUGOU)
        delegate.prepare(999L, emptySet()) {}
        assertEquals(listOf(MusicPlatform.KUGOU), delegate.state.value.collectItems.map { it.platform }.distinct())

        // 切回网易时只列网易歌单
        userPreferences.setCurrentPlatform(MusicPlatform.NETEASE)
        delegate.prepare(999L, emptySet()) {}
        assertEquals(listOf(MusicPlatform.NETEASE), delegate.state.value.collectItems.map { it.platform }.distinct())
    }

    @Test
    fun `酷狗评论能力标记暴露不支持点赞但支持回复删除楼层`() {
        val kugouCommentRepo = java.lang.reflect.Proxy.newProxyInstance(
            PlatformCommentRepository::class.java.classLoader,
            arrayOf(PlatformCommentRepository::class.java)
        ) { _, method, _ ->
            if (method.name == "getComments") {
                flowOf(Result.success(UnifiedCommentsPage(hotComments = emptyList(), comments = emptyList(), totalCount = 0, hasMore = false)))
            } else {
                flowOf(Result.failure<Any>(UnsupportedOperationException()))
            }
        } as PlatformCommentRepository
        val commentRouter = PlatformCommentRouter(
            netease = kugouCommentRepo,
            kugou = kugouCommentRepo,
            qq = kugouCommentRepo
        )

        val dummyRepo = java.lang.reflect.Proxy.newProxyInstance(
            CommentRepository::class.java.classLoader,
            arrayOf(CommentRepository::class.java)
        ) { _, _, _ -> flowOf(Result.failure<Any>(UnsupportedOperationException())) } as CommentRepository

        val controller = CommentsSectionController(
            scope = testScope,
            repository = dummyRepo,
            onToast = {},
            resourceProvider = testResourceProvider,
            platformCommentRouter = commentRouter
        )

        controller.loadTarget(CommentTarget.TrackTarget(TrackId.kugou("abc")))
        val state = controller.commentsState.value as CommentsState.Success
        assertEquals(MusicPlatform.KUGOU, state.platform)
        assertFalse(state.canLike)
        assertTrue(state.canReply)
        assertTrue(state.canDelete)
        assertTrue(state.supportsFloor)
        assertFalse(state.supportsSort)
    }
}
