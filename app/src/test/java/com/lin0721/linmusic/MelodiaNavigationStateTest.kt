package com.lin0721.linmusic

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodiaNavigationStateTest {

    // 状态读写发生在组合之外，需显式包一层快照
    private fun <T> inSnapshot(block: () -> T): T {
        val snapshot = Snapshot.takeMutableSnapshot()
        return try {
            val result = snapshot.enter(block)
            snapshot.apply()
            result
        } finally {
            snapshot.dispose()
        }
    }

    @Test
    fun `初始处于主页且无法回退`() = inSnapshot {
        val nav = MelodiaNavigationState()
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `跳转到详情页后可以回退`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Settings)
        assertEquals(Screen.Settings, nav.currentScreen)
        assertTrue(nav.canNavigateBack)

        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `重复跳转到当前页不入栈`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Settings)
        nav.navigateTo(Screen.Settings)
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
    }

    @Test
    fun `跳转到主页会清空历史`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Settings)
        nav.navigateTo(Screen.Artist(1L))
        nav.navigateTo(Screen.Home)
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `底栏入口始终保留主页作为回退目标`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Settings)
        nav.navigateTo(Screen.Artist(1L))
        nav.navigateTo(Screen.Library)

        assertEquals(Screen.Library, nav.currentScreen)
        nav.navigateBack()
        // 中间的 Settings/Artist 已被清掉，直接回到主页
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `已在栈底时回退不越界`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateBack()
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
    }

    @Test
    fun `打开歌单会记录ID与专辑标记并跳转`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openPlaylist(id = 123L, isAlbum = true)
        assertEquals(Screen.Playlist(123L, true), nav.currentScreen)
    }

    @Test
    fun `打开歌手会记录ID并跳转`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(456L)
        assertEquals(Screen.Artist(456L), nav.currentScreen)
    }

    @Test
    fun `从主页搜索框进入时自动聚焦`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openSearch(autoFocus = true)
        assertEquals(Screen.Search, nav.currentScreen)
        assertTrue(nav.searchAutoFocus)
    }

    @Test
    fun `从底栏进入搜索页时不自动聚焦`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openSearch(autoFocus = true)
        nav.navigateTo(Screen.Home)
        nav.openTab(Screen.Search)
        assertEquals(Screen.Search, nav.currentScreen)
        assertFalse(nav.searchAutoFocus)
    }

    @Test
    fun `非相邻的同类型页面各自保留自己的参数`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openPlaylist(id = 1L, isAlbum = false)
        nav.openArtist(2L)
        nav.openPlaylist(id = 3L, isAlbum = false)
        assertEquals(Screen.Playlist(3L, false), nav.currentScreen)

        nav.navigateBack()
        assertEquals(Screen.Artist(2L), nav.currentScreen)

        nav.navigateBack()
        assertEquals(Screen.Playlist(1L, false), nav.currentScreen)
    }

    @Test
    fun `从播放器跳转后退回起点会提示重新打开播放器`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(1L)
        assertFalse(nav.isNavigatingFromPlayer)

        nav.navigateFromPlayer { nav.openArtist(2L) }
        assertTrue(nav.isNavigatingFromPlayer)
        assertEquals(Screen.Artist(2L), nav.currentScreen)

        val shouldReopenPlayer = nav.navigateBack()
        assertTrue(shouldReopenPlayer)
        assertFalse(nav.isNavigatingFromPlayer)
        assertEquals(Screen.Artist(1L), nav.currentScreen)
    }

    @Test
    fun `关闭播放器会重置播放器跳转标记`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateFromPlayer { nav.openArtist(1L) }
        assertTrue(nav.isNavigatingFromPlayer)

        nav.resetPlayerNavigation()
        assertFalse(nav.isNavigatingFromPlayer)
    }
}
