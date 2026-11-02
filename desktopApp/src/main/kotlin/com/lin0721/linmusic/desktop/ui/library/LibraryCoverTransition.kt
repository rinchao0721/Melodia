package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.IntOffset
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.home.LocalPaneResizing
import com.lin0721.linmusic.desktop.ui.shell.PANE_ANIMATION_MS
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import kotlinx.coroutines.flow.filterNotNull

// 封面过渡与音乐库宽度动画同长，两者同时到位
internal const val COVER_TRANSITION_MS = PANE_ANIMATION_MS

@OptIn(ExperimentalSharedTransitionApi::class)
private val CoverBoundsTransform = BoundsTransform { _, _ ->
    tween(COVER_TRANSITION_MS, easing = FastOutSlowInEasing)
}

// 形态切换与视图切换各有一层 AnimatedContent，封面在所有层都处于目标可见时才算可见
@OptIn(ExperimentalSharedTransitionApi::class)
internal class LibraryCoverTransition(
    val sharedScope: SharedTransitionScope,
    private val visibilityScopes: List<AnimatedVisibilityScope>
) {
    val isTargetVisible: Boolean
        get() = visibilityScopes.all { it.transition.targetState == EnterExitState.Visible }

    fun nested(scope: AnimatedVisibilityScope) = LibraryCoverTransition(sharedScope, visibilityScopes + scope)
}

internal val LocalLibraryCoverTransition = staticCompositionLocalOf<LibraryCoverTransition?> { null }

// 同一条目的封面在窄条、列表、网格之间切换时从旧位置与尺寸移动缩放到新位置
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.libraryCoverElement(itemId: String): Modifier {
    val transition = LocalLibraryCoverTransition.current ?: return this
    val visible = transition.isTargetVisible
    return with(transition.sharedScope) {
        this@libraryCoverElement.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState("libraryCover:$itemId"),
            visible = visible,
            boundsTransform = CoverBoundsTransform
        )
    }
}

// 网格列数随宽度切换时（如全展开态开合右侧栏），格子位移交给 animateItem，尺寸由这里从旧排版过渡到新排版；
// 拖动调宽时逐帧跟手，不做过渡
@OptIn(ExperimentalSharedTransitionApi::class)
private val GridReflowBoundsTransform = BoundsTransform { _, _ ->
    tween(COVER_TRANSITION_MS, easing = FastOutSlowInEasing)
}

internal val GridReflowPlacementSpec = tween<IntOffset>(COVER_TRANSITION_MS, easing = FastOutSlowInEasing)

internal val LocalLibraryGridLookahead = staticCompositionLocalOf<LookaheadScope?> { null }

@Composable
internal fun isGridReflowEnabled(): Boolean = !LocalPaneResizing.current && LocalUiMotion.current.enabled

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.libraryGridReflow(): Modifier {
    if (!isGridReflowEnabled()) return this
    val scope = LocalLibraryGridLookahead.current ?: return this
    return animateBounds(lookaheadScope = scope, boundsTransform = GridReflowBoundsTransform)
}

// 各形态、各视图的列表共用滚动锚点：新列表从同一条目开始显示，封面过渡只需横向移动和缩放
internal class LibraryScrollAnchor {
    var firstVisibleKey: Any? = null
}

internal val LocalLibraryScrollAnchor = staticCompositionLocalOf<LibraryScrollAnchor?> { null }

private fun LibraryScrollAnchor?.indexIn(items: List<LibraryItem>): Int {
    val key = this?.firstVisibleKey ?: return 0
    return items.indexOfFirst { it.id == key }.coerceAtLeast(0)
}

@Composable
private fun TrackScrollAnchor(anchor: LibraryScrollAnchor?, firstVisibleKey: () -> Any?) {
    if (anchor == null) return
    LaunchedEffect(anchor) {
        snapshotFlow(firstVisibleKey).filterNotNull().collect { anchor.firstVisibleKey = it }
    }
}

@Composable
internal fun rememberAnchoredListState(items: List<LibraryItem>): LazyListState {
    val anchor = LocalLibraryScrollAnchor.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = anchor.indexIn(items))
    TrackScrollAnchor(anchor) { state.layoutInfo.visibleItemsInfo.firstOrNull()?.key }
    return state
}

@Composable
internal fun rememberAnchoredGridState(items: List<LibraryItem>): LazyGridState {
    val anchor = LocalLibraryScrollAnchor.current
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = anchor.indexIn(items))
    TrackScrollAnchor(anchor) { state.layoutInfo.visibleItemsInfo.firstOrNull()?.key }
    return state
}
