package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.contentColorOn
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Check

internal const val SUCCESS_CAPSULE_DURATION_MS = 2_500L

internal fun recipientDisplayName(nickname: String): String = nickname.ifBlank { "对方" }

internal fun buildSuccessBannerMessage(
    prefix: String,
    nickname: String,
    suffix: String = ""
): AnnotatedString = buildAnnotatedString {
    append(prefix)
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
        append(recipientDisplayName(nickname))
    }
    if (suffix.isNotEmpty()) {
        append(suffix)
    }
}

internal suspend fun awaitCapsuleTimeout(
    hovered: Flow<Boolean>,
    timeoutMs: Long = SUCCESS_CAPSULE_DURATION_MS,
    nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    var remainingMs = timeoutMs
    var isHovered = hovered.first()
    while (remainingMs > 0L) {
        if (isHovered) {
            hovered.first { !it }
            isHovered = false
        } else {
            val startedMs = nowMs()
            val becameHovered = withTimeoutOrNull(remainingMs) {
                hovered.first { it }
            } != null
            if (!becameHovered) {
                remainingMs = 0L
            } else {
                val elapsedMs = (nowMs() - startedMs).coerceAtLeast(0L)
                remainingMs = (remainingMs - elapsedMs).coerceAtLeast(0L)
                isHovered = true
            }
        }
    }
}

// 底部浮动成功提示胶囊：悬停暂停倒计时，移出后继续淡出
@Composable
internal fun BoxScope.SuccessActionCapsule(
    bannerKey: Any?,
    visible: Boolean,
    message: AnnotatedString,
    actionLabel: String,
    onAction: () -> Unit,
    onHide: () -> Unit,
    avatarUrl: String? = null,
    showAvatar: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hoveredState = hoverSource.collectIsHoveredAsState()
    val actionHover = remember { MutableInteractionSource() }
    val actionHovered by actionHover.collectIsHoveredAsState()

    LaunchedEffect(bannerKey, visible) {
        if (bannerKey == null || !visible) return@LaunchedEffect
        awaitCapsuleTimeout(snapshotFlow { hoveredState.value })
        onHide()
    }

    AnimatedVisibility(
        visible = visible && bannerKey != null,
        modifier = modifier.align(Alignment.BottomCenter),
        enter = uiEnter(fadeIn(uiTween(180)) + slideInVertically(uiTween(200)) { it / 3 }),
        exit = uiExit(fadeOut(uiTween(220)))
    ) {
        Row(
            modifier = Modifier
                // 阴影绘制在边距内，避免被动画层边界裁掉
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .widthIn(max = 460.dp)
                .themedShadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(DesktopColors.PopupSurface)
                .border(1.dp, DesktopColors.Outline, CircleShape)
                .hoverable(hoverSource)
                // 吞掉胶囊空白处点击，避免误触背后的内容
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(DesktopColors.Success),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    MelodiaIcons.Check,
                    contentDescription = null,
                    tint = contentColorOn(DesktopColors.Success),
                    modifier = Modifier.size(14.dp)
                )
            }
            if (showAvatar) {
                Cover(avatarUrl, 22.dp, shape = CircleShape)
            }
            Text(
                text = message,
                color = DesktopColors.TextPrimary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .hoverable(actionHover)
                    .background(if (actionHovered) DesktopColors.Hover else Color.Transparent)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    color = DesktopColors.AccentText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
