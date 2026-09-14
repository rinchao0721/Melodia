package com.lin0721.linmusic.core.comment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.domain.CommentComposerState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.TextGray

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// 全屏主评论页：全屏 overlay 覆盖层，展示完整评论流并内嵌底部输入栏
@Composable
fun CommentFullScreen(
    commentsState: CommentsState,
    currentUserId: Long?,
    composerState: CommentComposerState,
    onBack: () -> Unit,
    onSortChange: (CommentSortType) -> Unit,
    onLikeComment: (CommentItem) -> Unit,
    onUserClick: (Long) -> Unit,
    onExpandFloor: (CommentItem) -> Unit,
    onDeleteClick: (CommentItem) -> Unit,
    onSubmitComment: (content: String, replyTarget: CommentItem?) -> Unit,
    onRequireLogin: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        val totalCount = commentsState.totalCount ?: (commentsState as? CommentsState.Success)?.total ?: 0
        val titleText = if (totalCount > 0) "评论 ($totalCount)" else "评论"

        // 顶部导航栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
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
                text = titleText,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Box(modifier = Modifier.size(40.dp))
        }

        // 排序胶囊栏：常驻顶部，不受内容区加载态影响
        CommentSortTabs(
            current = commentsState.sortType,
            onSelect = onSortChange,
            modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs)
        )

        // 中间内容与列表区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (commentsState) {
                is CommentsState.Loading -> {
                    CommentListSkeleton(modifier = Modifier.fillMaxSize())
                }
                is CommentsState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = MelodiaSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "加载失败: ${commentsState.message}",
                            color = TextGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        MelodiaButton(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = NeteaseRed)
                        ) {
                            Text("重试", color = Color.White)
                        }
                    }
                }
                is CommentsState.Success -> {
                    val allComments = (commentsState.hotComments + commentsState.comments)
                        .distinctBy { it.commentId }

                    if (allComments.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无评论",
                                color = TextGray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val shouldLoadMore by remember(listState) {
                            derivedStateOf {
                                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible >= allComments.size - 3
                            }
                        }
                        LaunchedEffect(shouldLoadMore, commentsState.hasMore, commentsState.isLoadingMore) {
                            if (shouldLoadMore && commentsState.hasMore && !commentsState.isLoadingMore) {
                                onLoadMore()
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = MelodiaSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
                            contentPadding = PaddingValues(vertical = MelodiaSpacing.sm)
                        ) {
                            items(allComments, key = { it.commentId }) { comment ->
                                CommentRowItem(
                                    comment = comment,
                                    onLikeClick = { onLikeComment(comment) },
                                    onUserClick = onUserClick,
                                    onReplyClick = {
                                        if (currentUserId == null) {
                                            onRequireLogin()
                                        } else {
                                            replyTarget = comment
                                            focusRequester.requestFocus()
                                        }
                                    },
                                    onExpandFloorClick = { onExpandFloor(comment) },
                                    onDeleteClick = if (comment.user.userId == currentUserId) {
                                        { onDeleteClick(comment) }
                                    } else null
                                )
                            }
                            if (commentsState.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = MelodiaSpacing.md),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = NeteaseRed,
                                            modifier = Modifier.size(20.dp),
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

        // 底部输入栏：常驻底部，不因中间内容状态消失
        CommentInputBar(
            replyTarget = replyTarget,
            composerState = composerState,
            focusRequester = focusRequester,
            onClearReplyTarget = { replyTarget = null },
            onSubmit = { content ->
                if (currentUserId == null) {
                    onRequireLogin()
                } else {
                    onSubmitComment(content, replyTarget)
                    replyTarget = null
                }
            }
        )
    }
}
