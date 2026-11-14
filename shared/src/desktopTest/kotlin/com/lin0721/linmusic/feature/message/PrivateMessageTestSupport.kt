package com.lin0721.linmusic.feature.message

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.feature.message.data.MessageRepository
import com.lin0721.linmusic.feature.message.domain.CommentMessage
import com.lin0721.linmusic.feature.message.domain.ForwardMessage
import com.lin0721.linmusic.feature.message.domain.MessagePage
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.message.domain.NoticeItem
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import com.lin0721.linmusic.feature.message.domain.PrivateMessage
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import com.lin0721.linmusic.feature.player.domain.SongWikiData
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.message.data.PrivateReadPreferences
import com.lin0721.linmusic.feature.profile.data.ProfileRepository
import com.lin0721.linmusic.feature.profile.domain.ProfileEventPage
import com.lin0721.linmusic.feature.profile.domain.ProfileFollowListPage
import com.lin0721.linmusic.feature.profile.domain.ProfileListenRankItem
import com.lin0721.linmusic.feature.profile.domain.ProfilePlaylistPage
import com.lin0721.linmusic.feature.profile.domain.ProfileUserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

fun createTestUserPreferences(): UserPreferences = UserPreferences(InMemoryPreferencesStore())

fun createTestReadPreferences(): PrivateReadPreferences = PrivateReadPreferences(InMemoryPreferencesStore())

suspend fun UserPreferences.loginAs(uid: Long, nickname: String = "用户$uid", avatarUrl: String = "http://a/$uid.jpg") {
    saveUserProfile(UserProfile(uid = uid, nickname = nickname, avatarUrl = avatarUrl))
}

class FakeMessageRepository : MessageRepository {

    data class ConversationCall(val selfUid: Long, val cursor: Long, val limit: Int)
    data class HistoryCall(val userId: Long, val cursor: Long, val limit: Int)
    data class SendTextCall(val userId: Long, val text: String)
    data class SendSongCall(val userId: Long, val songId: Long, val text: String)

    val conversationCalls = mutableListOf<ConversationCall>()
    val historyCalls = mutableListOf<HistoryCall>()
    val sendTextCalls = mutableListOf<SendTextCall>()
    val sendSongCalls = mutableListOf<SendSongCall>()

    var conversationsHandler: suspend (Long, Long, Int) -> Result<MessagePage<PrivateConversation>> =
        { _, _, _ -> Result.success(MessagePage(emptyList(), false, 0L)) }

    var historyHandler: suspend (Long, Long, Int) -> Result<MessagePage<PrivateMessage>> =
        { _, _, _ -> Result.success(MessagePage(emptyList(), false, 0L)) }

    var sendTextHandler: suspend (Long, String) -> Result<Unit> =
        { _, _ -> Result.success(Unit) }

    var sendSongHandler: suspend (Long, Long, String) -> Result<Unit> =
        { _, _, _ -> Result.success(Unit) }

    override fun getReceivedComments(uid: Long, cursor: Long, limit: Int): Flow<Result<MessagePage<CommentMessage>>> =
        flowOf(Result.success(MessagePage(emptyList(), false, cursor)))

    override fun getForwards(cursor: Long, limit: Int): Flow<Result<MessagePage<ForwardMessage>>> =
        flowOf(Result.success(MessagePage(emptyList(), false, cursor)))

    override fun getNotices(cursor: Long, limit: Int): Flow<Result<MessagePage<NoticeItem>>> =
        flowOf(Result.success(MessagePage(emptyList(), false, cursor)))

    override fun getPrivateConversations(
        selfUid: Long,
        cursor: Long,
        limit: Int
    ): Flow<Result<MessagePage<PrivateConversation>>> = flow {
        conversationCalls += ConversationCall(selfUid, cursor, limit)
        emit(conversationsHandler(selfUid, cursor, limit))
    }

    override fun getPrivateHistory(
        userId: Long,
        cursor: Long,
        limit: Int
    ): Flow<Result<MessagePage<PrivateMessage>>> = flow {
        historyCalls += HistoryCall(userId, cursor, limit)
        emit(historyHandler(userId, cursor, limit))
    }

