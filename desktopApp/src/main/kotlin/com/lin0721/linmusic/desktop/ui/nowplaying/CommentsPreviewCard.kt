package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.comment.presentation.previewComments
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.desktop.ui.comments.CommentRow
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private const val PREVIEW_MAX_LINES = 3

// 面板里的评论预览：标题带总数，展示前两条，点击进入完整评论
@Composable
fun CommentsPreviewCard(
    state: CommentsState,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = if (state is CommentsState.Success) "评论 (${state.total})" else "评论"
    InfoCard(title, modifier.clip(RoundedCornerShape(8.dp)).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onOpen)) {
        when (state) {
            is CommentsState.Loading -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            is CommentsState.Error -> Column(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("加载评论失败: ${state.message}", color = DesktopColors.TextGray, fontSize = 13.sp, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text("重试", color = DesktopColors.AccentText, fontWeight = FontWeight.Bold) }
            }
            is CommentsState.Success -> {
                val comments = previewComments(state)
                if (comments.isEmpty()) {
                    Text("暂无评论", color = DesktopColors.TextGray, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                } else {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        comments.forEachIndexed { index, comment ->
                            CommentRow(comment, interactive = false, contentMaxLines = PREVIEW_MAX_LINES)
                            if (index < comments.lastIndex) {
                                HorizontalDivider(color = DesktopColors.Divider, thickness = 0.5.dp)
                            }
                        }
                        Text(
                            "查看全部 ${state.total} 条评论",
                            color = DesktopColors.AccentText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
