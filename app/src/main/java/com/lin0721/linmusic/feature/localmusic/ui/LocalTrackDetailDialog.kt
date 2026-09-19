package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.localmusic.LocalTrack
import com.lin0721.linmusic.core.localmusic.LocalTrackSource
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize

private fun formatTrackDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
fun LocalTrackDetailDialog(track: LocalTrack, onDismiss: () -> Unit) {
    val extension = track.path?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.isNotBlank() }
        ?: track.uri.toString().substringAfterLast('.', "").uppercase().takeIf { it.isNotBlank() }
        ?: "未知"
    val sourceLabel = if (track.source == LocalTrackSource.MELODIA_DOWNLOAD) "Melodia 下载" else "设备本地文件"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(track.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)) {
                DetailRow("歌手", track.artist)
                DetailRow("专辑", track.album?.takeIf { it.isNotBlank() } ?: "未知专辑")
                DetailRow("格式", extension)
                DetailRow("时长", formatTrackDuration(track.durationMs))
                DetailRow("大小", formatFileSize(track.sizeBytes))
                DetailRow("来源", sourceLabel)
                if (track.path != null) {
                    DetailRow("路径", track.path)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = MelodiaSpacing.md)
        )
    }
}
