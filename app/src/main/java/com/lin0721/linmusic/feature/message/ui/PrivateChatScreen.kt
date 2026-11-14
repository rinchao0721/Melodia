package com.lin0721.linmusic.feature.message.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.SuppressMiniPlayer
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.share.ShareCardMessage
import com.lin0721.linmusic.core.share.ui.ShareCard
import com.lin0721.linmusic.core.ui.components.AdaptiveContentWidth
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.PlaceholderTextField
import com.lin0721.linmusic.core.ui.components.PlayingEqualizerBars
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaSystemBarsConsumed
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.melodiaNavigationBarBottomPadding
import com.lin0721.linmusic.core.ui.theme.uiEnter
import com.lin0721.linmusic.core.ui.theme.uiExit
import com.lin0721.linmusic.core.ui.theme.uiTween
import com.lin0721.linmusic.feature.cloud.domain.formatUploadedAt
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.message.domain.PrivateCard
import com.lin0721.linmusic.feature.message.domain.PrivateCardKind
import com.lin0721.linmusic.feature.player.share.rememberShareCardArt
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val BubbleBigRadius = 16.dp
private val BubbleSmallRadius = 5.dp
private val ChatAvatarSize = 34.dp

@Composable
fun PrivateChatScreen(
    userId: Long,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    onPlaylistClick: (id: Long, isAlbum: Boolean) -> Unit,
    viewModel: PrivateChatViewModel = koinViewModel(key = "private_chat_$userId"),
    userPreferences: UserPreferences = koinInject()
) {
    SuppressMiniPlayer(active = true)

    val state by viewModel.state.collectAsStateWithLifecycle()
    val chatItems by viewModel.chatItems.collectAsStateWithLifecycle()
    val peerUser by viewModel.peerUser.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val shareableNowPlaying by viewModel.shareableNowPlaying.collectAsStateWithLifecycle()
    val selfProfile by userPreferences.userProfile.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(userId) {
        viewModel.open(userId, initialUser = null)
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    Scaffold(
        topBar = {
            PrivateChatTopBar(
                peerUser = peerUser,
                onBack = onBack,
                onPeerClick = { onUserClick(userId) }
            )
        },
        containerColor = MelodiaColors.PageFill,
        // TopAppBar 已避让状态栏；只保留顶部与横向 inset，避免 Scaffold 底部再次计入导航栏高度而与 bottomOverlayInset 叠加
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        AdaptiveContentWidth(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (val current = state) {
                        ChatState.Loading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        is ChatState.Error -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            ErrorState(message = current.message, onRetry = { viewModel.retry() })
                        }

                        ChatState.Ready -> {
                            if (chatItems.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyState(
                                        icon = Icons.Rounded.ChatBubbleOutline,
                                        title = "还没有私信消息",
                                        subtitle = "发送一条消息开始聊天吧"
                                    )
                                }
                            } else {
                                PrivateChatMessageList(
                                    chatItems = chatItems,
                                    peerUser = peerUser,
                                    selfAvatarUrl = selfProfile?.avatarUrl.orEmpty(),
                                    hasMore = hasMore,
                                    isLoadingMore = isLoadingMore,
                                    nowPlayingSongId = nowPlaying?.songId,
                                    isPlaying = isPlaying,
                                    onLoadMore = { viewModel.loadMore() },
                                    onPeerClick = { onUserClick(userId) },
                                    onSongClick = { songId -> viewModel.playSong(songId) },
                                    onPlaylistClick = onPlaylistClick
                                )
                            }
                        }
                    }
                }

                PrivateChatComposer(
                    draft = draft,
                    sending = sending,
                    shareableNowPlaying = shareableNowPlaying,
                    onDraftChange = viewModel::updateDraft,
                    onSendText = viewModel::sendDraft,
                    onSendCurrentSong = viewModel::sendCurrentSong
                )
            }
        }
    }
}

// 用 TopAppBar 自带的状态栏避让，和其他二级页一致
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivateChatTopBar(
    peerUser: MessageUser?,
    onBack: () -> Unit,
    onPeerClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onPeerClick)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = peerUser?.avatarUrl?.takeIf { it.isNotBlank() }?.let { "$it?param=120y120" },
                        contentDescription = peerUser?.nickname,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MelodiaColors.Fill),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = peerUser?.nickname?.takeIf { it.isNotBlank() } ?: "私信",
                        color = MelodiaColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                MelodiaIconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MelodiaColors.TextPrimary
                    )
                }
            },
            actions = {
                MelodiaIconButton(onClick = onPeerClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "对方主页",
                        tint = MelodiaColors.TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MelodiaColors.PageFill)
        )
        HorizontalDivider(color = MelodiaColors.Divider)
    }
}

