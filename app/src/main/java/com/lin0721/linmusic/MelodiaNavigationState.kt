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

import com.lin0721.linmusic.feature.home.ui.TAB_ALL
import com.lin0721.linmusic.feature.home.ui.TAB_MUSIC
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
    // 「播客」tab 的二级页
    @Serializable
    data object PodcastSubscribed : Screen()
    @Serializable
    data object PodcastToplist : Screen()
    @Serializable
    data class PodcastCategory(val id: Long, val name: String) : Screen()
    @Serializable
    data class PlaylistCategory(val category: String) : Screen()
    // 「音乐」tab 的曲风详情
    @Serializable
    data class Style(val id: Long, val name: String) : Screen()
    // 侧边栏二级页
    @Serializable
    data object RecentPlay : Screen()
    @Serializable
    data object ListenData : Screen()
    @Serializable
    data object Cloud : Screen()
    @Serializable
    data object Downloads : Screen()
    @Serializable
    data object LocalMusic : Screen()
    @Serializable
    data object LocalMusicSettings : Screen()
    @Serializable
    data object LocalSongs : Screen()
    @Serializable
    data object LocalArtists : Screen()
    @Serializable
    data object LocalAlbums : Screen()
    @Serializable
    data object LocalFolders : Screen()
    @Serializable
    data class LocalArtist(val name: String) : Screen()
    @Serializable
    data class LocalAlbum(val key: String) : Screen()
    @Serializable
    data class LocalFolder(val path: String) : Screen()
    @Serializable
    data object LocalPlaylists : Screen()
    @Serializable
    data class LocalPlaylist(val id: Long) : Screen()
    @Serializable
    data class LocalTagEditor(val uri: String) : Screen()
    @Serializable
    data object Message : Screen()
    @Serializable
    data class PrivateChat(val uid: Long) : Screen()
    @Serializable
    data object MyProfile : Screen()
    // 个人主页与关注/粉丝列表
    @Serializable
    data class Profile(val uid: Long) : Screen()
    @Serializable
    data class FollowList(val uid: Long, val mode: FollowListMode) : Screen()
}

// 回退栈栈帧：id 在整个应用生命周期内唯一，同一目标在栈里重复出现时仍可区分各自的页面现场
data class NavEntry(val id: Long, val screen: Screen)

