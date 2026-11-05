package com.lin0721.linmusic.desktop.ui.track

import androidx.compose.material.icons.rounded.Extension
import com.lin0721.linmusic.core.plugin.LocalPluginCommands
import com.lin0721.linmusic.core.plugin.PluginMenuLocation
import com.lin0721.linmusic.core.plugin.rememberPluginCommands
import kotlinx.serialization.json.JsonObject
import com.lin0721.linmusic.core.plugin.pluginSongArgs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.format.formatDuration
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.theme.densityScaled
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.MenuScope
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.components.SubMenuItem
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.components.songInfoText
import com.lin0721.linmusic.desktop.ui.components.detectLongPressReorder
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

// 双击播放，右键或悬停出现的更多按钮打开操作菜单
// 离线不可播放的歌曲行置灰程度
private const val DISABLED_ROW_ALPHA = 0.38f

// 与歌单页表头的“添加日期”列同宽
internal val ADDED_COLUMN_WIDTH = 120.dp

internal val DangerColor: Color @Composable @ReadOnlyComposable get() = DesktopColors.Error

// 多选态下的行勾选状态与切换回调
@Immutable
class TrackRowSelection(
    val checked: Boolean,
    val onToggle: () -> Unit
)

// 行内长按拖动排序：偏移与手势回调由列表页的 QueueReorderState 驱动
@Immutable
class TrackReorder(
    val dragging: Boolean,
    val offsetY: Float,
    val onDragStart: () -> Unit,
    val onDrag: (Float) -> Unit,
    val onDragEnd: () -> Unit
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TrackRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    actions: TrackActions? = null,
    enabled: Boolean = true,
    // 非 null 时在专辑后多一列“添加日期”（歌单页专用）
    addedAtText: String? = null,
    // 非 null 时整行可长按拖动
    reorder: TrackReorder? = null,
    // 无 actions 时行尾的自定义操作（推荐区的添加按钮）
    trailingAction: (@Composable () -> Unit)? = null,
    // 非 null 表示多选态：首列显示复选框，点击整行切换勾选，悬停操作与右键菜单停用
    selection: TrackRowSelection? = null,
    // 非 null 时用它替换默认的歌曲菜单（本地歌曲）
    menuContent: (@Composable MenuScope.(dismiss: () -> Unit) -> Unit)? = null,
    // 混排进歌单的外部平台歌曲，歌名后显示来源角标
    sourcePlatform: MusicPlatform? = null
) {
    val activeActions = actions.takeIf { selection == null }
    val activeMenu = menuContent.takeIf { selection == null }
    val hasMenu = activeActions != null || activeMenu != null
    val navigator = LocalDesktopNavigator.current
    val density = LocalDensity.current
    var hovered by remember { mutableStateOf(false) }
    var rowHeightPx by remember { mutableStateOf(0) }
    // 右键菜单相对行底部的偏移，null 为关闭
    var contextMenuOffset by remember { mutableStateOf<DpOffset?>(null) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    val isLiked = actions != null && track.id in actions.likedSongIds
    val currentDragStart by rememberUpdatedState(reorder?.onDragStart)
    val currentDrag by rememberUpdatedState(reorder?.onDrag)
    val currentDragEnd by rememberUpdatedState(reorder?.onDragEnd)
    val dragging = reorder?.dragging == true
    val lifted = reorder != null && (dragging || reorder.offsetY != 0f)
    val rowShape = RoundedCornerShape(4.dp)

    Box(
        modifier.fillMaxWidth()
            .then(
                if (lifted && reorder != null) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = reorder.offsetY }
                        .then(if (dragging) Modifier.themedShadow(8.dp, rowShape) else Modifier)
                } else {
                    Modifier
                }
            )
            .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA)
            .onSizeChanged { rowHeightPx = it.height }
    ) {
        Row(
            Modifier.fillMaxWidth().clip(rowShape)
                .background(
                    when {
                        dragging -> DesktopColors.Surface
                        hovered || contextMenuOffset != null || moreMenuOpen -> DesktopColors.Hover
                        else -> Color.Transparent
                    }
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (hasMenu && event.buttons.isSecondaryPressed) {
                        val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                        contextMenuOffset = with(density) {
                            DpOffset(position.x.toDp(), (position.y - rowHeightPx).toDp())
                        }
                    } else if (selection == null && enabled && event.awtEventOrNull?.clickCount == 2) {
                        onPlay()
                    }
                }
                .then(
                    if (selection != null) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = selection.onToggle)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (reorder != null) {
                        Modifier.pointerInput(Unit) {
                            detectLongPressReorder(
                                onDragStart = { currentDragStart?.invoke() },
                                onDrag = { delta -> currentDrag?.invoke(delta) },
                                onDragEnd = { currentDragEnd?.invoke() }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 12.dp, vertical = densityScaled(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selection != null) {
                Checkbox(
                    checked = selection.checked,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = DesktopColors.Accent,
                        uncheckedColor = DesktopColors.TextGray,
                        checkmarkColor = DesktopColors.TextPrimary
                    ),
                    modifier = Modifier.width(32.dp)
                )
            } else if (enabled && hovered && !dragging) {
                Box(
                    Modifier.width(32.dp).height(32.dp).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onPlay),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        "播放${track.name}",
                        tint = DesktopColors.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Text(
                    "${index + 1}",
                    color = if (isCurrent) DesktopColors.AccentText else DesktopColors.TextGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp)
                )
            }
            Cover(track.al.picUrl, 40.dp, modifier = Modifier.padding(start = 16.dp))
            Column(Modifier.weight(0.45f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        track.name,
                        color = if (isCurrent) DesktopColors.AccentText else DesktopColors.TextPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (sourcePlatform != null) PlatformBadge(sourcePlatform, Modifier.padding(start = 6.dp))
                }
                Text(
                    artistLinks(track.ar, navigator.openArtist),
                    color = DesktopColors.TextGray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                albumLink(track, navigator.openAlbum),
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.35f).padding(horizontal = 12.dp)
            )
            if (addedAtText != null) {
                Text(
                    addedAtText,
                    color = DesktopColors.TextGray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(ADDED_COLUMN_WIDTH)
                )
            }
            if (activeActions != null) {
                // 已喜欢的红心常亮，其余仅悬停时出现
                val showLike = hovered || isLiked
                IconButton(
                    onClick = { activeActions.onToggleLike(track) },
                    enabled = showLike,
                    modifier = Modifier.size(32.dp)
                ) {
                    if (showLike) {
                        Icon(
                            if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            if (isLiked) "取消喜欢" else "喜欢",
                            tint = if (isLiked) DesktopColors.Accent else DesktopColors.TextGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else if (activeMenu != null) {
                Spacer(Modifier.width(32.dp))
            }
            Text(
                formatDuration(track.dt),
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp)
            )
            if (!hasMenu) trailingAction?.invoke()
            if (hasMenu) {
                Box {
                    IconButton(
                        onClick = { moreMenuOpen = true },
                        enabled = hovered || moreMenuOpen,
                        modifier = Modifier.padding(start = 4.dp).size(32.dp)
                    ) {
                        if (hovered || moreMenuOpen) {
                            Icon(Icons.Rounded.MoreHoriz, "更多有关《${track.name}》的选项", tint = DesktopColors.TextGray)
                        }
                    }
                    RowMenu(moreMenuOpen, DpOffset.Zero, track, activeActions, activeMenu, onPlay) { moreMenuOpen = false }
                }
            }
        }
        if (hasMenu) {
            RowMenu(contextMenuOffset != null, contextMenuOffset ?: DpOffset.Zero, track, activeActions, activeMenu, onPlay) {
                contextMenuOffset = null
            }
        }
    }
}

@Composable
private fun RowMenu(
    expanded: Boolean,
    offset: DpOffset,
    track: Track,
    actions: TrackActions?,
    menuContent: (@Composable MenuScope.(dismiss: () -> Unit) -> Unit)?,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    when {
        menuContent != null -> DesktopMenu(expanded = expanded, onDismiss = onDismiss, offset = offset) { menuContent(onDismiss) }
        actions != null -> TrackMenu(expanded, offset, track, actions, onPlay, onDismiss)
    }
}

private fun copySongLink(songId: Long): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(
        StringSelection("https://music.163.com/song?id=$songId"), null
    )
}.isSuccess

@Composable
internal fun TrackMenu(
    expanded: Boolean,
    offset: DpOffset,
    track: Track,
    actions: TrackActions,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val pluginCommands = LocalPluginCommands.current
    val isLiked = track.id in actions.likedSongIds
    val artists = track.ar.filter { it.id > 0 }
    DesktopMenu(expanded = expanded, onDismiss = onDismiss, offset = offset) {
        MenuItem("播放", icon = Icons.Rounded.PlayArrow, onClick = {
            onDismiss()
            onPlay()
        })
        MenuItem("下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
            onDismiss()
            actions.onPlayNext(track)
        })
        if (navigator.isLoggedIn) {
            SubMenuItem("收藏到歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onOpen = { actions.onPrepareCollect(track) }) {
                CollectSubmenuContent(track, actions, onDismiss)
            }
        } else {
            MenuItem("收藏到歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = {
                onDismiss()
                navigator.showMessage("请先登录账号")
            })
        }
        MenuItem(
            if (isLiked) "取消喜欢" else "喜欢",
            icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            onClick = {
                onDismiss()
                actions.onToggleLike(track)
            }
        )
        MenuDivider()
        when {
            artists.size > 1 -> SubMenuItem("查看歌手", icon = Icons.Rounded.Person) {
                artists.forEach { artist ->
                    SimpleMenuItem(artist.name, onClick = {
                        onDismiss()
                        navigator.openArtist(artist.id, artist.name)
                    })
                }
            }
            artists.size == 1 -> MenuItem("查看歌手", icon = Icons.Rounded.Person, onClick = {
                onDismiss()
                navigator.openArtist(artists[0].id, artists[0].name)
            })
        }
        if (track.al.id > 0) {
            MenuItem("查看专辑", icon = Icons.Rounded.Album, onClick = {
                onDismiss()
                navigator.openAlbum(track.al.id, track.al.name)
            })
        }
        // 外部平台歌曲没有网易云 id，不能下载与分享网易云链接
        val isExternal = track.id < 0L
        if (!isExternal) {
            MenuItem("下载", icon = Icons.Rounded.Download, onClick = {
                onDismiss()
                actions.onDownload(track)
            })
        }
        actions.onReduceRecommend?.let { reduce ->
            MenuItem("减少推荐", icon = Icons.Rounded.HeartBroken, onClick = {
                onDismiss()
                reduce(track)
            })
        }
        if (!isExternal) {
            MenuItem("复制歌曲链接", icon = Icons.Rounded.Link, onClick = {
                onDismiss()
                val ok = copySongLink(track.id)
                navigator.showMessage(if (ok) "已复制链接" else "复制失败")
            })
        }
        MenuItem("复制歌曲信息", icon = Icons.Rounded.ContentCopy, onClick = {
            onDismiss()
            val ok = copyTextToClipboard(songInfoText(track.name, track.ar.joinToString(" / ") { it.name }))
            navigator.showMessage(if (ok) "已复制歌曲信息" else "复制失败")
        })
        actions.onRemove?.let { remove ->
            MenuDivider()
            MenuItem("从歌单中删除", icon = Icons.Rounded.Delete, danger = true, onClick = {
                onDismiss()
                remove(track)
            })
        }
        actions.extraMenuItems?.invoke(this, track, onDismiss)
        val pluginItems = rememberPluginCommands(PluginMenuLocation.SONG)
        if (pluginItems.isNotEmpty()) MenuDivider()
        pluginItems.forEach { item ->
            MenuItem(item.title, icon = Icons.Rounded.Extension, onClick = {
                onDismiss()
                pluginCommands?.run(item, track.toPluginArgs())
            })
        }
    }
}

