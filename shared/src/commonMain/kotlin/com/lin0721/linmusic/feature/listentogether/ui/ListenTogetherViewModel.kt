package com.lin0721.linmusic.feature.listentogether.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.listentogether.ListenTogetherSession
import com.lin0721.linmusic.core.listentogether.ListenTogetherState
import com.lin0721.linmusic.core.listentogether.RoomTrackResolver
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherInvite
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// 建房时同步给服务端的种子曲目上限
private const val SEED_SONG_LIMIT = 200

// 房间队列展示与解析的条数上限
internal const val ROOM_TRACK_LIMIT = 100

class ListenTogetherViewModel(
    private val session: ListenTogetherSession,
    private val controller: PlaybackController,
    private val resolver: RoomTrackResolver,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    val state: StateFlow<ListenTogetherState> = session.state
    val nowPlaying: StateFlow<NowPlaying?> = controller.nowPlaying
    val position: StateFlow<Long> = controller.currentPosition
    val duration: StateFlow<Long> = controller.duration

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // 房间队列曲目；解析失败时为空，界面退化为只显示数量
    private val _roomTracks = MutableStateFlow<List<QueueItem>>(emptyList())
    val roomTracks: StateFlow<List<QueueItem>> = _roomTracks.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        // 轮询每轮都会刷新状态，只在 id 列表变化时才拉歌曲详情
        viewModelScope.launch {
            session.state
                .map { if (it.active) it.roomSongIds.take(ROOM_TRACK_LIMIT) else emptyList() }
                .distinctUntilChanged()
                .collectLatest { ids ->
                    _roomTracks.value = if (ids.isEmpty()) emptyList() else resolveTracks(ids)
                }
        }
    }

    // 进入入口时接回服务端仍存在的房间，失败不打扰用户
    fun resumeExisting() {
        if (session.state.value.active) return
        viewModelScope.launch { runCatching { session.resumeExisting() } }
    }

    // 以当前播放队列中的网易云歌曲建房；成功后返回邀请文案
    fun startRoom(onCreated: (String) -> Unit) {
        if (_busy.value || session.state.value.active) return
        val ids = controller.queue.value.map { it.songId }.filter { it > 0L }.distinct().take(SEED_SONG_LIMIT)
        if (ids.isEmpty()) {
            _messages.tryEmit("当前队列没有网易云歌曲，无法发起一起听")
            return
        }
        viewModelScope.launch {
            _busy.value = true
            session.createRoom(ids)
                .onSuccess { room -> onCreated(inviteText(room.id, selfUid())) }
                .onFailure { _messages.tryEmit(it.toUserMessage(resourceProvider)) }
            _busy.value = false
        }
    }

    fun joinByInvite(text: String) {
        if (_busy.value || session.state.value.active) return
        val target = ListenTogetherInvite.parse(text)
        if (target == null) {
            _messages.tryEmit("未识别到邀请内容，请粘贴完整的邀请文案")
            return
        }
        viewModelScope.launch {
            _busy.value = true
            session.joinRoom(target.roomId, target.inviterId)
                .onSuccess { _messages.tryEmit("已加入，将跟随房主播放") }
                .onFailure { _messages.tryEmit(it.toUserMessage(resourceProvider)) }
            _busy.value = false
        }
    }

    fun leave() {
        viewModelScope.launch {
            session.leave().onFailure { _messages.tryEmit(it.toUserMessage(resourceProvider)) }
        }
    }

    // 仅房主可点播房间队列，好友随房主指令跟随；客机点击会被下一条指令拉回，故不响应
    fun playRoomTrack(index: Int) {
        val current = session.state.value
        if (!current.active || !current.isHost) return
        val tracks = _roomTracks.value
        if (index !in tracks.indices) return
        controller.playQueue(tracks, index)
    }

    // 当前房间的邀请文案，不在房间内返回空串
    suspend fun currentInviteText(): String {
        val roomId = session.state.value.roomId ?: return ""
        return inviteText(roomId, selfUid())
    }

    private suspend fun resolveTracks(ids: List<Long>): List<QueueItem> = try {
        val resolved = resolver.resolve(ids).associateBy { it.songId }
        ids.mapNotNull { resolved[it] }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w("ListenTogether", "房间队列解析失败", e)
        emptyList()
    }

    private fun inviteText(roomId: String, uid: Long): String = ListenTogetherInvite.format(roomId, uid)

    private suspend fun selfUid(): Long = userPreferences.userProfile.first()?.uid ?: 0L
}