    override fun sendPrivateText(userId: Long, text: String): Flow<Result<Unit>> = flow {
        sendTextCalls += SendTextCall(userId, text)
        emit(sendTextHandler(userId, text))
    }

    override fun sendPrivateSong(userId: Long, songId: Long, text: String): Flow<Result<Unit>> = flow {
        sendSongCalls += SendSongCall(userId, songId, text)
        emit(sendSongHandler(userId, songId, text))
    }
}

class FakeProfileRepository : ProfileRepository {
    val detailCalls = mutableListOf<Long>()
    val followCalls = mutableListOf<Pair<Int, Int>>()

    var userDetailHandler: suspend (Long) -> Result<ProfileUserInfo> = {
        Result.failure(IllegalStateException("no user detail"))
    }

    var userFollowsHandler: suspend (Long, Int, Int) -> Result<ProfileFollowListPage> = { _, _, _ ->
        Result.success(ProfileFollowListPage(emptyList(), false))
    }

    override fun getUserDetail(uid: Long): Flow<Result<ProfileUserInfo>> = flow {
        detailCalls += uid
        emit(userDetailHandler(uid))
    }

    override fun getUserDetailFallback(uid: Long): Flow<Result<ProfileUserInfo>> =
        flowOf(Result.failure(UnsupportedOperationException()))

    override fun followUser(uid: Long): Flow<Result<Unit>> = flowOf(Result.success(Unit))

    override fun unfollowUser(uid: Long): Flow<Result<Unit>> = flowOf(Result.success(Unit))

    override fun getUserPlaylists(uid: Long, offset: Int, limit: Int): Flow<Result<ProfilePlaylistPage>> =
        flowOf(Result.success(ProfilePlaylistPage(emptyList(), false)))

    override fun getUserFollows(uid: Long, offset: Int, limit: Int): Flow<Result<ProfileFollowListPage>> = flow {
        followCalls += offset to limit
        emit(userFollowsHandler(uid, offset, limit))
    }

    override fun getUserFolloweds(uid: Long, offset: Int, limit: Int): Flow<Result<ProfileFollowListPage>> =
        flowOf(Result.success(ProfileFollowListPage(emptyList(), false)))

    override fun getUserEvents(uid: Long, time: Long, limit: Int): Flow<Result<ProfileEventPage>> =
        flowOf(Result.success(ProfileEventPage(emptyList(), false, 0L)))

    override fun getUserListeningRank(uid: Long, type: Int): Flow<Result<List<ProfileListenRankItem>>> =
        flowOf(Result.success(emptyList()))
}

class FakePlayerRepository : PlayerRepository {
    var songDetailHandler: suspend (Long) -> Result<Track> = {
        Result.failure(IllegalStateException("not found"))
    }

    override fun getSongDetail(songId: Long): Flow<Result<Track>> = flow {
        emit(songDetailHandler(songId))
    }

    override fun getSongDetails(songIds: List<Long>): Flow<Result<List<Track>>> =
        flowOf(Result.success(emptyList()))

    override fun getSongWiki(songId: Long): Flow<Result<SongWikiData>> =
        flowOf(Result.failure(UnsupportedOperationException()))

    override fun getChorusStartTime(songId: Long): Flow<Result<Long?>> = flowOf(Result.success(null))
}

class FakePlaybackRepository : PlaybackRepository {
    var songDetailHandler: suspend (Long) -> Result<Track> = {
        Result.failure(IllegalStateException("not found"))
    }

    override val playlistRecorded: SharedFlow<Long> = MutableSharedFlow()

    override fun getSongUrl(songId: Long): Flow<Result<String>> =
        flowOf(Result.failure(UnsupportedOperationException()))

    override fun getSongPlaybackInfo(songId: Long): Flow<Result<SongPlaybackInfo>> =
        flowOf(Result.failure(UnsupportedOperationException()))

    override fun getLyrics(songId: Long): Flow<Result<List<LyricLine>>> = flowOf(Result.success(emptyList()))

    override fun getRawLyrics(songId: Long): Flow<Result<String>> = flowOf(Result.success(""))

    override fun getSongDetail(songId: Long): Flow<Result<Track>> = flow {
        emit(songDetailHandler(songId))
    }

    override fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>> = flowOf(Result.success(emptyList()))

