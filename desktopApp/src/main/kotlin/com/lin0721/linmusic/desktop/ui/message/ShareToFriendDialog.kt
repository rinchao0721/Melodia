package com.lin0721.linmusic.desktop.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.shell.PageViewModelFactory
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.message.ui.ShareToFriendUiState
import com.lin0721.linmusic.feature.message.ui.ShareToFriendViewModel

private val ListHeight = 380.dp
private const val FOLLOW_LOAD_MORE_THRESHOLD = 5

@Composable
internal fun ShareToFriendDialog(
    songId: Long,
    songName: String,
    factory: PageViewModelFactory,
    note: String = "",
    // 由外层接管提示时传入，否则走窗口提示栏
    onMessage: ((String) -> Unit)? = null,
    onSent: (MessageUser) -> Unit = {},
    onDismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    // 弹窗关闭即释放独立 ViewModelStore，避免选人协程泄漏
    val store = remember { ViewModelStore() }
    val viewModel = remember(store, factory) {
        val vmFactory = viewModelFactory {
            addInitializer(ShareToFriendViewModel::class) {
                factory.create(ShareToFriendViewModel::class)
            }
        }
        ViewModelProvider.create(store, vmFactory)[ShareToFriendViewModel::class]
    }
    DisposableEffect(store) {
        onDispose { store.clear() }
    }

    LaunchedEffect(viewModel) { viewModel.loadIfNeeded() }
    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { msg -> onMessage?.invoke(msg) ?: navigator.showMessage(msg) }
    }
    LaunchedEffect(viewModel) {
        viewModel.shareCompleted.collect { user ->
            onSent(user)
            onDismiss()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val query = (uiState as? ShareToFriendUiState.Ready)?.query.orEmpty()

    DesktopDialog(
        title = "分享给好友",
        onDismiss = onDismiss,
        width = 440.dp,
        actions = { DialogButton("取消", onDismiss) }
    ) {
        Text(
            text = if (songName.isNotBlank()) "分享单曲：《$songName》" else "分享单曲",
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        DialogTextField(
            value = query,
            onValueChange = viewModel::updateQuery,
            placeholder = "搜索好友昵称",
            enabled = uiState is ShareToFriendUiState.Ready,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        Box(Modifier.fillMaxWidth().height(ListHeight)) {
            when (val state = uiState) {
                ShareToFriendUiState.Idle, ShareToFriendUiState.Loading -> {
                    CircularProgressIndicator(
                        color = DesktopColors.Accent,
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                        strokeWidth = 2.dp
                    )
                }

                is ShareToFriendUiState.Error -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(state.message, color = DesktopColors.TextGray, fontSize = 13.sp)
                        TextButton(onClick = viewModel::refresh) {
                            Text("重试", color = DesktopColors.TextPrimary)
                        }
                    }
                }

                is ShareToFriendUiState.Ready -> {
                    val recent = state.filteredRecentUsers
                    val follows = state.filteredFollowUsers
                    if (recent.isEmpty() && follows.isEmpty()) {
                        Text(
                            text = if (state.query.isNotBlank()) "没有匹配的好友" else "暂无最近联系人或关注好友",
                            color = DesktopColors.TextGray,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val listState = rememberLazyListState()
                        HoverScrollbarBox(listState, Modifier.fillMaxSize()) {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                if (recent.isNotEmpty()) {
                                    item(key = "section_recent") {
                                        ShareSectionLabel("最近联系人")
                                    }
                                    items(recent, key = { "recent_${it.uid}" }) { user ->
                                        ShareFriendRow(
                                            user = user,
                                            sending = state.sendingToUid == user.uid,
                                            enabled = state.sendingToUid == null,
                                            onClick = { viewModel.shareSong(user.uid, songId, songName, note) }
                                        )
                                    }
                                }
                                if (follows.isNotEmpty()) {
                                    item(key = "section_follow") {
                                        ShareSectionLabel(
                                            "关注列表",
                                            modifier = Modifier.padding(top = if (recent.isNotEmpty()) 8.dp else 0.dp)
                                        )
                                    }
                                    itemsIndexed(follows, key = { _, u -> "follow_${u.uid}" }) { index, user ->
                                        ShareFriendRow(
                                            user = user,
                                            sending = state.sendingToUid == user.uid,
                                            enabled = state.sendingToUid == null,
                                            onClick = { viewModel.shareSong(user.uid, songId, songName, note) }
                                        )
                                        if (state.hasMoreFollows &&
                                            !state.isLoadingMoreFollows &&
                                            index >= follows.size - FOLLOW_LOAD_MORE_THRESHOLD
                                        ) {
                                            LaunchedEffect(state.followUsers.size) { viewModel.loadMoreFollows() }
                                        }
                                    }
                                }
                                if (state.isLoadingMoreFollows) {
                                    item(key = "follow_loading_more") {
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
    }
}

@Composable
private fun ShareSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = DesktopColors.TextTertiary,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun ShareFriendRow(
    user: MessageUser,
    sending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .hoverable(hoverSource)
            .background(if (hovered && enabled) DesktopColors.Hover else Color.Transparent)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(user.avatarUrl, 36.dp, shape = CircleShape)
        Spacer(Modifier.width(10.dp))
        Text(
            text = user.nickname.ifBlank { "用户 ${user.uid}" },
            color = DesktopColors.TextPrimary,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (sending) {
            CircularProgressIndicator(
                color = DesktopColors.Accent,
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        } else if (hovered && enabled) {
            Icon(
                Icons.AutoMirrored.Rounded.Send,
                "发送",
                tint = DesktopColors.AccentText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
