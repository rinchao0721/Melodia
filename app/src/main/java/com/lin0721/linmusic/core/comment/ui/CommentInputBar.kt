package com.lin0721.linmusic.core.comment.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import com.lin0721.linmusic.core.comment.domain.CommentComposerState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.ui.components.PlaceholderTextField
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaSystemBarsConsumed
import com.lin0721.linmusic.core.ui.theme.melodiaNavigationBarBottomPadding

// 评论区底部常驻输入栏：随软键盘升降，支持直接发表主评论与针对指定用户的回复
@Composable
fun CommentInputBar(
    replyTarget: CommentItem?,
    composerState: CommentComposerState,
    focusRequester: FocusRequester,
    onClearReplyTarget: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "说点什么...",
    bottomOverlayInset: Dp = 0.dp
) {
    var text by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isSubmitting = composerState is CommentComposerState.Submitting
    val canSubmit = text.isNotBlank() && !isSubmitting

    // composerState 是跨输入框共享的全局状态（全屏评论页与楼层详情页的输入栏可能同时挂载）。
    // 只在"这个输入框自己发起的提交"结束时才清空/保留草稿，避免误清掉另一个输入框里还没发的内容。
    var mySubmissionPending by remember { mutableStateOf(false) }
    LaunchedEffect(composerState) {
        if (mySubmissionPending) {
            when (composerState) {
                is CommentComposerState.Idle -> {
                    text = ""
                    mySubmissionPending = false
                }
                is CommentComposerState.Failed -> {
                    mySubmissionPending = false
                }
                is CommentComposerState.Submitting -> Unit
            }
        }
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val bottomPadding = if (imeBottom > 0) {
        val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        // 外层卡片已让出手势条时，键盘高度里这一截不在卡片内，需扣掉
        if (LocalMelodiaSystemBarsConsumed.current) {
            (imePadding - WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()).coerceAtLeast(0.dp)
        } else {
            imePadding
        }
    } else {
        val navBarsPadding = melodiaNavigationBarBottomPadding()
        if (bottomOverlayInset > 0.dp) bottomOverlayInset else navBarsPadding
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark.copy(alpha = 0.98f))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(bottom = bottomPadding)
    ) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // 回复目标标签栏
        AnimatedVisibility(
            visible = replyTarget != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (replyTarget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MelodiaSpacing.md, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "回复 @${replyTarget.user.nickname}:",
                        color = NeteaseRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .pressable(MelodiaPress.Pill, onClick = onClearReplyTarget)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "取消回复",
                            tint = TextGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 输入框与发送按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
        ) {
            val dynamicPlaceholder = if (replyTarget != null) {
                "回复 @${replyTarget.user.nickname}..."
            } else {
                placeholder
            }

            PlaceholderTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = dynamicPlaceholder,
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                focusRequester = focusRequester,
                containerColor = Color.White.copy(alpha = 0.12f),
                textColor = Color.White,
                placeholderColor = TextGray.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (canSubmit) NeteaseRed else NeteaseRed.copy(alpha = 0.35f))
                    .then(
                        if (canSubmit) {
                            Modifier.pressable(MelodiaPress.Pill) {
                                mySubmissionPending = true
                                onSubmit(text.trim())
                                keyboardController?.hide()
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "发送",
                        color = if (canSubmit) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
