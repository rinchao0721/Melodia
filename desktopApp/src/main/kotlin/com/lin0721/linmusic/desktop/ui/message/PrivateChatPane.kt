package com.lin0721.linmusic.desktop.ui.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.share.ShareCardMessage
import com.lin0721.linmusic.core.share.ui.ShareCard
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.components.trackTextInputFocus
import com.lin0721.linmusic.desktop.ui.components.uiEnter
import com.lin0721.linmusic.desktop.ui.components.uiExit
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.share.rememberShareCardArt
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.message.domain.PrivateCard
import com.lin0721.linmusic.feature.message.domain.PrivateCardKind
import com.lin0721.linmusic.feature.message.ui.ChatState
import com.lin0721.linmusic.feature.message.ui.PrivateChatItem
import com.lin0721.linmusic.feature.message.ui.PrivateChatViewModel
import kotlinx.coroutines.delay

private val ChatAvatarSize = 36.dp
private val OuterBubbleRadius = 16.dp
private val InnerBubbleRadius = 5.dp
private val CardCoverSize = 44.dp
private val RenderedShareCardWidth = 240.dp
private val RenderedShareCardRadius = 12.dp
private const val RenderedShareCardCoverSize = 540
private val ComposerButtonSize = 36.dp
private val ComposerContainerRadius = 24.dp
private val NowPlayingPillCoverSize = 24.dp
private val NowPlayingPillMaxWidth = 260.dp

