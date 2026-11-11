package com.lin0721.linmusic.core.listentogether

import com.lin0721.linmusic.core.listentogether.data.ListenTogetherRepository
import com.lin0721.linmusic.core.listentogether.domain.FollowKind
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherMember
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherRoom
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherSyncPolicy
import com.lin0721.linmusic.core.listentogether.domain.LocalPlayback
import com.lin0721.linmusic.core.listentogether.domain.ReportedPlayback
import com.lin0721.linmusic.core.listentogether.domain.RoomCommand
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// 把房间里的 songId 换成本机可播的队列项，取不到的曲目直接略过
fun interface RoomTrackResolver {
    suspend fun resolve(songIds: List<Long>): List<QueueItem>
}

data class ListenTogetherState(
    val active: Boolean = false,
    val roomId: String? = null,
    val isHost: Boolean = false,
    val creatorId: Long = 0L,
    val members: List<ListenTogetherMember> = emptyList(),
    val roomSongIds: List<Long> = emptyList(),
    // 非空表示会话因该原因退出，或处于降级重试
    val message: String? = null
)

/**
 * 一起听同步引擎：房主上报本地播放状态与队列，客机轮询房间状态并本地跟随。
 * 只依赖 PlaybackController，两端共用；[playerDispatcher] 用于满足 Media3 只能在主线程操作播放器的约束。
 */
