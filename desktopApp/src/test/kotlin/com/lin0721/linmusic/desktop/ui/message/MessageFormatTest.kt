package com.lin0721.linmusic.desktop.ui.message

import com.lin0721.linmusic.feature.message.domain.PrivateCard
import com.lin0721.linmusic.feature.message.domain.PrivateCardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MessageFormatTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val nowMs = ZonedDateTime.of(2026, 4, 15, 16, 30, 0, 0, zone).toInstant().toEpochMilli()

    private fun timeMs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun formatConversationSummaryParsesShareTypesWithPrefix() {
        val song = formatConversationSummary("分享单曲：晴天 - 周杰伦")
        assertEquals("♪ [单曲]", song.musicTag)
        assertEquals("晴天 - 周杰伦", song.body)

        val playlist = formatConversationSummary("分享歌单: 深夜循环")
        assertEquals("♪ [歌单]", playlist.musicTag)
        assertEquals("深夜循环", playlist.body)

        val album = formatConversationSummary("分享专辑：范特西")
        assertEquals("♪ [专辑]", album.musicTag)
        assertEquals("范特西", album.body)

        val mv = formatConversationSummary("分享MV：夜曲")
        assertEquals("♪ [MV]", mv.musicTag)
        assertEquals("夜曲", mv.body)

        val bare = formatConversationSummary("分享单曲")
        assertEquals("♪ [单曲]", bare.musicTag)
        assertEquals("", bare.body)

        val plain = formatConversationSummary("今晚听什么？")
        assertNull(plain.musicTag)
        assertEquals("今晚听什么？", plain.body)

        val blank = formatConversationSummary("   ")
        assertNull(blank.musicTag)
        assertEquals("", blank.body)
    }

    @Test
    fun isRedundantShareTextSuppressesAutoTextOnly() {
        val card = PrivateCard(
            kind = PrivateCardKind.Song,
            title = "晴天",
            subtitle = "周杰伦",
            cover = "",
            resourceId = 186016L
        )
        assertTrue(isRedundantShareText("分享单曲", card))
        assertTrue(isRedundantShareText("分享单曲：晴天", card))
        assertTrue(isRedundantShareText("分享单曲:晴天", card))
        assertFalse(isRedundantShareText("强烈推荐这首", card))
    }

    @Test
    fun formatConversationTimeCoversTodayYesterdayWeekMonthAndYear() {
        assertEquals("", formatConversationTime(0L, nowMs, zone))
        assertEquals("09:05", formatConversationTime(timeMs(2026, 4, 15, 9, 5), nowMs, zone))
        assertEquals("昨天", formatConversationTime(timeMs(2026, 4, 14, 21, 0), nowMs, zone))
        // 2026-04-15 is Wednesday -> 2026-04-12 is Sunday (3 days ago)
        assertEquals("周日", formatConversationTime(timeMs(2026, 4, 12, 12, 0), nowMs, zone))
        assertEquals("4/1", formatConversationTime(timeMs(2026, 4, 1, 12, 0), nowMs, zone))
        assertEquals("2025/12/31", formatConversationTime(timeMs(2025, 12, 31, 23, 59), nowMs, zone))
    }

    @Test
    fun formatChatTimeDividerCoversSameDayYesterdayThisYearAndPastYear() {
        assertEquals("", formatChatTimeDivider(-1L, nowMs, zone))
        assertEquals("14:08", formatChatTimeDivider(timeMs(2026, 4, 15, 14, 8), nowMs, zone))
        assertEquals("昨天 20:15", formatChatTimeDivider(timeMs(2026, 4, 14, 20, 15), nowMs, zone))
        assertEquals("4月10日 08:30", formatChatTimeDivider(timeMs(2026, 4, 10, 8, 30), nowMs, zone))
        assertEquals("2025年11月2日 19:00", formatChatTimeDivider(timeMs(2025, 11, 2, 19, 0), nowMs, zone))
    }
}
