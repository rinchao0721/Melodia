package com.lin0721.linmusic.core.listentogether.domain

// 本地播放快照
data class LocalPlayback(val songId: Long?, val playing: Boolean, val progressMs: Long)

// 本机最近一次成功上报（或已跟随）的状态，atMs 为记录时刻，用于推算预期进度
data class ReportedPlayback(
    val songId: Long?,
    val playing: Boolean,
    val progressMs: Long,
    val seq: Long,
    val atMs: Long
)

enum class FollowKind { None, PlaySong, Resume, Pause, Seek }

data class FollowAction(val kind: FollowKind, val songId: Long? = null, val progressMs: Long = 0L)

enum class PlayCommandType(val wire: String) { Play("PLAY"), Pause("PAUSE"), GoTo("GOTO"), Progress("PROGRESS") }

data class PlayCommand(
    val type: PlayCommandType,
    val songId: Long?,
    val formerSongId: Long?,
    val progressMs: Long,
    val playing: Boolean,
    val clientSeq: Long
)

// 远端指令的处理方式：自己发出的回声忽略；房主关闭成员控制时用自身状态覆盖；其余跟随
enum class RemoteHandling { Ignore, Follow, Override }

// 跟随与上报决策，纯函数，不碰网络和播放器
object ListenTogetherSyncPolicy {

    // 进度偏差超过该值才重定位，避免每轮纠偏打断播放
    const val RESYNC_THRESHOLD_MS = 10_000L

    // 房主定期补报进度的间隔，供后加入的成员对齐
    const val PROGRESS_REPORT_INTERVAL_MS = 5_000L

    // 实际进度偏离预期超过该值视为手动拖动
    const val SEEK_DETECT_MS = 3_000L

    fun shouldFollow(lastAppliedSeq: Long, command: RoomCommand?): Boolean =
        command != null && command.serverSeq > lastAppliedSeq

    fun handleRemote(command: RoomCommand, selfId: Long, isHost: Boolean, allowMemberControl: Boolean): RemoteHandling =
        when {
            selfId != 0L && command.userId == selfId -> RemoteHandling.Ignore
            isHost && !allowMemberControl -> RemoteHandling.Override
            else -> RemoteHandling.Follow
        }

    // 收到他人新指令后的动作；已一致则返回 None
    fun decideFollow(command: RoomCommand, local: LocalPlayback): FollowAction {
        val target = command.targetSongId
        if (target != null && target != local.songId) {
            return FollowAction(FollowKind.PlaySong, target, command.progressMs)
        }
        val wantsPlaying = command.playing
        return when {
            wantsPlaying == true && !local.playing -> FollowAction(FollowKind.Resume, progressMs = command.progressMs)
            wantsPlaying == false && local.playing -> FollowAction(FollowKind.Pause, progressMs = command.progressMs)
            // 暂停状态下不纠偏
            wantsPlaying != false && local.playing && drift(local.progressMs, command.progressMs) > RESYNC_THRESHOLD_MS ->
                FollowAction(FollowKind.Seek, progressMs = command.progressMs)
            else -> FollowAction(FollowKind.None)
        }
    }

    /**
     * 本地状态相对上次记录是否需要上报；无实质差异返回 null。
     * 优先级：强制覆盖 > 换歌 > 播放状态 > 拖动 / 定期进度。periodicProgress 仅房主为真，成员只在拖动时报进度。
     */
    fun decideReport(
        last: ReportedPlayback?,
        current: LocalPlayback,
        nowMs: Long,
        periodicProgress: Boolean,
        force: Boolean = false
    ): PlayCommand? {
        current.songId ?: return null
        if (last == null) return build(PlayCommandType.GoTo, current, null, 1L)
        val seq = last.seq + 1L
        val former = last.songId?.takeIf { it != current.songId }
        if (force || last.songId != current.songId) return build(PlayCommandType.GoTo, current, former, seq)
        if (last.playing != current.playing) {
            return build(if (current.playing) PlayCommandType.Play else PlayCommandType.Pause, current, null, seq)
        }
        val elapsed = (nowMs - last.atMs).coerceAtLeast(0L)
        val expected = last.progressMs + if (last.playing) elapsed else 0L
        val seeked = drift(current.progressMs, expected) > SEEK_DETECT_MS
        val periodicDue = periodicProgress && current.playing && elapsed >= PROGRESS_REPORT_INTERVAL_MS
        return if (seeked || periodicDue) build(PlayCommandType.Progress, current, null, seq) else null
    }

    private fun build(type: PlayCommandType, c: LocalPlayback, former: Long?, seq: Long) =
        PlayCommand(type, c.songId, former, c.progressMs, c.playing, seq)

    private fun drift(a: Long, b: Long): Long = if (a > b) a - b else b - a
}
