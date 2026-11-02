package com.lin0721.linmusic.desktop.ui.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.comment.domain.CommentComposerState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.ui.theme.paneBackdrop
import com.lin0721.linmusic.desktop.ui.components.trackTextInputFocus
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private const val INPUT_MAX_LINES = 4

// 草稿与提交状态：composerState 是跨输入框共享的全局状态，只有本输入框发起的提交结束时才处理草稿
data class CommentDraft(val text: String = "", val submissionPending: Boolean = false)

// 提交成功（Idle）清空草稿，失败（Failed）保留草稿以便重试，提交中不变；非本框发起的状态变化一律忽略
fun resolveDraft(draft: CommentDraft, composer: CommentComposerState): CommentDraft = when {
    !draft.submissionPending -> draft
    composer is CommentComposerState.Idle -> CommentDraft()
    composer is CommentComposerState.Failed -> draft.copy(submissionPending = false)
    else -> draft
}

// 评论输入栏：Enter 发送，Shift+Enter 换行；回复时显示目标条，可取消
@Composable
fun CommentInputBar(
    replyTarget: CommentItem?,
    composerState: CommentComposerState,
    focusRequester: FocusRequester,
    onClearReplyTarget: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "说点什么..."
) {
    var draft by remember { mutableStateOf(CommentDraft()) }
    val isSubmitting = composerState is CommentComposerState.Submitting
    val canSubmit = draft.text.isNotBlank() && !isSubmitting
    LaunchedEffect(composerState) { draft = resolveDraft(draft, composerState) }

    val submit = {
        if (canSubmit) {
            draft = draft.copy(submissionPending = true)
            onSubmit(draft.text.trim())
        }
    }

    Column(modifier.fillMaxWidth().paneBackdrop(DesktopColors.Pane)) {
        HorizontalDivider(color = DesktopColors.Divider)
        if (replyTarget != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "回复 @${replyTarget.user.nickname}:",
                    color = DesktopColors.AccentText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearReplyTarget, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, "取消回复", tint = DesktopColors.TextGray, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft.text,
                onValueChange = { draft = draft.copy(text = it) },
                placeholder = { Text(if (replyTarget != null) "回复 @${replyTarget.user.nickname}..." else placeholder, fontSize = 13.sp) },
                maxLines = INPUT_MAX_LINES,
                shape = RoundedCornerShape(20.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DesktopColors.TextPrimary,
                    unfocusedBorderColor = DesktopColors.SurfaceLight,
                    cursorColor = DesktopColors.TextPrimary
                ),
                modifier = Modifier.weight(1f).focusRequester(focusRequester).trackTextInputFocus().onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                        submit()
                        true
                    } else {
                        false
                    }
                }
            )
            Button(
                onClick = { submit() },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesktopColors.Accent,
                    disabledContainerColor = DesktopColors.Accent.copy(alpha = 0.35f)
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = DesktopColors.OnAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("发送", color = DesktopColors.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
