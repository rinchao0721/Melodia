package com.lin0721.linmusic.desktop.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.comment.presentation.formatLikedCount
import com.lin0721.linmusic.core.comment.presentation.quotedReplyText
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val AvatarSize = 36.dp
private val MoreButtonSize = 28.dp
private val SubtleText get() = DesktopColors.TextGray.copy(alpha = 0.6f)

// 单条评论。interactive 为假时是预览卡里的静态展示，不响应悬停、点赞与菜单；
// 悬停出现“更多”按钮，右键同样打开菜单（复制 / 回复 / 自己的评论可删除）
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CommentRow(
    comment: CommentItem,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    contentMaxLines: Int = Int.MAX_VALUE,
    onLike: () -> Unit = {},
    onReply: (() -> Unit)? = null,
    onExpandFloor: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var rowHeightPx by remember { mutableIntStateOf(0) }
    var contextMenuOffset by remember { mutableStateOf<DpOffset?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val revealed = interactive && (hovered || menuOpen || contextMenuOffset != null)
    val navigator = if (interactive) LocalDesktopNavigator.current else null
    val userId = comment.user.userId
    val userClick = if (navigator != null && userId > 0) {
        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { navigator.openProfile(userId) }
    } else {
        Modifier
    }

    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .onSizeChanged { rowHeightPx = it.height }
            .then(
                if (interactive) {
                    Modifier.hoverable(hoverSource).onPointerEvent(PointerEventType.Press) { event ->
                        if (event.buttons.isSecondaryPressed) {
                            val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                            contextMenuOffset = with(density) {
                                DpOffset(position.x.toDp(), (position.y - rowHeightPx).toDp())
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .background(if (revealed) DesktopColors.PaneHover else Color.Transparent)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Cover(comment.user.avatarUrl, AvatarSize, modifier = userClick, shape = CircleShape)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        comment.user.nickname,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = userClick
                    )
                    Text(comment.timeStr.orEmpty(), color = SubtleText, fontSize = 11.sp)
                }
                if (interactive) {
                    Box {
                        HoverReveal(revealed = revealed) {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(MoreButtonSize)) {
                                Icon(Icons.Rounded.MoreHoriz, "更多评论操作", tint = DesktopColors.TextGray)
                            }
                        }
                        CommentMenu(
                            expanded = menuOpen,
                            canReply = onReply != null,
                            canDelete = onDelete != null,
                            onDismiss = { menuOpen = false },
                            onCopy = { clipboard.setText(AnnotatedString(comment.content)) },
                            onReply = { onReply?.invoke() },
                            onDelete = { onDelete?.invoke() }
                        )
                    }
                    if (contextMenuOffset != null) {
                        CommentMenu(
                            expanded = true,
                            offset = contextMenuOffset ?: DpOffset.Zero,
                            canReply = onReply != null,
                            canDelete = onDelete != null,
                            onDismiss = { contextMenuOffset = null },
                            onCopy = { clipboard.setText(AnnotatedString(comment.content)) },
                            onReply = { onReply?.invoke() },
                            onDelete = { onDelete?.invoke() }
                        )
                    }
                }
                LikeButton(comment, interactive, onLike)
            }

            Text(
                comment.content,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = contentMaxLines,
                overflow = if (contentMaxLines < Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = Modifier.padding(top = 4.dp)
            )
            comment.beReplied?.firstOrNull()?.let { quoted ->
                Text(
                    quotedReplyText(quoted),
                    color = DesktopColors.TextGray.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            if (interactive && onExpandFloor != null && comment.replyCount > 0) {
                Text(
                    "展开 ${comment.replyCount} 条回复 ›",
                    color = DesktopColors.Accent,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(4.dp))
                        .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onExpandFloor)
                )
            }
        }
    }
}

@Composable
private fun LikeButton(comment: CommentItem, interactive: Boolean, onLike: () -> Unit) {
    val tint = if (comment.liked) DesktopColors.Accent else DesktopColors.TextGray
    Row(
        Modifier.clip(CircleShape)
            .then(if (interactive) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onLike) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(formatLikedCount(comment.likedCount), color = tint.copy(alpha = if (comment.liked) 1f else 0.8f), fontSize = 12.sp)
        Icon(Icons.Rounded.ThumbUp, if (comment.liked) "取消点赞" else "点赞", tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun CommentMenu(
    expanded: Boolean,
    offset: DpOffset = DpOffset.Zero,
    canReply: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    DesktopMenu(expanded = expanded, onDismiss = onDismiss, offset = offset, width = 160.dp) {
        SimpleMenuItem("复制", icon = Icons.Rounded.ContentCopy, onClick = { onDismiss(); onCopy() })
        if (canReply) SimpleMenuItem("回复", icon = Icons.AutoMirrored.Rounded.Reply, onClick = { onDismiss(); onReply() })
        if (canDelete) SimpleMenuItem("删除", icon = Icons.Rounded.Delete, danger = true, onClick = { onDismiss(); onDelete() })
    }
}
