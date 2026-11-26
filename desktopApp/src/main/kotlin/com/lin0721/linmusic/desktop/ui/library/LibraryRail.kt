package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.SidebarExpand
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.local.LocalLibraryDesktopViewModel
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel

private val RailCoverSize = 48.dp

// 收起态：音乐库图标、快捷入口、创建按钮与竖排封面
@Composable
fun LibraryRail(
    viewModel: LibraryViewModel,
    isLoggedIn: Boolean,
    onItemClick: (LibraryItem) -> Unit,
    onModeChange: (LibraryMode) -> Unit,
    extras: LibraryExtrasEntry,
    shortcutsState: LibraryShortcutsState,
    modifier: Modifier = Modifier,
    localViewModel: LocalLibraryDesktopViewModel? = null
) {
    val navigator = LocalDesktopNavigator.current
    val hasCloudAccount = navigator.hasCloudAccount || isLoggedIn
    val displayItems = if (localViewModel != null) {
        rememberLocalLibraryDisplayItems(localViewModel, extras)
    } else {
        rememberLibraryDisplayItems(viewModel, extras, isLoggedIn, hasCloudAccount)
    }
    val handlers = rememberLibraryItemHandlers(viewModel, extras, localViewModel)
    val openItem: (LibraryItem) -> Unit = { item ->
        if (localViewModel != null) {
            openLocalModeItem(item, localViewModel, navigator) {}
        } else {
            extras.open(item, onItemClick)
        }
    }

    CompositionLocalProvider(LocalPureLocalMode provides (localViewModel != null)) {
        Column(modifier.fillMaxSize().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val headerContent: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LibraryTitleToggle(
                        baseIcon = MelodiaIcons.Library,
                        hoverIcon = MelodiaIcons.SidebarExpand,
                        hint = "展开音乐库",
                        showTitle = false
                    ) {
                        if (localViewModel != null) localViewModel.rescan() else viewModel.loadLibraryData()
                        onModeChange(LibraryMode.DEFAULT)
                    }
                    if (localViewModel != null || shortcutsState.visibleShortcuts.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                    } else {
                        LibraryShortcutRailColumn(shortcutsState)
                    }
                    LibraryCreateButton(viewModel, extras, isLoggedIn, pill = false, localViewModel = localViewModel)
                }
            }
            if (localViewModel == null) {
                LibraryShortcutsContextBox(shortcutsState) { headerContent() }
            } else {
                headerContent()
            }
            val items = displayItems ?: return@Column
            val listState = rememberAnchoredListState(items)
            val scrolled by remember { derivedStateOf { listState.isScrolled } }
            Box(Modifier.fillMaxSize()) {
                HoverScrollbarBox(listState) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(items, key = { it.id }) { item ->
                            LibraryContextMenuBox(item = item, viewModel = viewModel, handlers = handlers) {
                                DesktopTooltip(tooltipContent = { LibraryItemTooltipCard(item) }, side = TooltipSide.Right) {
                                    val shape = coverShape(item)
                                    Box(
                                        Modifier.clip(shape).pointerHoverIcon(PointerIcon.Hand).clickable { openItem(item) }
                                    ) {
                                        Cover(item.coverUrl, RailCoverSize, modifier = Modifier.libraryCoverElement(item.id), shape = shape)
                                    }
                                }
                            }
                        }
                    }
                }
                ListTopShadow(scrolled)
            }
        }
    }
}
