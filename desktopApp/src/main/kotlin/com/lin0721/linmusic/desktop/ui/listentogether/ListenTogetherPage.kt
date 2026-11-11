package com.lin0721.linmusic.desktop.ui.listentogether

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.listentogether.ListenTogetherState
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherMember
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.nowplaying.rememberCoverBase
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.feature.listentogether.ui.ListenTogetherViewModel
import kotlinx.coroutines.launch

private val CoverSize = 280.dp
private val AvatarSize = 44.dp
private val CardShape = RoundedCornerShape(12.dp)
private const val BACKGROUND_DARKEN = 0.35f
private const val COLOR_TRANSITION_MS = 800

// 一起听房间页：封面取色铺底，左栏为当前歌曲、成员与操作，右栏为房间队列
@Composable
fun ListenTogetherPage(viewModel: ListenTogetherViewModel, modifier: Modifier = Modifier) {
    val navigator = LocalDesktopNavigator.current
    val state by viewModel.state.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val tracks by viewModel.roomTracks.collectAsState()
    val scope = rememberCoroutineScope()

    val base = rememberCoverBase(nowPlaying?.artworkUri)
    val background by animateColorAsState(lerp(base, Color.Black, BACKGROUND_DARKEN), uiTween(COLOR_TRANSITION_MS), label = "roomBackground")
    val content = Color.White
    val secondary = Color.White.copy(alpha = 0.72f)
    val cardFill = Color.White.copy(alpha = 0.10f)

    Box(modifier.fillMaxSize().background(background)) {
        if (!state.active) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message ?: "当前不在一起听房间", color = content, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                PillButton("返回", content, cardFill) { navigator.goBack() }
            }
            return@Box
        }
        Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.width(CoverSize + 40.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RoomHeader(state, content, secondary)
                Cover(nowPlaying?.artworkUri, CoverSize, shape = RoundedCornerShape(16.dp))
                Column {
                    Text(
                        nowPlaying?.title?.ifBlank { null } ?: "暂无播放",
                        color = content,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(nowPlaying?.artist.orEmpty(), color = secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                        color = content,
                        trackColor = content.copy(alpha = 0.2f),
                        modifier = Modifier.width(CoverSize).height(3.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(if (state.isHost) "好友正在跟随你的播放" else "正在跟随房主播放", color = secondary, fontSize = 12.sp)
                }
                MembersCard(state, content, secondary, cardFill)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isHost) {
                        PillButton("复制邀请", background, content, icon = { Icon(Icons.Rounded.Link, null, tint = background, modifier = Modifier.size(16.dp)) }) {
                            scope.launch {
                                val text = viewModel.currentInviteText()
                                if (text.isNotBlank()) {
                                    navigator.showMessage(if (copyTextToClipboard(text)) "邀请文案已复制，发给好友粘贴加入" else "复制失败")
                                }
                            }
                        }
                    }
                    PillButton(if (state.isHost) "结束房间" else "退出一起听", content, cardFill) { viewModel.leave() }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    if (state.roomSongIds.isEmpty()) "房间队列" else "房间队列 · ${state.roomSongIds.size} 首",
                    color = content,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!state.isHost) {
                    Text("队列由房主控制", color = secondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (tracks.isEmpty()) {
                    Text(
                        if (state.roomSongIds.isEmpty()) "房主推送队列后显示" else "曲目信息加载中",
                        color = secondary,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize().clip(CardShape).background(cardFill)) {
                        itemsIndexed(tracks, key = { index, item -> "${item.songId}_$index" }) { index, item ->
                            RoomTrackRow(
                                index = index,
                                item = item,
                                active = item.songId == nowPlaying?.songId,
                                clickable = state.isHost,
                                content = content,
                                secondary = secondary,
                                onClick = { viewModel.playRoomTrack(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomHeader(state: ListenTogetherState, content: Color, secondary: Color) {
    Column {
        Text("一起听", color = content, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(6.dp).clip(CircleShape)
                    .background(if (state.message != null) Color(0xFFFFC107) else Color(0xFF4CAF50))
            )
            Spacer(Modifier.width(6.dp))
            Text((state.message ?: "同步中") + if (state.isHost) " · 你是房主" else " · 跟随中", color = secondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MembersCard(state: ListenTogetherState, content: Color, secondary: Color, cardFill: Color) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(cardFill).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("成员", color = content, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${state.members.size} 人", color = secondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (state.members.isEmpty()) {
            Text("等待好友加入", color = secondary, fontSize = 12.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                state.members.forEach { member ->
                    MemberAvatar(member, member.userId == state.creatorId, content, secondary)
                }
            }
        }
    }
}

@Composable
private fun MemberAvatar(member: ListenTogetherMember, isHost: Boolean, content: Color, secondary: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        Box {
            Cover(
                member.avatarUrl.ifBlank { null },
                AvatarSize,
                shape = CircleShape,
                modifier = if (isHost) Modifier.border(2.dp, content, CircleShape) else Modifier
            )
            if (isHost) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(content),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Star, "房主", tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            member.nickname.ifBlank { member.userId.toString() },
            color = if (isHost) content else secondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoomTrackRow(
    index: Int,
    item: QueueItem,
    active: Boolean,
    clickable: Boolean,
    content: Color,
    secondary: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .hoverable(interaction)
            .background(if (hovered && clickable) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .then(if (clickable) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (active) {
                Icon(Icons.Rounded.GraphicEq, "正在播放", tint = content, modifier = Modifier.size(16.dp))
            } else {
                Text("${index + 1}", color = secondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Cover(item.coverUrl, 40.dp, shape = RoundedCornerShape(6.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = content,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(item.artist, color = secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    textColor: Color,
    fill: Color,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.clip(CircleShape).background(fill)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
