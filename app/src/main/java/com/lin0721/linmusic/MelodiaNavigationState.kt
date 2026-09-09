package com.lin0721.linmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import com.lin0721.linmusic.feature.profile.ui.FollowListMode

enum class Screen {
    Home, Playlist, Search, Library, Settings, Artist, Radio, MvPlayer, PlaylistCategory,
    // 侧边栏二级页
    RecentPlay, ListenData, Cloud, Message, Account,
    // 个人主页与关注/粉丝列表
    Profile, FollowList
}

// 应用级导航状态：回退栈与各页面所需的跳转参数
class MelodiaNavigationState {

    private val backStack = mutableStateListOf(Screen.Home)

    val currentScreen: Screen by derivedStateOf { backStack.lastOrNull() ?: Screen.Home }

    // 栈深大于 1 时才有上一级可回退
    val canNavigateBack: Boolean get() = backStack.size > 1

    var activePlaylistId by mutableStateOf<Long?>(null)
        private set

    var activePlaylistIsAlbum by mutableStateOf(false)
        private set

    var activeArtistId by mutableStateOf<Long?>(null)
        private set

    var activeRadioId by mutableStateOf<Long?>(null)
        private set

    var activeMvId by mutableStateOf<Long?>(null)
        private set

    var activeMvName by mutableStateOf("")
        private set

    var activePlaylistCategory by mutableStateOf<String?>(null)
        private set

    var activeProfileUid by mutableStateOf<Long?>(null)
        private set

    var activeFollowListUid by mutableStateOf<Long?>(null)
        private set

    var activeFollowListMode by mutableStateOf(FollowListMode.FOLLOWS)
        private set

    // 主页三个 tab 的选中项。存在导航状态里而非 HomeScreen 内部——
    // 页面切走时 HomeScreen 会离开 composition，记在里面的话从电台详情页退回来会跳回「全部」
    var homeTab by mutableStateOf(0)
        private set

    // 音乐 tab「最新」二级药丸的选中态，同样存在导航状态里——
    // 从新作 feed 点进专辑详情再返回时，HomeScreen 会被销毁重建，本地 remember 状态会丢
    var showMusicNewWorks by mutableStateOf(false)
        private set

    var searchAutoFocus by mutableStateOf(false)
        private set

    fun navigateTo(screen: Screen) {
        if (backStack.lastOrNull() == screen) return
        when (screen) {
            // 主页为栈底，跳转时清空历史
            Screen.Home -> {
                backStack.clear()
                backStack.add(Screen.Home)
            }
            // 底栏一级入口，始终保留主页作为回退目标
            Screen.Search, Screen.Library -> {
                backStack.clear()
                backStack.add(Screen.Home)
                backStack.add(screen)
            }
            else -> backStack.add(screen)
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun openPlaylist(id: Long, isAlbum: Boolean) {
        activePlaylistId = id
        activePlaylistIsAlbum = isAlbum
        navigateTo(Screen.Playlist)
    }

    fun openArtist(id: Long) {
        activeArtistId = id
        navigateTo(Screen.Artist)
    }

    fun selectHomeTab(index: Int) {
        homeTab = index
        // 点任意主药丸都回到该 tab 的默认内容，「最新」只能通过下面的入口单独选中
        showMusicNewWorks = false
    }

    fun updateShowMusicNewWorks(show: Boolean) {
        showMusicNewWorks = show
    }

    fun openRadio(id: Long) {
        activeRadioId = id
        navigateTo(Screen.Radio)
    }

    fun openMvPlayer(id: Long, name: String) {
        activeMvId = id
        activeMvName = name
        navigateTo(Screen.MvPlayer)
    }

    fun openPlaylistCategory(category: String) {
        activePlaylistCategory = category
        navigateTo(Screen.PlaylistCategory)
    }

    fun openRecentPlay() {
        navigateTo(Screen.RecentPlay)
    }

    fun openListenData() {
        navigateTo(Screen.ListenData)
    }

    fun openCloud() {
        navigateTo(Screen.Cloud)
    }

    fun openMessage() {
        navigateTo(Screen.Message)
    }

    fun openAccount() {
        navigateTo(Screen.Account)
    }

    fun openProfile(uid: Long) {
        activeProfileUid = uid
        navigateTo(Screen.Profile)
    }

    fun openFollowList(uid: Long, mode: FollowListMode) {
        activeFollowListUid = uid
        activeFollowListMode = mode
        navigateTo(Screen.FollowList)
    }

    // 从主页搜索框进入时自动弹键盘，从底栏进入时展示发现内容
    fun openSearch(autoFocus: Boolean) {
        searchAutoFocus = autoFocus
        navigateTo(Screen.Search)
    }

    // 底栏一级入口跳转，进入搜索页时不自动弹键盘
    fun openTab(screen: Screen) {
        searchAutoFocus = false
        navigateTo(screen)
    }
}

@Composable
fun rememberMelodiaNavigationState(): MelodiaNavigationState = remember { MelodiaNavigationState() }
