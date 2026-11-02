package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.data.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.local.AddLocalPlaylistDialog
import com.lin0721.linmusic.desktop.ui.settings.SettingSwitch
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.library.ui.LibrarySortOrder
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import kotlinx.coroutines.launch

private const val PLAYLIST_NAME_MAX_LENGTH = 40
internal const val EMPTY_NAME_MESSAGE = "名字不能为空哦！"

private val SortMenuWidth = 232.dp
private val CreateMenuWidth = 176.dp
private val MenuGap = 6.dp

internal const val SEARCH_ANIMATION_MS = 220

internal fun sortLabel(order: LibrarySortOrder): String = when (order) {
    LibrarySortOrder.RECENTLY_PLAYED -> "最近播放"
    LibrarySortOrder.NAME -> "字母排序"
    LibrarySortOrder.CUSTOM -> "自定义"
}

internal fun viewModeIcon(mode: LibraryViewMode): ImageVector = when (mode) {
    LibraryViewMode.COMPACT_LIST -> Icons.Rounded.Menu
    LibraryViewMode.LIST -> Icons.Rounded.FormatListBulleted
    LibraryViewMode.SMALL_GRID -> Icons.Rounded.Apps
    LibraryViewMode.LARGE_GRID -> Icons.Rounded.GridView
}

private fun viewModeLabel(mode: LibraryViewMode): String = when (mode) {
    LibraryViewMode.COMPACT_LIST -> "紧凑列表"
    LibraryViewMode.LIST -> "列表"
    LibraryViewMode.SMALL_GRID -> "小网格"
    LibraryViewMode.LARGE_GRID -> "大网格"
}

// 音乐库内弹出的菜单统一样式：与触发按钮右缘对齐，保证落在音乐库范围内
@Composable
internal fun LibraryPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorWidth: Dp,
    menuWidth: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(menuWidth),
        offset = DpOffset(anchorWidth - menuWidth, MenuGap),
        shape = RoundedCornerShape(6.dp),
        containerColor = DesktopColors.PopupSurface,
        shadowElevation = 12.dp,
        content = content
    )
}

