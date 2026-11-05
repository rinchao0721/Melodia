package com.lin0721.linmusic.feature.message.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.cloud.domain.formatUploadedAt
import com.lin0721.linmusic.feature.message.domain.CommentMessage
import com.lin0721.linmusic.feature.message.domain.ForwardMessage
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.message.domain.NoticeItem

// 三个 Tab 共用的条目骨架：头像 + 昵称 + 副标题（动作与时间），正文由调用方填充
@Composable
private fun MessageRowFrame(
    user: MessageUser?,
    action: String,
    time: Long,
    onUserClick: (Long) -> Unit,
    body: @Composable ColumnScope.() -> Unit = {}
) {
    val userClick = user?.let { { onUserClick(it.uid) } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = user?.avatarUrl?.takeIf { it.isNotBlank() }?.let { "$it?param=120y120" },
            contentDescription = user?.nickname,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MelodiaColors.Fill)
                .then(if (userClick != null) Modifier.clickable(onClick = userClick) else Modifier),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user?.nickname?.takeIf { it.isNotBlank() } ?: "网易云用户",
                color = MelodiaColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = listOf(action, formatUploadedAt(time)).filter { it.isNotBlank() }.joinToString(" · "),
                color = MelodiaColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            body()
        }
    }
}

@Composable
private fun MessageContentText(text: String) {
    if (text.isBlank()) return
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = text.trim(),
        color = MelodiaColors.TextPrimary,
        fontSize = 14.sp,
        maxLines = 5,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MessageSubText(text: String) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = text,
        color = MelodiaColors.TextSecondary,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MessageQuote(text: String) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = text,
        color = MelodiaColors.TextSecondary,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MelodiaColors.Overlay.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
fun CommentMessageRow(
    item: CommentMessage,
    onUserClick: (Long) -> Unit
) {
    MessageRowFrame(
        user = item.user,
        action = "回复了你的评论",
        time = item.time,
        onUserClick = onUserClick
    ) {
        MessageContentText(item.content)
        item.repliedContent?.let { MessageQuote(it) }
        item.resourceName?.let { name ->
            MessageSubText(
                listOfNotNull("《$name》", item.resourceCreator).joinToString(" - ")
            )
        }
    }
}

@Composable
fun ForwardMessageRow(
    item: ForwardMessage,
    onUserClick: (Long) -> Unit
) {
    MessageRowFrame(
        user = item.user,
        action = "@了你",
        time = item.time,
        onUserClick = onUserClick
    ) {
        MessageContentText(item.content)
    }
}

@Composable
fun NoticeRow(
    item: NoticeItem,
    songNames: Map<Long, String>,
    onUserClick: (Long) -> Unit
) {
    when (item) {
        is NoticeItem.CommentLike -> MessageRowFrame(
            user = item.user,
            action = "赞了你的评论",
            time = item.time,
            onUserClick = onUserClick
        ) {
            if (item.commentContent.isNotBlank()) MessageQuote(item.commentContent.trim())
            item.songId?.let { songNames[it] }?.let { MessageSubText("《$it》") }
        }

        is NoticeItem.EventLike -> MessageRowFrame(
            user = item.user,
            action = "赞了你的动态",
            time = item.time,
            onUserClick = onUserClick
        ) {
            if (item.eventText.isNotBlank()) MessageQuote(item.eventText.trim())
        }

        is NoticeItem.PlaylistCollected -> MessageRowFrame(
            user = item.user,
            action = "收藏了你的歌单",
            time = item.time,
            onUserClick = onUserClick
        ) {
            val summary = listOfNotNull(
                item.playlistName.takeIf { it.isNotBlank() }?.let { "《$it》" },
                item.trackCount.takeIf { it > 0 }?.let { "共 $it 首" }
            ).joinToString(" · ")
            if (summary.isNotBlank()) MessageContentText(summary)
        }

        is NoticeItem.Unsupported -> UnsupportedNoticeRow(item.time)
    }
}

@Composable
private fun UnsupportedNoticeRow(time: Long) {
    Text(
        text = listOf("暂不支持的消息类型", formatUploadedAt(time)).filter { it.isNotBlank() }.joinToString(" · "),
        color = MelodiaColors.TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = 14.dp)
    )
}
