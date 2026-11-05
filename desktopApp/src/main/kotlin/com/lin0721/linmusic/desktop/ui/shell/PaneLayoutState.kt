package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.data.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// 橡皮筋起步时偏移与鼠标位移之比，越拉越小并逼近上限
private const val RUBBER_BAND_RATIO = 0.5f
private val LIBRARY_OVERSHOOT_LIMIT = 40.dp

internal fun rubberBand(distance: Dp, limit: Dp): Dp {
    if (distance <= 0.dp || limit <= 0.dp) return 0.dp
    return limit * (1f - 1f / (distance / limit * RUBBER_BAND_RATIO + 1f))
}

interface PaneLayoutPersistence {
    fun saveLibraryMode(mode: LibraryMode)
    fun saveLibraryViewMode(mode: LibraryViewMode)
    fun saveLibraryWidth(widthDp: Float)
    fun saveDockWidth(widthDp: Float)
}

private class PreferencesPaneLayoutPersistence(
    private val preferences: DesktopPreferences,
    private val scope: CoroutineScope
) : PaneLayoutPersistence {
    override fun saveLibraryMode(mode: LibraryMode) {
        scope.launch { preferences.saveLibraryMode(mode) }
    }

    override fun saveLibraryViewMode(mode: LibraryViewMode) {
        scope.launch { preferences.saveLibraryViewMode(mode) }
    }

    override fun saveLibraryWidth(widthDp: Float) {
        scope.launch { preferences.saveLibraryWidth(widthDp) }
    }

    override fun saveDockWidth(widthDp: Float) {
        scope.launch { preferences.saveNowPlayingWidth(widthDp) }
    }
}

// 一次排版下两侧栏的可用宽度
@Immutable
data class PaneMeasure(
    val libraryMax: Dp,
    val libraryDefaultWidth: Dp,
    val dockMax: Dp,
    val dockOpenWidth: Dp
)