// 排序名与当前视图图标合成一个按钮，点击弹出排序与视图菜单；showSortLabel 为 false 时只剩图标
@Composable
internal fun LibrarySortViewMenu(
    viewModel: LibraryViewModel,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    showSortLabel: Boolean
) {
    val sortOrder by viewModel.sortOrder.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(Modifier.onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }) {
        DesktopTooltip("排序与视图", side = TooltipSide.Right) {
            Row(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).pointerHoverIcon(PointerIcon.Hand)
                    .clickable { expanded = true }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = showSortLabel,
                    enter = fadeIn(uiTween(SEARCH_ANIMATION_MS)) + expandHorizontally(uiTween(SEARCH_ANIMATION_MS)),
                    exit = fadeOut(uiTween(SEARCH_ANIMATION_MS)) + shrinkHorizontally(uiTween(SEARCH_ANIMATION_MS))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sortLabel(sortOrder), color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Icon(viewModeIcon(viewMode), null, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
            }
        }
        LibraryPopupMenu(expanded, { expanded = false }, anchorWidth, SortMenuWidth) {
            MenuHeader("排序方式")
            LibrarySortOrder.entries.forEach { order ->
                SortOption(order, selected = order == sortOrder) {
                    onSortOrderChange(order)
                    expanded = false
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = DesktopColors.SurfaceLight)
            MenuHeader("视图模式", trailing = viewModeLabel(viewMode))
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(DesktopColors.Pane).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                LibraryViewMode.entries.forEach { mode ->
                    ViewModeOption(mode, selected = mode == viewMode, Modifier.weight(1f)) { onViewModeChange(mode) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun MenuHeader(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = DesktopColors.TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, color = DesktopColors.TextGray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SortOption(order: LibrarySortOrder, selected: Boolean, onClick: () -> Unit) =
    MenuOption(sortLabel(order), selected, onClick)

@Composable
internal fun MenuOption(label: String, selected: Boolean, onClick: () -> Unit) {
    SimpleMenuItem(
        text = label,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp),
        selected = selected,
        trailing = if (selected) {
            { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
        } else {
            null
        }
    )
}

@Composable
private fun ViewModeOption(mode: LibraryViewMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    DesktopTooltip(viewModeLabel(mode), modifier = modifier) {
        Box(
            Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp))
                .background(if (selected) DesktopColors.SurfaceLight else Color.Transparent)
                .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                viewModeIcon(mode),
                viewModeLabel(mode),
                tint = if (selected) DesktopColors.Accent else DesktopColors.TextGray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// 创建入口：“创建歌单”建云端歌单（需登录），“添加歌单”建本地歌单；创建成功后跳转新歌单页
@Composable
internal fun LibraryCreateButton(viewModel: LibraryViewModel, extras: LibraryExtrasEntry, isLoggedIn: Boolean, pill: Boolean) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    var localDialogOpen by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(Modifier.onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }) {
        if (pill) {
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(DesktopColors.Surface)
                    .pointerHoverIcon(PointerIcon.Hand).clickable { menuOpen = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(18.dp))
                Text(
                    "创建",
                    color = DesktopColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        } else {
            LibraryIconButton(Icons.Rounded.Add, "创建", filled = true) { menuOpen = true }
        }
        LibraryPopupMenu(menuOpen, { menuOpen = false }, anchorWidth, CreateMenuWidth) {
            if (isLoggedIn) {
                SimpleMenuItem(
                    text = "创建歌单",
                    icon = Icons.Rounded.PlaylistAdd,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    onClick = {
                        menuOpen = false
                        dialogOpen = true
                    }
                )
            }
            SimpleMenuItem(
                text = "添加歌单",
                icon = Icons.Rounded.LibraryAdd,
                modifier = Modifier.padding(horizontal = 4.dp),
                onClick = {
                    menuOpen = false
                    localDialogOpen = true
                }
            )
        }
    }
    if (localDialogOpen) {
        AddLocalPlaylistDialog(
            onDismiss = { localDialogOpen = false },
            onCreate = { name, folder ->
                localDialogOpen = false
                scope.launch {
                    val id = extras.local.repository.createPlaylist(name, folder)
                    extras.onOpenLocal(id, name.ifBlank { folder?.name.orEmpty() })
                }
            }
        )
    }
    if (dialogOpen) {
        CreatePlaylistDialog(
            onDismiss = { dialogOpen = false },
            onCreate = { name, isPrivate ->
                dialogOpen = false
                viewModel.createPlaylist(name, isPrivate) { id, playlistName -> navigator.openPlaylist(id, playlistName) }
            },
            onEmptyName = { navigator.showMessage(EMPTY_NAME_MESSAGE) }
        )
    }
}

@Composable
internal fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, isPrivate: Boolean) -> Unit,
    onEmptyName: () -> Unit,
    // 仅创建后顺带加歌的入口不支持隐私选项
    showPrivacy: Boolean = true
) {
    val defaultPrivate = LocalDesktopNavigator.current.defaultPlaylistPrivate
    var name by remember { mutableStateOf("") }
    var isPrivate by remember(defaultPrivate) { mutableStateOf(defaultPrivate) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val submit = {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) onEmptyName() else onCreate(trimmed, showPrivacy && isPrivate)
    }
    DesktopDialog(
        title = "新建歌单",
        onDismiss = onDismiss,
        width = 360.dp,
        actions = {
            DialogButton("取消", onDismiss)
            DialogButton("创建", submit, primary = true)
        }
    ) {
        DialogTextField(
            value = name,
            onValueChange = { name = it.take(PLAYLIST_NAME_MAX_LENGTH) },
            placeholder = "歌单名称",
            focusRequester = focusRequester,
            onEnter = submit
        )
        if (showPrivacy) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("设为隐私歌单", color = DesktopColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("仅自己可见", color = DesktopColors.TextGray, fontSize = 11.sp)
                }
                SettingSwitch(isPrivate) { isPrivate = it }
            }
        }
    }
}

