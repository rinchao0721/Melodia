package com.lin0721.linmusic.desktop.ui.library

import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.SimilarRoamingController
import com.lin0721.linmusic.desktop.data.LibraryShortcut
import com.lin0721.linmusic.desktop.data.local.LOCAL_ALL_TRACKS_ID
import com.lin0721.linmusic.desktop.ui.navigation.BackStack
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.playlist.DAILY_RECOMMEND_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryShortcutsToggleTest {

    @Test
    fun `全部入口在未开启与已开启状态下返回正确点击动作`() {
        val expected = mapOf(
            (LibraryShortcut.PERSONAL_FM to false) to LibraryShortcutClickAction.START_PERSONAL_FM,
            (LibraryShortcut.PERSONAL_FM to true) to LibraryShortcutClickAction.DISABLE_PERSONAL_FM,
            (LibraryShortcut.INTELLIGENCE to false) to LibraryShortcutClickAction.START_INTELLIGENCE,
            (LibraryShortcut.INTELLIGENCE to true) to LibraryShortcutClickAction.DISABLE_INTELLIGENCE,
            (LibraryShortcut.SIMILAR_ROAMING to false) to LibraryShortcutClickAction.START_SIMILAR_ROAMING,
            (LibraryShortcut.SIMILAR_ROAMING to true) to LibraryShortcutClickAction.DISABLE_SIMILAR_ROAMING,
            (LibraryShortcut.DAILY_RECOMMEND to false) to LibraryShortcutClickAction.OPEN_DAILY_RECOMMEND,
            (LibraryShortcut.DAILY_RECOMMEND to true) to LibraryShortcutClickAction.NAVIGATE_BACK,
            (LibraryShortcut.CLOUD to false) to LibraryShortcutClickAction.OPEN_CLOUD,
            (LibraryShortcut.CLOUD to true) to LibraryShortcutClickAction.NAVIGATE_BACK,
            (LibraryShortcut.LOCAL_MUSIC to false) to LibraryShortcutClickAction.OPEN_LOCAL_MUSIC,
            (LibraryShortcut.LOCAL_MUSIC to true) to LibraryShortcutClickAction.NAVIGATE_BACK
        )

        LibraryShortcut.entries.forEach { item ->
            listOf(false, true).forEach { active ->
                assertEquals(
                    "意外动作: $item, active=$active",
                    expected.getValue(item to active),
                    resolveShortcutClickAction(item, isActive = active)
                )
            }
        }
    }

    @Test
    fun `全部入口在不同状态下显示对应提示文案`() {
        assertEquals("私人FM", shortcutTooltip(LibraryShortcut.PERSONAL_FM, isActive = false))
        assertEquals("私人FM · 点击退出", shortcutTooltip(LibraryShortcut.PERSONAL_FM, isActive = true))

        assertEquals("心动模式", shortcutTooltip(LibraryShortcut.INTELLIGENCE, isActive = false))
        assertEquals("心动模式 · 点击退出", shortcutTooltip(LibraryShortcut.INTELLIGENCE, isActive = true))

        assertEquals("相似歌曲漫游", shortcutTooltip(LibraryShortcut.SIMILAR_ROAMING, isActive = false, enabled = true))
        assertEquals(
            "相似歌曲漫游（请先播放一首云端歌曲）",
            shortcutTooltip(LibraryShortcut.SIMILAR_ROAMING, isActive = false, enabled = false)
        )
        assertEquals("相似歌曲漫游 · 点击退出", shortcutTooltip(LibraryShortcut.SIMILAR_ROAMING, isActive = true, enabled = true))

        assertEquals("每日推荐", shortcutTooltip(LibraryShortcut.DAILY_RECOMMEND, isActive = false))
        assertEquals("每日推荐 · 点击返回", shortcutTooltip(LibraryShortcut.DAILY_RECOMMEND, isActive = true))

        assertEquals("云盘", shortcutTooltip(LibraryShortcut.CLOUD, isActive = false))
        assertEquals("云盘 · 点击返回", shortcutTooltip(LibraryShortcut.CLOUD, isActive = true))

        assertEquals("本地音乐", shortcutTooltip(LibraryShortcut.LOCAL_MUSIC, isActive = false))
        assertEquals("本地音乐 · 点击返回", shortcutTooltip(LibraryShortcut.LOCAL_MUSIC, isActive = true))
    }

    @Test
    fun `相似漫游已开启时即使无云端曲目也允许点击退出`() {
        assertFalse(
            isShortcutEnabled(
                item = LibraryShortcut.SIMILAR_ROAMING,
                isActive = false,
                isLoggedIn = true,
                hasCloudSong = false
            )
        )
        assertTrue(
            isShortcutEnabled(
                item = LibraryShortcut.SIMILAR_ROAMING,
                isActive = true,
                isLoggedIn = true,
                hasCloudSong = false
            )
        )
        assertTrue(
            isShortcutEnabled(
                item = LibraryShortcut.SIMILAR_ROAMING,
                isActive = false,
                isLoggedIn = true,
                hasCloudSong = true
            )
        )
        assertTrue(
            isShortcutEnabled(
                item = LibraryShortcut.SIMILAR_ROAMING,
                isActive = false,
                isLoggedIn = false,
                hasCloudSong = false
            )
        )
    }

    @Test
    fun `根据播放上下文与当前路由识别高亮快捷入口`() {
        assertEquals(
            setOf(LibraryShortcut.PERSONAL_FM, LibraryShortcut.DAILY_RECOMMEND),
            activeLibraryShortcuts(
                playContext = PlaybackController.CONTEXT_PERSONAL_FM,
                currentRoute = DesktopRoute.Playlist(DAILY_RECOMMEND_ID, "每日推荐")
            )
        )
        assertEquals(
            setOf(LibraryShortcut.INTELLIGENCE, LibraryShortcut.CLOUD),
            activeLibraryShortcuts(
                playContext = PlaybackController.CONTEXT_INTELLIGENCE,
                currentRoute = DesktopRoute.Cloud
            )
        )
        assertEquals(
            setOf(LibraryShortcut.SIMILAR_ROAMING, LibraryShortcut.LOCAL_MUSIC),
            activeLibraryShortcuts(
                playContext = SimilarRoamingController.CONTEXT_ROAMING,
                currentRoute = DesktopRoute.LocalPlaylist(LOCAL_ALL_TRACKS_ID, "全部本地歌曲")
            )
        )
        assertEquals(
            emptySet<LibraryShortcut>(),
            activeLibraryShortcuts(
                playContext = null,
                currentRoute = DesktopRoute.Playlist(12345L, "普通歌单")
            )
        )
    }

    @Test
    fun `返回栈没有上一页时退回上一页不崩溃且保持当前页`() {
        val stack = BackStack(DesktopRoute.Cloud, Unit)
        assertNull(stack.backToPreviousPage())
        assertEquals(DesktopRoute.Cloud, stack.current)
    }
}