@Composable
internal fun PrivateChatPane(
    viewModel: PrivateChatViewModel,
    peerUid: Long,
    onOpenProfile: (Long) -> Unit,
    onOpenPlaylist: (id: Long, title: String) -> Unit,
    onOpenAlbum: (id: Long, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chatState by viewModel.state.collectAsState()
    val chatItems by viewModel.chatItems.collectAsState()
    val peerUser by viewModel.peerUser.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val shareableNowPlaying by viewModel.shareableNowPlaying.collectAsState()

    Column(modifier.fillMaxSize()) {
        ChatHeaderBar(
            peerUid = peerUid,
            peerUser = peerUser,
            onOpenProfile = { onOpenProfile(peerUid) }
        )
        HorizontalDivider(color = DesktopColors.Divider)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val state = chatState) {
                ChatState.Loading -> {
                    CircularProgressIndicator(
                        color = DesktopColors.Accent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is ChatState.Error -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(state.message, color = DesktopColors.TextGray, fontSize = 14.sp)
                        TextButton(onClick = viewModel::retry) {
                            Text("重试", color = DesktopColors.TextPrimary)
                        }
                    }
                }

                ChatState.Ready -> {
                    if (chatItems.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无消息记录，发句话打个招呼吧", color = DesktopColors.TextGray, fontSize = 13.sp)
                        }
                    } else {
                        ChatMessageList(
                            peerUid = peerUid,
                            chatItems = chatItems,
                            peerAvatarUrl = peerUser?.avatarUrl,
                            hasMore = hasMore,
                            isLoadingMore = isLoadingMore,
                            currentSongId = nowPlaying?.songId,
                            isPlaying = isPlaying,
                            onLoadMore = viewModel::loadMore,
                            onOpenProfile = { onOpenProfile(peerUid) },
                            onPlaySong = viewModel::playSong,
                            onOpenPlaylist = onOpenPlaylist,
                            onOpenAlbum = onOpenAlbum
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = DesktopColors.Divider)
        ChatComposerBar(
            draft = draft,
            sending = sending,
            shareableNowPlaying = shareableNowPlaying,
            onDraftChange = viewModel::updateDraft,
            onSend = viewModel::sendDraft,
            onSendCurrentSong = viewModel::sendCurrentSong
        )
    }
}

@Composable
private fun ChatHeaderBar(
    peerUid: Long,
    peerUser: MessageUser?,
    onOpenProfile: () -> Unit
) {
    val displayName = peerUser?.nickname?.takeIf { it.isNotBlank() } ?: "用户 $peerUid"
    val buttonHover = remember { MutableInteractionSource() }
    val isButtonHovered by buttonHover.collectIsHoveredAsState()

    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f, fill = false)
                .clip(RoundedCornerShape(8.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onOpenProfile)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(peerUser?.avatarUrl, 34.dp, shape = CircleShape)
            Spacer(Modifier.width(10.dp))
            Text(
                text = displayName,
                color = DesktopColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(16.dp))
                .hoverable(buttonHover)
                .background(if (isButtonHovered) DesktopColors.Hover else DesktopColors.CardSurface)
                .border(1.dp, DesktopColors.Divider, RoundedCornerShape(16.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onOpenProfile)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Rounded.Person, null, tint = DesktopColors.TextGray, modifier = Modifier.size(15.dp))
            Text("查看主页", color = DesktopColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ChatMessageList(
    peerUid: Long,
    chatItems: List<PrivateChatItem>,
    peerAvatarUrl: String?,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    currentSongId: Long?,
    isPlaying: Boolean,
    onLoadMore: () -> Unit,
    onOpenProfile: () -> Unit,
    onPlaySong: (Long) -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit
) {
    val listState = rememberLazyListState()
    var initialScrollDone by remember(peerUid) { mutableStateOf(false) }
    var prevItems by remember(peerUid) { mutableStateOf<List<PrivateChatItem>>(emptyList()) }

    // 首次进入滚到底部；向上翻页时锚定原首条可见消息防止跳动；新发/收到消息时滚至底部
    LaunchedEffect(peerUid, chatItems) {
        if (chatItems.isEmpty()) {
            prevItems = emptyList()
            return@LaunchedEffect
        }
        if (!initialScrollDone) {
            listState.scrollToItem(chatItems.lastIndex)
            initialScrollDone = true
            prevItems = chatItems
            return@LaunchedEffect
        }
        val oldFirstId = prevItems.firstOrNull()?.message?.id
        val newFirstId = chatItems.first().message.id
        val oldLastId = prevItems.lastOrNull()?.message?.id
        val newLastId = chatItems.last().message.id

        if (oldFirstId != null && newFirstId != oldFirstId && chatItems.size > prevItems.size) {
            val anchorId = prevItems.getOrNull(listState.firstVisibleItemIndex)?.message?.id ?: oldFirstId
            val anchorOffset = listState.firstVisibleItemScrollOffset
            val newAnchorIndex = chatItems.indexOfFirst { it.message.id == anchorId }
            if (newAnchorIndex >= 0) {
                listState.scrollToItem(newAnchorIndex, anchorOffset)
            }
        } else if (oldLastId != null && newLastId != oldLastId) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val nearBottom = lastVisible >= prevItems.lastIndex - 2
            if (nearBottom || chatItems.last().isMine) {
                listState.animateScrollToItem(chatItems.lastIndex)
            }
        }
        prevItems = chatItems
    }

    val nearTop by remember(peerUid, hasMore, isLoadingMore) {
        derivedStateOf {
            initialScrollDone && hasMore && !isLoadingMore && listState.firstVisibleItemIndex <= 1
        }
    }
    LaunchedEffect(nearTop, chatItems) {
        if (nearTop) {
            // 让出帧，等待上方 scrollToItem 锚点恢复完成后再判定是否仍处于顶部
            delay(80)
            if (initialScrollDone && hasMore && !isLoadingMore && listState.firstVisibleItemIndex <= 1) {
                onLoadMore()
            }
        }
    }

    HoverScrollbarBox(listState, Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
        ) {
            if (isLoadingMore) {
                item(key = "history_loading") {
                    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = DesktopColors.Accent,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else if (!hasMore) {
                item(key = "history_end") {
                    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                        Text("已显示全部消息", color = DesktopColors.TextTertiary, fontSize = 11.sp)
                    }
                }
            }

            items(chatItems, key = { it.message.id }) { item ->
                ChatItemRow(
                    item = item,
                    peerAvatarUrl = peerAvatarUrl,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onOpenProfile = onOpenProfile,
                    onPlaySong = onPlaySong,
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenAlbum = onOpenAlbum
                )
            }
        }
    }
}

@Composable
private fun ChatItemRow(
    item: PrivateChatItem,
    peerAvatarUrl: String?,
    currentSongId: Long?,
    isPlaying: Boolean,
    onOpenProfile: () -> Unit,
    onPlaySong: (Long) -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit
) {
    val msg = item.message
    Column(Modifier.fillMaxWidth()) {
        if (item.showTimeDivider) {
            val timeText = formatChatTimeDivider(msg.time)
            if (timeText.isNotEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = timeText,
                        color = DesktopColors.TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(DesktopColors.Overlay.copy(alpha = 0.07f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        } else if (item.isGroupStart) {
            Spacer(Modifier.height(10.dp))
        } else {
            Spacer(Modifier.height(3.dp))
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val maxBubbleWidth = maxWidth * 0.70f
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (item.isMine) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (!item.isMine) {
                    if (item.isGroupEnd) {
                        Cover(
                            url = msg.fromUser?.avatarUrl?.takeIf { it.isNotBlank() } ?: peerAvatarUrl,
                            size = ChatAvatarSize,
                            shape = CircleShape,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onOpenProfile)
                        )
                    } else {
                        Spacer(Modifier.width(ChatAvatarSize))
                    }
                    Spacer(Modifier.width(8.dp))
                }

                MessageContentBlock(
                    item = item,
                    maxBubbleWidth = maxBubbleWidth,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onPlaySong = onPlaySong,
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenAlbum = onOpenAlbum
                )
            }
        }
    }
}

@Composable
private fun MessageContentBlock(
    item: PrivateChatItem,
    maxBubbleWidth: Dp,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySong: (Long) -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit
) {
    val msg = item.message
    val shape = bubbleShape(item.isMine, item.isGroupStart, item.isGroupEnd)

    if (msg.isUnsupported) {
        Text(
            text = "暂不支持显示该消息",
            color = DesktopColors.TextTertiary,
            fontSize = 12.sp,
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .clip(shape)
                .background(DesktopColors.Overlay.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
        return
    }

    val card = msg.card
    val shareCard = msg.shareCard
    val extraText = msg.text?.trim()?.takeIf { text ->
        text.isNotEmpty() && (card == null || !isRedundantShareText(text, card))
    }

    Column(
        horizontalAlignment = if (item.isMine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (card != null && shareCard != null) {
            RenderedShareCardBubble(
                card = card,
                shareCard = shareCard,
                isMine = item.isMine,
                maxWidth = maxBubbleWidth,
                currentSongId = currentSongId,
                isPlaying = isPlaying,
                onPlaySong = onPlaySong
            )
        } else if (card != null) {
            ShareCardBubble(
                card = card,
                isMine = item.isMine,
                shape = shape,
                maxWidth = maxBubbleWidth,
                currentSongId = currentSongId,
                isPlaying = isPlaying,
                onPlaySong = onPlaySong,
                onOpenPlaylist = onOpenPlaylist,
                onOpenAlbum = onOpenAlbum
            )
        }
        if (extraText != null) {
            if (card != null && shareCard != null) {
                SelectionContainer {
                    Text(
                        text = extraText,
                        color = DesktopColors.TextGray,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier
                            .widthIn(max = RenderedShareCardWidth.coerceAtMost(maxBubbleWidth))
                            .padding(horizontal = 4.dp)
                    )
                }
            } else {
                TextBubble(
                    text = extraText,
                    isMine = item.isMine,
                    shape = shape,
                    maxWidth = maxBubbleWidth
                )
            }
        }
    }
}

@Composable
private fun TextBubble(
    text: String,
    isMine: Boolean,
    shape: RoundedCornerShape,
    maxWidth: Dp
) {
    val bgColor = if (isMine) DesktopColors.Accent else DesktopColors.CardSurface
    val textColor = if (isMine) DesktopColors.OnAccent else DesktopColors.TextPrimary
    val borderModifier = if (isMine) Modifier else Modifier.border(1.dp, DesktopColors.Divider, shape)

    Box(
        Modifier.widthIn(max = maxWidth)
            .clip(shape)
            .background(bgColor)
            .then(borderModifier)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        SelectionContainer {
            Text(
                text = text,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ShareCardBubble(
    card: PrivateCard,
    isMine: Boolean,
    shape: RoundedCornerShape,
    maxWidth: Dp,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySong: (Long) -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenAlbum: (Long, String) -> Unit
) {
    val isSong = card.kind == PrivateCardKind.Song
    val isCurrentSong = isSong && card.resourceId > 0L && currentSongId == card.resourceId
    val isThisSongPlaying = isCurrentSong && isPlaying
    val clickAction: (() -> Unit)? = when (card.kind) {
        PrivateCardKind.Song -> card.resourceId.takeIf { it > 0L }?.let { id -> { onPlaySong(id) } }
        PrivateCardKind.Playlist -> card.resourceId.takeIf { it > 0L }?.let { id -> { onOpenPlaylist(id, card.title) } }
        PrivateCardKind.Album -> card.resourceId.takeIf { it > 0L }?.let { id -> { onOpenAlbum(id, card.title) } }
        PrivateCardKind.Mv, PrivateCardKind.General -> null
    }

    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val containerColor = if (isMine) DesktopColors.AccentContainer else DesktopColors.CardSurface
    val borderColor = when {
        isThisSongPlaying -> DesktopColors.Accent.copy(alpha = 0.55f)
        isMine -> DesktopColors.Accent.copy(alpha = if (hovered && clickAction != null) 0.35f else 0.18f)
        else -> if (hovered && clickAction != null) DesktopColors.Outline else DesktopColors.Divider
    }

    Row(
        Modifier.widthIn(min = 210.dp, max = maxWidth.coerceAtMost(320.dp))
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .hoverable(hoverSource)
            .then(
                if (clickAction != null) {
                    Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = clickAction)
                } else {
                    Modifier
                }
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(CardCoverSize).clip(RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Cover(card.cover, CardCoverSize, shape = RoundedCornerShape(9.dp))
            if (isSong) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (isThisSongPlaying) 0.48f else 0.32f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isThisSongPlaying) {
                        PlayingEqualizerBars(color = Color.White)
                    } else {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.88f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                "播放",
                                tint = Color(0xFF141416),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = card.title.ifBlank { card.kind.label },
                color = if (isThisSongPlaying) DesktopColors.AccentText else DesktopColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (card.subtitle.isNotBlank()) {
                Text(
                    text = card.subtitle,
                    color = DesktopColors.TextGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Text(
                text = if (isThisSongPlaying) "单曲 · 正在播放" else card.kind.label,
                color = if (isThisSongPlaying) DesktopColors.AccentText else DesktopColors.TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun RenderedShareCardBubble(
    card: PrivateCard,
    shareCard: ShareCardMessage,
    isMine: Boolean,
    maxWidth: Dp,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySong: (Long) -> Unit
) {
    val canPlay = card.resourceId > 0L
    val isThisSongPlaying = canPlay && currentSongId == card.resourceId && isPlaying
    val art = rememberShareCardArt(card.cover, RenderedShareCardCoverSize)
    val content = remember(shareCard, card.title, card.subtitle) {
        shareCard.content(title = card.title, artist = card.subtitle)
    }
    val typography = remember(shareCard) { shareCard.typography() }
    val cardShape = RoundedCornerShape(RenderedShareCardRadius)
    val cardWidth = RenderedShareCardWidth.coerceAtMost(maxWidth)
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val borderColor = when {
        isThisSongPlaying -> DesktopColors.Accent
        hovered && canPlay -> DesktopColors.Outline
        isMine -> DesktopColors.Accent.copy(alpha = 0.28f)
        else -> DesktopColors.Divider
    }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(shareCard.ratio.aspect)
            .clip(cardShape)
            .border(if (isThisSongPlaying) 1.5.dp else 1.dp, borderColor, cardShape)
            .hoverable(hoverSource)
            .then(
                if (canPlay) {
                    Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onPlaySong(card.resourceId) }
                } else {
                    Modifier
                }
            )
    ) {
        ShareCard(
            content = content,
            style = shareCard.style,
            background = shareCard.background,
            ratio = shareCard.ratio,
            art = art,
            modifier = Modifier.fillMaxSize(),
            typography = typography
        )
        if (isThisSongPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DesktopColors.Accent)
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                PlayingEqualizerBars(color = DesktopColors.OnAccent)
            }
        }
    }
}

@Composable
private fun PlayingEqualizerBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "eqBars")
    val bar1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq3"
    )
    Row(
        Modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(bar1, bar2, bar3).forEach { fraction ->
            Box(
                Modifier.width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun ChatComposerBar(
    draft: String,
    sending: Boolean,
    shareableNowPlaying: NowPlaying?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendCurrentSong: () -> Unit
) {
    var inputFocused by remember { mutableStateOf(false) }
    val canSend = draft.isNotBlank() && !sending
    val containerShape = RoundedCornerShape(ComposerContainerRadius)
    val borderColor by animateColorAsState(
        targetValue = if (inputFocused) DesktopColors.TextPrimary else DesktopColors.Divider,
        animationSpec = uiTween(150),
        label = "composerBorder"
    )

    // 保留最后一次非空曲目，确保胶囊淡出期间仍有内容可渲染
    var displaySong by remember { mutableStateOf(shareableNowPlaying) }
    if (shareableNowPlaying != null) {
        displaySong = shareableNowPlaying
    }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // 上限只随窗口宽度收窄，避免输入文字时挤压胶囊
        val pillMaxWidth = (maxWidth - 172.dp).coerceIn(96.dp, NowPlayingPillMaxWidth)

        Row(
            Modifier.fillMaxWidth()
                .clip(containerShape)
                .background(DesktopColors.CardSurface)
                .border(1.dp, borderColor, containerShape)
                .padding(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AnimatedVisibility(
                visible = shareableNowPlaying != null,
                enter = uiEnter(fadeIn(uiTween(180)) + expandHorizontally(uiTween(180), expandFrom = Alignment.Start)),
                exit = uiExit(fadeOut(uiTween(150)) + shrinkHorizontally(uiTween(150), shrinkTowards = Alignment.Start))
            ) {
                displaySong?.let { now ->
                    NowPlayingSharePill(
                        nowPlaying = now,
                        maxWidth = pillMaxWidth,
                        enabled = !sending,
                        onClick = onSendCurrentSong
                    )
                }
            }

            Box(
                Modifier.weight(1f)
                    .heightIn(min = ComposerButtonSize, max = 112.dp)
                    // 中文字体行高偏大，上下内边距收窄靠最小高度撑开，单行时才能与胶囊、按钮同高
                    .padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = "发送私信...（Enter 发送，Shift+Enter 换行）",
                        color = DesktopColors.TextTertiary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    maxLines = 4,
                    textStyle = TextStyle(color = DesktopColors.TextPrimary, fontSize = 13.sp, lineHeight = 19.sp),
                    cursorBrush = SolidColor(DesktopColors.TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                        .trackTextInputFocus()
                        .onFocusChanged { inputFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                            if (event.type == KeyEventType.KeyDown && isEnter && !event.isShiftPressed) {
                                if (canSend) onSend()
                                true
                            } else {
                                false
                            }
                        }
                )
            }

            Box(
                Modifier.size(ComposerButtonSize)
                    .clip(CircleShape)
                    .background(if (canSend) DesktopColors.Accent else DesktopColors.Surface)
                    .then(
                        if (canSend) {
                            Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onSend)
                        } else {
                            Modifier.border(1.dp, DesktopColors.Divider, CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        color = DesktopColors.TextTertiary,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        "发送",
                        tint = if (canSend) DesktopColors.OnAccent else DesktopColors.TextTertiary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingSharePill(
    nowPlaying: NowPlaying,
    maxWidth: Dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val pillShape = RoundedCornerShape(ComposerButtonSize / 2)

    DesktopTooltip("把正在播放的歌分享给对方", side = TooltipSide.Top) {
        Row(
            Modifier
                .height(ComposerButtonSize)
                .widthIn(max = maxWidth)
                .clip(pillShape)
                .background(DesktopColors.Surface)
                .background(if (hovered && enabled) DesktopColors.Hover else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (hovered && enabled) DesktopColors.Outline else DesktopColors.Divider,
                    shape = pillShape
                )
                .hoverable(hoverSource)
                .then(
                    if (enabled) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer { alpha = if (enabled) 1f else 0.58f }
                .padding(start = 6.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Cover(
                url = nowPlaying.artworkUri,
                size = NowPlayingPillCoverSize,
                shape = RoundedCornerShape(6.dp)
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = DesktopColors.TextPrimary, fontWeight = FontWeight.SemiBold)) {
                        append(nowPlaying.title.ifBlank { "未知歌曲" })
                    }
                    if (nowPlaying.artist.isNotBlank()) {
                        withStyle(SpanStyle(color = DesktopColors.TextTertiary, fontWeight = FontWeight.Normal)) {
                            append(" · ${nowPlaying.artist}")
                        }
                    }
                },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun bubbleShape(isMine: Boolean, isGroupStart: Boolean, isGroupEnd: Boolean): RoundedCornerShape {
    val outer = OuterBubbleRadius
    val inner = InnerBubbleRadius
    return if (isMine) {
        RoundedCornerShape(
            topStart = outer,
            bottomStart = outer,
            topEnd = if (isGroupStart) outer else inner,
            bottomEnd = if (isGroupEnd) outer else inner
        )
    } else {
        RoundedCornerShape(
            topStart = if (isGroupStart) outer else inner,
            bottomStart = if (isGroupEnd) outer else inner,
            topEnd = outer,
            bottomEnd = outer
        )
    }
}
