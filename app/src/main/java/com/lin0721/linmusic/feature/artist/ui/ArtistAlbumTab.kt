package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogSelection
import com.lin0721.linmusic.feature.artist.ui.catalog.ArtistCatalogUiState
import com.lin0721.linmusic.feature.artist.ui.catalog.ArtistCatalogStatus

// 专辑 Tab: 采用精致的行模式展示，滚动到底自动追加下一页
fun LazyListScope.artistAlbumTab(
    albums: List<ArtistAlbum>,
    sourceAlbumCount: Int,
    minTracks: Int,
    catalogState: ArtistCatalogUiState,
    catalogSelection: ArtistCatalogSelection?,
    onCatalogSettings: () -> Unit,
    onCatalogRetry: () -> Unit,
    onRestoreAll: () -> Unit,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    onAlbumClick: (Long) -> Unit
) {
    if (catalogState.enabled) item(key = "artist_catalog_summary") {
        Column(Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)) {
            Text("主要录音室专辑 · MusicBrainz", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            ArtistCatalogStatus(catalogState, catalogSelection)
            Text(
                when {
                    loadError != null -> "网易云专辑列表尚未读完，已加载 $sourceAlbumCount 张；请重试加载。"
                    hasMore -> "正在读取完整专辑列表…已加载 $sourceAlbumCount 张"
                    else -> "已读取完整网易云专辑列表，共 $sourceAlbumCount 张"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Row {
                TextButton(onClick = onCatalogSettings) { Text("资料库设置") }
                if (catalogState.error != null) TextButton(onClick = onCatalogRetry, enabled = !catalogState.loading) { Text("重试") }
                TextButton(onClick = onRestoreAll, enabled = !catalogState.saving) { Text("恢复全部") }
            }
        }
    }
    if (minTracks > 0) {
        item(key = "artist_album_filter_summary") {
            Text(
                "只显示至少 $minTracks 首歌曲的专辑",
                modifier = Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
    items(albums, key = { it.id }) { album ->
        ArtistAlbumRow(album = album, onClick = { onAlbumClick(album.id) })
    }
    if (albums.isEmpty() && !loadingMore && !hasMore && loadError == null) {
        item(key = "empty_albums") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (catalogSelection != null) "未识别到符合条件的主要录音室专辑，可恢复全部或检查资料库歌手。"
                    else if (minTracks > 0) "暂无至少 $minTracks 首的专辑，可在歌手菜单调整曲数" else "暂无专辑",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    }
    if (loadingMore || (albums.isEmpty() && hasMore && loadError == null)) {
        item(key = "albums_loading_more") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = MelodiaSpacing.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                if (minTracks > 0 && albums.isEmpty()) {
                    Spacer(Modifier.width(MelodiaSpacing.sm))
                    Text(if (catalogSelection != null) "继续查找主要录音室专辑…" else "继续查找符合曲数的专辑…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }
    if (loadError != null) {
        item(key = "albums_retry") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MelodiaSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(loadError, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                TextButton(onClick = onRetry) { Text("重试加载专辑") }
            }
        }
    }
}