// 应用级导航状态：主页/搜索/音乐库三个底栏 tab 各自持有一条独立回退栈，
// 切 tab 只切换「当前激活栈」，不清空其余 tab 已经积累的浏览历史
class MelodiaNavigationState(
    initialHomeStack: List<Screen> = listOf(Screen.Home),
    initialSearchStack: List<Screen> = listOf(Screen.Search),
    initialLibraryStack: List<Screen> = listOf(Screen.Library),
    initialActiveTab: Screen = Screen.Home,
    initialHomeTab: Int = 0,
    initialHomeIds: List<Long> = emptyList(),
    initialSearchIds: List<Long> = emptyList(),
    initialLibraryIds: List<Long> = emptyList(),
    initialNextEntryId: Long = 0L,
    initialLocalMusicStack: List<Screen> = listOf(Screen.LocalMusic),
    initialDownloadsStack: List<Screen> = listOf(Screen.Downloads),
    initialCloudStack: List<Screen> = listOf(Screen.Cloud),
    initialMyProfileStack: List<Screen> = listOf(Screen.MyProfile),
    initialLocalMusicIds: List<Long> = emptyList(),
    initialDownloadsIds: List<Long> = emptyList(),
    initialCloudIds: List<Long> = emptyList(),
    initialMyProfileIds: List<Long> = emptyList()
) {

    private var nextEntryId = initialNextEntryId

    private fun buildEntries(screens: List<Screen>, ids: List<Long>): List<NavEntry> =
        if (ids.size == screens.size) {
            screens.mapIndexed { index, screen -> NavEntry(ids[index], screen) }
        } else {
            screens.map { screen -> NavEntry(nextEntryId++, screen) }
        }

    private val homeStack = mutableStateListOf<NavEntry>()
        .apply { addAll(buildEntries(initialHomeStack, initialHomeIds)) }
    private val searchStack = mutableStateListOf<NavEntry>()
        .apply { addAll(buildEntries(initialSearchStack, initialSearchIds)) }
    private val libraryStack = mutableStateListOf<NavEntry>()
        .apply { addAll(buildEntries(initialLibraryStack, initialLibraryIds)) }
    private val localMusicStack = mutableStateListOf<NavEntry>()
        .apply { addAll(buildEntries(initialLocalMusicStack, initialLocalMusicIds)) }
    private val downloadsStack = mutableStateListOf<NavEntry>()
        .apply { addAll(buildEntries(initialDownloadsStack, initialDownloadsIds)) }
    private val cloudStack = mutableStateListOf<NavEntry>()
        .apply { addAll(buildEntries(initialCloudStack, initialCloudIds)) }
    private val myProfileStack = mutableStateListOf<NavEntry>()
        .apply { addAll(buildEntries(initialMyProfileStack, initialMyProfileIds)) }

    private val allStacks: List<List<NavEntry>>
        get() = listOf(homeStack, searchStack, libraryStack, localMusicStack, downloadsStack, cloudStack, myProfileStack)

    init {
        // 恢复的 id 可能大于计数器，避免之后分配出重复 id
        val maxId = allStacks.flatten().maxOfOrNull { it.id } ?: -1L
        if (nextEntryId <= maxId) nextEntryId = maxId + 1
    }

    var defaultRootTab by mutableStateOf<Screen>(Screen.Home)

    fun updateDefaultRootTab(screen: Screen) {
        defaultRootTab = screen
    }

    var activeTab by mutableStateOf(initialActiveTab)
        private set

    private fun isTopLevelTab(screen: Screen): Boolean = when (screen) {
        Screen.Home, Screen.Search, Screen.Library,
        Screen.LocalMusic, Screen.Downloads, Screen.Cloud, Screen.MyProfile -> true
        else -> false
    }

    private fun stackFor(tab: Screen): MutableList<NavEntry> = when (tab) {
        Screen.Search -> searchStack
        Screen.Library -> libraryStack
        Screen.LocalMusic -> localMusicStack
        Screen.Downloads -> downloadsStack
        Screen.Cloud -> cloudStack
        Screen.MyProfile -> myProfileStack
        else -> homeStack
    }

    private val activeStack: MutableList<NavEntry> get() = stackFor(activeTab)

    val currentEntry: NavEntry by derivedStateOf { activeStack.last() }

    val currentScreen: Screen by derivedStateOf { currentEntry.screen }

    // 所有栈里仍然存在的全部栈帧，用于判断哪些页面现场该释放
    val liveEntryIds: Set<Long> get() = allStacks.flatten().mapTo(HashSet()) { it.id }

    // 当前 tab 内栈深大于 1 时才有上一级可回退
    val canNavigateBack: Boolean get() = activeStack.size > 1

    // 是否还有可回退的页面层级（包括二级页面、非首选根 tab、主页非「全部」分类或展开的最新药丸）
    val canGoBackToHomeAll: Boolean
        get() = canNavigateBack || activeTab != defaultRootTab || (activeTab == Screen.Home && (homeTab != TAB_ALL || showMusicNewWorks))

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
        when {
            isTopLevelTab(screen) -> {
                if (activeTab == screen) {
                    resetStackToRoot(screen)
                    return
                }
                resetPlayerNavigation()
                activeTab = screen
            }
            else -> {
                if (activeStack.lastOrNull()?.screen == screen) return
                activeStack.add(NavEntry(nextEntryId++, screen))
            }
        }
    }

    // true 表示这次返回顺带弹出了全屏播放器；false 表示已在当前 tab 根、切回了首选根 tab（或已在首选根 tab 根，交还系统处理）
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
        if (activeTab != defaultRootTab) {
            resetStackToRoot(defaultRootTab)
            activeTab = defaultRootTab
            if (defaultRootTab == Screen.Home) {
                homeTab = TAB_ALL
                showMusicNewWorks = false
            }
            return false
        }
        if (defaultRootTab == Screen.Home) {
            if (showMusicNewWorks) {
                showMusicNewWorks = false
                return false
            }
            if (homeTab != TAB_ALL) {
                homeTab = TAB_ALL
                return false
            }
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
        // 已在「音乐」默认内容时再点「音乐」回到「全部」，收起「最新」
        homeTab = if (index == TAB_MUSIC && homeTab == TAB_MUSIC && !showMusicNewWorks) TAB_ALL else index
        // 点任意主药丸都回到该 tab 的默认内容，「最新」只能通过下面的入口单独选中
        showMusicNewWorks = false
    }

    fun updateShowMusicNewWorks(show: Boolean) {
        showMusicNewWorks = show
    }

    fun openRadio(id: Long) {
        navigateTo(Screen.Radio(id))
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

    fun openDownloads() {
        navigateTo(Screen.Downloads)
    }

    fun openLocalMusic() {
        navigateTo(Screen.LocalMusic)
    }

    fun openMessage() {
        navigateTo(Screen.Message)
    }

    fun openPrivateChat(uid: Long) {
        if (uid > 0L) navigateTo(Screen.PrivateChat(uid))
    }

    fun openMyProfile() {
        navigateTo(Screen.MyProfile)
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

    // 底栏一级入口跳转：重置目标 Tab 的回退栈至第一级根界面，并切换激活态
    fun openTab(screen: Screen) {
        searchAutoFocus = false
        resetPlayerNavigation()
        resetStackToRoot(screen)
        activeTab = screen
    }

    internal fun toSnapshot(): NavigationSnapshot = NavigationSnapshot(
        homeStack = homeStack.map { it.screen },
        searchStack = searchStack.map { it.screen },
        libraryStack = libraryStack.map { it.screen },
        activeTabIndex = tabIndex(activeTab),
        homeTab = homeTab,
        homeIds = homeStack.map { it.id },
        searchIds = searchStack.map { it.id },
        libraryIds = libraryStack.map { it.id },
        nextEntryId = nextEntryId,
        localMusicStack = localMusicStack.map { it.screen },
        downloadsStack = downloadsStack.map { it.screen },
        cloudStack = cloudStack.map { it.screen },
        myProfileStack = myProfileStack.map { it.screen },
        localMusicIds = localMusicStack.map { it.id },
        downloadsIds = downloadsStack.map { it.id },
        cloudIds = cloudStack.map { it.id },
        myProfileIds = myProfileStack.map { it.id },
        activeTabScreen = activeTab
    )
}

// 多条回退栈 + 当前 tab 的可序列化快照，用于跨进程重建保留浏览历史。
@Serializable
internal data class NavigationSnapshot(
    val homeStack: List<Screen> = listOf(Screen.Home),
    val searchStack: List<Screen> = listOf(Screen.Search),
    val libraryStack: List<Screen> = listOf(Screen.Library),
    val activeTabIndex: Int = 0,
    val homeTab: Int = 0,
    val homeIds: List<Long> = emptyList(),
    val searchIds: List<Long> = emptyList(),
    val libraryIds: List<Long> = emptyList(),
    val nextEntryId: Long = 0L,
    val localMusicStack: List<Screen> = listOf(Screen.LocalMusic),
    val downloadsStack: List<Screen> = listOf(Screen.Downloads),
    val cloudStack: List<Screen> = listOf(Screen.Cloud),
    val myProfileStack: List<Screen> = listOf(Screen.MyProfile),
    val localMusicIds: List<Long> = emptyList(),
    val downloadsIds: List<Long> = emptyList(),
    val cloudIds: List<Long> = emptyList(),
    val myProfileIds: List<Long> = emptyList(),
    val activeTabScreen: Screen? = null
)

private fun tabIndex(tab: Screen): Int = when (tab) {
    Screen.Search -> 1
    Screen.Library -> 2
    Screen.LocalMusic -> 3
    Screen.Downloads -> 4
    Screen.Cloud -> 5
    Screen.MyProfile -> 6
    else -> 0
}

private fun tabFromIndex(index: Int): Screen = when (index) {
    1 -> Screen.Search
    2 -> Screen.Library
    3 -> Screen.LocalMusic
    4 -> Screen.Downloads
    5 -> Screen.Cloud
    6 -> Screen.MyProfile
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
            val restoredTab = snapshot.activeTabScreen ?: tabFromIndex(snapshot.activeTabIndex)
            MelodiaNavigationState(
                initialHomeStack = snapshot.homeStack.ifEmpty { listOf(Screen.Home) },
                initialSearchStack = snapshot.searchStack.ifEmpty { listOf(Screen.Search) },
                initialLibraryStack = snapshot.libraryStack.ifEmpty { listOf(Screen.Library) },
                initialActiveTab = restoredTab,
                initialHomeTab = snapshot.homeTab,
                initialHomeIds = snapshot.homeIds,
                initialSearchIds = snapshot.searchIds,
                initialLibraryIds = snapshot.libraryIds,
                initialNextEntryId = snapshot.nextEntryId,
                initialLocalMusicStack = snapshot.localMusicStack.ifEmpty { listOf(Screen.LocalMusic) },
                initialDownloadsStack = snapshot.downloadsStack.ifEmpty { listOf(Screen.Downloads) },
                initialCloudStack = snapshot.cloudStack.ifEmpty { listOf(Screen.Cloud) },
                initialMyProfileStack = snapshot.myProfileStack.ifEmpty { listOf(Screen.MyProfile) },
                initialLocalMusicIds = snapshot.localMusicIds,
                initialDownloadsIds = snapshot.downloadsIds,
                initialCloudIds = snapshot.cloudIds,
                initialMyProfileIds = snapshot.myProfileIds
            )
        }
    }
)

@Composable
fun rememberMelodiaNavigationState(): MelodiaNavigationState =
    rememberSaveable(saver = MelodiaNavigationStateSaver) { MelodiaNavigationState() }
