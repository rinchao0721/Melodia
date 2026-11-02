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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel

private val RailCoverSize = 48.dp

// 收起态：只保留图标、创建按钮与竖排封面
@Composable
fun LibraryRail(
    viewModel: LibraryViewModel,
    isLoggedIn: Boolean,
    onItemClick: (LibraryItem) -> Unit,
    onModeChange: (LibraryMode) -> Unit,
    extras: LibraryExtrasEntry,
    modifier: Modifier = Modifier
) {
    val displayItems = rememberLibraryDisplayItems(viewModel, extras, isLoggedIn)
    val handlers = rememberLibraryItemHandlers(viewModel, extras)

    Column(modifier.fillMaxSize().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LibraryTitleToggle(
            baseIcon = Icons.Rounded.LibraryMusic,
            hoverIcon = Icons.Rounded.KeyboardDoubleArrowRight,
            hint = "展开音乐库",
            showTitle = false
        ) {
            viewModel.loadLibraryData()
            onModeChange(LibraryMode.DEFAULT)
        }
        Spacer(Modifier.height(12.dp))
        LibraryCreateButton(viewModel, extras, isLoggedIn, pill = false)
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
                                    Modifier.clip(shape).pointerHoverIcon(PointerIcon.Hand).clickable { extras.open(item, onItemClick) }
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
