package com.lin0721.linmusic.desktop.ui.deeplink

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.deeplink.ClipboardLink
import com.lin0721.linmusic.core.deeplink.LinkPreviewState
import com.lin0721.linmusic.core.deeplink.SongLinkTarget
import com.lin0721.linmusic.core.deeplink.typeLabel
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.uiEnter
import com.lin0721.linmusic.desktop.ui.components.uiExit
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.delay

private const val BANNER_AUTO_DISMISS_MS = 12_000L

// 桌面端切回窗口识别到剪贴板网易云链接时展示的轻浮条，12 秒后自动收起
@Composable
internal fun ClipboardLinkBanner(
    link: ClipboardLink?,
    previewState: LinkPreviewState,
    onPrimaryAction: () -> Unit,
    onEnqueueNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = link != null,
        enter = uiEnter(slideInVertically { it / 2 } + fadeIn()),
        exit = uiExit(slideOutVertically { it / 2 } + fadeOut()),
        modifier = modifier
    ) {
        if (link != null) {
            LaunchedEffect(link, previewState) {
                if (previewState !is LinkPreviewState.Loading) {
                    delay(BANNER_AUTO_DISMISS_MS)
                    onDismiss()
                }
            }
            val preview = (previewState as? LinkPreviewState.Ready)?.preview
            val target = link.target
            val isSong = target is SongLinkTarget.Song
            val isCircle = preview?.isCircleCover ?: (target is SongLinkTarget.Artist)
            val cardShape = RoundedCornerShape(10.dp)
            val titleText = when {
                preview != null -> preview.title
                previewState is LinkPreviewState.Loading -> "正在读取${target.typeLabel}信息…"
                else -> "网易云${target.typeLabel}"
            }
            val subtitleText = when {
                preview != null && preview.subtitle.isNotBlank() -> preview.subtitle
                previewState is LinkPreviewState.Loading -> "正在解析…"
                else -> "点击${if (isSong) "插播" else "打开"}"
            }

            Row(
                modifier = Modifier
                    .widthIn(min = 340.dp, max = 520.dp)
                    .themedShadow(12.dp, cardShape)
                    .clip(cardShape)
                    .background(DesktopColors.PopupSurface)
                    .floatingOutline(cardShape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onPrimaryAction)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(
                    url = preview?.coverUrl.orEmpty(),
                    size = 44.dp,
                    shape = if (isCircle) CircleShape else RoundedCornerShape(4.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = titleText,
                        color = DesktopColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleText,
                        color = DesktopColors.TextGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (isSong) {
                    IconButton(
                        onClick = onEnqueueNext,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "下一首播放",
                            tint = DesktopColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Button(
                    onClick = onPrimaryAction,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesktopColors.Accent,
                        contentColor = DesktopColors.OnAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 1.dp, minHeight = 30.dp)
                        .height(30.dp)
                ) {
                    Text(
                        text = if (isSong) "插播" else "打开",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = DesktopColors.TextGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
