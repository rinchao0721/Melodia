package com.lin0721.linmusic.core.listentogether.domain

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class ListenTogetherSyncPolicyTest {

    private fun command(song: Long? = 1L, progress: Long = 0L, playing: Boolean? = true, seq: Long = 10L) =
        RoomCommand("PLAY", song, progress, playing, seq)

    @Test
    fun `序号未前进不跟随`() {
        assertFalse(ListenTogetherSyncPolicy.shouldFollow(10L, command(seq = 10L)))
        assertTrue(ListenTogetherSyncPolicy.shouldFollow(10L, command(seq = 11L)))
        assertFalse(ListenTogetherSyncPolicy.shouldFollow(0L, null))
    }

    @Test
    fun `歌曲不一致时换歌并带进度`() {
        val action = ListenTogetherSyncPolicy.decideFollow(command(song = 2L, progress = 5_000L), LocalPlayback(1L, true, 0L))
        assertEquals(FollowKind.PlaySong, action.kind)
        assertEquals(2L, action.songId)
        assertEquals(5_000L, action.progressMs)
    }

    @Test
    fun `房间播放而本地暂停时恢复`() {
        val action = ListenTogetherSyncPolicy.decideFollow(command(progress = 3_000L), LocalPlayback(1L, false, 0L))
        assertEquals(FollowKind.Resume, action.kind)
        assertEquals(3_000L, action.progressMs)
    }

    @Test
    fun `房间暂停而本地播放时暂停`() {
        val action = ListenTogetherSyncPolicy.decideFollow(command(playing = false), LocalPlayback(1L, true, 0L))
        assertEquals(FollowKind.Pause, action.kind)
    }

    @Test
    fun `进度偏差在阈值内不动作，超过阈值重定位`() {
        val local = LocalPlayback(1L, true, 0L)
        assertEquals(FollowKind.None, ListenTogetherSyncPolicy.decideFollow(command(progress = 9_000L), local).kind)
        assertEquals(FollowKind.Seek, ListenTogetherSyncPolicy.decideFollow(command(progress = 11_000L), local).kind)
    }

    @Test
    fun `双方都暂停时不纠偏`() {
        val action = ListenTogetherSyncPolicy.decideFollow(command(progress = 60_000L, playing = false), LocalPlayback(1L, false, 0L))
        assertEquals(FollowKind.None, action.kind)
    }

    @Test
    fun `房主首次上报为换歌指令`() {
        val result = ListenTogetherSyncPolicy.decideHostCommand(null, LocalPlayback(1L, true, 0L))
        assertEquals(HostCommandType.GoTo, result?.type)
        assertEquals(1L, result?.clientSeq)
    }

    @Test
    fun `房主无歌曲或无变化不上报`() {
        assertNull(ListenTogetherSyncPolicy.decideHostCommand(null, LocalPlayback(null, true, 0L)))
        assertNull(ListenTogetherSyncPolicy.decideHostCommand(ReportedPlayback(1L, true, 1_000L, 3L), LocalPlayback(1L, true, 2_000L)))
    }

    @Test
    fun `房主换歌优先于状态变化并带上一首`() {
        val result = ListenTogetherSyncPolicy.decideHostCommand(ReportedPlayback(1L, true, 0L, 3L), LocalPlayback(2L, false, 0L))
        assertEquals(HostCommandType.GoTo, result?.type)
        assertEquals(1L, result?.formerSongId)
        assertEquals(4L, result?.clientSeq)
    }

    @Test
    fun `房主播放状态变化与进度漂移`() {
        val last = ReportedPlayback(1L, true, 0L, 3L)
        assertEquals(HostCommandType.Pause, ListenTogetherSyncPolicy.decideHostCommand(last, LocalPlayback(1L, false, 0L))?.type)
        assertEquals(HostCommandType.Progress, ListenTogetherSyncPolicy.decideHostCommand(last, LocalPlayback(1L, true, 6_000L))?.type)
    }
}