private fun Track.toPluginArgs(): JsonObject =
    pluginSongArgs(id, name, ar.joinToString(" / ") { it.name }, al.name, al.picUrl)

private val LinkStyles: TextLinkStyles @Composable @ReadOnlyComposable get() = TextLinkStyles(hoveredStyle = SpanStyle(color = DesktopColors.TextPrimary, textDecoration = TextDecoration.Underline))

// 多位歌手各自可点，无 id 的歌手只展示名字
@Composable
fun PlatformBadge(platform: MusicPlatform, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(6.dp).background(Color(platform.brandColorArgb), CircleShape))
        Text(platform.badgeName, color = DesktopColors.TextGray, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun artistLinks(artists: List<Artist>, onClick: (Long, String) -> Unit): AnnotatedString = buildAnnotatedString {
    artists.forEachIndexed { i, artist ->
        if (i > 0) append(" / ")
        if (artist.id > 0) {
            withLink(LinkAnnotation.Clickable("artist_${artist.id}", LinkStyles) { onClick(artist.id, artist.name) }) {
                append(artist.name)
            }
        } else {
            append(artist.name)
        }
    }
}

@Composable
private fun albumLink(track: Track, onClick: (Long, String) -> Unit): AnnotatedString = buildAnnotatedString {
    if (track.al.id > 0) {
        withLink(LinkAnnotation.Clickable("album_${track.al.id}", LinkStyles) { onClick(track.al.id, track.al.name) }) {
            append(track.al.name)
        }
    } else {
        append(track.al.name)
    }
}
