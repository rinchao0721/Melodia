package com.lin0721.linmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import com.lin0721.linmusic.feature.profile.ui.FollowListMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 导航目标：每一帧自带跳转参数（而非存在导航状态里的单一全局变量），
// 这样栈里任意两帧即使是同一种页面类型，也各自持有自己的参数，互不覆盖
@Serializable
sealed class Screen {
    @Serializable
    data object Home : Screen()
    @Serializable
    data class Playlist(val id: Long, val isAlbum: Boolean) : Screen()
    @Serializable
    data object Search : Screen()
    @Serializable
    data object Library : Screen()
    @Serializable
    data object Settings : Screen()
    @Serializable
    data class Artist(val id: Long) : Screen()
    @Serializable
    data class Radio(val id: Long) : Screen()
    @Serializable
    data class MvPlayer(val id: Long, val name: String) : Screen()
    @Serializable
    data class PlaylistCategory(val category: String) : Screen()
    // 侧边栏二级页
    @Serializable
    data object RecentPlay : Screen()
    @Serializable
    data object ListenData : Screen()
    @Serializable
    data object Cloud : Screen()
    @Serializable
    data object LocalMusic : Screen()
    @Serializable
    data object Message : Screen()
    @Serializable
    data object Account : Screen()
    // 个人主页与关注/粉丝列表
    @Serializable
    data class Profile(val uid: Long) : Screen()
    @Serializable
    data class FollowList(val uid: Long, val mode: FollowListMode) : Screen()
}

