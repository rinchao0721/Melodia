package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.extractBaseColorFromUrl
import com.lin0721.linmusic.core.model.PlaylistDetail

// ────────────────────────────────────────────────────────────────────────────
// 全出血 Hero（LazyColumn Item 1）
// 封面区包含 overlayHeight 的内边距，使封面在透明 overlay 下方自然显示
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun PlaylistHeaderItem(
    playlist: PlaylistDetail,
    coverSize: Dp,
    coverAlpha: Float,
    progress: Float,
    statusBarHeight: Dp,
    dominantColor: Color,
    onColorCalculated: (Color) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleToggle: () -> Unit,
    isShuffleActive: Boolean,
    isCurrentlyPlayingThis: Boolean,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onMoreClick: () -> Unit,
    // "我喜欢的音乐"是系统歌单，收藏/更多这两项对它没有意义，改成分享+下载
    isLikedSongsPlaylistView: Boolean = false,
    // 自建歌单不能收藏自己，隐藏收藏按钮；分享/下载对自建、订阅他人的歌单都通用
    isOwnedPlaylist: Boolean = false,
    onShareClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    selectedHistoryDate: String = "今天",
    // 播放按钮在根坐标系下的实时位置（用于顶层叠加的按钮跟手滑动、到位后锁停）
    onPlayButtonPositioned: (Float) -> Unit = {}
) {
    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val displayDateStr = remember(selectedHistoryDate) {
        if (selectedHistoryDate == "今天") {
            todayStr
        } else {
            val parts = selectedHistoryDate.split("-")
            if (parts.size == 3) {
                "${parts[0]}年${parts[1]}月${parts[2]}日"
            } else {
                selectedHistoryDate
            }
        }
    }
    // 只有标题随折叠渐隐（40%-80%），跟顶栏小标题的淡入
    // 区间（60%-100%）有重叠形成交错过渡；创建者/简介/歌曲数/操作按钮不做特殊处理，
    // 当作普通内容随手指滚动划走即可
    val titleAlpha = 1f - ((progress - 0.4f) / 0.4f).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 使用从封面提取的主色调渐变到背景黑
            .background(Brush.verticalGradient(listOf(dominantColor, MaterialTheme.colorScheme.background)))
    ) {
        // 封面：与操作区的返回键水平对齐
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarHeight + MelodiaSpacing.md, bottom = MelodiaSpacing.md),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            val coverRequest = remember(playlist.coverImgUrl) {
                ImageRequest.Builder(context)
                    .data("${playlist.coverImgUrl}?param=400y400")
                    .allowHardware(false)
                    .crossfade(true)
                    .build()
            }
            // 取色跟封面显示解码完全脱钩，单独发一次固定尺寸的请求
            LaunchedEffect(playlist.coverImgUrl) {
                if (playlist.coverImgUrl.isNotEmpty()) {
                    onColorCalculated(extractBaseColorFromUrl(context, playlist.coverImgUrl))
                }
            }
            SubcomposeAsyncImage(
                model              = coverRequest,
                contentDescription = playlist.name,
                contentScale       = ContentScale.Crop,
                loading            = { CoverPlaceholder() },
                error              = { CoverPlaceholder() },
                modifier           = Modifier
                    .size(coverSize)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(RadiusCompact), clip = false)
                    .clip(RoundedCornerShape(RadiusCompact))
                    .then(if (coverAlpha < 1f) Modifier.alpha(coverAlpha) else Modifier)
            )
        }

        // 歌单信息与操作行：普通内容，随列表正常滚动划走，不做特殊处理
        Column(modifier = Modifier.padding(horizontal = MelodiaSpacing.md)) {
                    Text(playlist.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(titleAlpha))
                    Spacer(Modifier.height(6.dp))
                    if (playlist.id == -1L) {
                        Text(displayDateStr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    } else if (playlist.id == -2L) {
                        Text("网易云个人听歌记录统计", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (playlist.creator != null) {
                                AsyncImage(
                                    model = "${playlist.creator.avatarUrl}?param=50y50",
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(playlist.creator.nickname, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("为你打造", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                        if (!playlist.description.isNullOrBlank()) {
                            Spacer(Modifier.height(MelodiaSpacing.xs))
                            Text(playlist.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(MelodiaSpacing.xs))
                    val playCountText = if (playlist.playCount > 0) " • 播放 ${formatPlayCount(playlist.playCount)} 次" else ""
                    Text("${playlist.tracks.size} 首歌曲$playCountText", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }

                if (playlist.id != -1L && playlist.id != -2L) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = MelodiaSpacing.sm, end = MelodiaSpacing.md)
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            // 收藏对系统歌单和自建歌单都没有意义：前者本来就是自己的红心集合，
                            // 后者不能收藏自己创建的东西
                            if (!isLikedSongsPlaylistView && !isOwnedPlaylist) {
                                MelodiaIconButton(onClick = onSubscribeClick) {
                                    Icon(
                                        imageVector = if (isSubscribed) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = if (isSubscribed) "已收藏" else "收藏",
                                        tint = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            MelodiaIconButton(onClick = onCommentsClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Comment,
                                    contentDescription = "评论",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            MelodiaIconButton(onClick = onShareClick) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "分享",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // 订阅他人的歌单不在顶部放下载入口，收进"更多"里；系统歌单和自建歌单顶部保留
                            if (isLikedSongsPlaylistView || isOwnedPlaylist) {
                                MelodiaIconButton(onClick = onDownloadClick) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "下载",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            // "更多"固定放最右侧
                            if (!isLikedSongsPlaylistView) {
                                MelodiaIconButton(onClick = onMoreClick) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "更多",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            MelodiaIconButton(
                                onClick = onShuffleToggle,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    "随机播放",
                                    tint = if (isShuffleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            // 真实按钮永远透明，只用来占位和上报自身位置；
                            // 可见的播放按钮是顶层叠加的那一个，见 PlaylistTopBar 的 PlaylistDockedPlayButton
                            FloatingActionButton(
                                onClick        = onPlayAll,
                                containerColor = MaterialTheme.colorScheme.primary,
                                shape          = CircleShape,
                                modifier       = Modifier
                                    .size(56.dp)
                                    .onGloballyPositioned { coordinates ->
                                        onPlayButtonPositioned(coordinates.positionInRoot().y)
                                    }
                                    .alpha(0f)
                            ) {
                                Icon(
                                    imageVector = if (isCurrentlyPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isCurrentlyPlayingThis) "暂停" else "播放",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
    }
}
