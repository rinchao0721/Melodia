package com.lin0721.linmusic.core.preferences

import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationButtonLayoutTest {

    @Test
    fun `空值解码返回默认配置且仅喜欢和不喜欢开启`() {
        assertEquals(NotificationButtonLayout.DEFAULT, NotificationButtonLayout.decode(null))
        assertEquals(NotificationButtonLayout.DEFAULT, NotificationButtonLayout.decode("   "))

        val visibleItems = NotificationButtonLayout.DEFAULT.filter { it.visible }.map { it.item }
        assertEquals(listOf(NotificationButton.LIKE, NotificationButton.DISLIKE), visibleItems)
    }

    @Test
    fun `编解码往返保留顺序与开关状态`() {
        val custom = listOf(
            NotificationButtonSetting(NotificationButton.SLEEP_TIMER, true),
            NotificationButtonSetting(NotificationButton.LIKE, false),
            NotificationButtonSetting(NotificationButton.PLAY_MODE, true),
            NotificationButtonSetting(NotificationButton.CLOSE_APP, true),
            NotificationButtonSetting(NotificationButton.DISLIKE, true),
            NotificationButtonSetting(NotificationButton.DESKTOP_LRC, false),
            NotificationButtonSetting(NotificationButton.SEEK_FORWARD, true),
            NotificationButtonSetting(NotificationButton.SEEK_BACK, false)
        )

        val encoded = NotificationButtonLayout.encode(custom)
        val decoded = NotificationButtonLayout.decode(encoded)

        assertEquals(custom, decoded)
    }

    @Test
    fun `解码丢弃未知项并将缺失枚举按默认值补齐到末尾`() {
        val raw = "sleep_timer:1,unknown_btn:1,like:0,unknown_2:0"
        val decoded = NotificationButtonLayout.decode(raw)

        assertEquals(NotificationButton.entries.size, decoded.size)
        assertEquals(NotificationButton.SLEEP_TIMER, decoded[0].item)
        assertTrue(decoded[0].visible)
        assertEquals(NotificationButton.LIKE, decoded[1].item)
        assertFalse(decoded[1].visible)

        // 缺失的项应按各自 defaultVisible 补在末尾，例如 DISLIKE 默认为 true
        val tail = decoded.drop(2)
        assertEquals(
            NotificationButton.entries.filter {
                it != NotificationButton.SLEEP_TIMER && it != NotificationButton.LIKE
            },
            tail.map { it.item }
        )
        assertTrue(tail.first { it.item == NotificationButton.DISLIKE }.visible)
        assertFalse(tail.first { it.item == NotificationButton.CLOSE_APP }.visible)
    }

    @Test
    fun `归一化保留首个重复项并去重`() {
        val duplicated = listOf(
            NotificationButtonSetting(NotificationButton.CLOSE_APP, true),
            NotificationButtonSetting(NotificationButton.LIKE, false),
            NotificationButtonSetting(NotificationButton.CLOSE_APP, false)
        )

        val normalized = NotificationButtonLayout.normalize(duplicated)

        assertEquals(NotificationButton.entries.size, normalized.size)
        assertEquals(NotificationButton.CLOSE_APP, normalized.first().item)
        assertTrue(normalized.first().visible)
        assertEquals(1, normalized.count { it.item == NotificationButton.CLOSE_APP })
    }

    @Test
    fun `按播放上下文过滤显示按钮`() {
        val allVisible = NotificationButton.entries.map { NotificationButtonSetting(it, true) }

        val normal = NotificationButtonLayout.resolveVisibleForContext(
            layout = allVisible,
            isPersonalFm = false,
            isPodcast = false
        )
        assertFalse(NotificationButton.DISLIKE in normal)
        assertTrue(NotificationButton.PLAY_MODE in normal)
        assertFalse(NotificationButton.SEEK_BACK in normal)
        assertFalse(NotificationButton.SEEK_FORWARD in normal)

        val fm = NotificationButtonLayout.resolveVisibleForContext(
            layout = allVisible,
            isPersonalFm = true,
            isPodcast = false
        )
        assertTrue(NotificationButton.DISLIKE in fm)
        assertFalse(NotificationButton.PLAY_MODE in fm)
        assertFalse(NotificationButton.SEEK_BACK in fm)
        assertFalse(NotificationButton.SEEK_FORWARD in fm)

        val podcast = NotificationButtonLayout.resolveVisibleForContext(
            layout = allVisible,
            isPersonalFm = false,
            isPodcast = true
        )
        assertFalse(NotificationButton.DISLIKE in podcast)
        assertTrue(NotificationButton.SEEK_BACK in podcast)
        assertTrue(NotificationButton.SEEK_FORWARD in podcast)
    }

    @Test
    fun `SettingsPreferences 持久化读写通知栏按钮配置`() = runTest {
        val prefs = SettingsPreferences(InMemoryPreferencesStore())
        assertEquals(NotificationButtonLayout.DEFAULT, prefs.notificationButtons.first())

        val updated = NotificationButtonLayout.DEFAULT.reversed().map {
            if (it.item == NotificationButton.SLEEP_TIMER) it.copy(visible = true) else it
        }
        prefs.saveNotificationButtons(updated)

        assertEquals(updated, prefs.notificationButtons.first())
    }
}
