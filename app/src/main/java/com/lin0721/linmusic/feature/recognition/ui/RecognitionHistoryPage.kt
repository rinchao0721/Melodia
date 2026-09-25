package com.lin0721.linmusic.feature.recognition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.components.SwipeDeleteBackground
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.feature.player.ui.formatTime
import com.lin0721.linmusic.feature.recognition.data.MAX_RECOGNITION_HISTORY
import com.lin0721.linmusic.feature.recognition.domain.RecognitionHistoryEntry
import com.lin0721.linmusic.feature.recognition.domain.RecognitionNowPlaying
import java.time.ZoneId

@Composable
internal fun RecognitionHistoryPage(
    history: List<RecognitionHistoryEntry>,
    nowPlaying: RecognitionNowPlaying?,
    onBack: () -> Unit,
    onPlay: (RecognitionHistoryEntry) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val sections = remember(history) {
        groupRecognitionHistory(history, System.currentTimeMillis(), ZoneId.systemDefault())
    }

    SecondaryScreenScaffold(
        title = "识别历史",
        onBack = onBack,
        actions = {
            if (history.isNotEmpty()) {
                MelodiaTextButton(onClick = { confirmClear = true }) {
                    Text(text = "清空", color = TextGray, fontSize = 15.sp)
                }
            }
        }
    ) {
        if (sections.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.History,
                title = "还没有识别记录",
                subtitle = "识别成功的歌曲会保存在这里"
            )
        } else {
            HistoryList(sections = sections, nowPlaying = nowPlaying, onPlay = onPlay, onRemove = onRemove)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空识别历史", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("确定要清空全部 ${history.size} 条识别记录吗？", color = TextGray, fontSize = 14.sp) },
            confirmButton = {
                MelodiaTextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeteaseRed)
                ) {
                    Text("是的", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                MelodiaTextButton(onClick = { confirmClear = false }) {
                    Text("取消", color = Color.White)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(10.dp)
        )
    }
}

@Composable
private fun HistoryList(
    sections: List<RecognitionHistorySection>,
    nowPlaying: RecognitionNowPlaying?,
    onPlay: (RecognitionHistoryEntry) -> Unit,
    onRemove: (String) -> Unit
) {
    LazyColumn {
        sections.forEach { section ->
            item(key = "header_${section.label}") {
                Text(
                    text = section.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        start = MelodiaSpacing.md,
                        end = MelodiaSpacing.md,
                        top = MelodiaSpacing.md,
                        bottom = MelodiaSpacing.xs
                    )
                )
            }
            items(items = section.rows, key = { it.entry.id }) { row ->
                HistoryRow(row = row, nowPlaying = nowPlaying, onPlay = onPlay, onRemove = onRemove)
            }
        }
        item(key = "footer") {
            Text(
                text = "只保存识别成功的记录，最多 $MAX_RECOGNITION_HISTORY 条 · 左滑删除单条",
                color = TextGray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = MelodiaSpacing.lg)
            )
        }
    }
}

@Composable
private fun HistoryRow(
    row: RecognitionHistoryRow,
    nowPlaying: RecognitionNowPlaying?,
    onPlay: (RecognitionHistoryEntry) -> Unit,
    onRemove: (String) -> Unit
) {
    val entry = row.entry
    val isCurrent = nowPlaying?.songId == entry.songId
    val positionText = formatTime(entry.startTimeMs)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove(entry.id)
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeDeleteBackground() },
        enableDismissFromStartToEnd = false
    ) {
        // 前景必须有底色，否则左滑前就会透出红色底层
        Box(modifier = Modifier.background(BackgroundDark)) {
            SongRow(
                data = SongRowData(
                    id = entry.songId,
                    title = entry.title,
                    artist = "${entry.artists} · ${row.timeLabel}",
                    coverUrl = entry.coverUrl
                ),
                isActive = isCurrent,
                isPlaying = nowPlaying?.isPlaying == true,
                onClick = { onPlay(entry) },
                showDownloadBadge = false
            ) {
                // 同一首歌可能有多条历史，正在播放时这些行都标为播放态
                if (!isCurrent) MelodiaButton(
                    onClick = { onPlay(entry) },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.padding(start = MelodiaSpacing.sm).height(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "从 $positionText 播放 ${entry.title}",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(MelodiaSpacing.xs))
                    Text(text = positionText, fontSize = 13.sp)
                }
            }
        }
    }
}
