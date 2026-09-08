package com.lin0721.linmusic.feature.player.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.Dialog
import com.lin0721.linmusic.core.model.getQualityDisplayName
import kotlinx.coroutines.launch
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMoreOptionsSheet(
    title: String,
    artist: String,
    coverUrl: String,
    albumName: String,
    isLiked: Boolean,
    sleepTimerRemaining: Long,
    currentQuality: String,
    onToggleLike: () -> Unit,
    onAlbumClick: () -> Unit,
    onArtistClick: () -> Unit,
    onShowTimerClick: () -> Unit,
    onQualitySelected: (String) -> Unit,
    isIntelligence: Boolean,
    onToggleIntelligence: (Boolean) -> Unit,
    onStartSimilarRoaming: () -> Unit,
    onInsertSimilarSongs: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundDark,
        shape = BottomSheetShape,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = MelodiaSpacing.xs)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(DragHandleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            // 头部：封面 + 歌曲名 + 歌手名
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubcomposeAsyncImage(
                    model = coverUrl.ifEmpty { null },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(RadiusCompact))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist,
                        color = TextGray,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = MelodiaSpacing.sm)
            )



            // 选项列表区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. 专辑信息项
                OptionRow(
                    icon = Icons.Rounded.Album,
                    text = "专辑: $albumName",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onAlbumClick()
                        }
                    }
                )

                // 2. 歌手信息项
                OptionRow(
                    icon = Icons.Rounded.Person,
                    text = "歌手: $artist",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onArtistClick()
                        }
                    }
                )


                // 3. 收藏到歌单
                OptionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    text = "收藏到歌单",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onCollectClick()
                        }
                    }
                )

                // 4. 心动模式：开=以当前歌曲为种子开启，关=恢复开启前的队列
                OptionRow(
                    icon = Icons.Rounded.AutoAwesome,
                    text = if (isIntelligence) "关闭心动模式" else "打开心动模式",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onToggleIntelligence(!isIntelligence)
                        }
                    }
                )

                // 5. 开始相似歌曲漫游
                OptionRow(
                    icon = Icons.Rounded.Explore,
                    text = "开始相似歌曲漫游",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onStartSimilarRoaming()
                        }
                    }
                )

                // 5. 插播相似歌曲
                OptionRow(
                    icon = Icons.Rounded.QueueMusic,
                    text = "插播相似歌曲",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onInsertSimilarSongs()
                        }
                    }
                )


                // 6. 分享
                OptionRow(
                    icon = Icons.Rounded.Share,
                    text = "分享",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onShareClick()
                        }
                    }
                )

                // 7. 音质（带有 VIP Tag）
                var showQualityDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showQualityDialog = true
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "音质: ${getQualityDisplayName(currentQuality)}",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    // VIP Tag
                    if (currentQuality == "lossless" || currentQuality == "hires" || currentQuality == "jymaster") {
                        Box(
                            modifier = Modifier
                                .border(1.dp, NeteaseRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "VIP",
                                color = NeteaseRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (showQualityDialog) {
                    val qualitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val qualities = listOf(
                        "standard" to "标准音质",
                        "exhigh" to "极高音质",
                        "lossless" to "无损音质 (FLAC)",
                        "hires" to "Hi-Res 无损",
                        "jymaster" to "超清母带"
                    )
                    ModalBottomSheet(
                        onDismissRequest = { showQualityDialog = false },
                        sheetState = qualitySheetState,
                        containerColor = BackgroundDark,
                        shape = BottomSheetShape,
                        dragHandle = {
                            Box(
                                modifier = Modifier
                                    .padding(top = 12.dp, bottom = MelodiaSpacing.xs)
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(DragHandleShape)
                                    .background(Color.White.copy(alpha = 0.3f))
                            )
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
                        ) {
                            Text(
                                text = "选择播放音质",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                            )
                            qualities.forEach { pair ->
                                val key = pair.first
                                val label = pair.second
                                val isSelected = currentQuality == key
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onQualitySelected(key)
                                            scope.launch {
                                                qualitySheetState.hide()
                                                showQualityDialog = false
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) NeteaseRed else Color.White,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = NeteaseRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                // 9. 定时关闭
                val timerText = if (sleepTimerRemaining > 0L) {
                    val mins = (sleepTimerRemaining + 59999L) / (60 * 1000L)
                    "定时关闭 (${mins})"
                } else {
                    "定时关闭"
                }
                OptionRow(
                    icon = Icons.Rounded.AccessTime,
                    text = timerText,
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onShowTimerClick()
                        }
                    }
                )
            }
        }
    }
}


@Composable
private fun OptionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
