package com.lin0721.linmusic.feature.localmusic.ui.components

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.FallbackBase
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.extractBaseColorFromUrl
import com.lin0721.linmusic.core.ui.theme.smoothVerticalGradient
import com.lin0721.linmusic.core.ui.theme.themedCoverBase
import com.lin0721.linmusic.core.ui.theme.uiTween
import com.lin0721.linmusic.feature.localmusic.data.LocalCoverArtCache
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.queueSongId
import com.lin0721.linmusic.feature.player.ui.formatTime
import org.koin.compose.koinInject

@Composable
fun rememberLocalCoverUrl(sourceUri: Uri?): String? {
    val coverCache: LocalCoverArtCache = koinInject()
    val coverUrl by produceState<String?>(initialValue = null, sourceUri) {
        value = sourceUri?.let { coverCache.coverUriFor(it)?.toString() }
    }
    return coverUrl
}

@Composable
fun LocalCover(
    sourceUri: Uri?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(RadiusCompact),
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote
) {
    val coverUrl = rememberLocalCoverUrl(sourceUri)
    val placeholder = @Composable {
        Box(
            modifier = Modifier.fillMaxSize().background(MelodiaColors.SurfaceLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.4f)
            )
        }
    }
    Box(modifier = modifier.size(size).clip(shape)) {
        if (coverUrl == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun LocalTrackRow(
    track: LocalTrack,
    subtitle: String,
    playingMediaId: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: (coverUrl: String?) -> Unit,
    index: Int? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false
) {
    val coverUrl = rememberLocalCoverUrl(track.uri)
    SongRow(
        data = SongRowData(
            id = track.songId ?: track.mediaStoreId,
            title = track.title,
            artist = subtitle,
            coverUrl = coverUrl,
            durationText = track.durationMs.takeIf { it > 0 }?.let(::formatTime)
        ),
        isActive = playingMediaId == track.queueSongId.toString(),
        isPlaying = isPlaying,
        index = index,
        showDownloadBadge = false,
        onClick = onClick,
        trailingSlot = {
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (selected) "已选中" else "未选中",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = MelodiaSpacing.sm, end = MelodiaSpacing.md).size(20.dp)
                )
            } else {
                MelodiaIconButton(onClick = { onMoreClick(coverUrl) }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
fun LocalPlayShuffleButtons(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)) {
        MelodiaButton(
            onClick = onPlay,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(MelodiaSpacing.xs))
            Text("播放", fontWeight = FontWeight.Bold)
        }
        MelodiaButton(
            onClick = onShuffle,
            colors = ButtonDefaults.buttonColors(
                containerColor = MelodiaColors.Overlay.copy(alpha = 0.14f),
                contentColor = MelodiaColors.TextPrimary
            ),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(MelodiaSpacing.xs))
            Text("随机", fontWeight = FontWeight.Bold)
        }
    }
}

// 底色取封面主色
@Composable
fun LocalHeroHeader(
    coverSourceUri: Uri?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(coverUrl: String?) -> Unit
) {
    val context = LocalContext.current
    val coverUrl = rememberLocalCoverUrl(coverSourceUri)
    var baseColor by remember { mutableStateOf(FallbackBase) }
    LaunchedEffect(coverUrl) {
        baseColor = coverUrl?.let { extractBaseColorFromUrl(context, it) } ?: FallbackBase
    }
    val animatedBase by animateColorAsState(baseColor, uiTween(500), label = "local_hero_base")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.smoothVerticalGradient(from = themedCoverBase(animatedBase, 0.25f), to = MelodiaColors.PageFill))
            .statusBarsPadding()
            .padding(bottom = MelodiaSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MelodiaIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MelodiaColors.TextPrimary)
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Column(modifier = Modifier.padding(horizontal = MelodiaSpacing.md)) {
            content(coverUrl)
        }
    }
}

@Composable
fun LocalSectionHeader(title: String, onMoreClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MelodiaSpacing.md, end = MelodiaSpacing.xs, top = MelodiaSpacing.lg, bottom = MelodiaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (onMoreClick != null) {
            MelodiaTextButton(onClick = onMoreClick) {
                Text("全部", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun LocalAlbumCard(
    coverSourceUri: Uri?,
    title: String,
    subtitle: String,
    width: Dp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(width)
            .pressable(MelodiaPress.Card, onClick = onClick)
    ) {
        LocalCover(sourceUri = coverSourceUri, size = width, shape = RoundedCornerShape(8.dp), fallbackIcon = Icons.Rounded.Album)
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