@Composable
private fun PrivateChatMessageList(
    chatItems: List<PrivateChatItem>,
    peerUser: MessageUser?,
    selfAvatarUrl: String,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    nowPlayingSongId: Long?,
    isPlaying: Boolean,
    onLoadMore: () -> Unit,
    onPeerClick: () -> Unit,
    onSongClick: (Long) -> Unit,
    onPlaylistClick: (id: Long, isAlbum: Boolean) -> Unit
) {
    // reverseLayout 保证打开即落在最新消息（index 0）底部，上滑接近列表尾部时触发 loadMore
    val reversedItems = remember(chatItems) { chatItems.asReversed() }
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(hasMore, isLoadingMore, reversedItems.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            reversedItems.isNotEmpty() && lastVisible >= reversedItems.size - 3 && hasMore && !isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    val newestId = chatItems.lastOrNull()?.message?.id
    var lastSeenNewestId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(newestId) {
        if (newestId != null) {
            val prev = lastSeenNewestId
            lastSeenNewestId = newestId
            if (prev != null && prev != newestId && listState.firstVisibleItemIndex <= 2) {
                listState.animateScrollToItem(0)
            }
        }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        modifier = Modifier.fillMaxSize()
    ) {
        items(reversedItems, key = { it.message.id }) { item ->
            // reverseLayout 只反转条目的排列顺序，Column 内部仍按从上到下渲染，时间胶囊放在气泡上方
            Column(modifier = Modifier.fillMaxWidth()) {
                if (item.showTimeDivider) {
                    TimeDividerCapsule(time = item.message.time)
                } else {
                    Spacer(modifier = Modifier.height(if (item.isGroupStart) 8.dp else 3.dp))
                }
                PrivateChatMessageRow(
                    item = item,
                    peerAvatarUrl = peerUser?.avatarUrl?.takeIf { it.isNotBlank() }
                        ?: item.message.fromUser?.avatarUrl.orEmpty(),
                    selfAvatarUrl = item.message.fromUser?.avatarUrl?.takeIf { it.isNotBlank() }
                        ?: selfAvatarUrl,
                    nowPlayingSongId = nowPlayingSongId,
                    isPlaying = isPlaying,
                    onPeerClick = onPeerClick,
                    onSongClick = onSongClick,
                    onPlaylistClick = onPlaylistClick
                )
            }
        }

        if (isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MelodiaSpacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeDividerCapsule(time: Long) {
    val label = formatUploadedAt(time)
    if (label.isBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MelodiaColors.CardSurface)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = label,
                color = MelodiaColors.TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PrivateChatMessageRow(
    item: PrivateChatItem,
    peerAvatarUrl: String,
    selfAvatarUrl: String,
    nowPlayingSongId: Long?,
    isPlaying: Boolean,
    onPeerClick: () -> Unit,
    onSongClick: (Long) -> Unit,
    onPlaylistClick: (id: Long, isAlbum: Boolean) -> Unit
) {
    val message = item.message
    val bubbleShape = remember(item.isMine, item.isGroupStart, item.isGroupEnd) {
        if (item.isMine) {
            RoundedCornerShape(
                topStart = BubbleBigRadius,
                bottomStart = BubbleBigRadius,
                topEnd = if (item.isGroupStart) BubbleBigRadius else BubbleSmallRadius,
                bottomEnd = if (item.isGroupEnd) BubbleBigRadius else BubbleSmallRadius
            )
        } else {
            RoundedCornerShape(
                topStart = if (item.isGroupStart) BubbleBigRadius else BubbleSmallRadius,
                bottomStart = if (item.isGroupEnd) BubbleBigRadius else BubbleSmallRadius,
                topEnd = BubbleBigRadius,
                bottomEnd = BubbleBigRadius
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!item.isMine) {
            ChatAvatarSlot(
                visible = item.isGroupEnd,
                avatarUrl = peerAvatarUrl,
                onClick = onPeerClick
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (item.isMine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.widthIn(max = 272.dp)
        ) {
            when {
                message.isUnsupported -> {
                    Box(
                        modifier = Modifier
                            .clip(bubbleShape)
                            .background(MelodiaColors.CardSurface)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "暂不支持显示该消息",
                            color = MelodiaColors.TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }

                message.card != null -> {
                    val card = message.card!!
                    val shareCard = message.shareCard
                    if (shareCard != null) {
                        val noteText = message.text?.trim()?.takeIf {
                            it.isNotEmpty() && !isBoilerplateCardText(it, card)
                        }
                        PrivateShareCardBubble(
                            card = card,
                            shareCard = shareCard,
                            noteText = noteText,
                            isMine = item.isMine,
                            isPlayingSong = card.kind == PrivateCardKind.Song &&
                                card.resourceId > 0L &&
                                nowPlayingSongId == card.resourceId,
                            isPlaying = isPlaying,
                            onSongClick = onSongClick
                        )
                    } else {
                        val rawText = message.text?.trim().orEmpty()
                        if (rawText.isNotEmpty() && !isBoilerplateCardText(rawText, card)) {
                            TextMessageBubble(
                                text = rawText,
                                isMine = item.isMine,
                                shape = bubbleShape
                            )
                        }
                        PrivateCardBubble(
                            card = card,
                            isMine = item.isMine,
                            shape = bubbleShape,
                            isPlayingSong = card.kind == PrivateCardKind.Song &&
                                card.resourceId > 0L &&
                                nowPlayingSongId == card.resourceId,
                            isPlaying = isPlaying,
                            onSongClick = onSongClick,
                            onPlaylistClick = onPlaylistClick
                        )
                    }
                }

                !message.text.isNullOrBlank() -> {
                    TextMessageBubble(
                        text = message.text!!.trim(),
                        isMine = item.isMine,
                        shape = bubbleShape
                    )
                }
            }
        }

        if (item.isMine) {
            Spacer(modifier = Modifier.width(8.dp))
            ChatAvatarSlot(
                visible = item.isGroupEnd,
                avatarUrl = selfAvatarUrl,
                onClick = null
            )
        }
    }
}

@Composable
private fun ChatAvatarSlot(
    visible: Boolean,
    avatarUrl: String,
    onClick: (() -> Unit)?
) {
    if (!visible) {
        Spacer(modifier = Modifier.size(ChatAvatarSize))
        return
    }
    AsyncImage(
        model = avatarUrl.takeIf { it.isNotBlank() }?.let { "$it?param=100y100" },
        contentDescription = null,
        modifier = Modifier
            .size(ChatAvatarSize)
            .clip(CircleShape)
            .background(MelodiaColors.Fill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun TextMessageBubble(
    text: String,
    isMine: Boolean,
    shape: RoundedCornerShape
) {
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (isMine) MelodiaColors.Accent else MelodiaColors.CardSurface)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            color = if (isMine) MelodiaColors.OnAccent else MelodiaColors.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun PrivateShareCardBubble(
    card: PrivateCard,
    shareCard: ShareCardMessage,
    noteText: String?,
    isMine: Boolean,
    isPlayingSong: Boolean,
    isPlaying: Boolean,
    onSongClick: (Long) -> Unit
) {
    val cardShape = RoundedCornerShape(12.dp)
    val art = rememberShareCardArt(coverUrl = card.cover, coverSizePx = 640)
    val content = remember(card.title, card.subtitle, card.kind, shareCard) {
        shareCard.content(
            title = card.title.ifBlank { card.kind.label },
            artist = card.subtitle
        )
    }
    val typography = remember(shareCard) { shareCard.typography() }
    val clickableModifier = if (card.resourceId > 0L) {
        Modifier.pressable(MelodiaPress.Row, shape = cardShape) { onSongClick(card.resourceId) }
    } else {
        Modifier
    }

    Column(
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(shareCard.ratio.aspect)
                .clip(cardShape)
                .then(clickableModifier)
                .border(1.dp, MelodiaColors.Divider, cardShape)
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
            if (isPlayingSong) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MelodiaColors.Scrim),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.scale(0.8f)) {
                        PlayingEqualizerBars(color = MelodiaColors.OnPlayer, animate = isPlaying)
                    }
                }
            }
        }
        if (!noteText.isNullOrBlank()) {
            Text(
                text = noteText,
                color = MelodiaColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
    }
}

@Composable
private fun PrivateCardBubble(
    card: PrivateCard,
    isMine: Boolean,
    shape: RoundedCornerShape,
    isPlayingSong: Boolean,
    isPlaying: Boolean,
    onSongClick: (Long) -> Unit,
    onPlaylistClick: (id: Long, isAlbum: Boolean) -> Unit
) {
    val clickableModifier = when (card.kind) {
        PrivateCardKind.Song -> if (card.resourceId > 0L) {
            Modifier.pressable(MelodiaPress.Row, shape = shape) { onSongClick(card.resourceId) }
        } else {
            Modifier
        }
        PrivateCardKind.Playlist -> if (card.resourceId > 0L) {
            Modifier.pressable(MelodiaPress.Row, shape = shape) { onPlaylistClick(card.resourceId, false) }
        } else {
            Modifier
        }
        PrivateCardKind.Album -> if (card.resourceId > 0L) {
            Modifier.pressable(MelodiaPress.Row, shape = shape) { onPlaylistClick(card.resourceId, true) }
        } else {
            Modifier
        }
        PrivateCardKind.Mv, PrivateCardKind.General -> Modifier
    }

    val containerColor = if (isMine) MelodiaColors.AccentContainer else MelodiaColors.CardSurface
    val borderModifier = if (isMine) {
        Modifier
    } else {
        Modifier.border(1.dp, MelodiaColors.Divider, shape)
    }

    Row(
        modifier = Modifier
            .widthIn(min = 212.dp, max = 264.dp)
            .clip(shape)
            .then(clickableModifier)
            .background(containerColor)
            .then(borderModifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(RadiusCompact))
                .background(MelodiaColors.CoverPlaceholder),
            contentAlignment = Alignment.Center
        ) {
            val coverUrl = card.cover?.takeIf { it.isNotBlank() }?.let { url ->
                if (url.startsWith("http://") || url.startsWith("https://")) "$url?param=120y120" else url
            }
            if (coverUrl != null) {
                SubcomposeAsyncImage(
                    model = coverUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CardFallbackCover(card.kind) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CardFallbackCover(card.kind)
            }

            if (card.kind == PrivateCardKind.Song) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MelodiaColors.Scrim),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlayingSong) {
                        Box(modifier = Modifier.scale(0.75f)) {
                            PlayingEqualizerBars(color = MelodiaColors.OnPlayer, animate = isPlaying)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "播放",
                            tint = MelodiaColors.OnPlayer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.title.ifBlank { card.kind.label },
                color = if (isPlayingSong) MelodiaColors.AccentText else MelodiaColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (card.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = card.subtitle,
                    color = MelodiaColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isPlayingSong) "${card.kind.label} · 正在播放" else card.kind.label,
                color = if (isPlayingSong) MelodiaColors.AccentText else MelodiaColors.TextTertiary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CardFallbackCover(kind: PrivateCardKind) {
    val icon = when (kind) {
        PrivateCardKind.Song -> Icons.Rounded.MusicNote
        PrivateCardKind.Playlist -> Icons.Rounded.QueueMusic
        PrivateCardKind.Album -> Icons.Rounded.Album
        PrivateCardKind.Mv -> Icons.Rounded.Videocam
        PrivateCardKind.General -> Icons.Rounded.Share
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MelodiaColors.CoverPlaceholder),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MelodiaColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun isBoilerplateCardText(text: String, card: PrivateCard): Boolean {
    if (text == card.title) return true
    return when (card.kind) {
        PrivateCardKind.Song -> text == "分享单曲" || text.startsWith("分享单曲：") || text.startsWith("分享单曲:")
        PrivateCardKind.Playlist -> text == "分享歌单" || text.startsWith("分享歌单：") || text.startsWith("分享歌单:")
        PrivateCardKind.Album -> text == "分享专辑" || text.startsWith("分享专辑：") || text.startsWith("分享专辑:")
        PrivateCardKind.Mv -> text == "分享MV" || text.startsWith("分享MV：") || text.startsWith("分享MV:")
        PrivateCardKind.General -> text == "分享" || text == "分享分享"
    }
}

@Composable
private fun PrivateChatComposer(
    draft: String,
    sending: Boolean,
    shareableNowPlaying: NowPlaying?,
    onDraftChange: (String) -> Unit,
    onSendText: () -> Unit,
    onSendCurrentSong: () -> Unit
) {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val bottomOverlayInset = LocalBottomOverlayInset.current
    val imePadding = if (imeBottom > 0) {
        val rawImePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        // 平板双卡片模式下外层已消费系统栏高度，需扣掉键盘内包含的手势条高度
        if (LocalMelodiaSystemBarsConsumed.current) {
            (rawImePadding - WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()).coerceAtLeast(0.dp)
        } else {
            rawImePadding
        }
    } else {
        0.dp
    }
    val navBarsPadding = melodiaNavigationBarBottomPadding()
    val restingPadding = if (bottomOverlayInset > 0.dp) bottomOverlayInset else navBarsPadding
    // 键盘收起动画末段高度会低于底栏，取较大值避免先沉到底栏下面再弹回
    val bottomPadding = maxOf(imePadding, restingPadding)

    val canSend = draft.isNotBlank() && !sending
    val focusRequester = remember { FocusRequester() }

    // 保留退场动画期间的歌曲信息，避免刚清空播放态时胶囊先闪白再缩回
    var heldNowPlaying by remember { mutableStateOf(shareableNowPlaying) }
    if (shareableNowPlaying != null) {
        heldNowPlaying = shareableNowPlaying
    }

    val capsuleShape = RoundedCornerShape(26.dp)
    val ambientShadow = if (MelodiaColors.IsDark) MelodiaColors.ShadowAmbient.copy(alpha = 0.4f) else MelodiaColors.ShadowAmbient
    val spotShadow = if (MelodiaColors.IsDark) MelodiaColors.ShadowSpot.copy(alpha = 0.4f) else MelodiaColors.ShadowSpot
    val sendBgColor by animateColorAsState(
        targetValue = if (canSend) MelodiaColors.Accent else Color.Transparent,
        animationSpec = uiTween(150),
        label = "private_chat_send_bg"
    )
    val sendIconTint by animateColorAsState(
        targetValue = if (canSend) MelodiaColors.OnAccent else MelodiaColors.TextTertiary,
        animationSpec = uiTween(150),
        label = "private_chat_send_tint"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(
                start = MelodiaSpacing.md,
                end = MelodiaSpacing.md,
                top = MelodiaSpacing.sm,
                bottom = MelodiaSpacing.sm + bottomPadding
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = capsuleShape,
                    ambientColor = ambientShadow,
                    spotColor = spotShadow
                )
                .clip(capsuleShape)
                .background(MelodiaColors.CardSurface)
                .border(1.dp, MelodiaColors.Outline, capsuleShape)
                .padding(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AnimatedVisibility(
                visible = shareableNowPlaying != null,
                enter = uiEnter(fadeIn() + expandHorizontally(expandFrom = Alignment.Start)),
                exit = uiExit(fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start))
            ) {
                heldNowPlaying?.let { now ->
                    NowPlayingSharePill(
                        nowPlaying = now,
                        collapsed = draft.isNotEmpty(),
                        enabled = !sending && shareableNowPlaying != null,
                        onClick = onSendCurrentSong,
                        modifier = Modifier.padding(end = MelodiaSpacing.xs)
                    )
                }
            }

            PlaceholderTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = "发私信…",
                modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                singleLine = false,
                maxLines = 5,
                shape = RoundedCornerShape(18.dp),
                focusRequester = focusRequester,
                containerColor = Color.Transparent,
                textColor = MelodiaColors.TextPrimary,
                placeholderColor = MelodiaColors.TextTertiary,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSendText() }),
                // 单行时与胶囊、发送按钮同为 36dp，底部对齐后上下间距一致
                multiLinePadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.width(MelodiaSpacing.xs))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pressable(
                        style = MelodiaPress.Pill,
                        shape = CircleShape,
                        enabled = canSend,
                        role = Role.Button,
                        onClick = onSendText
                    )
                    .background(sendBgColor),
                contentAlignment = Alignment.Center
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        color = MelodiaColors.TextTertiary,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "发送",
                        tint = sendIconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingSharePill(
    nowPlaying: NowPlaying,
    collapsed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .semantics(mergeDescendants = true) {
                contentDescription = "分享正在播放：${nowPlaying.title}"
            }
            .pressable(
                style = MelodiaPress.Pill,
                shape = CircleShape,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .background(MelodiaColors.Fill)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = nowPlaying.artworkUri?.takeIf { it.isNotBlank() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CardFallbackCover(PrivateCardKind.Song) },
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(5.dp))
        )

        AnimatedVisibility(
            visible = !collapsed,
            enter = uiEnter(fadeIn() + expandHorizontally(expandFrom = Alignment.Start)),
            exit = uiExit(fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = nowPlaying.title,
                    color = MelodiaColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}