class ListenTogetherSession(
    private val repository: ListenTogetherRepository,
    private val controller: PlaybackController,
    private val resolver: RoomTrackResolver,
    private val selfUserId: suspend () -> Long,
    private val scope: CoroutineScope,
    private val playerDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "ListenTogether"
        private const val POLL_INTERVAL_MS = 3_000L
        private const val STATUS_EVERY_TICKS = 4
        private const val DEFAULT_HEARTBEAT_SECONDS = 30
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val REJECT_COOLDOWN_TICKS = 4
    }

    private val _state = MutableStateFlow(ListenTogetherState())
    val state: StateFlow<ListenTogetherState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var loopJob: Job? = null
    private var watchJob: Job? = null

    private var tick = 0
    private var failures = 0
    private var lastAppliedSeq = 0L
    private var lastReported: ReportedPlayback? = null
    private var rejectCooldown = 0
    private var pushedSongIds: List<Long> = emptyList()
    private var playlistVersion = 0L
    private var heartbeatSeconds = DEFAULT_HEARTBEAT_SECONDS
    private var sinceHeartbeatMs = 0L

    suspend fun createRoom(seedSongIds: List<Long>): Result<ListenTogetherRoom> {
        val ids = seedSongIds.filter { it > 0L }.distinct()
        if (ids.isEmpty()) return Result.failure(IllegalArgumentException("没有可同步的网易云歌曲"))
        return repository.createRoom(ids).first().onSuccess { start(it, isHost = true) }
    }

    // 先校验房间可加入，再接受邀请
    suspend fun joinRoom(roomId: String, inviterId: Long): Result<ListenTogetherRoom> {
        val check = repository.checkRoom(roomId).first().getOrElse { return Result.failure(it) }
        if (!check.joinable) return Result.failure(IllegalStateException(check.message ?: "房间已无法加入"))
        val room = repository.acceptInvitation(roomId, inviterId).first().getOrElse { return Result.failure(it) }
        start(room, isHost = room.isHost(selfUserId()))
        return Result.success(room)
    }

    // 进程重启后接回服务端仍存在的房间
    suspend fun resumeExisting(): Boolean {
        if (_state.value.active) return true
        val room = repository.status().first().getOrNull()?.room ?: return false
        start(room, isHost = room.isHost(selfUserId()))
        return true
    }

    // 房主结束房间；客机仅停止跟随，本地播放保持不变
    suspend fun leave(): Result<Unit> {
        val current = _state.value
        val roomId = current.roomId
        val result = if (current.isHost && roomId != null) {
            repository.endRoom(roomId).first().map { }
        } else {
            Result.success(Unit)
        }
        stop()
        return result
    }

    fun stop(message: String? = null) {
        loopJob?.cancel()
        loopJob = null
        watchJob?.cancel()
        watchJob = null
        _state.value = ListenTogetherState(message = message)
    }

    private fun start(room: ListenTogetherRoom, isHost: Boolean) {
        stop()
        tick = 0
        failures = 0
        lastAppliedSeq = 0L
        lastReported = null
        rejectCooldown = 0
        pushedSongIds = emptyList()
        playlistVersion = 0L
        sinceHeartbeatMs = 0L
        _state.value = ListenTogetherState(
            active = true,
            roomId = room.id,
            isHost = isHost,
            creatorId = room.creatorId,
            members = room.members
        )
        loopJob = scope.launch { runLoop(room.id, isHost) }
        if (isHost) watchJob = scope.launch { watchLocalChanges() }
    }

    private suspend fun runLoop(roomId: String, isHost: Boolean) {
        while (scope.isActive && _state.value.active) {
            try {
                mutex.withLock { pollOnce(roomId, isHost) }
                failures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 异常必须记录，否则表现为「不跟随」而无从排查
                AppLogger.w(TAG, "轮询失败 ${failures + 1}/$MAX_CONSECUTIVE_FAILURES", e)
                if (++failures >= MAX_CONSECUTIVE_FAILURES) {
                    stop("网络异常，已退出一起听")
                    return
                }
                _state.value = _state.value.copy(message = "连接不稳定，重试中")
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun pollOnce(roomId: String, isHost: Boolean) {
        if (tick++ % STATUS_EVERY_TICKS == 0) {
            val status = repository.status().first().getOrThrow()
            if (!status.inRoom) {
                stop("房间已结束")
                return
            }
            status.room?.let { room ->
                _state.value = _state.value.copy(creatorId = room.creatorId, members = room.members)
            }
        }
        val playback = repository.playback(roomId).first().getOrThrow()
        _state.value = _state.value.copy(
            roomSongIds = playback?.songIds ?: _state.value.roomSongIds,
            message = null
        )
        val local = snapshot()
        if (isHost) {
            pushQueueIfChanged(roomId)
            reportIfChanged(roomId, local)
        } else {
            followIfNeeded(playback?.command, local)
        }
        sendHeartbeatIfDue(roomId, local)
    }

    private suspend fun followIfNeeded(command: RoomCommand?, local: LocalPlayback) {
        if (command == null || !ListenTogetherSyncPolicy.shouldFollow(lastAppliedSeq, command)) return
        val action = ListenTogetherSyncPolicy.decideFollow(command, local)
        if (action.kind == FollowKind.None) {
            lastAppliedSeq = command.serverSeq
            return
        }
        val targetId = action.songId
        val item = if (action.kind == FollowKind.PlaySong && targetId != null) {
            resolver.resolve(listOf(targetId)).firstOrNull()
        } else {
            null
        }
        // 曲目解析不到时不推进序号，下一轮重试
        if (action.kind == FollowKind.PlaySong && item == null && controller.queue.value.none { it.songId == targetId }) {
            AppLogger.w(TAG, "房间曲目 $targetId 解析失败，下一轮重试")
            return
        }
        withContext(playerDispatcher) {
            when (action.kind) {
                FollowKind.PlaySong -> playRoomSong(targetId, item, action.progressMs)
                FollowKind.Resume -> {
                    controller.resume()
                    if (action.progressMs > 0L) controller.seekTo(action.progressMs)
                }
                FollowKind.Pause -> controller.pause()
                FollowKind.Seek -> controller.seekTo(action.progressMs)
                FollowKind.None -> Unit
            }
        }
        lastAppliedSeq = command.serverSeq
    }

    // 目标曲目已在队列就跳转，否则追加后再播
    private fun playRoomSong(songId: Long?, resolved: QueueItem?, progressMs: Long) {
        val id = songId ?: return
        var index = controller.queue.value.indexOfFirst { it.songId == id }
        if (index < 0) {
            val item = resolved ?: return
            controller.appendToQueue(listOf(item))
            index = controller.queue.value.indexOfFirst { it.songId == id }
        }
        if (index < 0) return
        controller.playAtIndex(index)
        if (progressMs > 0L) controller.seekTo(progressMs)
    }

    private suspend fun pushQueueIfChanged(roomId: String) {
        val ids = controller.queue.value.map { it.songId }.filter { it > 0L }.distinct()
        if (ids.isEmpty() || ids == pushedSongIds) return
        val uid = selfUserId()
        if (uid <= 0L) return
        val anchor = ids.indexOf(controller.nowPlaying.value?.songId).coerceAtLeast(0)
        val accepted = repository.reportPlaylist(roomId, uid, playlistVersion + 1, ids, anchor).first().getOrThrow()
        if (accepted) {
            pushedSongIds = ids
            playlistVersion += 1
        } else {
            AppLogger.w(TAG, "房间队列未被服务端接受，下一轮重试")
        }
    }

    private suspend fun reportIfChanged(roomId: String, local: LocalPlayback) {
        if (rejectCooldown > 0) {
            rejectCooldown--
            return
        }
        val command = ListenTogetherSyncPolicy.decideHostCommand(lastReported, local) ?: return
        val accepted = repository.reportCommand(roomId, command).first().getOrThrow()
        if (accepted) {
            lastReported = ReportedPlayback(local.songId, local.playing, local.progressMs, command.clientSeq)
        } else {
            // 未被接受不能记为已上报，冷却几轮后再试
            rejectCooldown = REJECT_COOLDOWN_TICKS
            AppLogger.w(TAG, "指令 ${command.type.wire} 未被服务端接受")
        }
    }

    private suspend fun sendHeartbeatIfDue(roomId: String, local: LocalPlayback) {
        sinceHeartbeatMs += POLL_INTERVAL_MS
        if (sinceHeartbeatMs < heartbeatSeconds * 1000L) return
        sinceHeartbeatMs = 0L
        val songId = local.songId ?: return
        repository.heartbeat(roomId, songId, local.playing, local.progressMs).first()
            .onSuccess { interval -> interval?.takeIf { it > 0 }?.let { heartbeatSeconds = it } }
    }

    // 用 playWhenReady：弱网缓冲期间 isPlaying 为 false，但不应被当作房主暂停
    private fun snapshot() = LocalPlayback(
        songId = controller.nowPlaying.value?.songId,
        playing = controller.playWhenReady.value,
        progressMs = controller.currentPosition.value
    )

    // 房主切歌或暂停立即上报，不等下一轮轮询
    private suspend fun watchLocalChanges() {
        combine(controller.nowPlaying, controller.playWhenReady) { song, playing -> song?.songId to playing }
            .collect {
                val roomId = _state.value.roomId ?: return@collect
                try {
                    mutex.withLock { reportIfChanged(roomId, snapshot()) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "房主即时上报失败", e)
                }
            }
    }
}
