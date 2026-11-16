package com.lin0721.linmusic

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
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

    @Test
    fun `导航快照包含本地音乐页面时可以序列化并还原`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.navigateTo(Screen.Library)
        nav.openLocalMusic()
        nav.navigateTo(Screen.LocalMusicSettings)
        nav.navigateTo(Screen.LocalArtist("茶太"))
        nav.navigateTo(Screen.LocalAlbum("id:42"))
        nav.navigateTo(Screen.LocalFolder("/sdcard/Music"))
        nav.navigateTo(Screen.LocalPlaylists)
        nav.navigateTo(Screen.LocalPlaylist(7L))

        val json = Json.encodeToString(NavigationSnapshot.serializer(), nav.toSnapshot())
        val restored = Json.decodeFromString(NavigationSnapshot.serializer(), json)
        assertEquals(listOf(Screen.Library), restored.libraryStack)
        assertEquals(
            listOf(
                Screen.LocalMusic,
                Screen.LocalMusicSettings,
                Screen.LocalArtist("茶太"),
                Screen.LocalAlbum("id:42"),
                Screen.LocalFolder("/sdcard/Music"),
                Screen.LocalPlaylists,
                Screen.LocalPlaylist(7L)
            ),
            restored.localMusicStack
        )
    }

    @Test
    fun `首页处于非全部分类时回退会切回全部`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC, nav.homeTab)
        assertTrue(nav.canGoBackToHomeAll)

        nav.navigateBack()
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `首页展开最新时回退优先收起最新再切回全部`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.updateShowMusicNewWorks(true)
        assertTrue(nav.showMusicNewWorks)
        assertTrue(nav.canGoBackToHomeAll)

        // 第一次返回收起最新
        nav.navigateBack()
        assertFalse(nav.showMusicNewWorks)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC, nav.homeTab)
        assertTrue(nav.canGoBackToHomeAll)

        // 第二次返回切回全部
        nav.navigateBack()
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `从其他Tab根页面回退到首页时重置分类为全部`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.navigateTo(Screen.Library)
        assertEquals(Screen.Library, nav.currentScreen)

        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `从首页音乐二级页面回退到栈底时保留音乐分类`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.openPlaylist(1L, false)
        assertEquals(Screen.Playlist(1L, false), nav.currentScreen)

        // 第一次返回回到首页音乐分类
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC, nav.homeTab)
        assertTrue(nav.canGoBackToHomeAll)

        // 第二次返回切回全部
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `从首页播客二级页面回退到栈底时保留播客分类`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_PODCAST)
        nav.navigateTo(Screen.PodcastCategory(100L, "分类"))
        assertEquals(Screen.PodcastCategory(100L, "分类"), nav.currentScreen)

        // 第一次返回回到播客分类
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_PODCAST, nav.homeTab)
        assertTrue(nav.canGoBackToHomeAll)

        // 第二次返回切回全部
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `从最新Feed进入二级页回退到栈底时保留最新展开态`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.updateShowMusicNewWorks(true)
        nav.openPlaylist(1L, true)
        assertEquals(Screen.Playlist(1L, true), nav.currentScreen)

        // 第一次返回保留最新Feed展开态
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC, nav.homeTab)
        assertTrue(nav.showMusicNewWorks)
        assertTrue(nav.canGoBackToHomeAll)

        // 第二次返回收起最新
        nav.navigateBack()
        assertFalse(nav.showMusicNewWorks)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC, nav.homeTab)
        assertTrue(nav.canGoBackToHomeAll)

        // 第三次返回切回全部
        nav.navigateBack()
        assertEquals(Screen.Home, nav.currentScreen)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
        assertFalse(nav.canGoBackToHomeAll)
    }

    @Test
    fun `栈里重复出现的同一目标拥有不同的栈帧 id`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(1L)
        val first = nav.currentEntry
        nav.openPlaylist(2L, false)
        nav.openArtist(1L)
        val second = nav.currentEntry

        assertEquals(first.screen, second.screen)
        assertTrue(first.id != second.id)
    }

    @Test
    fun `出栈后栈帧 id 不再存活，其余仍存活`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(1L)
        val artistId = nav.currentEntry.id
        nav.openPlaylist(2L, true)
        val playlistId = nav.currentEntry.id

        nav.navigateBack()

        assertTrue(artistId in nav.liveEntryIds)
        assertFalse(playlistId in nav.liveEntryIds)
    }

    @Test
    fun `各 tab 的栈帧 id 互不重复`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.Library)
        nav.openArtist(1L)
        nav.openTab(Screen.Home)

        val ids = nav.liveEntryIds
        assertEquals(8, ids.size)
    }

    @Test
    fun `新增顶层 tab 拥有独立回退栈且点击底栏 tab 时重置为第一级界面`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openTab(Screen.LocalMusic)
        assertEquals(Screen.LocalMusic, nav.currentScreen)

        nav.navigateTo(Screen.LocalSongs)
        assertEquals(Screen.LocalSongs, nav.currentScreen)
        assertTrue(nav.canNavigateBack)

        nav.openTab(Screen.Home)
        assertEquals(Screen.Home, nav.currentScreen)

        // 3A 规则：点击底栏 Tab 时重置目标 Tab 栈至第一级界面
        nav.openTab(Screen.LocalMusic)
        assertEquals(Screen.LocalMusic, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    @Test
    fun `动态更新首选根 Tab 后回退时回退到新根 Tab`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.updateDefaultRootTab(Screen.Search)
        nav.openTab(Screen.Library)
        assertEquals(Screen.Library, nav.currentScreen)

        nav.navigateBack()
        assertEquals(Screen.Search, nav.currentScreen)
    }

    @Test
    fun `快照恢复后保留栈帧 id 且后续分配不重复`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(1L)
        nav.openPlaylist(2L, false)
        val before = nav.liveEntryIds
        val currentId = nav.currentEntry.id

        val snapshot = nav.toSnapshot()
        val restored = MelodiaNavigationState(
            initialHomeStack = snapshot.homeStack,
            initialSearchStack = snapshot.searchStack,
            initialLibraryStack = snapshot.libraryStack,
            initialActiveTab = Screen.Home,
            initialHomeIds = snapshot.homeIds,
            initialSearchIds = snapshot.searchIds,
            initialLibraryIds = snapshot.libraryIds,
            initialNextEntryId = snapshot.nextEntryId,
            initialLocalMusicStack = snapshot.localMusicStack,
            initialDownloadsStack = snapshot.downloadsStack,
            initialCloudStack = snapshot.cloudStack,
            initialMyProfileStack = snapshot.myProfileStack,
            initialLocalMusicIds = snapshot.localMusicIds,
            initialDownloadsIds = snapshot.downloadsIds,
            initialCloudIds = snapshot.cloudIds,
            initialMyProfileIds = snapshot.myProfileIds
        )

        assertEquals(before, restored.liveEntryIds)
        assertEquals(currentId, restored.currentEntry.id)
        restored.openArtist(9L)
        assertFalse(restored.currentEntry.id in before)
    }

    @Test
    fun `旧版本快照缺少栈帧 id 时重新分配`() = inSnapshot {
        val legacy = Json { ignoreUnknownKeys = true }.decodeFromString(
            NavigationSnapshot.serializer(),
            """{"homeStack":[{"type":"com.lin0721.linmusic.Screen.Home"}],"searchStack":[{"type":"com.lin0721.linmusic.Screen.Search"}],"libraryStack":[{"type":"com.lin0721.linmusic.Screen.Library"}],"activeTabIndex":0,"homeTab":0}"""
        )
        val restored = MelodiaNavigationState(
            initialHomeStack = legacy.homeStack,
            initialSearchStack = legacy.searchStack,
            initialLibraryStack = legacy.libraryStack,
            initialHomeIds = legacy.homeIds,
            initialSearchIds = legacy.searchIds,
            initialLibraryIds = legacy.libraryIds,
            initialNextEntryId = legacy.nextEntryId
        )

        assertEquals(7, restored.liveEntryIds.size)
    }

    @Test
    fun `点击底栏Tab按钮时始终重置目标Tab回退栈至第一级界面`() = inSnapshot {
        val nav = MelodiaNavigationState()
        // 主页打开设置页
        nav.navigateTo(Screen.Settings)
        assertEquals(Screen.Settings, nav.currentScreen)

        // 点击底栏切换到搜索页
        nav.openTab(Screen.Search)
        assertEquals(Screen.Search, nav.currentScreen)

        // 点击底栏切回主页，应回到主页第一级界面而不是设置页
        nav.openTab(Screen.Home)
        assertEquals(Screen.Home, nav.currentScreen)
        assertFalse(nav.canNavigateBack)
    }

    private data class NavStateCheck(
        val snapshot: NavigationSnapshot,
        val defaultRootTab: Screen,
        val showMusicNewWorks: Boolean,
        val searchAutoFocus: Boolean,
        val playerNavTargetStackDepth: Int,
        val playerNavOriginMediaId: String?
    )

    private fun captureState(nav: MelodiaNavigationState) = NavStateCheck(
        snapshot = nav.toSnapshot(),
        defaultRootTab = nav.defaultRootTab,
        showMusicNewWorks = nav.showMusicNewWorks,
        searchAutoFocus = nav.searchAutoFocus,
        playerNavTargetStackDepth = nav.playerNavTargetStackDepth,
        playerNavOriginMediaId = nav.playerNavOriginMediaId
    )

    @Test
    fun `peekBackTarget 在栈内返回时指向栈内上一帧且不改变状态`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.openArtist(1L)
        val expectedEntry = nav.currentEntry
        nav.openPlaylist(2L, false)

        val before = captureState(nav)
        val target = nav.peekBackTarget()
        assertEquals(before, captureState(nav))
        assertEquals(BackTarget.Entry(expectedEntry), target)

        nav.navigateBack()
        assertEquals((target as BackTarget.Entry).entry, nav.currentEntry)
    }

    @Test
    fun `peekBackTarget 在非首选 Tab 根页面跨 Tab 返回时指向首选根 Tab 的根页面`() = inSnapshot {
        val nav = MelodiaNavigationState()
        val homeRootEntry = nav.currentEntry
        // 首选根 Tab 栈里原本有多级页面，跨 Tab 返回时应直达根页面
        nav.openArtist(1L)
        nav.openPlaylist(2L, true)
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.openTab(Screen.Library)

        val before = captureState(nav)
        val target = nav.peekBackTarget()
        assertEquals(before, captureState(nav))
        assertEquals(BackTarget.Entry(homeRootEntry), target)

        nav.navigateBack()
        assertEquals((target as BackTarget.Entry).entry, nav.currentEntry)
    }

    @Test
    fun `peekBackTarget 在主页展开最新时返回 HomeCollapse`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.updateShowMusicNewWorks(true)
        val entryBefore = nav.currentEntry

        val before = captureState(nav)
        val target = nav.peekBackTarget()
        assertEquals(before, captureState(nav))
        assertEquals(BackTarget.HomeCollapse, target)

        nav.navigateBack()
        assertEquals(entryBefore, nav.currentEntry)
        assertFalse(nav.showMusicNewWorks)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC, nav.homeTab)
    }

    @Test
    fun `peekBackTarget 在主页非全部分类时返回 HomeCollapse`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        val entryBefore = nav.currentEntry

        val before = captureState(nav)
        val target = nav.peekBackTarget()
        assertEquals(before, captureState(nav))
        assertEquals(BackTarget.HomeCollapse, target)

        nav.navigateBack()
        assertEquals(entryBefore, nav.currentEntry)
        assertEquals(com.lin0721.linmusic.feature.home.ui.TAB_ALL, nav.homeTab)
    }

    @Test
    fun `peekBackTarget 已在首选根 Tab 根页面时返回 Exit`() = inSnapshot {
        val nav = MelodiaNavigationState()
        val entryBefore = nav.currentEntry

        val before = captureState(nav)
        val target = nav.peekBackTarget()
        assertEquals(before, captureState(nav))
        assertEquals(BackTarget.Exit, target)

        nav.navigateBack()
        assertEquals(entryBefore, nav.currentEntry)
        assertEquals(before, captureState(nav))
    }

    @Test
    fun `peekBackTarget 首选根 Tab 非主页且在其根页面时返回 Exit`() = inSnapshot {
        val nav = MelodiaNavigationState()
        nav.selectHomeTab(com.lin0721.linmusic.feature.home.ui.TAB_MUSIC)
        nav.updateShowMusicNewWorks(true)
        nav.updateDefaultRootTab(Screen.Library)
        nav.openTab(Screen.Library)
        val entryBefore = nav.currentEntry

        val before = captureState(nav)
        val target = nav.peekBackTarget()
        assertEquals(before, captureState(nav))
        assertEquals(BackTarget.Exit, target)

        nav.navigateBack()
        assertEquals(entryBefore, nav.currentEntry)
        assertEquals(before, captureState(nav))
    }
}
