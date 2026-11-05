package com.lin0721.linmusic.core.comment.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.ui.components.shimmerBackground
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
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
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.uiSpring
import com.lin0721.linmusic.core.ui.theme.uiTween

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
                        tint = MelodiaColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "评论",
                        color = MelodiaColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (commentsState is CommentsState.Success) {
                        Text(
                            text = "(${commentsState.total})",
                            color = MelodiaColors.TextSecondary.copy(alpha = 0.8f),
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
                            color = MelodiaColors.AccentText,
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
                            color = MelodiaColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        MelodiaTextButton(
                            onClick = onRetry,
                            colors = ButtonDefaults.textButtonColors(contentColor = MelodiaColors.Accent)
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
                            color = MelodiaColors.TextSecondary,
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
                                        color = MelodiaColors.Divider,
                                        thickness = 0.5.dp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "查看全部 ${commentsState.total} 条评论",
                                color = MelodiaColors.AccentText,
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
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MelodiaColors.Overlay.copy(alpha = 0.1f),
                animationSpec = uiTween(220),
                label = "sort_pill_bg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MelodiaColors.PillContent,
                animationSpec = uiTween(220),
                label = "sort_pill_content"
            )
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .pressable(MelodiaPress.Pill) { onSelect(type) }
                    .clip(RoundedCornerShape(PillRadius))
                    .background(bgColor)
                    .animateContentSize(uiSpring(stiffness = Spring.StiffnessMedium))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentRowItem(
    comment: CommentItem,
    modifier: Modifier = Modifier,
    contentMaxLines: Int = Int.MAX_VALUE,
    isLikeClickable: Boolean = true,
    // 只读评论（QQ 音乐）不能回复，也没有楼层
    allowReply: Boolean = true,
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

    // 只有底部弹窗/全屏列表里的完整行才响应点击回复/长按菜单，预览卡片里的迷你行保持静态展示
    val isInteractive = contentMaxLines == Int.MAX_VALUE
    var showActionMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = "${comment.user.avatarUrl}?param=80y80",
            contentDescription = comment.user.nickname,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
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
                        color = MelodiaColors.TextPrimary.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = comment.timeStr ?: "",
                        color = MelodiaColors.TextTertiary,
                        fontSize = 11.sp
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
                        color = if (comment.liked) MelodiaColors.AccentText else MelodiaColors.TextSecondary.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Icon(
                        imageVector = Icons.Rounded.ThumbUp,
                        contentDescription = null,
                        tint = if (comment.liked) MelodiaColors.Accent else MelodiaColors.TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isInteractive) {
                                Modifier.combinedClickable(
                                    onClick = { if (allowReply) onReplyClick() },
                                    onLongClick = { showActionMenu = true }
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Text(
                        text = comment.content,
                        color = MelodiaColors.TextPrimary.copy(alpha = 0.95f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = contentMaxLines,
                        overflow = if (contentMaxLines < Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip
                    )

                    comment.beReplied?.firstOrNull()?.let { quoted ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MelodiaColors.Overlay.copy(alpha = 0.06f))
                                .padding(horizontal = MelodiaSpacing.sm, vertical = 6.dp)
                        ) {
                            Text(
                                text = "回复 ${quoted.user?.nickname.orEmpty()}：${quoted.content.orEmpty()}",
                                color = MelodiaColors.TextSecondary.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isInteractive) {
                    DropdownMenu(
                        expanded = showActionMenu,
                        onDismissRequest = { showActionMenu = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MelodiaColors.Surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = MelodiaSpacing.sm, vertical = MelodiaSpacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.lg)
                        ) {
                            CommentActionMenuItem(
                                icon = Icons.Rounded.ContentCopy,
                                label = "复制",
                                onClick = {
                                    showActionMenu = false
                                    clipboardManager.setText(AnnotatedString(comment.content))
                                }
                            )
                            if (allowReply) {
                                CommentActionMenuItem(
                                    icon = Icons.AutoMirrored.Rounded.Reply,
                                    label = "回复",
                                    onClick = {
                                        showActionMenu = false
                                        onReplyClick()
                                    }
                                )
                            }
                            CommentActionMenuItem(
                                icon = Icons.Rounded.Share,
                                label = "分享",
                                onClick = {
                                    showActionMenu = false
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, comment.content)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "分享评论"))
                                }
                            )
                            if (onDeleteClick != null) {
                                CommentActionMenuItem(
                                    icon = Icons.Rounded.Delete,
                                    label = "删除",
                                    tint = MelodiaColors.Accent,
                                    onClick = {
                                        showActionMenu = false
                                        onDeleteClick()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isInteractive && allowReply && comment.replyCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "展开 ${comment.replyCount} 条回复 ›",
                    color = MelodiaColors.AccentText,
                    fontSize = 12.sp,
                    modifier = Modifier.pressable(MelodiaPress.Pill, onClick = onExpandFloorClick)
                )
            }
        }
    }
}

// 长按评论弹出的横向操作菜单里的单个图标项：图标在上、文字标签在下
@Composable
private fun CommentActionMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MelodiaColors.TextPrimary
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = MelodiaSpacing.xs, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp
        )
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
            color = MelodiaColors.Divider
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
                color = MelodiaColors.Overlay.copy(alpha = 0.06f)
            )
        }
    }
}

