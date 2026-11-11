package com.lin0721.linmusic.core.listentogether.domain

// 本地播放快照
data class LocalPlayback(val songId: Long?, val playing: Boolean, val progressMs: Long)

// 房主最近一次成功上报的状态
data class ReportedPlayback(val songId: Long?, val playing: Boolean, val progressMs: Long, val seq: Long)

enum class FollowKind { None, PlaySong, Resume, Pause, Seek }

data class FollowAction(val kind: FollowKind, val songId: Long? = null, val progressMs: Long = 0L)

enum class HostCommandType(val wire: String) { Play("PLAY"), Pause("PAUSE"), GoTo("GOTO"), Progress("PROGRESS") }

data class HostCommand(
    val type: HostCommandType,
    val songId: Long?,
    val formerSongId: Long?,
    val progressMs: Long,
    val playing: Boolean,
    val clientSeq: Long
)

// 跟随与上报决策，纯函数，不碰网络和播放器
object ListenTogetherSyncPolicy {

    // 进度偏差超过该值才重定位，避免每轮纠偏打断播放
    const val RESYNC_THRESHOLD_MS = 10_000L

    // 房主进度漂移超过该值才补报一次 PROGRESS
    const val PROGRESS_REPORT_STEP_MS = 5_000L

    fun shouldFollow(lastAppliedSeq: Long, command: RoomCommand?): Boolean =
        command != null && command.serverSeq > lastAppliedSeq

    // 客机收到新指令后的动作；已一致则返回 None
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

    // 房主本地状态与上次上报无实质差异时返回 null；优先级：换歌 > 播放状态 > 进度漂移
    fun decideHostCommand(last: ReportedPlayback?, current: LocalPlayback): HostCommand? {
        val songId = current.songId ?: return null
        if (last == null) return build(HostCommandType.GoTo, current, null, 1L)
        val seq = last.seq + 1L
        return when {
            last.songId != songId -> build(HostCommandType.GoTo, current, last.songId, seq)
            last.playing != current.playing ->
                build(if (current.playing) HostCommandType.Play else HostCommandType.Pause, current, null, seq)
            current.playing && drift(last.progressMs, current.progressMs) > PROGRESS_REPORT_STEP_MS ->
                build(HostCommandType.Progress, current, null, seq)
            else -> null
        }
    }

    private fun build(type: HostCommandType, c: LocalPlayback, former: Long?, seq: Long) =
        HostCommand(type, c.songId, former, c.progressMs, c.playing, seq)

    private fun drift(a: Long, b: Long): Long = if (a > b) a - b else b - a
}
