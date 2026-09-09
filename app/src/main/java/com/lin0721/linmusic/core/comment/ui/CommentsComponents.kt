package com.lin0721.linmusic.core.comment.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable
fun CommentRowItem(
    comment: CommentItem,
    modifier: Modifier = Modifier,
    contentMaxLines: Int = Int.MAX_VALUE,
    isLikeClickable: Boolean = true,
    onLikeClick: () -> Unit = {},
    onUserClick: (Long) -> Unit = {}
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
    object Loading : CommentsState
    data class Success(
        val hotComments: List<CommentItem>,
        val comments: List<CommentItem>,
        val total: Int
    ) : CommentsState
    data class Error(val message: String) : CommentsState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    commentsState: CommentsState,
    onLikeComment: (CommentItem) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onUserClick: (Long) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = BottomSheetShape,
        dragHandle = {
            Box(modifier = Modifier.padding(top = 12.dp, bottom = MelodiaSpacing.xs)) {
                Surface(
                    modifier = Modifier.width(40.dp).height(4.dp),
                    shape = DragHandleShape,
                    color = Color.White.copy(alpha = 0.3f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "评论",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )

            when (commentsState) {
                is CommentsState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeteaseRed)
                    }
                }
                is CommentsState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无评论",
                                color = TextGray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(allComments, key = { it.commentId }) { comment ->
                                CommentRowItem(
                                    comment = comment,
                                    onLikeClick = { onLikeComment(comment) },
                                    onUserClick = onUserClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

