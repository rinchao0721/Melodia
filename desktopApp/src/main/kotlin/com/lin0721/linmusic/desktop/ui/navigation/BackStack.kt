package com.lin0721.linmusic.desktop.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.derivedStateOf
import com.lin0721.linmusic.feature.profile.ui.FollowListMode

sealed interface DesktopRoute {
    data object Home : DesktopRoute
    data class Playlist(val id: Long, val title: String, val isAlbum: Boolean = false) : DesktopRoute
    data class LocalPlaylist(val id: Long, val title: String) : DesktopRoute
    data object Cloud : DesktopRoute
    data object Search : DesktopRoute
    data object Browse : DesktopRoute
    data class PlaylistCategory(val name: String) : DesktopRoute
    data class Artist(val id: Long, val name: String) : DesktopRoute
    data class Style(val id: Long, val name: String) : DesktopRoute
    data class Radio(val id: Long) : DesktopRoute
    data object PodcastSubscribed : DesktopRoute
    data object PodcastToplist : DesktopRoute
    data class PodcastCategory(val id: Long, val name: String) : DesktopRoute
    data object RecentPlay : DesktopRoute
    data class Profile(val uid: Long) : DesktopRoute
    data class FollowList(val uid: Long, val mode: FollowListMode) : DesktopRoute
    data object Settings : DesktopRoute
    data class PluginPage(val pluginId: String, val pageId: String) : DesktopRoute
}

// 历史栈帧：id 唯一，同一路由在历史里重复出现时仍可区分各自的页面现场
data class DesktopEntry(val id: Long, val route: DesktopRoute)

// 一步历史：所在页面栈帧 + 当时的界面快照；只改界面不换页的步骤沿用同一栈帧，页面现场不受影响
data class HistoryStep<S>(val entry: DesktopEntry, val ui: S)

// 历史最多保留的步数，超出丢弃最早的
private const val MAX_HISTORY_STEPS = 100

// 浏览器式历史：新动作清空前进栈，前进/后退只在两栈间搬移
@Stable
class BackStack<S>(start: DesktopRoute, initialUi: S) {
    private var nextEntryId = 0L
    private val history = mutableStateListOf(HistoryStep(DesktopEntry(nextEntryId++, start), initialUi))
    private val forwardStack = mutableStateListOf<HistoryStep<S>>()

    val currentStep: HistoryStep<S> by derivedStateOf { history.last() }
    val currentEntry: DesktopEntry by derivedStateOf { currentStep.entry }
    val current: DesktopRoute by derivedStateOf { currentEntry.route }
    val canGoBack: Boolean by derivedStateOf { history.size > 1 }
    val canGoForward: Boolean by derivedStateOf { forwardStack.isNotEmpty() }

    // 历史与前进栈里仍然存在的全部栈帧
    val liveEntryIds: Set<Long> get() = (history + forwardStack).mapTo(HashSet()) { it.entry.id }

    // 记一步：route 为空或与当前页相同则留在本页；页面与快照都没变时不记
    fun push(route: DesktopRoute?, ui: S) {
        val top = history.last()
        val target = route?.takeIf { it != top.entry.route }
        if (target == null && ui == top.ui) return
        val entry = if (target == null) top.entry else DesktopEntry(nextEntryId++, target)
        history.add(HistoryStep(entry, ui))
        forwardStack.clear()
        while (history.size > MAX_HISTORY_STEPS) history.removeAt(0)
    }

    // 未记步的界面变化并入当前步骤；并入后与上一步完全相同则合并，避免出现点了没反应的后退
    fun replaceUi(ui: S) {
        val top = history.last()
        if (top.ui == ui) return
        val updated = top.copy(ui = ui)
        if (history.size > 1 && history[history.lastIndex - 1] == updated) {
            history.removeAt(history.lastIndex)
        } else {
            history[history.lastIndex] = updated
        }
    }

    fun back(): HistoryStep<S>? {
        if (history.size <= 1) return null
        forwardStack.add(history.removeAt(history.lastIndex))
        return history.last()
    }

    fun forward(): HistoryStep<S>? {
        if (forwardStack.isEmpty()) return null
        history.add(forwardStack.removeAt(forwardStack.lastIndex))
        return history.last()
    }

    // 退到上一个页面，跳过本页上只改界面的步骤；同一栈帧的步骤在历史里总是连续的
    fun backToPreviousPage(): HistoryStep<S>? {
        val entryId = history.last().entry.id
        if (history.first().entry.id == entryId) return null
        while (history.last().entry.id == entryId) forwardStack.add(history.removeAt(history.lastIndex))
        return history.last()
    }
}