    override fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>> =
        flowOf(Result.success(emptyList()))

    override fun reportStartPlay(songId: Long, source: PlaySource?): Flow<Result<Unit>> = flowOf(Result.success(Unit))

    override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?): Flow<Result<Unit>> =
        flowOf(Result.success(Unit))
}

class MutableFakePlaybackController : PlaybackController {
    data class QueueCall(
        val items: List<QueueItem>,
        val startIndex: Int,
        val playContext: String?,
        val source: PlaySource?,
        val startPositionMs: Long
    )

    val queueCalls = mutableListOf<QueueCall>()

    val mutableIsPlaying = MutableStateFlow(false)
    val mutableNowPlaying = MutableStateFlow<NowPlaying?>(null)
    val mutableQueueItem = MutableStateFlow<QueueItem?>(null)

    override val isPlaying: StateFlow<Boolean> = mutableIsPlaying
    override val playWhenReady: StateFlow<Boolean> = MutableStateFlow(false)
    override val nowPlaying: StateFlow<NowPlaying?> = mutableNowPlaying
    override val currentPosition: StateFlow<Long> = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = MutableStateFlow(0L)
    override val sleepTimerRemaining: StateFlow<Long> = MutableStateFlow(0L)
    override val playContext: StateFlow<String?> = MutableStateFlow(null)
    override val playSource: StateFlow<PlaySource?> = MutableStateFlow(null)
    override val currentIndex: StateFlow<Int> = MutableStateFlow(0)
    override val playMode: StateFlow<PlayMode> = MutableStateFlow(PlayMode.LIST_LOOP)
    override val queue: StateFlow<List<QueueItem>> = MutableStateFlow(emptyList())
    override val currentQueueItem: StateFlow<QueueItem?> = mutableQueueItem
    override val previousQueueItem: StateFlow<QueueItem?> = MutableStateFlow(null)
    override val nextQueueItem: StateFlow<QueueItem?> = MutableStateFlow(null)

    override suspend fun initController() = Unit

    override fun playQueue(
        items: List<QueueItem>,
        startIndex: Int,
        playContext: String?,
        source: PlaySource?,
        startPositionMs: Long
    ) {
        queueCalls += QueueCall(items, startIndex, playContext, source, startPositionMs)
    }

    override fun playAudio(
        songId: Long,
        url: String,
        title: String,
        artist: String,
        coverUrl: String,
        startPosition: Long,
        playContext: String?
    ) = Unit

    override fun addToPlayNext(items: List<QueueItem>) = Unit
    override fun appendToQueue(items: List<QueueItem>) = Unit
    override fun extendQueue(source: PlaySource, items: List<QueueItem>) = Unit
    override fun playNext() = Unit
    override fun playPrevious() = Unit
    override fun skipToPrevious() = Unit
    override fun playAtIndex(index: Int) = Unit
    override fun removeFromQueue(index: Int) = Unit
    override fun moveInQueue(from: Int, to: Int) = Unit
    override fun clearQueue() = Unit
    override fun toggleShuffle() = Unit
    override fun toggleRepeat() = Unit
    override fun rotatePlayMode() = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun togglePlayPause() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun reloadCurrentTrack() = Unit
    override fun setSleepTimer(minutes: Int) = Unit
    override fun setPositionUpdateInterval(intervalMs: Long) = Unit
    override fun cancelPendingSkip(): Boolean = false
    override fun disableRoaming() = Unit
    override fun disableIntelligence() = Unit
}

fun testMessageUser(uid: Long, nickname: String = "好友$uid") = MessageUser(
    uid = uid,
    nickname = nickname,
    avatarUrl = "http://a/$uid.jpg"
)

fun testPrivateMessage(
    id: Long,
    time: Long,
    fromUserId: Long = 10L,
    toUserId: Long = 20L,
    text: String? = "消息$id",
    fromUser: MessageUser? = testMessageUser(fromUserId),
    toUser: MessageUser? = testMessageUser(toUserId)
) = PrivateMessage(
    id = id,
    time = time,
    fromUserId = fromUserId,
    toUserId = toUserId,
    type = 6,
    text = text,
    card = null,
    raw = null,
    fromUser = fromUser,
    toUser = toUser
)
