package com.lin0721.linmusic.core.comment.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material.icons.rounded.Close
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.domain.CommentComposerState
import com.lin0721.linmusic.core.ui.components.shimmerBackground
import com.lin0721.linmusic.core.ui.theme.ContentSwitchDurationMs
import com.lin0721.linmusic.core.ui.theme.PillRadius
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@Composable
fun CommentsPreviewCard(
    commentsState: CommentsState,
    cardColor: Color,
    onClick: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            .pressable(MelodiaPress.Card, onClick = onClick),
        shape = RoundedCornerShape(InfoCardRadius),
        color = cardColor
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Comment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "评论",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (commentsState is CommentsState.Success) {
                        Text(
                            text = "(${commentsState.total})",
                            color = TextGray.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (commentsState) {
                is CommentsState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MelodiaSpacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = NeteaseRed,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                is CommentsState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MelodiaSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                    ) {
                        Text(
                            text = "加载评论失败: ${commentsState.message}",
                            color = TextGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        MelodiaTextButton(
                            onClick = onRetry,
                            colors = ButtonDefaults.textButtonColors(contentColor = NeteaseRed)
                        ) {
                            Text("重试", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is CommentsState.Success -> {
                    val allComments = (commentsState.hotComments + commentsState.comments)
                        .distinctBy { it.commentId }
                        .take(2)

                    if (allComments.isEmpty()) {
                        Text(
                            text = "暂无评论",
                            color = TextGray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = MelodiaSpacing.md)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
                        ) {
                            allComments.forEachIndexed { index, comment ->
                                CommentRowItem(
                                    comment = comment,
                                    contentMaxLines = 3,
                                    isLikeClickable = false
                                )
                                if (index < allComments.size - 1) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.08f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "查看全部 ${commentsState.total} 条评论",
                                color = NeteaseRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(vertical = MelodiaSpacing.xs)
                            )
                        }
                    }
                }
            }
        }
    }
}

// 评论排序胶囊行：复用音乐库胶囊视觉表现与微动效，三项常驻单选切换
@Composable
fun CommentSortTabs(
    current: CommentSortType,
    onSelect: (CommentSortType) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        CommentSortType.RECOMMEND to "推荐",
        CommentSortType.HOT to "最热",
        CommentSortType.LATEST to "最新"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (type, label) ->
            val isSelected = type == current
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                animationSpec = tween(220),
                label = "sort_pill_bg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.LightGray,
                animationSpec = tween(220),
                label = "sort_pill_content"
            )
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .pressable(MelodiaPress.Pill) { onSelect(type) }
                    .clip(RoundedCornerShape(PillRadius))
                    .background(bgColor)
                    .animateContentSize(spring(stiffness = Spring.StiffnessMedium))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun CommentRowItem(
    comment: CommentItem,
    modifier: Modifier = Modifier,
    contentMaxLines: Int = Int.MAX_VALUE,
    isLikeClickable: Boolean = true,
    onLikeClick: () -> Unit = {},
    onUserClick: (Long) -> Unit = {},
    onReplyClick: () -> Unit = {},
    onExpandFloorClick: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null
) {
    val userClickModifier = if (comment.user.userId > 0L) {
        Modifier.clickable { onUserClick(comment.user.userId) }
    } else {
        Modifier
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = "${comment.user.avatarUrl}?param=80y80",
            contentDescription = comment.user.nickname,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(userClickModifier)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = MelodiaSpacing.sm)
                        .then(userClickModifier)
                ) {
                    Text(
                        text = comment.user.nickname,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = comment.timeStr ?: "",
                        color = TextGray.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }

                val likeModifier = if (isLikeClickable) {
                    Modifier
                        .clip(CircleShape)
                        .clickable { onLikeClick() }
                        .padding(horizontal = MelodiaSpacing.sm, vertical = 6.dp)
                } else {
                    Modifier.padding(horizontal = MelodiaSpacing.sm, vertical = 6.dp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.xs),
                    modifier = likeModifier
                ) {
                    Text(
                        text = formatLikedCount(comment.likedCount),
                        color = if (comment.liked) NeteaseRed else TextGray.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                    Icon(
                        imageVector = Icons.Rounded.ThumbUp,
                        contentDescription = null,
                        tint = if (comment.liked) NeteaseRed else TextGray.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = comment.content,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = contentMaxLines,
                overflow = if (contentMaxLines < Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip
            )

            comment.beReplied?.firstOrNull()?.let { quoted ->
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = MelodiaSpacing.sm, vertical = 6.dp)
                ) {
                    Text(
                        text = "回复 ${quoted.user?.nickname.orEmpty()}：${quoted.content.orEmpty()}",
                        color = TextGray.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (contentMaxLines == Int.MAX_VALUE) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "回复",
                        color = TextGray.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        modifier = Modifier.pressable(MelodiaPress.Pill, onClick = onReplyClick)
                    )
                    if (comment.replyCount > 0) {
                        Text(
                            text = "展开 ${comment.replyCount} 条回复 ›",
                            color = NeteaseRed,
                            fontSize = 11.sp,
                            modifier = Modifier.pressable(MelodiaPress.Pill, onClick = onExpandFloorClick)
                        )
                    }
                    if (onDeleteClick != null) {
                        Text(
                            text = "删除",
                            color = TextGray.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            modifier = Modifier.pressable(MelodiaPress.Pill, onClick = onDeleteClick)
                        )
                    }
                }
            }
        }
    }
}

fun formatLikedCount(count: Int): String {
    return when {
        count >= 100_000 -> "${count / 10_000}w+"
        count >= 10_000 -> String.format(java.util.Locale.getDefault(), "%.1fw", count / 10000f)
        count >= 1000 -> "${count / 1000}k+"
        else -> count.toString()
    }
}

sealed interface CommentsState {
    val sortType: CommentSortType
    val totalCount: Int?

    data class Loading(
        override val sortType: CommentSortType = CommentSortType.RECOMMEND,
        override val totalCount: Int? = null
    ) : CommentsState

    data class Success(
        val hotComments: List<CommentItem>,
        val comments: List<CommentItem>,
        val total: Int,
        override val sortType: CommentSortType = CommentSortType.RECOMMEND,
        val cursor: String = "0",
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false
    ) : CommentsState {
        override val totalCount: Int get() = total
    }

    data class Error(
        val message: String,
        override val sortType: CommentSortType = CommentSortType.RECOMMEND,
        override val totalCount: Int? = null
    ) : CommentsState
}

// 单条评论扫光骨架条目
@Composable
fun CommentItemSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MelodiaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .shimmerBackground(CircleShape)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(14.dp)
                        .shimmerBackground(RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(14.dp)
                        .shimmerBackground(RoundedCornerShape(4.dp))
                )
            }
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(10.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(14.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(12.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
        }
    }
}

