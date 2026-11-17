package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.CheckCircle
import com.lin0721.linmusic.core.ui.icons.Warning

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
                    imageVector = if (isBlocked) MelodiaIcons.CheckCircle else MelodiaIcons.Warning,
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
