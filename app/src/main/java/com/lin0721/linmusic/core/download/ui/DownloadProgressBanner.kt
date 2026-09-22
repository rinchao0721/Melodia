package com.lin0721.linmusic.core.download.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.download.SongDownloadManager
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.TextGray
import org.koin.compose.koinInject

// 下载进度横幅
@Composable
fun DownloadProgressBanner(modifier: Modifier = Modifier) {
    val songDownloadManager: SongDownloadManager = koinInject()
    val activeDownloads by songDownloadManager.observeActiveDownloads()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    if (activeDownloads.isEmpty()) return

    val overallProgress = (activeDownloads.map { it.progress }.average().toFloat() / 100f).coerceIn(0f, 1f)
    val titleText = if (activeDownloads.size == 1) {
        activeDownloads.first().songName.ifBlank { "歌曲" }
    } else {
        "${activeDownloads.size} 首歌曲"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.sm)
            .padding(bottom = MelodiaSpacing.xs)
            .clip(RoundedCornerShape(InfoCardRadius))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(InfoCardRadius))
            .background(BackgroundDark)
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = NeteaseRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "正在下载 $titleText",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { overallProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = NeteaseRed
                )
            }
            Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
            Text(
                text = "${(overallProgress * 100).toInt()}%",
                color = TextGray,
                fontSize = 12.sp
            )
        }
    }
}
