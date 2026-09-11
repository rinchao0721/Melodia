package com.lin0721.linmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import com.lin0721.linmusic.feature.profile.ui.FollowListMode

// 导航目标：每一帧自带跳转参数（而非存在导航状态里的单一全局变量），
// 这样栈里任意两帧即使是同一种页面类型，也各自持有自己的参数，互不覆盖
sealed class Screen {
    data object Home : Screen()
    data class Playlist(val id: Long, val isAlbum: Boolean) : Screen()
    data object Search : Screen()
    data object Library : Screen()
    data object Settings : Screen()
    data class Artist(val id: Long) : Screen()
    data class Radio(val id: Long) : Screen()
    data class MvPlayer(val id: Long, val name: String) : Screen()
    data class PlaylistCategory(val category: String) : Screen()
    // 侧边栏二级页
    data object RecentPlay : Screen()
    data object ListenData : Screen()
    data object Cloud : Screen()
    data object Message : Screen()
    data object Account : Screen()
    // 个人主页与关注/粉丝列表
    data class Profile(val uid: Long) : Screen()
    data class FollowList(val uid: Long, val mode: FollowListMode) : Screen()
}

// 应用级导航状态：回退栈，每一帧自带跳转参数
class MelodiaNavigationState {

    private val backStack = mutableStateListOf<Screen>(Screen.Home)

    val currentScreen: Screen by derivedStateOf { backStack.lastOrNull() ?: Screen.Home }

    // 栈深大于 1 时才有上一级可回退
    val canNavigateBack: Boolean get() = backStack.size > 1

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

    // 记录从全屏播放器发起跳转时的栈深；当前栈深大于该深度时表示处于从播放器打开的二级页面中
    var playerNavTargetStackDepth by mutableIntStateOf(-1)
        private set

    val isNavigatingFromPlayer: Boolean
        get() = playerNavTargetStackDepth != -1 && backStack.size > playerNavTargetStackDepth


    fun navigateFromPlayer(action: () -> Unit) {
        if (playerNavTargetStackDepth == -1) {
            playerNavTargetStackDepth = backStack.size
        }
        action()
    }

    fun resetPlayerNavigation() {
        playerNavTargetStackDepth = -1
    }

    fun navigateTo(screen: Screen) {
        if (backStack.lastOrNull() == screen) return
        when (screen) {
            // 主页为栈底，跳转时清空历史
            Screen.Home -> {
                playerNavTargetStackDepth = -1
                backStack.clear()
                backStack.add(Screen.Home)
            }
            // 底栏一级入口，始终保留主页作为回退目标
            Screen.Search, Screen.Library -> {
                playerNavTargetStackDepth = -1
                backStack.clear()
                backStack.add(Screen.Home)
                backStack.add(screen)
            }
            else -> backStack.add(screen)
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.size > 1) {
            val willExitPlayerNav = isNavigatingFromPlayer && (backStack.size - 1) <= playerNavTargetStackDepth
            backStack.removeAt(backStack.lastIndex)
            if (willExitPlayerNav) {
                playerNavTargetStackDepth = -1
            }
            return willExitPlayerNav
        }
        return false
    }

    fun openPlaylist(id: Long, isAlbum: Boolean) {
        navigateTo(Screen.Playlist(id, isAlbum))
    }

    fun openArtist(id: Long) {
        navigateTo(Screen.Artist(id))
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
        navigateTo(Screen.Radio(id))
    }

    fun openMvPlayer(id: Long, name: String) {
        navigateTo(Screen.MvPlayer(id, name))
    }

    fun openPlaylistCategory(category: String) {
        navigateTo(Screen.PlaylistCategory(category))
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
        navigateTo(Screen.Profile(uid))
    }

    fun openFollowList(uid: Long, mode: FollowListMode) {
        navigateTo(Screen.FollowList(uid, mode))
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
