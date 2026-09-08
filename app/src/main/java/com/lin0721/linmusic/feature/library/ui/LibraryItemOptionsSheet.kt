package com.lin0721.linmusic.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DownloadedGreen
import com.lin0721.linmusic.core.ui.theme.LibraryVioletGradient
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact

// 菜单单项数据模型
private data class LibraryOptionsMenuItem(
    val icon: ImageVector,
    val title: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

// 音乐库条目快速编辑底部弹层
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryItemOptionsSheet(
    item: LibraryItem,
    onDismiss: () -> Unit,
    onTogglePin: (String) -> Unit,
    onEditOrder: () -> Unit,
    onShare: (LibraryItem) -> Unit,
    onDownload: (LibraryItem) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onUnsubscribePlaylist: (Long) -> Unit,
    onUnsubscribeAlbum: (Long) -> Unit,
    onUnsubscribeArtist: (Long) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            // 1. 顶部条目头部（封面 + 标题 + 副标题）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.isLikedSongs) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(RadiusCompact))
                            .background(Brush.linearGradient(LibraryVioletGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    val shape = if (item.type == LibraryItemType.ARTIST) CircleShape else RoundedCornerShape(RadiusCompact)
                    SubcomposeAsyncImage(
                        model = if (item.coverUrl.isNotEmpty()) "${item.coverUrl}?param=150y150" else null,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        loading = { CoverPlaceholder() },
                        error = { CoverPlaceholder() },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(shape)
                    )
                }

                Spacer(modifier = Modifier.width(MelodiaSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "已置顶",
                                tint = DownloadedGreen,
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(end = MelodiaSpacing.xs)
                            )
                        }
                        Text(
                            text = item.subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = MelodiaSpacing.sm)
            )

            // 2. 根据条目类型构建菜单项列表
            val menuItems = buildList {
                val pinTitle = if (item.isPinned) "取消置顶" else "置顶"

                when (item.type) {
                    LibraryItemType.PLAYLIST -> {
                        if (item.isLikedSongs) {
                            // "我喜欢的音乐"：不提供置顶与删除，支持分享与下载
                            add(
                                LibraryOptionsMenuItem(
                                    icon = Icons.Default.Share,
                                    title = "分享歌单",
                                    onClick = {
                                        onDismiss()
                                        onShare(item)
                                    }
                                )
                            )
                            add(
                                LibraryOptionsMenuItem(
                                    icon = Icons.Default.Download,
                                    title = "下载歌单",
                                    onClick = {
                                        onDismiss()
                                        onDownload(item)
                                    }
                                )
                            )
                        } else {
                            // 普通歌单：置顶、修改位置、分享、下载、删除/取消收藏
                            add(
                                LibraryOptionsMenuItem(
                                    icon = Icons.Default.PushPin,
                                    title = pinTitle,
                                    onClick = {
                                        onDismiss()
                                        onTogglePin(item.id)
                                    }
                                )
                            )
                            add(
                                LibraryOptionsMenuItem(
                                    icon = Icons.Rounded.SwapVert,
                                    title = "修改位置",
                                    onClick = {
                                        onDismiss()
                                        onEditOrder()
                                    }
                                )
                            )
                            add(
                                LibraryOptionsMenuItem(
                                    icon = Icons.Default.Share,
                                    title = "分享歌单",
                                    onClick = {
                                        onDismiss()
                                        onShare(item)
                                    }
                                )
                            )
                            add(
                                LibraryOptionsMenuItem(
                                    icon = Icons.Default.Download,
                                    title = "下载歌单",
                                    onClick = {
                                        onDismiss()
                                        onDownload(item)
                                    }
                                )
                            )
                            if (item.isOwnedByMe) {
                                add(
                                    LibraryOptionsMenuItem(
                                        icon = Icons.Default.Delete,
                                        title = "删除歌单",
                                        isDestructive = true,
                                        onClick = {
                                            onDismiss()
                                            item.id.toLongOrNull()?.let { onDeletePlaylist(it) }
                                        }
                                    )
                                )
                            } else {
                                add(
                                    LibraryOptionsMenuItem(
                                        icon = Icons.Default.Favorite,
                                        title = "取消收藏歌单",
                                        isDestructive = true,
                                        onClick = {
                                            onDismiss()
                                            item.id.toLongOrNull()?.let { onUnsubscribePlaylist(it) }
                                        }
                                    )
                                )
                            }
                        }
                    }

                    LibraryItemType.ALBUM -> {
                        // 专辑：置顶、取消收藏、分享、下载
                        add(
                            LibraryOptionsMenuItem(
                                icon = Icons.Default.PushPin,
                                title = pinTitle,
                                onClick = {
                                    onDismiss()
                                    onTogglePin(item.id)
                                }
                            )
                        )
                        add(
                            LibraryOptionsMenuItem(
                                icon = Icons.Default.Favorite,
                                title = "取消收藏专辑",
                                isDestructive = true,
                                onClick = {
                                    onDismiss()
                                    item.id.toLongOrNull()?.let { onUnsubscribeAlbum(it) }
                                }
                            )
                        )
                        add(
                            LibraryOptionsMenuItem(
                                icon = Icons.Default.Share,
                                title = "分享专辑",
                                onClick = {
                                    onDismiss()
                                    onShare(item)
                                }
                            )
                        )
                        add(
                            LibraryOptionsMenuItem(
                                icon = Icons.Default.Download,
                                title = "下载专辑",
                                onClick = {
                                    onDismiss()
                                    onDownload(item)
                                }
                            )
                        )
                    }

                    LibraryItemType.ARTIST -> {
                        // 歌手：置顶、取消关注、分享
                        add(
                            LibraryOptionsMenuItem(
                                icon = Icons.Default.PushPin,
                                title = pinTitle,
                                onClick = {
                                    onDismiss()
                                    onTogglePin(item.id)
                                }
                            )
                        )
                        add(
                            LibraryOptionsMenuItem(
                                icon = Icons.Default.Person,
                                title = "取消关注歌手",
                                isDestructive = true,
                                onClick = {
                                    onDismiss()
                                    item.id.toLongOrNull()?.let { onUnsubscribeArtist(it) }
                                }
                            )
                        )
                        add(
                            LibraryOptionsMenuItem(
                                icon = Icons.Default.Share,
                                title = "分享歌手",
                                onClick = {
                                    onDismiss()
                                    onShare(item)
                                }
                            )
                        )
                    }

                    LibraryItemType.MV -> Unit
                }
            }

            // 3. 渲染菜单列表
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(menuItems, key = { it.title }) { menuItem ->
                    val iconColor = if (menuItem.isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val titleColor = if (menuItem.isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = menuItem.onClick)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = menuItem.icon,
                            contentDescription = menuItem.title,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                        Text(
                            text = menuItem.title,
                            color = titleColor,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
