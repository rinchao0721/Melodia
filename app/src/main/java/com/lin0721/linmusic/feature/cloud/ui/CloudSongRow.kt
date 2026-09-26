package com.lin0721.linmusic.feature.cloud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.feature.cloud.domain.CloudSong

private val CoverSize = 56.dp
private val UnmatchedTint = Color(0xFFE0A030)

@Composable
fun CloudSongRow(
    song: CloudSong,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(CoverSize)) {
            SubcomposeAsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() },
                modifier = Modifier
                    .size(CoverSize)
                    .clip(RoundedCornerShape(RadiusCompact))
            )
            if (song.isUnmatched) {
                Text(
                    text = "未识别",
                    color = Color(0xFF241A08),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(UnmatchedTint, RoundedCornerShape(bottomStart = RadiusCompact, topEnd = 6.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(start = MelodiaSpacing.sm).weight(1f)) {
            Text(
                text = song.name,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} · ${song.fileSizeText}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        MelodiaIconButton(onClick = onOptionsClick) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "更多操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
