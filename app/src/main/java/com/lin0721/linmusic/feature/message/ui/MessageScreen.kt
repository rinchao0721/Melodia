package com.lin0721.linmusic.feature.message.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.FilterChipsRow
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// 消息：私信、通知、@我、评论回复
@Composable
fun MessageScreen(
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    onConversationClick: (Long) -> Unit,
    viewModel: MessageViewModel = koinViewModel(),
    unreadMonitor: PrivateUnreadMonitor = koinInject()
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val songNames by viewModel.songNames.collectAsStateWithLifecycle()
    val privateUnreadCount by unreadMonitor.unreadCount.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    LaunchedEffect(isLoggedIn, selectedTab) {
        if (isLoggedIn == true) viewModel.ensureLoaded(selectedTab)
    }

    SecondaryScreenScaffold(title = "消息", onBack = onBack) {
        when (isLoggedIn) {
            null -> Unit

            false -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Notifications,
                    title = "请先登录",
                    subtitle = "登录后查看私信、通知、@我与评论回复"
                )
            }

            true -> {
                FilterChipsRow(
                    items = MessageTab.entries.map { it.title },
                    selectedIndex = selectedTab.ordinal,
                    onSelected = { viewModel.selectTab(MessageTab.entries[it]) },
                    badges = if (privateUnreadCount > 0) {
                        mapOf(MessageTab.PRIVATE.ordinal to privateUnreadCount)
                    } else {
                        emptyMap()
                    },
                    onBadgeDismiss = { index ->
                        if (index == MessageTab.PRIVATE.ordinal) {
                            unreadMonitor.markAllRead()
                        }
                    },
                    modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                )

                when (selectedTab) {
                    MessageTab.PRIVATE -> MessageList(
                        pager = viewModel.privatePager,
                        emptyTitle = "还没有私信",
                        itemKey = { it.user.uid }
                    ) { item ->
                        PrivateConversationRow(
                            item = item,
                            onClick = { onConversationClick(item.user.uid) },
                            onDismissUnread = { unreadMonitor.markConversationRead(item) }
                        )
                    }

                    MessageTab.NOTICE -> MessageList(
                        pager = viewModel.noticePager,
                        emptyTitle = "还没有通知",
                        itemKey = { it.id }
                    ) { item -> NoticeRow(item, songNames, onUserClick) }

                    MessageTab.FORWARD -> MessageList(
                        pager = viewModel.forwardPager,
                        emptyTitle = "还没有人@你",
                        itemKey = { it.key }
                    ) { item -> ForwardMessageRow(item, onUserClick) }

                    MessageTab.COMMENT -> MessageList(
                        pager = viewModel.commentPager,
                        emptyTitle = "还没有收到评论回复",
                        itemKey = { it.commentId }
                    ) { item -> CommentMessageRow(item, onUserClick) }
                }
            }
        }
    }
}

@Composable
private fun <T> MessageList(
    pager: MessagePager<T>,
    emptyTitle: String,
    itemKey: (T) -> Any,
    itemContent: @Composable (T) -> Unit
) {
    val state by pager.state.collectAsStateWithLifecycle()

    when (val current = state) {
        MessageListState.Idle, MessageListState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        is MessageListState.Error -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ErrorState(message = current.message, onRetry = { pager.refresh() })
        }

        is MessageListState.Success -> {
            if (current.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(icon = Icons.Rounded.Notifications, title = emptyTitle)
                }
            } else {
                val listState = rememberLazyListState()
                val shouldLoadMore by remember(current.hasMore, current.isLoadingMore, current.items.size) {
                    derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= current.items.size - 3 && current.hasMore && !current.isLoadingMore
                    }
                }

                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) pager.loadMore()
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(current.items, key = { itemKey(it) }) { item -> itemContent(item) }

                    if (current.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MelodiaSpacing.md),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
