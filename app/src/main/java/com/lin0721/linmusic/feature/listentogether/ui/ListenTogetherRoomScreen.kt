package com.lin0721.linmusic.feature.listentogether.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.listentogether.ListenTogetherState
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherMember
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.ui.components.AntiFlickerCoverImage
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.PlayingEqualizerBars
import com.lin0721.linmusic.core.ui.theme.FallbackBackdropPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PaletteMemoryCache
import com.lin0721.linmusic.core.ui.theme.extractBackdropPaletteFromUrl
import com.lin0721.linmusic.core.ui.theme.rememberImmersiveColors
import com.lin0721.linmusic.core.ui.theme.uiTween
import kotlinx.coroutines.launch

private val CoverSize = 220.dp
private val AvatarSize = 44.dp
private val QueueCoverSize = 40.dp
private val CardShape = RoundedCornerShape(16.dp)
private const val COLOR_TRANSITION_MS = 800

// 一起听房间页：封面取色铺底，自上而下为状态栏、封面与当前歌曲、成员、房间队列、邀请与退出
@Composable
fun ListenTogetherRoomScreen(
    viewModel: ListenTogetherViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val tracks by viewModel.roomTracks.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val coverUrl = nowPlaying?.artworkUri.orEmpty()
    var paletteBase by remember { mutableStateOf(FallbackBackdropPalette.base) }
    LaunchedEffect(nowPlaying?.mediaId, coverUrl) {
        val mediaId = nowPlaying?.mediaId
        val cached = mediaId?.let { PaletteMemoryCache.get(it) }
        paletteBase = when {
            cached != null -> cached.base
            coverUrl.isNotEmpty() -> extractBackdropPaletteFromUrl(context, coverUrl).also { palette ->
                if (mediaId != null) PaletteMemoryCache.put(mediaId, palette)
            }.base
            else -> FallbackBackdropPalette.base
        }
    }
    val immersive = rememberImmersiveColors(paletteBase)
    val background by animateColorAsState(immersive.fill, uiTween(COLOR_TRANSITION_MS), label = "roomBackground")
    val content = Color.White
    val secondary = Color.White.copy(alpha = 0.72f)
    val cardFill = Color.White.copy(alpha = 0.10f)

    // 会话结束（被结束、断网退出）后自动关闭
    LaunchedEffect(state.active) { if (!state.active) onClose() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
            ) {
                item {
                    RoomTopBar(state = state, content = content, secondary = secondary, onClose = onClose)
                }
                item {
                    NowPlayingSection(
                        coverUrl = coverUrl,
                        title = nowPlaying?.title.orEmpty(),
                        artist = nowPlaying?.artist.orEmpty(),
                        progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        followText = if (state.isHost) "好友正在跟随你的播放" else "正在跟随房主播放",
                        content = content,
                        secondary = secondary
                    )
                }
                item {
                    MembersSection(state = state, content = content, secondary = secondary, cardFill = cardFill)
                }
                item {
                    Text(
                        text = if (state.roomSongIds.isEmpty()) "房间队列" else "房间队列 · ${state.roomSongIds.size} 首",
                        color = content,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = MelodiaSpacing.lg)
                    )
                }
                if (tracks.isEmpty()) {
                    item {
                        Text(
                            text = if (state.roomSongIds.isEmpty()) "房主推送队列后显示" else "曲目信息加载中",
                            color = secondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = MelodiaSpacing.lg)
                        )
                    }
                } else {
                    itemsIndexed(tracks, key = { index, item -> "${item.songId}_$index" }) { index, item ->
                        RoomTrackRow(
                            item = item,
                            active = item.songId == nowPlaying?.songId,
                            clickable = state.isHost,
                            content = content,
                            secondary = secondary,
                            onClick = { viewModel.playRoomTrack(index) }
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MelodiaSpacing.lg, vertical = MelodiaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                    ) {
                        if (state.isHost) {
                            MelodiaButton(
                                onClick = {
                                    scope.launch {
                                        val text = viewModel.currentInviteText()
                                        if (text.isNotBlank()) {
                                            clipboard.setText(AnnotatedString(text))
                                            Toast.makeText(context, "邀请文案已复制，发给好友粘贴加入", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = background),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(MelodiaSpacing.sm))
                                Text("复制邀请文案")
                            }
                        }
                        MelodiaButton(
                            onClick = { viewModel.leave() },
                            colors = ButtonDefaults.buttonColors(containerColor = cardFill, contentColor = content),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (state.isHost) "结束房间" else "退出一起听") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomTopBar(state: ListenTogetherState, content: Color, secondary: Color, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MelodiaIconButton(onClick = onClose) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起房间", tint = content)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "一起听", color = content, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val unstable = state.message != null
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (unstable) Color(0xFFFFC107) else Color(0xFF4CAF50))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = (state.message ?: "同步中") + if (state.isHost) " · 你是房主" else " · 跟随中",
                    color = secondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun NowPlayingSection(
    coverUrl: String,
    title: String,
    artist: String,
    progress: Float,
    followText: String,
    content: Color,
    secondary: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AntiFlickerCoverImage(
            url = coverUrl,
            modifier = Modifier
                .size(CoverSize)
                .clip(RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.height(MelodiaSpacing.lg))
        Text(
            text = title.ifBlank { "暂无播放" },
            color = content,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (artist.isNotBlank()) {
            Text(text = artist, color = secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(MelodiaSpacing.md))
        LinearProgressIndicator(
            progress = { progress },
            color = content,
            trackColor = content.copy(alpha = 0.2f),
            modifier = Modifier
                .widthIn(max = CoverSize + 80.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(MelodiaSpacing.sm))
        Text(text = followText, color = secondary, fontSize = 12.sp)
    }
}

@Composable
private fun MembersSection(state: ListenTogetherState, content: Color, secondary: Color, cardFill: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg)
            .clip(CardShape)
            .background(cardFill)
            .padding(MelodiaSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "成员", color = content, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(text = "${state.members.size} 人", color = secondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(MelodiaSpacing.sm))
        if (state.members.isEmpty()) {
            Text(text = "等待好友加入", color = secondary, fontSize = 13.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                items(state.members, key = { it.userId }) { member ->
                    MemberAvatar(member, isHost = member.userId == state.creatorId, content = content, secondary = secondary)
                }
                if (state.members.size == 2) {
                    item {
                        Icon(Icons.Rounded.SyncAlt, contentDescription = null, tint = secondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberAvatar(member: ListenTogetherMember, isHost: Boolean, content: Color, secondary: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box {
            AsyncImage(
                model = member.avatarUrl.takeIf { it.isNotBlank() },
                contentDescription = member.nickname,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .background(content.copy(alpha = 0.2f))
                    .then(if (isHost) Modifier.border(2.dp, content, CircleShape) else Modifier)
            )
            if (isHost) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(content),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = "房主", tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = member.nickname.ifBlank { member.userId.toString() },
            color = if (isHost) content else secondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// 队列行在有色铺底上，不复用主题色的 SongRow
@Composable
private fun RoomTrackRow(
    item: QueueItem,
    active: Boolean,
    clickable: Boolean,
    content: Color,
    secondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = MelodiaSpacing.lg, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AntiFlickerCoverImage(
            url = item.coverUrl,
            modifier = Modifier
                .size(QueueCoverSize)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(MelodiaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = content,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = item.artist, color = secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (active) {
            Spacer(Modifier.width(MelodiaSpacing.sm))
            PlayingEqualizerBars(color = content, animate = true)
        }
    }
}
