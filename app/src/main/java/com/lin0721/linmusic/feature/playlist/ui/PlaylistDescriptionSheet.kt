package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 歌单与专辑完整介绍底部抽屉
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDescriptionSheet(
    playlist: PlaylistDetail,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (playlist.coverImgUrl.isNotBlank()) {
                    AsyncImage(
                        model = "${playlist.coverImgUrl}?param=140y140",
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    if (playlist.creator != null && playlist.creator.nickname.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = playlist.creator.nickname,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 460.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = playlist.description.orEmpty().trim(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}
