package com.lin0721.linmusic.desktop.ui.track

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.CollectPromptEvent
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.ui.icons.FavoriteFilled
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.uiEnter
import com.lin0721.linmusic.desktop.ui.components.uiExit
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

private const val COLLECT_PROMPT_DURATION_MS = 4_000L
private val PromptCoverShape = RoundedCornerShape(4.dp)
private val PromptCardShape = RoundedCornerShape(10.dp)
private val LikedCoverGradient = listOf(Color(0xFF450AF5), Color(0xFF8E8EE5))

// 一键收藏成功后在主窗底部展示的提示条：带“已点赞的歌曲”小封面与“更改”按钮，弹窗直接锚定在提示条旁
@Composable
internal fun CollectPromptHost(
    promptEvents: Flow<CollectPromptEvent>,
    likedCoverUrl: String,
    collectState: PlaylistCollectState,
    onPrepareCollect: (Long) -> Unit,
    onSaveCollect: (Long, List<PlaylistCollectItem>) -> Unit,
    onCreateAndAdd: (String, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var activePrompt by remember { mutableStateOf<CollectPromptEvent?>(null) }
    var promptSerial by remember { mutableIntStateOf(0) }
    var popupOpen by remember { mutableStateOf(false) }

    LaunchedEffect(promptEvents) {
        promptEvents.collect { event ->
            activePrompt = event
            promptSerial++
            popupOpen = false
        }
    }

    // 弹窗打开期间暂停倒计时，关闭或超时后自动收起
    LaunchedEffect(activePrompt, promptSerial, popupOpen) {
        if (activePrompt != null && !popupOpen) {
            delay(COLLECT_PROMPT_DURATION_MS)
            activePrompt = null
        }
    }

    Box(modifier) {
        AnimatedVisibility(
            visible = activePrompt != null,
            enter = uiEnter(slideInVertically { it / 2 } + fadeIn()),
            exit = uiExit(slideOutVertically { it / 2 } + fadeOut())
        ) {
            val prompt = activePrompt ?: return@AnimatedVisibility
            Row(
                modifier = Modifier
                    .widthIn(min = 300.dp, max = 440.dp)
                    .themedShadow(12.dp, PromptCardShape)
                    .clip(PromptCardShape)
                    .background(DesktopColors.PopupSurface)
                    .floatingOutline(PromptCardShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LikedSongsMiniCover(coverUrl = likedCoverUrl, size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "已添加到“已点赞的歌曲”。",
                    color = DesktopColors.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    onClick = {
                        onPrepareCollect(prompt.songId)
                        popupOpen = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 28.dp)
                ) {
                    Text(
                        text = "更改",
                        color = DesktopColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        val songId = activePrompt?.songId
        if (popupOpen && songId != null) {
            CollectToPlaylistPopup(
                songId = songId,
                state = collectState,
                onSave = { items ->
                    onSaveCollect(songId, items)
                    popupOpen = false
                    activePrompt = null
                },
                onCreate = { name ->
                    onCreateAndAdd(name, songId)
                    popupOpen = false
                    activePrompt = null
                },
                onDismiss = { popupOpen = false }
            )
        }
    }
}

@Composable
private fun LikedSongsMiniCover(coverUrl: String, size: Dp) {
    if (coverUrl.isNotBlank()) {
        Cover(url = coverUrl, size = size, shape = PromptCoverShape)
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(PromptCoverShape)
                .background(Brush.linearGradient(LikedCoverGradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MelodiaIcons.FavoriteFilled,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}
