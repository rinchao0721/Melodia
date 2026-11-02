package com.lin0721.linmusic.desktop.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.MenuScope
import com.lin0721.linmusic.desktop.ui.components.onDoubleClick
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val MoreButtonSize = 32.dp

// 搜索结果的通用行：封面 + 标题副标题 + 右键与悬停“更多”菜单。
// 单击或双击二选一由调用方决定，歌曲类用双击，歌单专辑等容器类用单击
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SearchResultRow(
    coverUrl: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    circleCover: Boolean = false,
    coverSize: Dp = 48.dp,
    highlighted: Boolean = false,
    leading: (@Composable (hovered: Boolean) -> Unit)? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    menu: (@Composable MenuScope.(dismiss: () -> Unit) -> Unit)? = null
) {
    val density = LocalDensity.current
    var hovered by remember { mutableStateOf(false) }
    var rowHeightPx by remember { mutableStateOf(0) }
    // 右键菜单相对行底部的偏移，null 为关闭
    var contextMenuOffset by remember { mutableStateOf<DpOffset?>(null) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(4.dp)

    Box(modifier.fillMaxWidth().onSizeChanged { rowHeightPx = it.height }) {
        Row(
            Modifier.fillMaxWidth().clip(rowShape)
                .background(if (hovered || contextMenuOffset != null || moreMenuOpen) DesktopColors.Hover else Color.Transparent)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (menu != null && event.buttons.isSecondaryPressed) {
                        val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                        contextMenuOffset = with(density) {
                            DpOffset(position.x.toDp(), (position.y - rowHeightPx).toDp())
                        }
                    }
                }
                .then(if (onDoubleClick != null) Modifier.onDoubleClick(onDoubleClick) else Modifier)
                .then(
                    if (onClick != null) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterEnd) { leading(hovered) }
                Box(Modifier.width(16.dp))
            }
            Cover(coverUrl, coverSize, shape = if (circleCover) CircleShape else RoundedCornerShape(4.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    title,
                    color = if (highlighted) DesktopColors.AccentText else DesktopColors.TextPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailingText != null) {
                Text(trailingText, color = DesktopColors.TextGray, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (menu != null) {
                Box {
                    HoverReveal(revealed = hovered || moreMenuOpen) {
                        IconButton(
                            onClick = { moreMenuOpen = true },
                            enabled = hovered || moreMenuOpen,
                            modifier = Modifier.size(MoreButtonSize)
                        ) {
                            Icon(Icons.Rounded.MoreHoriz, "更多有关《$title》的选项", tint = DesktopColors.TextGray)
                        }
                    }
                    DesktopMenu(expanded = moreMenuOpen, onDismiss = { moreMenuOpen = false }) {
                        menu { moreMenuOpen = false }
                    }
                }
            }
        }
        if (menu != null) {
            DesktopMenu(
                expanded = contextMenuOffset != null,
                offset = contextMenuOffset ?: DpOffset.Zero,
                onDismiss = { contextMenuOffset = null }
            ) {
                menu { contextMenuOffset = null }
            }
        }
    }
}

// 给已有的行组件补右键菜单，行组件自身的悬停与双击不受影响
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun RowContextMenu(
    modifier: Modifier = Modifier,
    menu: @Composable MenuScope.(dismiss: () -> Unit) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var rowHeightPx by remember { mutableStateOf(0) }
    var contextMenuOffset by remember { mutableStateOf<DpOffset?>(null) }
    Box(
        modifier.fillMaxWidth()
            .onSizeChanged { rowHeightPx = it.height }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    contextMenuOffset = with(density) {
                        DpOffset(position.x.toDp(), (position.y - rowHeightPx).toDp())
                    }
                }
            }
    ) {
        content()
        DesktopMenu(
            expanded = contextMenuOffset != null,
            offset = contextMenuOffset ?: DpOffset.Zero,
            onDismiss = { contextMenuOffset = null }
        ) {
            menu { contextMenuOffset = null }
        }
    }
}