// 音乐库与正在播放栏的形态、记忆宽度与拖动；拖动期间只改内存值，松手才落盘
@Stable
class PaneLayoutState(
    initialMode: LibraryMode,
    initialViewMode: LibraryViewMode,
    initialLibraryWidth: Dp,
    initialDockWidth: Dp,
    private val persistence: PaneLayoutPersistence
) {
    var libraryMode by mutableStateOf(initialMode)
        private set
    var libraryViewMode by mutableStateOf(initialViewMode)
        private set
    var libraryWidthPref by mutableStateOf(initialLibraryWidth)
        private set
    var dockWidthPref by mutableStateOf(initialDockWidth)
        private set
    var resizing by mutableStateOf(false)
        private set
    // 拖动越界时叠加在形态宽度上的橡皮筋偏移，松手归零
    var libraryStretch by mutableStateOf(0.dp)
        private set
    // 音乐库宽度走弹簧：拖动开始置位，松手后保留到回弹结束，按钮切换形态时恢复常规过渡
    var librarySpring by mutableStateOf(false)
        private set

    private var libraryDragStart = 0.dp
    private var libraryDragExpanded = false
    private var dockDragStart = 0.dp

    // 形态切换涉及右侧栏的联动，统一由 DesktopAppState.setLibraryMode 调用
    internal fun applyLibraryMode(mode: LibraryMode) {
        if (mode == libraryMode) return
        librarySpring = false
        libraryMode = mode
        persistence.saveLibraryMode(mode)
    }

    fun updateViewMode(mode: LibraryViewMode) {
        if (mode == libraryViewMode) return
        libraryViewMode = mode
        persistence.saveLibraryViewMode(mode)
    }

    // 侧栏最宽不超过固定上限，且尽量给中间内容区留出 CenterMinWidth；
    // 两侧互相让位时，音乐库按正在播放栏的记忆宽度算，正在播放栏按音乐库的实际占用算
    fun measure(available: Dp, hasTrack: Boolean, dockOpen: Boolean): PaneMeasure {
        val dockStaticOccupied = when {
            !hasTrack -> 0.dp
            dockOpen -> dockWidthPref.coerceIn(DesktopDimens.NowPlayingMinWidth, DesktopDimens.NowPlayingMaxWidth) +
                DesktopDimens.PaneGap
            else -> DesktopDimens.NowPlayingHandleWidth + DesktopDimens.PaneGap
        }
        val libraryMax = maxOf(
            DesktopDimens.LibraryMinWidth,
            minOf(DesktopDimens.LibraryMaxWidth, available - dockStaticOccupied - DesktopDimens.PaneGap - DesktopDimens.CenterMinWidth)
        )
        val libraryDefaultWidth = libraryWidthPref.coerceIn(DesktopDimens.LibraryMinWidth, libraryMax)
        val libraryOccupied = if (libraryMode == LibraryMode.RAIL) DesktopDimens.LibraryRailWidth else libraryDefaultWidth
        val dockMax = maxOf(
            DesktopDimens.NowPlayingMinWidth,
            minOf(
                DesktopDimens.NowPlayingMaxWidth,
                available - libraryOccupied - DesktopDimens.PaneGap * 2 - DesktopDimens.CenterMinWidth
            )
        )
        val dockOpenWidth = dockWidthPref.coerceIn(DesktopDimens.NowPlayingMinWidth, dockMax)
        return PaneMeasure(libraryMax, libraryDefaultWidth, dockMax, dockOpenWidth)
    }

    fun startLibraryDrag(currentWidth: Dp) {
        resizing = true
        librarySpring = true
        libraryDragStart = currentWidth
        libraryDragExpanded = libraryMode != LibraryMode.RAIL
    }

    // 宽度全程由弹簧追随目标：窄条态往外拉带阻尼拉伸，越过展开阈值切到默认态；默认态拖窄过收起阈值吸附回窄条，
    // 两个阈值不同，在临界处来回拖不会反复切换。默认态越过上下限时带阻尼越界，拖宽不会变成展开态
    fun dragLibrary(delta: Dp, libraryMax: Dp, setMode: (LibraryMode) -> Unit) {
        val proposed = libraryDragStart + delta
        val threshold = if (libraryDragExpanded) DesktopDimens.LibraryCollapseWidth else DesktopDimens.PaneExpandWidth
        libraryDragExpanded = proposed >= threshold
        if (libraryDragExpanded) {
            if (libraryMode != LibraryMode.DEFAULT) setMode(LibraryMode.DEFAULT)
            val settled = proposed.coerceIn(DesktopDimens.LibraryMinWidth, libraryMax)
            libraryWidthPref = settled
            libraryStretch = when {
                proposed > settled -> rubberBand(proposed - settled, LIBRARY_OVERSHOOT_LIMIT)
                proposed < settled -> -rubberBand(settled - proposed, LIBRARY_OVERSHOOT_LIMIT)
                else -> 0.dp
            }
        } else {
            if (libraryMode != LibraryMode.RAIL) setMode(LibraryMode.RAIL)
            libraryStretch = rubberBand(
                (proposed - DesktopDimens.LibraryRailWidth).coerceAtLeast(0.dp),
                DesktopDimens.LibraryMinWidth - DesktopDimens.LibraryRailWidth
            )
        }
        librarySpring = true
    }

    fun endLibraryDrag() {
        resizing = false
        libraryStretch = 0.dp
        persistence.saveLibraryWidth(libraryWidthPref.value)
    }

    fun settleLibrarySpring() {
        if (!resizing) librarySpring = false
    }

    fun startDockDrag(currentWidth: Dp) {
        resizing = true
        dockDragStart = currentWidth
    }

    // 右侧栏向右拖窄过阈值直接收起
    fun dragDock(delta: Dp, dockMax: Dp, dockOpen: Boolean, closeDock: () -> Unit) {
        val proposed = dockDragStart - delta
        if (proposed < DesktopDimens.PaneSnapWidth) {
            resizing = false
            if (dockOpen) closeDock()
        } else {
            resizing = true
            dockWidthPref = proposed.coerceIn(DesktopDimens.NowPlayingMinWidth, dockMax)
        }
    }

    fun endDockDrag() {
        resizing = false
        persistence.saveDockWidth(dockWidthPref.value)
    }
}

// 启动时同步读到上次的形态与宽度，避免先按默认值排版再动画过去
@Composable
fun rememberPaneLayoutState(preferences: DesktopPreferences, scope: CoroutineScope): PaneLayoutState =
    remember(preferences) {
        runBlocking {
            PaneLayoutState(
                initialMode = preferences.libraryMode.first(),
                initialViewMode = preferences.libraryViewMode.first(),
                initialLibraryWidth = (preferences.libraryWidth.first() ?: DesktopDimens.SidebarWidth.value).dp,
                initialDockWidth = (preferences.nowPlayingWidth.first() ?: DesktopDimens.NowPlayingWidth.value).dp,
                persistence = PreferencesPaneLayoutPersistence(preferences, scope)
            )
        }
    }
