package com.lin0721.linmusic.feature.library.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.interaction.pressScale
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.LibraryVioletGradient
import com.lin0721.linmusic.core.ui.theme.LibraryBlueGreenGradient
import com.lin0721.linmusic.core.ui.theme.DownloadedGreen
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.PillRadius

// ────────────────────────────────────────────────────────────────────────────
// 分类筛选胶囊行：默认（未筛选）展示全部胶囊；选中某个胶囊后仅保留该胶囊，
// 并在最前面追加一个"×"胶囊用于取消筛选、恢复展示全部
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun LibraryFilterPillsRow(
    selectedFilter: LibraryFilter?,
    playlistCount: Int,
    albumCount: Int,
    artistCount: Int,
    onSelect: (LibraryFilter) -> Unit,
    onClear: () -> Unit,
    selectedPlaylistOwnerFilter: LibraryPlaylistOwnerFilter? = null,
    onSelectPlaylistOwnerFilter: (LibraryPlaylistOwnerFilter) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        LibraryFilter.PLAYLIST to "歌单${if (playlistCount > 0) " $playlistCount" else ""}",
        LibraryFilter.ALBUM to "专辑${if (albumCount > 0) " $albumCount" else ""}",
        LibraryFilter.ARTIST to "歌手${if (artistCount > 0) " $artistCount" else ""}",
        LibraryFilter.MV to "MV"
    )
    val visibleFilters = if (selectedFilter == null) filters else filters.filter { it.first == selectedFilter }
    val ownerFilters = listOf(
        LibraryPlaylistOwnerFilter.MINE to "我创建的",
        LibraryPlaylistOwnerFilter.OTHERS to "其他"
    )

    // 增删胶囊时的位移动画：略带回弹但不过冲
    val placementSpec = spring<androidx.compose.ui.unit.IntOffset>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val fadeInSpec = spring<Float>(stiffness = Spring.StiffnessMedium)
    val fadeOutSpec = spring<Float>(stiffness = Spring.StiffnessMedium)

    LazyRow(
        modifier = modifier.padding(top = MelodiaSpacing.sm, bottom = 10.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedFilter != null) {
            item(key = "clear_filter") {
                Box(
                    modifier = Modifier
                        .animateItem(fadeInSpec, placementSpec, fadeOutSpec)
                        .pressable(MelodiaPress.Pill) { onClear() }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除筛选",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        items(visibleFilters, key = { it.first }) { (filter, label) ->
            val isSelected = filter == selectedFilter
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                animationSpec = tween(220),
                label = "pill_bg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.LightGray,
                animationSpec = tween(220),
                label = "pill_content"
            )
            Box(
                modifier = Modifier
                    .animateItem(fadeInSpec, placementSpec, fadeOutSpec)
                    .height(36.dp)
                    .pressable(MelodiaPress.Pill) { onSelect(filter) }
                    .clip(RoundedCornerShape(PillRadius))
                    .background(bgColor)
                    .animateContentSize(spring(stiffness = Spring.StiffnessMedium))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 14.sp
                )
            }
        }
        // 歌单二级筛选：选中"歌单"时才在其后追加扩展胶囊
        if (selectedFilter == LibraryFilter.PLAYLIST) {
            items(ownerFilters, key = { "owner_${it.first}" }) { (ownerFilter, label) ->
                val isSelected = ownerFilter == selectedPlaylistOwnerFilter
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                    animationSpec = tween(220),
                    label = "owner_pill_bg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.LightGray,
                    animationSpec = tween(220),
                    label = "owner_pill_content"
                )
                Box(
                    modifier = Modifier
                        .animateItem(fadeInSpec, placementSpec, fadeOutSpec)
                        .height(36.dp)
                        .pressable(MelodiaPress.Pill) { onSelectPlaylistOwnerFilter(ownerFilter) }
                        .clip(RoundedCornerShape(PillRadius))
                        .background(bgColor)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = contentColor,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 未登录占位页
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun NotLoggedInView(onLoginClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MelodiaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(MelodiaSpacing.lg))

        Text(
            text = "开启您的专属乐库",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "登录后即可同步您的歌单、收藏的歌手与专辑。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = MelodiaSpacing.md)
        )

        Spacer(modifier = Modifier.height(MelodiaSpacing.xl))

        MelodiaButton(
            onClick = onLoginClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)
        ) {
            Text(
                text = "立即登录",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// 混合列表的单行条目
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryItemRow(
    item: LibraryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧封面图（针对歌手做圆图，歌单/专辑方图，已点赞歌曲做定制心形渐变图）
        if (item.isLikedSongs) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(RadiusCompact))
                    .background(
                        Brush.linearGradient(
                            colors = LibraryVioletGradient
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else if (item.id == "-2") {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(RadiusCompact))
                    .background(
                        Brush.linearGradient(
                            colors = LibraryBlueGreenGradient
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            val shape = if (item.type == LibraryItemType.ARTIST) CircleShape else RoundedCornerShape(RadiusCompact)

            SubcomposeAsyncImage(
                model = "${item.coverUrl}?param=150y150",
                contentDescription = item.title,
                modifier = Modifier
                    .size(60.dp)
                    .clip(shape),
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() }
            )
        }

        Spacer(modifier = Modifier.width(MelodiaSpacing.md))

        // 右侧文字内容
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(MelodiaSpacing.xs))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 如果是置顶条目，显示绿色的置顶图标
                if (item.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "已置顶",
                        tint = DownloadedGreen,
                        modifier = Modifier
                            .size(13.dp)
                            .padding(end = MelodiaSpacing.xs)
                    )
                }

                Text(
                    text = when (item.type) {
                        LibraryItemType.PLAYLIST -> {
                            if (item.trackCount > 0) "${item.subtitle} • ${item.trackCount}首" else item.subtitle
                        }
                        else -> item.subtitle
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridItem(
    item: LibraryItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val shape = if (item.type == LibraryItemType.ARTIST) CircleShape else RoundedCornerShape(RadiusCompact)
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .pressScale(MelodiaPress.Card, interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (item.isLikedSongs) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(RadiusCompact))
                    .background(
                        Brush.linearGradient(
                            colors = LibraryVioletGradient
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else if (item.id == "-2") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(RadiusCompact))
                    .background(
                        Brush.linearGradient(
                            colors = LibraryBlueGreenGradient
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = "${item.coverUrl}?param=300y300",
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape),
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已置顶",
                    tint = DownloadedGreen,
                    modifier = Modifier
                        .size(11.dp)
                        .padding(end = MelodiaSpacing.xxs)
                )
            }
            Text(
                text = item.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 可拖拽排序的歌单行
@Composable
fun DraggablePlaylistRow(
    item: LibraryItem,
    isDragging: Boolean,
    dragOffsetY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    enabled: Boolean = true
) {
    val liftSpec = remember { spring<Dp>(stiffness = Spring.StiffnessMediumLow) }
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = liftSpec,
        label = "drag_playlist_elev"
    )
    val isLifted = isDragging || dragOffsetY != 0f
    val bgColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
        label = "playlist_row_bg"
    )

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLifted) Modifier
                    .zIndex(1f)
                    .graphicsLayer { translationY = dragOffsetY }
                    .shadow(elevation, RoundedCornerShape(8.dp))
                else Modifier
            )
            .background(bgColor)
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = "${item.coverUrl}?param=150y150",
            contentDescription = item.title,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(RadiusCompact)),
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() }
        )

        Spacer(modifier = Modifier.width(MelodiaSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
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
                            .size(13.dp)
                            .padding(end = MelodiaSpacing.xs)
                    )
                }
                Text(
                    text = if (item.trackCount > 0) "${item.subtitle} • ${item.trackCount}首" else item.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "拖拽调整顺序",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(44.dp)
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { currentOnDragStart() },
                                onDrag = { change, offset ->
                                    change.consume()
                                    currentOnDrag(offset.y)
                                },
                                onDragEnd = { currentOnDragEnd() },
                                onDragCancel = { currentOnDragEnd() }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(10.dp)
        )
    }
}

