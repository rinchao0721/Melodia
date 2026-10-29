package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.data.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.shell.fixedWidthAtStart
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.feature.library.ui.LibraryFilter
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibrarySortOrder
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel

// 音乐库容器：宽度随形态平滑过渡，各形态内容按自己的稳定宽度排版并被裁剪；
// 形态切换时其余内容交叉淡化，封面在新旧位置间移动缩放
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryPane(
    mode: LibraryMode,
    width: Dp,
    defaultWidth: Dp,
    expandedWidth: Dp,
    viewModel: LibraryViewModel,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onItemClick: (LibraryItem) -> Unit,
    onModeChange: (LibraryMode) -> Unit,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onFilterToggle: (LibraryFilter) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    extras: LibraryExtrasEntry
) {
    val motion = LocalUiMotion.current
    val scrollAnchor = remember { LibraryScrollAnchor() }
    Box(
        Modifier.width(width).fillMaxHeight()
            .clip(RoundedCornerShape(DesktopDimens.PaneRadius))
            .background(DesktopColors.ShellFill)
    ) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            val sharedScope = this
            AnimatedContent(
                targetState = mode,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    motion.transform(
                        fadeIn(tween(COVER_TRANSITION_MS)) togetherWith fadeOut(tween(COVER_TRANSITION_MS)) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    )
                },
                contentAlignment = Alignment.TopStart,
                label = "libraryMode"
            ) { current ->
                val contentWidth = when (current) {
                    LibraryMode.RAIL -> DesktopDimens.LibraryRailWidth
                    LibraryMode.DEFAULT -> defaultWidth
                    LibraryMode.EXPANDED -> expandedWidth
                }
                CompositionLocalProvider(
                    LocalLibraryCoverTransition provides LibraryCoverTransition(sharedScope, listOf(this)),
                    LocalLibraryScrollAnchor provides scrollAnchor
                ) {
                    Box(Modifier.fixedWidthAtStart(contentWidth)) {
                        when (current) {
                            LibraryMode.RAIL -> LibraryRail(viewModel, isLoggedIn, onItemClick, onModeChange, extras)
                            LibraryMode.DEFAULT -> LibrarySidebar(
                                viewModel, isLoggedIn, onLoginClick, onItemClick, onModeChange, viewMode, onViewModeChange,
                                onFilterToggle, onSortOrderChange, extras
                            )
                            LibraryMode.EXPANDED -> LibraryExpanded(
                                viewModel, isLoggedIn, onLoginClick, onItemClick, onModeChange, viewMode, onViewModeChange,
                                onFilterToggle, onSortOrderChange, extras
                            )
                        }
                    }
                }
            }
        }
    }
}
