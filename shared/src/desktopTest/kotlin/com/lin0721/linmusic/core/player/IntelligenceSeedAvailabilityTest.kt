package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceSeedAvailabilityTest {

    private fun item(
        id: Long = 1L,
        localUri: String? = null,
        radioId: Long = 0L,
        external: ExternalTrack? = null,
        cloudDisk: Boolean = false
    ) = QueueItem(
        songId = id,
        title = "歌曲",
        artist = "歌手",
        coverUrl = "",
        localUri = localUri,
        radioId = radioId,
        external = external,
        cloudDisk = cloudDisk
    )

    @Test
    fun `在线普通歌曲可以作为心动模式种子`() {
        assertTrue(canUseIntelligenceAsSeed(item(), isPodcast = false, playContext = "我喜欢的音乐"))
        assertNull(intelligenceSeedBlockReason(item(), isPodcast = false, playContext = "我喜欢的音乐"))
    }

    @Test
    fun `播客节目不能作为心动模式种子`() {
        val reason = intelligenceSeedBlockReason(item(radioId = 12L), isPodcast = true, playContext = "podcast")
        assertEquals("播客节目不支持心动模式", reason)
    }

    @Test
    fun `播客上下文即使状态未同步也不能开启`() {
        val reason = intelligenceSeedBlockReason(item(), isPodcast = false, playContext = PlaybackController.CONTEXT_PODCAST)
        assertEquals("播客节目不支持心动模式", reason)
    }

    @Test
    fun `云盘上下文不能开启心动模式`() {
        val reason = intelligenceSeedBlockReason(item(), isPodcast = false, playContext = PlaybackController.CONTEXT_CLOUD)
        assertEquals("云盘歌曲不支持心动模式", reason)
    }

    @Test
    fun `带云盘标记的队列项不能开启心动模式`() {
        val reason = intelligenceSeedBlockReason(item(cloudDisk = true), isPodcast = false, playContext = "我喜欢的音乐")
        assertEquals("云盘歌曲不支持心动模式", reason)
    }

    @Test
    fun `本地歌曲不能作为心动模式种子`() {
        val reason = intelligenceSeedBlockReason(item(id = -1L, localUri = "content://local/1"), isPodcast = false, playContext = null)
        assertEquals("本地歌曲不支持心动模式", reason)
    }

    @Test
    fun `没有在线曲目id的歌曲不能作为种子`() {
        val reason = intelligenceSeedBlockReason(item(id = 0L), isPodcast = false, playContext = "歌单")
        assertEquals("本地歌曲不支持心动模式", reason)
    }

    @Test
    fun `第三方音源不能作为心动模式种子`() {
        val external = ExternalTrack(
            id = "songmid",
            name = "外部歌曲",
            artists = "外部歌手",
            platform = MusicPlatform.QQ
        )
        val reason = intelligenceSeedBlockReason(item(id = -99L, external = external), isPodcast = false, playContext = "QQ音乐")
        assertEquals("当前音源不支持心动模式", reason)
    }

    @Test
    fun `没有当前曲目时不能开启心动模式`() {
        val reason = intelligenceSeedBlockReason(null, isPodcast = false, playContext = null)
        assertEquals("请先播放一首歌曲", reason)
    }
}
