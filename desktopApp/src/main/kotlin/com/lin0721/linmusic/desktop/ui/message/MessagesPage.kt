package com.lin0721.linmusic.desktop.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.badge.DraggableUnreadBadge
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.cloud.domain.formatUploadedAt
import com.lin0721.linmusic.feature.message.domain.CommentMessage
import com.lin0721.linmusic.feature.message.domain.ForwardMessage
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.message.domain.NoticeItem
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import com.lin0721.linmusic.feature.message.ui.MessageListState
import com.lin0721.linmusic.feature.message.ui.MessagePager
import com.lin0721.linmusic.feature.message.ui.MessageTab
import com.lin0721.linmusic.feature.message.ui.MessageViewModel
import com.lin0721.linmusic.feature.message.ui.PrivateChatViewModel
import com.lin0721.linmusic.feature.message.ui.PrivateUnreadMonitor

private val LeftPaneWidth = 260.dp
private val ConversationAvatarSize = 40.dp
private val FeedAvatarSize = 40.dp
private const val LOAD_MORE_THRESHOLD = 3

@Composable
fun MessagesPage(
    initialPeerUid: Long?,
    initialPeerName: String,
    initialPeerAvatar: String,
    messageViewModel: MessageViewModel,
    chatViewModel: PrivateChatViewModel,
    unreadMonitor: PrivateUnreadMonitor,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val isLoggedIn by messageViewModel.isLoggedIn.collectAsState()
    val selectedTab by messageViewModel.selectedTab.collectAsState()
    val unreadCount by unreadMonitor.unreadCount.collectAsState()
    val songNames by messageViewModel.songNames.collectAsState()
    val privateState by messageViewModel.privatePager.state.collectAsState()

    var selectedPeerUid by rememberSaveable(initialPeerUid) { mutableStateOf(initialPeerUid) }
    var peerHint by remember(initialPeerUid) {
        mutableStateOf(
            initialPeerUid?.let { MessageUser(it, initialPeerName, initialPeerAvatar) }
        )
    }

    LaunchedEffect(isLoggedIn, selectedTab) {
        if (isLoggedIn == true) {
            messageViewModel.ensureLoaded(selectedTab)
        }
    }

    LaunchedEffect(isLoggedIn, selectedPeerUid) {
        val uid = selectedPeerUid
        if (isLoggedIn == true && uid != null && uid > 0L) {
            val fromList = (privateState as? MessageListState.Success)?.items
                ?.firstOrNull { it.user.uid == uid }?.user
            chatViewModel.open(uid, fromList ?: peerHint?.takeIf { it.uid == uid })
        }
    }

    when (isLoggedIn) {
        null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesktopColors.Accent)
        }

        false -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.MailOutline, null, tint = DesktopColors.TextGray, modifier = Modifier.size(40.dp))
                Text("登录后查看消息", color = DesktopColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("私信、通知、@我与评论回复仅对登录账号开放", color = DesktopColors.TextGray, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .background(DesktopColors.Accent)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { navigator.openLogin() }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text("去登录", color = DesktopColors.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        true -> Row(modifier.fillMaxSize()) {
            // 左栏：标题 + 分段控件 + 私信会话列表（其他 Tab 显示频道概要）
            Column(Modifier.width(LeftPaneWidth).fillMaxHeight()) {
                LeftPaneHeader(
                    selectedTab = selectedTab,
                    unreadCount = unreadCount,
                    onSelectTab = messageViewModel::selectTab,
                    onDismissUnread = unreadMonitor::markAllRead
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = DesktopColors.Divider)

                when (selectedTab) {
                    MessageTab.PRIVATE -> PrivateConversationList(
                        pager = messageViewModel.privatePager,
                        selectedPeerUid = selectedPeerUid,
                        onSelectConversation = { conv ->
                            selectedPeerUid = conv.user.uid
                            peerHint = conv.user
                        },
                        onDismissUnread = unreadMonitor::markConversationRead,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )

                    MessageTab.NOTICE -> ChannelOverviewPanel(
                        tab = MessageTab.NOTICE,
                        description = "查看赞了你的评论、赞了你的动态和收藏你歌单的提醒。",
                        pager = messageViewModel.noticePager,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )

                    MessageTab.FORWARD -> ChannelOverviewPanel(
                        tab = MessageTab.FORWARD,
                        description = "查看其他用户在动态或评论中 @ 你的内容。",
                        pager = messageViewModel.forwardPager,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )

                    MessageTab.COMMENT -> ChannelOverviewPanel(
                        tab = MessageTab.COMMENT,
                        description = "查看收到的评论回复，点击头像可前往对方个人主页。",
                        pager = messageViewModel.commentPager,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }

            Box(Modifier.width(1.dp).fillMaxHeight().background(DesktopColors.Divider))

            // 右栏：私信聊天区或通知/@我/评论列表
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (selectedTab) {
                    MessageTab.PRIVATE -> {
                        val uid = selectedPeerUid
                        if (uid == null || uid <= 0L) {
                            EmptyPrivateChatState(Modifier.fillMaxSize())
                        } else {
                            PrivateChatPane(
                                viewModel = chatViewModel,
                                peerUid = uid,
                                onOpenProfile = navigator.openProfile,
                                onOpenPlaylist = navigator.openPlaylist,
                                onOpenAlbum = navigator.openAlbum,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    MessageTab.NOTICE -> MessageFeedPane(
                        title = "通知",
                        pager = messageViewModel.noticePager,
                        emptyTitle = "还没有收到通知",
                        keyOf = { it.id },
                        modifier = Modifier.fillMaxSize()
                    ) { item ->
                        NoticeRow(item, songNames, navigator.openProfile)
                    }

                    MessageTab.FORWARD -> MessageFeedPane(
                        title = "@我",
                        pager = messageViewModel.forwardPager,
                        emptyTitle = "还没有人@你",
                        keyOf = { it.key },
                        modifier = Modifier.fillMaxSize()
                    ) { item ->
                        ForwardMessageRow(item, navigator.openProfile)
                    }

                    MessageTab.COMMENT -> MessageFeedPane(
                        title = "评论回复",
                        pager = messageViewModel.commentPager,
                        emptyTitle = "还没有收到评论回复",
                        keyOf = { it.commentId },
                        modifier = Modifier.fillMaxSize()
                    ) { item ->
                        CommentMessageRow(item, navigator.openProfile)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeftPaneHeader(
    selectedTab: MessageTab,
    unreadCount: Int,
    onSelectTab: (MessageTab) -> Unit,
    onDismissUnread: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
        Text(
            text = "消息",
            color = DesktopColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DesktopColors.CardSurface)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MessageTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val tabShape = RoundedCornerShape(7.dp)
                val activeFill = if (DesktopColors.IsDark) DesktopColors.Surface else DesktopColors.PopupSurface
                val hoverSource = remember { MutableInteractionSource() }
                val hovered by hoverSource.collectIsHoveredAsState()

                Box(
                    Modifier.weight(1f)
                        .height(28.dp)
                        .clip(tabShape)
                        .hoverable(hoverSource)
                        .then(
                            if (selected) {
                                Modifier.background(activeFill).then(
                                    if (DesktopColors.IsDark) Modifier else Modifier.floatingOutline(tabShape)
                                )
                            } else {
                                Modifier.background(if (hovered) DesktopColors.Hover else Color.Transparent)
                            }
                        )
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onSelectTab(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = tab.title,
                            color = if (selected) DesktopColors.TextPrimary else DesktopColors.TextGray,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                        if (tab == MessageTab.PRIVATE && unreadCount > 0) {
                            DraggableUnreadBadge(
                                count = unreadCount,
                                onDismiss = onDismissUnread,
                                containerColor = DesktopColors.Accent,
                                contentColor = DesktopColors.OnAccent,
                                fontSize = 9.sp,
                                minWidth = 15.dp,
                                height = 15.dp,
                                horizontalPadding = 4.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateConversationList(
    pager: MessagePager<PrivateConversation>,
    selectedPeerUid: Long?,
    onSelectConversation: (PrivateConversation) -> Unit,
    onDismissUnread: (PrivateConversation) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by pager.state.collectAsState()
    when (val current = state) {
        MessageListState.Idle, MessageListState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        is MessageListState.Error -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(current.message, color = DesktopColors.TextGray, fontSize = 13.sp)
                TextButton(onClick = pager::refresh) { Text("重试", color = DesktopColors.TextPrimary) }
            }
        }

        is MessageListState.Success -> {
            if (current.items.isEmpty()) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text("暂无私信会话", color = DesktopColors.TextGray, fontSize = 13.sp)
                }
            } else {
                val listState = rememberLazyListState()
                val shouldLoadMore by remember(current.hasMore, current.isLoadingMore, current.items.size) {
                    derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= current.items.size - LOAD_MORE_THRESHOLD && current.hasMore && !current.isLoadingMore
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) pager.loadMore()
                }

                HoverScrollbarBox(listState, modifier) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(current.items, key = { it.user.uid }) { conv ->
                            ConversationRow(
                                conversation = conv,
                                selected = selectedPeerUid == conv.user.uid,
                                onClick = { onSelectConversation(conv) },
                                onDismissUnread = { onDismissUnread(conv) }
                            )
                        }
                        if (current.isLoadingMore) {
                            item(key = "conv_loading_more") {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        color = DesktopColors.Accent,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: PrivateConversation,
    selected: Boolean,
    onClick: () -> Unit,
    onDismissUnread: () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val summary = remember(conversation.lastMessage) { formatConversationSummary(conversation.lastMessage) }
    val timeText = remember(conversation.lastTime) { formatConversationTime(conversation.lastTime) }
    val summaryColor = DesktopColors.TextGray
    val tagColor = DesktopColors.AccentText

    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .hoverable(hoverSource)
            .background(
                when {
                    selected -> DesktopColors.Selected
                    hovered -> DesktopColors.Hover
                    else -> Color.Transparent
                }
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(conversation.user.avatarUrl, ConversationAvatarSize, shape = CircleShape)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.user.nickname.ifBlank { "用户 ${conversation.user.uid}" },
                    color = DesktopColors.TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = if (selected || conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (timeText.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = timeText,
                        color = DesktopColors.TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val summaryText = buildAnnotatedString {
                    if (summary.musicTag != null) {
                        withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Medium)) {
                            append(summary.musicTag)
                        }
                        if (summary.body.isNotEmpty()) {
                            append(" ")
                        }
                    }
                    withStyle(SpanStyle(color = summaryColor)) {
                        append(summary.body.ifEmpty { if (summary.musicTag == null) "[消息]" else "" })
                    }
                }
                Text(
                    text = summaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    DraggableUnreadBadge(
                        count = conversation.unreadCount,
                        onDismiss = onDismissUnread,
                        containerColor = DesktopColors.Accent,
                        contentColor = DesktopColors.OnAccent,
                        fontSize = 10.sp,
                        minWidth = 17.dp,
                        height = 17.dp,
                        horizontalPadding = 5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> ChannelOverviewPanel(
    tab: MessageTab,
    description: String,
    pager: MessagePager<T>,
    modifier: Modifier = Modifier
) {
    val state by pager.state.collectAsState()
    val icon = when (tab) {
        MessageTab.NOTICE -> Icons.Rounded.NotificationsNone
        MessageTab.FORWARD -> Icons.Rounded.AlternateEmail
        MessageTab.COMMENT -> Icons.Rounded.ChatBubbleOutline
        MessageTab.PRIVATE -> Icons.Rounded.MailOutline
    }
    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(18.dp))
            Text(tab.title, color = DesktopColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(description, color = DesktopColors.TextGray, fontSize = 12.sp, lineHeight = 18.sp)
        val countText = (state as? MessageListState.Success)?.let { "已加载 ${it.items.size} 条" }
        if (countText != null) {
            Text(countText, color = DesktopColors.TextTertiary, fontSize = 12.sp)
        }
        Row(
            Modifier.clip(RoundedCornerShape(14.dp))
                .background(DesktopColors.CardSurface)
                .border(1.dp, DesktopColors.Divider, RoundedCornerShape(14.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = pager::refresh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Rounded.Refresh, null, tint = DesktopColors.TextGray, modifier = Modifier.size(14.dp))
            Text("刷新", color = DesktopColors.TextPrimary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyPrivateChatState(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.MailOutline, null, tint = DesktopColors.TextTertiary, modifier = Modifier.size(42.dp))
            Text("选择一个会话开始聊天", color = DesktopColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("也可从好友主页发私信，或在歌曲菜单中选择「分享」", color = DesktopColors.TextGray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun <T> MessageFeedPane(
    title: String,
    pager: MessagePager<T>,
    emptyTitle: String,
    keyOf: (T) -> Any,
    modifier: Modifier = Modifier,
    rowContent: @Composable (T) -> Unit
) {
    val state by pager.state.collectAsState()
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = DesktopColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            DesktopTooltip("刷新", side = TooltipSide.Bottom) {
                IconButton(onClick = pager::refresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Refresh, "刷新", tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider(color = DesktopColors.Divider)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val current = state) {
                MessageListState.Idle, MessageListState.Loading -> {
                    CircularProgressIndicator(
                        color = DesktopColors.Accent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is MessageListState.Error -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(current.message, color = DesktopColors.TextGray, fontSize = 14.sp)
                    TextButton(onClick = pager::refresh) { Text("重试", color = DesktopColors.TextPrimary) }
                }

                is MessageListState.Success -> {
                    if (current.items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(emptyTitle, color = DesktopColors.TextGray, fontSize = 14.sp)
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val shouldLoadMore by remember(current.hasMore, current.isLoadingMore, current.items.size) {
                            derivedStateOf {
                                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible >= current.items.size - LOAD_MORE_THRESHOLD && current.hasMore && !current.isLoadingMore
                            }
                        }
                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore) pager.loadMore()
                        }

                        HoverScrollbarBox(listState, Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(current.items, key = { keyOf(it) }) { item -> rowContent(item) }
                                if (current.isLoadingMore) {
                                    item(key = "feed_loading_more") {
                                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                color = DesktopColors.Accent,
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRowFrame(
    user: MessageUser?,
    action: String,
    time: Long,
    onUserClick: (Long) -> Unit,
    body: @Composable ColumnScope.() -> Unit = {}
) {
    val validUser = user?.takeIf { it.uid > 0L }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DesktopColors.CardSurface)
            .border(1.dp, DesktopColors.Divider, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Cover(
            url = user?.avatarUrl,
            size = FeedAvatarSize,
            shape = CircleShape,
            modifier = if (validUser != null) {
                Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onUserClick(validUser.uid) }
            } else {
                Modifier
            }
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user?.nickname?.takeIf { it.isNotBlank() } ?: "网易云用户",
                    color = DesktopColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (validUser != null) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onUserClick(validUser.uid) }
                    } else {
                        Modifier
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = listOf(action, formatUploadedAt(time)).filter { it.isNotBlank() }.joinToString(" · "),
                    color = DesktopColors.TextTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            body()
        }
    }
}

@Composable
private fun FeedBodyText(text: String) {
    if (text.isBlank()) return
    Spacer(Modifier.height(6.dp))
    Text(
        text = text.trim(),
        color = DesktopColors.TextPrimary,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    )
}

@Composable
private fun FeedQuoteText(text: String) {
    if (text.isBlank()) return
    Spacer(Modifier.height(6.dp))
    Text(
        text = text.trim(),
        color = DesktopColors.TextGray,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DesktopColors.Overlay.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun FeedSubText(text: String) {
    if (text.isBlank()) return
    Spacer(Modifier.height(6.dp))
    Text(
        text = text,
        color = DesktopColors.TextTertiary,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun NoticeRow(
    item: NoticeItem,
    songNames: Map<Long, String>,
    onUserClick: (Long) -> Unit
) {
    when (item) {
        is NoticeItem.CommentLike -> FeedRowFrame(item.user, "赞了你的评论", item.time, onUserClick) {
            FeedQuoteText(item.commentContent)
            item.songId?.let { songNames[it] }?.let { FeedSubText("《$it》") }
        }

        is NoticeItem.EventLike -> FeedRowFrame(item.user, "赞了你的动态", item.time, onUserClick) {
            FeedQuoteText(item.eventText)
        }

        is NoticeItem.PlaylistCollected -> FeedRowFrame(item.user, "收藏了你的歌单", item.time, onUserClick) {
            val summary = listOfNotNull(
                item.playlistName.takeIf { it.isNotBlank() }?.let { "《$it》" },
                item.trackCount.takeIf { it > 0 }?.let { "共 $it 首" }
            ).joinToString(" · ")
            FeedBodyText(summary)
        }

        is NoticeItem.Unsupported -> Text(
            text = listOf("暂不支持的消息类型", formatUploadedAt(item.time)).filter { it.isNotBlank() }.joinToString(" · "),
            color = DesktopColors.TextTertiary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DesktopColors.CardSurface)
                .border(1.dp, DesktopColors.Divider, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ForwardMessageRow(
    item: ForwardMessage,
    onUserClick: (Long) -> Unit
) {
    FeedRowFrame(item.user, "@了你", item.time, onUserClick) {
        FeedBodyText(item.content)
    }
}

@Composable
private fun CommentMessageRow(
    item: CommentMessage,
    onUserClick: (Long) -> Unit
) {
    FeedRowFrame(item.user, "回复了你的评论", item.time, onUserClick) {
        FeedBodyText(item.content)
        item.repliedContent?.let { FeedQuoteText(it) }
        item.resourceName?.let { name ->
            FeedSubText(listOfNotNull("《$name》", item.resourceCreator).joinToString(" - "))
        }
    }
}
