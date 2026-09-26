package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// ────────────────────────────────────────────────────────────────────────────
// 歌曲"更多操作"弹层（下一首播放/喜欢/收藏到歌单/歌手/专辑）
// 注意：与 feature/player/ui/SongMoreOptionsSheet.kt 是完全不同的组件
// （那个含睡眠定时器/音质选择，服务于全屏播放页），不要混淆或合并。
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSongOptionsSheet(
    track: Track,
    isLiked: Boolean,
    isLoggedIn: Boolean,
    onDismiss: () -> Unit,
    onAddToPlayNext: (Track) -> Unit,
    onToggleLike: (Long, Boolean) -> Unit,
    onCollectClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onRequireLogin: () -> Unit,
    // 歌手详情页场景下点击「歌手」等于跳到当前页本身，传 false 隐藏该项
    showArtistOption: Boolean = true,
    // 仅当前歌单创建者可从歌单中移除歌曲，由调用方按登录态与创建者身份算好传入
    canRemoveFromPlaylist: Boolean = false,
    onRemoveFromPlaylist: (Long) -> Unit = {},
    onDownloadClick: ((Track) -> Unit)? = null,
    // 额外操作项
    extraOptions: @Composable ColumnScope.() -> Unit = {}
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val coverModel = remember(track.al.picUrl) {
                    val url = track.al.picUrl
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        "$url?param=150y150"
                    } else {
                        url
                    }
                }
                SubcomposeAsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(MaterialTheme.shapes.small)
                )
                Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
                    Text(
                        text = track.ar.joinToString(" • ") { it.name },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = MelodiaSpacing.sm)
            )

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OptionRow(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    text = "下一首播放",
                    onClick = {
                        onDismiss()
                        onAddToPlayNext(track)
                    }
                )

                val likeIcon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder
                val likeIconTint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val likeText = if (isLiked) "取消喜欢" else "喜欢"

                OptionRow(
                    icon = likeIcon,
                    text = likeText,
                    iconTint = likeIconTint,
                    onClick = {
                        onDismiss()
                        if (!isLoggedIn) {
                            onRequireLogin()
                        } else {
                            onToggleLike(track.id, !isLiked)
                        }
                    }
                )

                OptionRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    text = "收藏到歌单",
                    onClick = {
                        onDismiss()
                        if (!isLoggedIn) {
                            onRequireLogin()
                        } else {
                            onCollectClick(track.id)
                        }
                    }
                )

                if (onDownloadClick != null) {
                    OptionRow(
                        icon = Icons.Default.Download,
                        text = "下载",
                        onClick = {
                            onDismiss()
                            onDownloadClick(track)
                        }
                    )
                }

                if (showArtistOption) {
                    val artistsText = track.ar.joinToString(" • ") { it.name }
                    OptionRow(
                        icon = Icons.Default.Person,
                        text = "歌手: $artistsText",
                        onClick = {
                            onDismiss()
                            val artistId = track.ar.firstOrNull()?.id ?: 0L
                            if (artistId > 0) {
                                onArtistClick(artistId)
                            } else {
                                ToastManager.showToast("暂无歌手信息")
                            }
                        }
                    )
                }

                if (canRemoveFromPlaylist) {
                    OptionRow(
                        icon = Icons.Default.Delete,
                        text = "从歌单中删除",
                        onClick = {
                            onDismiss()
                            onRemoveFromPlaylist(track.id)
                        }
                    )
                }

                OptionRow(
                    icon = Icons.Default.Album,
                    text = "专辑: ${track.al.name}",
                    onClick = {
                        onDismiss()
                        if (track.al.id > 0) {
                            onAlbumClick(track.al.id)
                        } else {
                            ToastManager.showToast("暂无专辑信息")
                        }
                    }
                )

                extraOptions()
            }
        }
    }
}
