package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistInfo
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SimilarArtistCard(
    artist: ArtistInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .pressable(MelodiaPress.Card, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = "${artist.avatarUrl}?param=150y150",
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .border(1.dp, MelodiaColors.Overlay.copy(alpha = 0.1f), CircleShape)
        )

        Spacer(Modifier.height(MelodiaSpacing.sm))

        Text(
            text = artist.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = MelodiaSpacing.xs)
        )
    }
}

@Composable
fun ArtistAlbumRow(
    album: ArtistAlbum,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MelodiaColors.PageFill)
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = "${album.picUrl}?param=150y150",
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() },
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(MelodiaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            val publishTimeStr = remember(album.publishTime) {
                if (album.publishTime > 0) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(album.publishTime))
                } else ""
            }
            val desc = buildString {
                if (publishTimeStr.isNotEmpty()) append(publishTimeStr)
                if (album.size > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("${album.size}首歌曲")
                }
            }
            Text(
                text = desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "详情",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

fun formatFansCount(count: Long): String {
    return if (count >= 10000) {
        val num = count / 10000.0
        String.format(Locale.getDefault(), "%.1f万", num)
    } else {
        NumberFormat.getNumberInstance(Locale.US).format(count)
    }
}