// 评论流扫光骨架屏
@Composable
fun CommentListSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
    ) {
        repeat(6) {
            CommentItemSkeleton()
        }
    }
}

// 楼层详情扫光骨架屏
@Composable
fun CommentFloorSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
    ) {
        CommentItemSkeleton()
        HorizontalDivider(
            modifier = Modifier.padding(vertical = MelodiaSpacing.sm),
            color = Color.White.copy(alpha = 0.08f)
        )
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(12.dp)
                .shimmerBackground(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(MelodiaSpacing.sm))
        repeat(4) {
            CommentItemSkeleton()
            HorizontalDivider(
                modifier = Modifier.padding(vertical = MelodiaSpacing.sm),
                color = Color.White.copy(alpha = 0.06f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// MV 视频下方内嵌评论区视图：非全屏时直接在视频正下方图层展开，无任何弹窗遮罩，顶部保留关闭返回入口
@Composable
fun MvInlineCommentsView(
    commentsState: CommentsState,
    composerState: CommentComposerState = CommentComposerState.Idle,
    currentUserId: Long?,
    bottomOverlayInset: Dp = 0.dp,
    onLikeComment: (CommentItem) -> Unit,
    onSubmitComment: (String, CommentItem?) -> Unit,
    onDeleteClick: (CommentItem) -> Unit,
    onExpandFloor: (CommentItem) -> Unit,
    onSortChange: (CommentSortType) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onRequireLogin: () -> Unit,
    onUserClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    val focusRequester = remember { FocusRequester() }

    val totalCount = commentsState.totalCount ?: (commentsState as? CommentsState.Success)?.total ?: 0
    val titleText = if (totalCount > 0) "评论 ($totalCount)" else "评论"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // 顶栏：标题 (总数) 与关闭按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titleText,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            MelodiaIconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭评论区",
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 排序胶囊栏：常驻顶部
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
                            .padding(MelodiaSpacing.lg),
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
                            contentPadding = PaddingValues(top = MelodiaSpacing.sm, bottom = MelodiaSpacing.md)
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

        // 底部常驻输入栏
        CommentInputBar(
            replyTarget = replyTarget,
            composerState = composerState,
            focusRequester = focusRequester,
            bottomOverlayInset = 0.dp,
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

