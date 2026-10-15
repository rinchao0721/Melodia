package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.ui.platform.LocalConfiguration
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 全屏歌词页顶部栏：折叠入口、歌曲标题与歌手、更多菜单，整行承载下拉关闭手势
@Composable
fun FullScreenLyricsHeader(
    title: String,
    artist: String,
    onClose: () -> Unit,
    onMoreClick: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragRelease: (Float) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscapeOrWide = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(
                start = if (isLandscapeOrWide) MelodiaSpacing.lg else MelodiaSpacing.sm,
                end = if (isLandscapeOrWide) MelodiaSpacing.lg else 6.dp
            )
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    onDragDelta(delta)
                },
                onDragStarted = {
                    onDragStart()
                },
                onDragStopped = { velocity ->
                    onDragRelease(velocity)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MelodiaIconButton(onClick = onClose) {
            Icon(
                imageVector = if (isLandscapeOrWide) Icons.Rounded.FullscreenExit else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isLandscapeOrWide) "退出全屏" else "折叠歌词",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        MelodiaIconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多选项",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
