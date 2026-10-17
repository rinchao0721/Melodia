package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.model.ArtistDetailInfo

// ────────────────────────────────────────────────────────────────────────────
// 艺人简介 Dialog
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun ArtistBioDialog(
    artist: ArtistDetailInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "关于 ${artist.name}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Box(modifier = Modifier.heightIn(max = 300.dp)) {
                LazyColumn {
                    item {
                        Text(
                            text = artist.briefDesc.trim(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            MelodiaTextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("关闭", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    )
}

// ────────────────────────────────────────────────────────────────────────────
// "歌手操作" 更多菜单弹层（屏蔽/取消屏蔽歌手）
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistMoreMenuSheet(
    isBlocked: Boolean,
    albumMinTracks: Int,
    albumHasCustomMinimum: Boolean,
    onChangeAlbumMinimum: () -> Unit,
    catalogEnabled: Boolean,
    catalogUsesDefault: Boolean = true,
    onCatalogSettings: () -> Unit,
    onBlockClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MelodiaSpacing.lg, vertical = MelodiaSpacing.md)
        ) {
            Text(
                text = "歌手操作",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )

            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    onDismiss()
                    onCatalogSettings()
                }.padding(vertical = MelodiaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Album, contentDescription = null,
                    tint = if (catalogEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(MelodiaSpacing.md))
                Column {
                    Text("主要录音室专辑（资料库）", color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    val catalogSource = if (catalogUsesDefault) "跟随全局" else "此歌手单独设置"
                    Text("${if (catalogEnabled) "已开启" else "已关闭"} · $catalogSource",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    onDismiss()
                    onChangeAlbumMinimum()
                }.padding(vertical = MelodiaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(MelodiaSpacing.md))
                Column {
                    Text("专辑最少曲数", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    val source = if (albumHasCustomMinimum) "此歌手单独设置" else "使用默认值"
                    Text(
                        if (albumMinTracks == 0) "不限制曲数 · $source" else "至少 $albumMinTracks 首 · $source",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onBlockClick()
                    }
                    .padding(vertical = MelodiaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isBlocked) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = if (isBlocked) "取消屏蔽" else "屏蔽歌手",
                    tint = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                Column {
                    Text(
                        text = if (isBlocked) "取消屏蔽该艺人所有歌曲" else "屏蔽该艺人所有歌曲",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isBlocked) "取消屏蔽后，其歌曲将重新显示在推荐和搜索中" else "屏蔽后，其歌曲将不再在推荐和搜索中展示",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
