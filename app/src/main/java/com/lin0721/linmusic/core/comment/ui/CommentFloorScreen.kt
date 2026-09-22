package com.lin0721.linmusic.core.comment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.comment.domain.CommentComposerState
import com.lin0721.linmusic.core.comment.domain.CommentFloorState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.TextGray

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// 楼层详情页：某条评论的完整子回复列表，全屏 overlay，内嵌底部回复输入栏
@Composable
fun CommentFloorScreen(
    floorState: CommentFloorState,
    currentUserId: Long?,
    composerState: CommentComposerState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onSubmitReply: (parentCommentId: Long, content: String) -> Unit,
    onRequireLogin: () -> Unit,
    onDeleteClick: (CommentItem) -> Unit,
    onLikeClick: (CommentItem) -> Unit,
    onRetry: () -> Unit,
    onUserClick: (Long) -> Unit
) {
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.sm, vertical = MelodiaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MelodiaIconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Text(
                text = "评论详情",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = MelodiaSpacing.xs)
            )
        }

        when (floorState) {
            is CommentFloorState.Idle -> Unit
            is CommentFloorState.Loading -> {
                CommentFloorSkeleton(modifier = Modifier.weight(1f))
            }
            is CommentFloorState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(floorState.message, color = TextGray, fontSize = 14.sp, textAlign = TextAlign.Center)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(MelodiaSpacing.sm))
                    MelodiaButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = NeteaseRed)
                    ) {
                        Text("重试", color = Color.White)
                    }
                }
            }
            is CommentFloorState.Success -> {
                val listState = rememberLazyListState()
                val shouldLoadMore by remember(listState) {
                    derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        // 1 条楼首评论 + 回复条数
                        lastVisible >= floorState.replies.size - 2
                    }
                }
                LaunchedEffect(shouldLoadMore, floorState.hasMore, floorState.isLoadingMore) {
                    if (shouldLoadMore && floorState.hasMore && !floorState.isLoadingMore) {
                        onLoadMore()
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = MelodiaSpacing.md),
                    contentPadding = PaddingValues(vertical = MelodiaSpacing.sm)
                ) {
                    item {
                        CommentRowItem(
                            comment = floorState.ownerComment,
                            onLikeClick = { onLikeClick(floorState.ownerComment) },
                            onUserClick = onUserClick,
                            onReplyClick = {
                                if (currentUserId == null) {
                                    onRequireLogin()
                                } else {
                                    focusManager.clearFocus()
                                    replyTarget = floorState.ownerComment
                                    focusRequester.requestFocus()
                                }
                            },
                            onDeleteClick = if (floorState.ownerComment.user.userId == currentUserId) {
                                { onDeleteClick(floorState.ownerComment) }
                            } else null
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = MelodiaSpacing.sm),
                            color = Color.White.copy(alpha = 0.08f)
                        )
                        Text(
                            text = "全部回复 · ${floorState.replies.size}",
                            color = TextGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                        )
                    }
                    items(floorState.replies, key = { it.commentId }) { reply ->
                        CommentRowItem(
                            comment = reply,
                            onLikeClick = { onLikeClick(reply) },
                            onUserClick = onUserClick,
                            onReplyClick = {
                                if (currentUserId == null) {
                                    onRequireLogin()
                                } else {
                                    focusManager.clearFocus()
                                    replyTarget = reply
                                    focusRequester.requestFocus()
                                }
                            },
                            onDeleteClick = if (reply.user.userId == currentUserId) {
                                { onDeleteClick(reply) }
                            } else null
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = MelodiaSpacing.sm),
                            color = Color.White.copy(alpha = 0.06f)
                        )
                    }
                    if (floorState.hasMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = MelodiaSpacing.md),
                                contentAlignment = Alignment.Center
                            ) {
                                if (floorState.isLoadingMore) {
                                    CircularProgressIndicator(color = NeteaseRed, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = "加载更多",
                                        color = NeteaseRed,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                CommentInputBar(
                    replyTarget = replyTarget,
                    composerState = composerState,
                    focusRequester = focusRequester,
                    placeholder = "回复 @${floorState.ownerComment.user.nickname}...",
                    onClearReplyTarget = { replyTarget = null },
                    onSubmit = { content ->
                        if (currentUserId == null) {
                            onRequireLogin()
                        } else {
                            val targetId = replyTarget?.commentId ?: floorState.ownerComment.commentId
                            onSubmitReply(targetId, content)
                            replyTarget = null
                        }
                    }
                )
            }
        }
    }
}