// 应用级导航状态：主页/搜索/音乐库三个底栏 tab 各自持有一条独立回退栈，
// 切 tab 只切换「当前激活栈」，不清空其余 tab 已经积累的浏览历史
class MelodiaNavigationState(
    initialHomeStack: List<Screen> = listOf(Screen.Home),
    initialSearchStack: List<Screen> = listOf(Screen.Search),
    initialLibraryStack: List<Screen> = listOf(Screen.Library),
    initialActiveTab: Screen = Screen.Home,
    initialHomeTab: Int = 0
) {

    private val homeStack = mutableStateListOf<Screen>().apply { addAll(initialHomeStack) }
    private val searchStack = mutableStateListOf<Screen>().apply { addAll(initialSearchStack) }
    private val libraryStack = mutableStateListOf<Screen>().apply { addAll(initialLibraryStack) }

    var activeTab by mutableStateOf(initialActiveTab)
        private set

    private fun stackFor(tab: Screen): MutableList<Screen> = when (tab) {
        Screen.Search -> searchStack
        Screen.Library -> libraryStack
        else -> homeStack
    }

    private val activeStack: MutableList<Screen> get() = stackFor(activeTab)

    val currentScreen: Screen by derivedStateOf { activeStack.lastOrNull() ?: activeTab }

    // 当前 tab 内栈深大于 1 时才有上一级可回退
    val canNavigateBack: Boolean get() = activeStack.size > 1

    // 主页三个 tab 的选中项。存在导航状态里而非 HomeScreen 内部——
    // 页面切走时 HomeScreen 会离开 composition，记在里面的话从电台详情页退回来会跳回「全部」
    var homeTab by mutableStateOf(initialHomeTab)
        private set

    // 音乐 tab「最新」二级药丸的选中态，同样存在导航状态里——
    // 从新作 feed 点进专辑详情再返回时，HomeScreen 会被销毁重建，本地 remember 状态会丢
    var showMusicNewWorks by mutableStateOf(false)
        private set

    var searchAutoFocus by mutableStateOf(false)
        private set

    // 记录从全屏播放器发起跳转时所在 tab 的栈深与歌曲 ID；当前栈深大于该深度时表示处于从播放器打开的二级页面中
    var playerNavTargetStackDepth by mutableIntStateOf(-1)
        private set

    var playerNavOriginMediaId by mutableStateOf<String?>(null)
        private set

    val isNavigatingFromPlayer: Boolean
        get() = playerNavTargetStackDepth != -1 && activeStack.size > playerNavTargetStackDepth


    fun navigateFromPlayer(originMediaId: String? = null, action: () -> Unit) {
        if (playerNavTargetStackDepth == -1) {
            playerNavTargetStackDepth = activeStack.size
            playerNavOriginMediaId = originMediaId
        }
        action()
    }

    fun resetPlayerNavigation() {
        playerNavTargetStackDepth = -1
        playerNavOriginMediaId = null
    }

    fun navigateTo(screen: Screen) {
        when (screen) {
            // 底栏 tab
            Screen.Home, Screen.Search, Screen.Library -> {
                if (activeTab == screen) {
                    resetStackToRoot(screen)
                    return
                }
                resetPlayerNavigation()
                activeTab = screen
            }
            else -> {
                if (activeStack.lastOrNull() == screen) return
                activeStack.add(screen)
            }
        }
    }

    // true 表示这次返回顺带弹出了全屏播放器；false 表示已在当前 tab 根、切回了主页 tab（或已在主页 tab 根，交还系统处理）
    fun navigateBack(): Boolean {
        if (activeStack.size > 1) {
            val willExitPlayerNav = isNavigatingFromPlayer && (activeStack.size - 1) <= playerNavTargetStackDepth
            activeStack.removeAt(activeStack.lastIndex)
            if (willExitPlayerNav) {
                resetPlayerNavigation()
            }
            return willExitPlayerNav
        }
        // 已在当前 tab 的根页面
        if (activeTab != Screen.Home) {
            resetStackToRoot(Screen.Home)
            activeTab = Screen.Home
        }
        return false
    }

    // 把指定 tab 的栈清回只剩根页面
    private fun resetStackToRoot(tab: Screen) {
        val stack = stackFor(tab)
        while (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
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

    fun openLocalMusic() {
        navigateTo(Screen.LocalMusic)
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

    internal fun toSnapshot(): NavigationSnapshot = NavigationSnapshot(
        homeStack = homeStack.toList(),
        searchStack = searchStack.toList(),
        libraryStack = libraryStack.toList(),
        activeTabIndex = tabIndex(activeTab),
        homeTab = homeTab
    )
}

// 三条回退栈 + 当前 tab 的可序列化快照，用于跨进程重建保留浏览历史。
// searchAutoFocus/playerNavTargetStackDepth 是一次性动作标记，不算浏览历史，重建后重置为默认值即可
@Serializable
internal data class NavigationSnapshot(
    val homeStack: List<Screen>,
    val searchStack: List<Screen>,
    val libraryStack: List<Screen>,
    val activeTabIndex: Int,
    val homeTab: Int
)

private fun tabIndex(tab: Screen): Int = when (tab) {
    Screen.Search -> 1
    Screen.Library -> 2
    else -> 0
}

private fun tabFromIndex(index: Int): Screen = when (index) {
    1 -> Screen.Search
    2 -> Screen.Library
    else -> Screen.Home
}

private val navigationJson = Json { ignoreUnknownKeys = true }

private val MelodiaNavigationStateSaver: Saver<MelodiaNavigationState, String> = Saver(
    save = { state -> navigationJson.encodeToString(NavigationSnapshot.serializer(), state.toSnapshot()) },
    restore = { raw ->
        val snapshot = runCatching {
            navigationJson.decodeFromString(NavigationSnapshot.serializer(), raw)
        }.getOrNull()
        if (snapshot == null) {
            MelodiaNavigationState()
        } else {
            MelodiaNavigationState(
                initialHomeStack = snapshot.homeStack.ifEmpty { listOf(Screen.Home) },
                initialSearchStack = snapshot.searchStack.ifEmpty { listOf(Screen.Search) },
                initialLibraryStack = snapshot.libraryStack.ifEmpty { listOf(Screen.Library) },
                initialActiveTab = tabFromIndex(snapshot.activeTabIndex),
                initialHomeTab = snapshot.homeTab
            )
        }
    }
)

@Composable
fun rememberMelodiaNavigationState(): MelodiaNavigationState =
    rememberSaveable(saver = MelodiaNavigationStateSaver) { MelodiaNavigationState() }
