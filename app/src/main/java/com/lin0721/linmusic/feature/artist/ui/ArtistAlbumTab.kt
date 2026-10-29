package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 专辑 Tab: 采用精致的行模式展示，滚动到底自动追加下一页
fun LazyListScope.artistAlbumTab(
    albums: List<ArtistAlbum>,
    loadingMore: Boolean,
    onAlbumClick: (Long) -> Unit
) {
    if (albums.isEmpty() && !loadingMore) {
        item(key = "empty_albums") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MelodiaColors.PageFill),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无专辑", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    } else {
        items(albums, key = { it.id }) { album ->
            ArtistAlbumRow(album = album, onClick = { onAlbumClick(album.id) })
        }
        if (loadingMore) {
            item(key = "albums_loading_more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = MelodiaSpacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}
