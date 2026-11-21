package com.lin0721.linmusic.feature.localmusic.ui.collection

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaGridColumns
import com.lin0721.linmusic.feature.localmusic.ui.LocalLibraryStateGate
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicNavigation
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalAlbumCard
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover
import org.koin.androidx.compose.koinViewModel
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons

@Composable
fun LocalArtistsScreen(
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    CollectionScaffold(title = "歌手", navigation = navigation, viewModel = viewModel, isEmpty = library.artists.isEmpty()) {
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md)) {
            items(library.artists, key = { it.name }) { artist ->
                CollectionRow(
                    coverSourceUri = artist.tracks.first().uri,
                    coverShape = CircleShape,
                    fallbackIcon = MelodiaIcons.Person,
                    title = artist.name,
                    subtitle = "${artist.tracks.size} 首 · ${artist.albumCount} 张专辑",
                    onClick = { navigation.openArtist(artist.name) }
                )
            }
        }
    }
}

@Composable
fun LocalAlbumsScreen(
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val columns = rememberMelodiaGridColumns(compact = 2, expandedPortrait = 4, expandedLandscape = 6)
    CollectionScaffold(title = "专辑", navigation = navigation, viewModel = viewModel, isEmpty = library.albums.isEmpty()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(
                start = MelodiaSpacing.md,
                end = MelodiaSpacing.md,
                top = MelodiaSpacing.sm,
                bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md
            )
        ) {
            items(library.albums, key = { it.key }) { album ->
                BoxWithConstraints {
                    LocalAlbumCard(
                        coverSourceUri = album.coverSourceUri,
                        title = album.name,
                        subtitle = "${album.artist} · ${album.tracks.size} 首",
                        width = maxWidth,
                        onClick = { navigation.openAlbum(album.key) }
                    )
                }
            }
        }
    }
}

@Composable
fun LocalFoldersScreen(
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    CollectionScaffold(title = "文件夹", navigation = navigation, viewModel = viewModel, isEmpty = library.folders.isEmpty()) {
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md)) {
            items(library.folders, key = { it.path }) { folder ->
                CollectionRow(
                    coverSourceUri = null,
                    coverShape = RoundedCornerShape(8.dp),
                    fallbackIcon = MelodiaIcons.LocalMusic,
                    title = folder.name,
                    subtitle = "${folder.tracks.size} 首 · ${folder.path}",
                    onClick = { navigation.openFolder(folder.path) }
                )
            }
        }
    }
}

@Composable
private fun CollectionScaffold(
    title: String,
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel,
    isEmpty: Boolean,
    content: @Composable () -> Unit
) {
    SecondaryScreenScaffold(title = title, onBack = navigation.onBack) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LocalLibraryStateGate(viewModel) {
                if (isEmpty) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(icon = MelodiaIcons.Library, title = "还没有本地音频文件")
                    }
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun CollectionRow(
    coverSourceUri: Uri?,
    coverShape: Shape,
    fallbackIcon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverSourceUri == null) {
            Box(
                modifier = Modifier.size(48.dp).clip(coverShape).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(fallbackIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LocalCover(sourceUri = coverSourceUri, size = 48.dp, shape = coverShape, fallbackIcon = fallbackIcon)
        }
        Spacer(Modifier.width(MelodiaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
