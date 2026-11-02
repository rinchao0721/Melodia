package com.lin0721.linmusic.desktop.ui.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.comment.domain.CommentComposerState
import com.lin0721.linmusic.core.comment.domain.CommentFloorState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.OverlayPanelHeader
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

// 距回复末尾还剩这么多条时开始加载下一页
private const val LOAD_MORE_THRESHOLD = 2

// 楼层详情：某条评论下的全部回复，盖在评论列表之上，关闭后回到评论列表；底部可直接回复
@Composable
fun CommentFloorPanel(
    floorState: CommentFloorState,
    owner: CommentItem?,
    composerState: CommentComposerState,
    currentUserId: Long?,
    host: CommentsHost,
    onRequestDelete: (CommentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    val focusRequester = remember { FocusRequester() }
    val ownerComment = (floorState as? CommentFloorState.Success)?.ownerComment ?: owner

    Column(modifier.fillMaxSize().padding(top = 16.dp)) {
        OverlayPanelHeader(
            title = "评论详情",
            closeDescription = "返回评论",
            onClose = host::closeFloor,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            when (floorState) {
                is CommentFloorState.Idle -> Unit
                is CommentFloorState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                is CommentFloorState.Error -> Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(floorState.message, color = DesktopColors.TextGray, fontSize = 14.sp, textAlign = TextAlign.Center)
                    if (ownerComment != null) {
                        Button(
                            onClick = { host.openFloor(ownerComment) },
                            colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent),
                            modifier = Modifier.padding(top = 12.dp)
                        ) { Text("重试", color = DesktopColors.TextPrimary) }
                    }
                }
                is CommentFloorState.Success -> FloorList(
                    state = floorState,
                    currentUserId = currentUserId,
                    host = host,
                    onReply = { comment ->
                        if (currentUserId == null) {
                            navigator.showMessage("请先登录账号")
                        } else {
                            replyTarget = comment
                            focusRequester.requestFocus()
                        }
                    },
                    onRequestDelete = onRequestDelete
                )
            }
        }
        if (floorState is CommentFloorState.Success) {
            CommentInputBar(
                replyTarget = replyTarget,
                composerState = composerState,
                focusRequester = focusRequester,
                placeholder = "回复 @${floorState.ownerComment.user.nickname}...",
                onClearReplyTarget = { replyTarget = null },
                onSubmit = { content ->
                    if (currentUserId == null) {
                        navigator.showMessage("请先登录账号")
                    } else {
                        // 未指定回复对象时回复楼主
                        val parentId = replyTarget?.commentId ?: floorState.ownerComment.commentId
                        host.reply(parentId, content)
                        replyTarget = null
                    }
                }
            )
        }
    }
}

@Composable
private fun FloorList(
    state: CommentFloorState.Success,
    currentUserId: Long?,
    host: CommentsHost,
    onReply: (CommentItem) -> Unit,
    onRequestDelete: (CommentItem) -> Unit
) {
    val listState = rememberLazyListState()
    val nearEnd by remember(listState, state.replies.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // 前面还有楼主评论与回复数标题两项
            lastVisible >= state.replies.size - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.hasMore, state.isLoadingMore) {
        if (nearEnd && state.hasMore && !state.isLoadingMore) host.loadMoreFloor()
    }

    fun deleteAction(comment: CommentItem): (() -> Unit)? =
        if (comment.user.userId == currentUserId) ({ onRequestDelete(comment) }) else null

    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            item(key = "owner") {
                CommentRow(
                    comment = state.ownerComment,
                    onLike = { host.like(state.ownerComment) },
                    onReply = { onReply(state.ownerComment) },
                    onDelete = deleteAction(state.ownerComment)
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = DesktopColors.Divider)
                Text(
                    "全部回复 · ${state.replies.size}",
                    color = DesktopColors.TextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
            }
            items(state.replies, key = { it.commentId }) { reply ->
                CommentRow(
                    comment = reply,
                    onLike = { host.like(reply) },
                    onReply = { onReply(reply) },
                    onDelete = deleteAction(reply)
                )
            }
            if (state.isLoadingMore) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}
