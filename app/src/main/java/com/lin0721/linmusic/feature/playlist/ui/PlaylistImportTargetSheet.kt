package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.lin0721.linmusic.core.ui.components.CreatePlaylistDialog
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.userplaylist.UserPlaylist

// ────────────────────────────────────────────────────────────────────────────
// "添加到歌单"批量导入的目标歌单选择：单选即导入，不做逐首"是否已包含"核对
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportTargetSheet(
    importState: PlaylistImportState,
    onDismiss: () -> Unit,
    onSelectTarget: (Long) -> Unit,
    onCreateAndImport: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.lg)
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "添加到歌单",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )

            var showCreatePlaylistDialog by remember { mutableStateOf(false) }

            if (importState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                ) {
                    item(key = "create_new_playlist_item") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCreatePlaylistDialog = true }
                                .padding(vertical = MelodiaSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "新建歌单",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "新建歌单",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (importState.items.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = MelodiaSpacing.lg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无其他可用歌单", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(importState.items, key = { it.id }) { item: UserPlaylist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTarget(item.id) }
                                    .padding(vertical = MelodiaSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SubcomposeAsyncImage(
                                    model = "${item.coverImgUrl}?param=100y100",
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    loading = { CoverPlaceholder() },
                                    error = { CoverPlaceholder() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(RadiusCompact))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = item.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (showCreatePlaylistDialog) {
                CreatePlaylistDialog(
                    onDismiss = { showCreatePlaylistDialog = false },
                    onCreate = { name -> onCreateAndImport(name) }
                )
            }
        }
    }
}
