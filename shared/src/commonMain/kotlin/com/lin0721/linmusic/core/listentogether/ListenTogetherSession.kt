package com.lin0721.linmusic.core.listentogether

import com.lin0721.linmusic.core.listentogether.data.ListenTogetherRepository
import com.lin0721.linmusic.core.listentogether.domain.FollowAction
import com.lin0721.linmusic.core.listentogether.domain.FollowKind
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherMember
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherRoom
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherSyncPolicy
import com.lin0721.linmusic.core.listentogether.domain.LocalPlayback
import com.lin0721.linmusic.core.listentogether.domain.RemoteHandling
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
import kotlinx.coroutines.flow.update
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
    // 房主设置，仅在房主端生效：关闭后忽略成员操作并以自身状态覆盖
    val allowMemberControl: Boolean = true,
    // 非空表示会话因该原因退出，或处于降级重试
    val message: String? = null
)

/**
 * 一起听同步引擎：所有成员都上报本地播放变化，也跟随他人发出的新指令，以服务端序号最新者为准。
 * 只依赖 PlaybackController，两端共用；[playerDispatcher] 用于满足 Media3 只能在主线程操作播放器的约束。
 */
class ListenTogetherSession(
    private val repository: ListenTogetherRepository,
    private val controller: PlaybackController,
    private val resolver: RoomTrackResolver,
    private val selfUserId: suspend () -> Long,
    private val scope: CoroutineScope,
    private val playerDispatcher: CoroutineDispatcher,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        private const val TAG = "ListenTogether"
        private const val POLL_INTERVAL_MS = 3_000L
        private const val STATUS_EVERY_TICKS = 4
        private const val DEFAULT_HEARTBEAT_SECONDS = 30
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val REJECT_COOLDOWN_TICKS = 4

        // 跟随后播放器切歌、缓冲的过渡期内不上报，避免把中间状态当成本地操作回报
        private const val FOLLOW_SETTLE_MS = 2_500L
    }

    private val _state = MutableStateFlow(ListenTogetherState())
    val state: StateFlow<ListenTogetherState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var loopJob: Job? = null
    private var watchJob: Job? = null

    private var selfId = 0L
    private var tick = 0
    private var failures = 0
    private var lastAppliedSeq = 0L
    private var lastReported: ReportedPlayback? = null
    private var reportEnabled = false
    private var forceReport = false
    private var suppressReportUntil = 0L
    private var rejectCooldown = 0
    private var playlistVersion = 0L
    private var mergedRoomIds: Set<Long>? = null
    private var heartbeatSeconds = DEFAULT_HEARTBEAT_SECONDS
    private var sinceHeartbeatMs = 0L
    private var lastLoggedSeq = 0L

    // 成员主动退出的房间：服务端仍记其在房内，接回时跳过，再次通过邀请加入时清除
    private var leftRoomId: String? = null

    suspend fun createRoom(seedSongIds: List<Long>): Result<ListenTogetherRoom> {
        val ids = seedSongIds.filter { it > 0L }.distinct()
        if (ids.isEmpty()) return Result.failure(IllegalArgumentException("没有可同步的网易云歌曲"))
        val room = repository.createRoom(ids).first().getOrElse { return Result.failure(it) }
        leftRoomId = null
        start(room, isHost = true, uid = selfUserId())
        return Result.success(room)
    }

    // 先校验房间可加入，再接受邀请
    suspend fun joinRoom(roomId: String, inviterId: Long): Result<ListenTogetherRoom> {
        val check = repository.checkRoom(roomId).first().getOrElse { return Result.failure(it) }
        if (!check.joinable) return Result.failure(IllegalStateException(check.message ?: "房间已无法加入"))
        val room = repository.acceptInvitation(roomId, inviterId).first().getOrElse { return Result.failure(it) }
        leftRoomId = null
        val uid = selfUserId()
        start(room, isHost = room.isHost(uid), uid = uid)
        return Result.success(room)
    }

    // 进程重启后接回服务端仍存在的房间
    suspend fun resumeExisting(): Boolean {
        if (_state.value.active) return true
        val room = repository.status().first().getOrNull()?.room ?: return false
        if (room.id == leftRoomId) return false
        val uid = selfUserId()
        start(room, isHost = room.isHost(uid), uid = uid)
        return true
    }

    // 房主结束房间；成员仅停止同步，本地播放保持不变
    suspend fun leave(): Result<Unit> {
        val current = _state.value
        val roomId = current.roomId
        val result = if (current.isHost && roomId != null) {
            repository.endRoom(roomId).first().map { }
        } else {
            leftRoomId = roomId
            Result.success(Unit)
        }
        stop()
        return result
    }

    fun setAllowMemberControl(allowed: Boolean) {
        _state.update { it.copy(allowMemberControl = allowed) }
    }

    /**
     * 把歌曲追加到房间队列末尾：先取最新房间队列再合并上报，避免覆盖他人刚加的歌。
     * 返回实际新增的数量；已在房间队列中的歌不重复添加。
     */
    suspend fun addToRoomQueue(songIds: List<Long>): Result<Int> {
        val roomId = _state.value.roomId ?: return Result.failure(IllegalStateException("当前不在一起听房间"))
        return mutex.withLock {
            val latest = repository.playback(roomId).first().getOrElse { return@withLock Result.failure(it) }
            val existing = latest?.songIds ?: _state.value.roomSongIds
            val added = songIds.filter { it > 0L && it !in existing }.distinct()
            if (added.isEmpty()) return@withLock Result.success(0)
            if (selfId <= 0L) return@withLock Result.failure(IllegalStateException("未登录"))
            val ids = existing + added
            val accepted = repository.reportPlaylist(roomId, selfId, playlistVersion + 1, ids, 0).first()
                .getOrElse { return@withLock Result.failure(it) }
            if (!accepted) return@withLock Result.failure(IllegalStateException("房间队列未被服务端接受"))
            playlistVersion += 1
            _state.update { it.copy(roomSongIds = ids) }
            Result.success(added.size)
        }
    }

    fun stop(message: String? = null) {
        loopJob?.cancel()
        loopJob = null
        watchJob?.cancel()
        watchJob = null
        _state.value = ListenTogetherState(message = message)
    }

    private fun start(room: ListenTogetherRoom, isHost: Boolean, uid: Long) {
        stop()
        selfId = uid
        tick = 0
        failures = 0
        lastAppliedSeq = 0L
        lastReported = null
        // 成员先对齐房间再上报，否则一进房就会用本机歌曲覆盖房间
        reportEnabled = isHost
        forceReport = false
        suppressReportUntil = 0L
        rejectCooldown = 0
        playlistVersion = 0L
        mergedRoomIds = null
        sinceHeartbeatMs = 0L
        lastLoggedSeq = 0L
        _state.value = ListenTogetherState(
            active = true,
            roomId = room.id,
            isHost = isHost,
            creatorId = room.creatorId,
            members = room.members
        )
        loopJob = scope.launch { runLoop(room.id, isHost) }
        watchJob = scope.launch { watchLocalChanges() }
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
                _state.update { it.copy(message = "连接不稳定，重试中") }
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
                _state.update { it.copy(creatorId = room.creatorId, members = room.members) }
            }
        }
        val playback = repository.playback(roomId).first().getOrThrow()
        _state.update { it.copy(roomSongIds = playback?.songIds ?: it.roomSongIds, message = null) }
        playback?.command?.let { logCommand(it, isHost, playback.songIds.size) }

        mergeRoomQueue(playback?.songIds.orEmpty())
        handleRemote(playback?.command)
        reportIfChanged(roomId)
        sendHeartbeatIfDue(roomId, snapshot())
    }

    private fun logCommand(command: RoomCommand, isHost: Boolean, queueSize: Int) {
        if (command.serverSeq == lastLoggedSeq) return
        lastLoggedSeq = command.serverSeq
        AppLogger.i(
            TAG,
            "房间指令 role=${if (isHost) "host" else "guest"} from=${command.userId} type=${command.commandType} " +
                "target=${command.targetSongId} progress=${command.progressMs} playing=${command.playing} " +
                "serverSeq=${command.serverSeq} queue=$queueSize"
        )
    }

    private suspend fun handleRemote(command: RoomCommand?) {
        if (command == null || !ListenTogetherSyncPolicy.shouldFollow(lastAppliedSeq, command)) {
            // 房间尚无任何指令时，成员以当前本地状态为起点开始上报
            if (command == null && !reportEnabled) enableReporting()
            return
        }
        val current = _state.value
        when (ListenTogetherSyncPolicy.handleRemote(command, selfId, current.isHost, current.allowMemberControl)) {
            RemoteHandling.Ignore -> {
                lastAppliedSeq = command.serverSeq
                if (!reportEnabled) enableReporting()
            }
            RemoteHandling.Override -> {
                lastAppliedSeq = command.serverSeq
                forceReport = true
                AppLogger.i(TAG, "成员控制已关闭，覆盖成员 ${command.userId} 的指令")
            }
            RemoteHandling.Follow -> follow(command)
        }
    }

    private suspend fun follow(command: RoomCommand) {
        val local = snapshot()
        val action = ListenTogetherSyncPolicy.decideFollow(command, local)
        if (action.kind == FollowKind.None) {
            lastAppliedSeq = command.serverSeq
            acknowledge(action, command, local)
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
        suppressReportUntil = now() + FOLLOW_SETTLE_MS
        acknowledge(action, command, local)
    }

    // 把跟随后的目标状态记为已上报，后续本地状态与之一致时不会回报
    private fun acknowledge(action: FollowAction, command: RoomCommand, local: LocalPlayback) {
        val playing = when (action.kind) {
            FollowKind.Pause -> false
            FollowKind.Resume, FollowKind.PlaySong -> true
            else -> command.playing ?: local.playing
        }
        lastReported = ReportedPlayback(
            songId = action.songId ?: local.songId,
            playing = playing,
            progressMs = if (action.kind == FollowKind.None) local.progressMs else command.progressMs,
            seq = lastReported?.seq ?: 0L,
            atMs = now()
        )
        reportEnabled = true
    }

    private fun enableReporting() {
        val local = snapshot()
        lastReported = ReportedPlayback(local.songId, local.playing, local.progressMs, lastReported?.seq ?: 0L, now())
        reportEnabled = true
    }

    // 目标曲目已在队列就跳转，否则追加后再播；起播进度交给 playQueue，切歌取直链期间单独 seek 会丢失
    private fun playRoomSong(songId: Long?, resolved: QueueItem?, progressMs: Long) {
        val id = songId ?: return
        var queue = controller.queue.value
        if (queue.none { it.songId == id }) {
            val item = resolved ?: return
            queue = queue + item
        }
        val index = queue.indexOfFirst { it.songId == id }
        if (index < 0) return
        controller.playQueue(
            items = queue,
            startIndex = index,
            playContext = controller.playContext.value,
            source = controller.playSource.value,
            startPositionMs = progressMs
        )
    }

    // 首次读到房间队列作为基线；之后他人新增的曲目追加到本地队列末尾，不整份替换
    private suspend fun mergeRoomQueue(roomIds: List<Long>) {
        val localIds = controller.queue.value.map { it.songId }
        val merged = mergedRoomIds
        if (merged == null) {
            mergedRoomIds = roomIds.toSet() + localIds
            return
        }
        val newIds = roomIds.filter { it > 0L && it !in merged }.distinct()
        if (newIds.isEmpty()) return
        mergedRoomIds = merged + newIds
        val toAppend = newIds.filter { it !in localIds }
        if (toAppend.isEmpty()) return
        val resolved = resolver.resolve(toAppend).associateBy { it.songId }
        val items = toAppend.mapNotNull { resolved[it] }
        if (items.isEmpty()) return
        withContext(playerDispatcher) { controller.appendToQueue(items) }
        AppLogger.i(TAG, "房间新增 ${items.size} 首，已追加到本地队列")
    }

    private suspend fun reportIfChanged(roomId: String) {
        if (!reportEnabled || now() < suppressReportUntil) return
        if (rejectCooldown > 0) {
            rejectCooldown--
            return
        }
        val local = snapshot()
        // 缓冲中进度停滞，按停滞后的进度重新计时，避免被误判为拖动
        val last = lastReported
        if (last != null && local.playing && !controller.isPlaying.value && !forceReport &&
            last.songId == local.songId && last.playing
        ) {
            lastReported = last.copy(progressMs = local.progressMs, atMs = now())
            return
        }
        val command = ListenTogetherSyncPolicy.decideReport(
            last = lastReported,
            current = local,
            nowMs = now(),
            periodicProgress = _state.value.isHost,
            force = forceReport
        ) ?: return
        val accepted = repository.reportCommand(roomId, command).first().getOrThrow()
        if (accepted) {
            lastReported = ReportedPlayback(local.songId, local.playing, local.progressMs, command.clientSeq, now())
            forceReport = false
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

    // 用 playWhenReady：弱网缓冲期间 isPlaying 为 false，但不应被当作暂停
    private fun snapshot() = LocalPlayback(
        songId = controller.nowPlaying.value?.songId,
        playing = controller.playWhenReady.value,
        progressMs = controller.currentPosition.value
    )

    // 本地切歌或暂停立即上报，不等下一轮轮询
    private suspend fun watchLocalChanges() {
        combine(controller.nowPlaying, controller.playWhenReady) { song, playing -> song?.songId to playing }
            .collect {
                val roomId = _state.value.roomId ?: return@collect
                try {
                    mutex.withLock { reportIfChanged(roomId) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "即时上报失败", e)
                }
            }
    }
}
