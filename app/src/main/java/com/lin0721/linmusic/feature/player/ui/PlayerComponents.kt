package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@Composable
fun MiniPlayer(
    currentTrack: MediaItem?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth().height(64.dp).clickable { onClick() }
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = MelodiaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            SubcomposeAsyncImage(
                model = currentTrack.mediaMetadata.artworkUri,
                contentDescription = "Now Playing",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(RadiusCompact)),
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack.mediaMetadata.title?.toString() ?: "Unknown Title",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = currentTrack.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                    color = TextGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MelodiaIconButton(onClick = { }) { Icon(Icons.Default.Share, "Share", tint = Color.White, modifier = Modifier.size(20.dp)) }
            MelodiaIconButton(onClick = onTogglePlay) {
                if (isPlaying) {
                    Icon(Icons.Default.Pause, "Pause", tint = Color.White, modifier = Modifier.size(28.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

// 播放页通用白底胶囊按钮（显示歌词、编辑卡片、恢复默认）
@Composable
fun PlayerCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    MelodiaButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.12f),
            disabledContentColor = Color.White.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs)
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
