package com.lin0721.linmusic.feature.profile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.feature.profile.domain.ProfilePlaylistInfo

// 小封面列表行样式
fun LazyListScope.profilePlaylistItems(
    playlists: List<ProfilePlaylistInfo>,
    isLoading: Boolean,
    onPlaylistClick: (id: Long) -> Unit
) {
    if (playlists.isEmpty()) {
        item(key = "playlist_empty") {
            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            } else {
                EmptyState(icon = Icons.Rounded.LibraryMusic, title = "暂无歌单")
            }
        }
        return
    }

    items(playlists, key = { it.id }) { playlist ->
        ProfilePlaylistRow(
            playlist = playlist,
            onClick = { onPlaylistClick(playlist.id) }
        )
    }

    if (isLoading) {
        item(key = "playlist_loading_more") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(MelodiaSpacing.md),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ProfilePlaylistRow(
    playlist: ProfilePlaylistInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = "${playlist.coverImgUrl}?param=150y150",
            contentDescription = playlist.name,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(RadiusCompact)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(MelodiaSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (playlist.trackCount > 0) "${playlist.trackCount}首" else playlist.creatorName,
                color = TextGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
