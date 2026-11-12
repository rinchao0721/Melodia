package com.lin0721.linmusic.core.listentogether.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenTogetherSyncPolicyTest {

    private fun command(song: Long? = 1L, progress: Long = 0L, playing: Boolean? = true, seq: Long = 10L, from: Long? = 2L) =
        RoomCommand("PLAY", song, progress, playing, seq, from)

    private fun reported(song: Long? = 1L, playing: Boolean = true, progress: Long = 0L, seq: Long = 3L, at: Long = 0L) =
        ReportedPlayback(song, playing, progress, seq, at)

    @Test
    fun `序号未前进不跟随`() {
        assertFalse(ListenTogetherSyncPolicy.shouldFollow(10L, command(seq = 10L)))
        assertTrue(ListenTogetherSyncPolicy.shouldFollow(10L, command(seq = 11L)))
        assertFalse(ListenTogetherSyncPolicy.shouldFollow(0L, null))
    }

    @Test
    fun `自己发出的指令忽略`() {
        assertEquals(RemoteHandling.Ignore, ListenTogetherSyncPolicy.handleRemote(command(from = 7L), 7L, isHost = false, allowMemberControl = true))
        assertEquals(RemoteHandling.Ignore, ListenTogetherSyncPolicy.handleRemote(command(from = 7L), 7L, isHost = true, allowMemberControl = false))
    }

    @Test
    fun `房主关闭成员控制时覆盖他人指令，开启时跟随`() {
        assertEquals(RemoteHandling.Override, ListenTogetherSyncPolicy.handleRemote(command(from = 2L), 7L, isHost = true, allowMemberControl = false))
        assertEquals(RemoteHandling.Follow, ListenTogetherSyncPolicy.handleRemote(command(from = 2L), 7L, isHost = true, allowMemberControl = true))
        assertEquals(RemoteHandling.Follow, ListenTogetherSyncPolicy.handleRemote(command(from = 2L), 7L, isHost = false, allowMemberControl = false))
    }

    @Test
    fun `发起人未知时按他人处理`() {
        assertEquals(RemoteHandling.Follow, ListenTogetherSyncPolicy.handleRemote(command(from = null), 7L, isHost = false, allowMemberControl = true))
    }

    @Test
    fun `歌曲不一致时换歌并带进度`() {
        val action = ListenTogetherSyncPolicy.decideFollow(command(song = 2L, progress = 5_000L), LocalPlayback(1L, true, 0L))
        assertEquals(FollowKind.PlaySong, action.kind)
        assertEquals(2L, action.songId)
        assertEquals(5_000L, action.progressMs)
    }

    @Test
    fun `播放状态不一致时恢复或暂停`() {
        assertEquals(FollowKind.Resume, ListenTogetherSyncPolicy.decideFollow(command(progress = 3_000L), LocalPlayback(1L, false, 0L)).kind)
        assertEquals(FollowKind.Pause, ListenTogetherSyncPolicy.decideFollow(command(playing = false), LocalPlayback(1L, true, 0L)).kind)
    }

    @Test
    fun `进度偏差在阈值内不动作，超过阈值重定位`() {
        val local = LocalPlayback(1L, true, 0L)
        assertEquals(FollowKind.None, ListenTogetherSyncPolicy.decideFollow(command(progress = 9_000L), local).kind)
        assertEquals(FollowKind.Seek, ListenTogetherSyncPolicy.decideFollow(command(progress = 11_000L), local).kind)
        assertEquals(FollowKind.None, ListenTogetherSyncPolicy.decideFollow(command(progress = 60_000L, playing = false), LocalPlayback(1L, false, 0L)).kind)
    }

    @Test
    fun `首次上报为换歌指令，无歌曲不上报`() {
        assertEquals(PlayCommandType.GoTo, ListenTogetherSyncPolicy.decideReport(null, LocalPlayback(1L, true, 0L), 0L, true)?.type)
        assertNull(ListenTogetherSyncPolicy.decideReport(null, LocalPlayback(null, true, 0L), 0L, true))
    }

    @Test
    fun `换歌优先于状态变化并带上一首`() {
        val result = ListenTogetherSyncPolicy.decideReport(reported(), LocalPlayback(2L, false, 0L), 1_000L, false)
        assertEquals(PlayCommandType.GoTo, result?.type)
        assertEquals(1L, result?.formerSongId)
        assertEquals(4L, result?.clientSeq)
    }

    @Test
    fun `播放状态变化上报播放或暂停`() {
        assertEquals(PlayCommandType.Pause, ListenTogetherSyncPolicy.decideReport(reported(), LocalPlayback(1L, false, 0L), 1_000L, false)?.type)
        assertEquals(PlayCommandType.Play, ListenTogetherSyncPolicy.decideReport(reported(playing = false), LocalPlayback(1L, true, 0L), 1_000L, false)?.type)
    }

    @Test
    fun `成员只在拖动时报进度，房主定期补报`() {
        val last = reported(progress = 10_000L, at = 0L)
        // 正常播放 6 秒：成员不报，房主到点补报
        assertNull(ListenTogetherSyncPolicy.decideReport(last, LocalPlayback(1L, true, 16_000L), 6_000L, periodicProgress = false))
        assertEquals(PlayCommandType.Progress, ListenTogetherSyncPolicy.decideReport(last, LocalPlayback(1L, true, 16_000L), 6_000L, periodicProgress = true)?.type)
        // 1 秒内跳到 60 秒：视为拖动
        assertEquals(PlayCommandType.Progress, ListenTogetherSyncPolicy.decideReport(last, LocalPlayback(1L, true, 60_000L), 1_000L, periodicProgress = false)?.type)
    }

    @Test
    fun `暂停中进度不变不上报，暂停时拖动上报`() {
        val last = reported(playing = false, progress = 10_000L, at = 0L)
        assertNull(ListenTogetherSyncPolicy.decideReport(last, LocalPlayback(1L, false, 10_000L), 20_000L, periodicProgress = true))
        assertEquals(PlayCommandType.Progress, ListenTogetherSyncPolicy.decideReport(last, LocalPlayback(1L, false, 30_000L), 20_000L, periodicProgress = false)?.type)
    }

    @Test
    fun `强制上报覆盖时发换歌指令且序号递增`() {
        val result = ListenTogetherSyncPolicy.decideReport(reported(seq = 5L), LocalPlayback(1L, true, 0L), 500L, false, force = true)
        assertEquals(PlayCommandType.GoTo, result?.type)
        assertEquals(6L, result?.clientSeq)
        assertNull(result?.formerSongId)
    }
}
