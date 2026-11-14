package com.lin0721.linmusic.core.deeplink

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.core.ui.theme.uiEnter
import com.lin0721.linmusic.core.ui.theme.uiExit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val AUTO_DISMISS_DELAY_MS = 12_000L

// 识别到剪贴板网易云链接时的轻量浮动卡片：支持左右滑动关闭与 12 秒无操作自动收起
@Composable
fun ClipboardLinkFloatingCard(
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
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current
            val swipeThresholdPx = with(density) { 88.dp.toPx() }
            val offsetX = remember(link) { Animatable(0f) }

            LaunchedEffect(link) {
                offsetX.snapTo(0f)
            }

            // 元信息加载完成后等待 12 秒自动收起，避免长时间挡住页面底部
            LaunchedEffect(link, previewState) {
                if (previewState !is LinkPreviewState.Loading) {
                    delay(AUTO_DISMISS_DELAY_MS)
                    onDismiss()
                }
            }

            val preview = (previewState as? LinkPreviewState.Ready)?.preview
            val target = link.target
            val isSong = target is SongLinkTarget.Song
            val isCircle = preview?.isCircleCover ?: (target is SongLinkTarget.Artist)
            val cardShape = RoundedCornerShape(InfoCardRadius)
            val badgeText = preview?.typeLabel ?: target.typeLabel
            val titleText = when {
                preview != null -> preview.title
                previewState is LinkPreviewState.Loading -> "正在读取${target.typeLabel}信息…"
                else -> "网易云${target.typeLabel}"
            }
            val subtitleText = when {
                preview != null && preview.subtitle.isNotBlank() -> preview.subtitle
                previewState is LinkPreviewState.Loading -> "来自剪贴板"
                else -> "点击${if (isSong) "插播" else "打开"}"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MelodiaSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .graphicsLayer {
                            val progress = (abs(offsetX.value) / (swipeThresholdPx * 2f)).coerceIn(0f, 1f)
                            alpha = 1f - progress * 0.7f
                        }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                scope.launch { offsetX.snapTo(offsetX.value + delta) }
                            },
                            onDragStopped = { velocity ->
                                scope.launch {
                                    if (abs(offsetX.value) > swipeThresholdPx || abs(velocity) > 800f) {
                                        onDismiss()
                                    } else {
                                        offsetX.animateTo(0f)
                                    }
                                }
                            }
                        )
                        .themedShadow(10.dp, cardShape)
                        .clip(cardShape)
                        .background(MelodiaColors.PopupSurface)
                        .border(0.5.dp, MelodiaColors.Divider, cardShape)
                        .clickable(onClick = onPrimaryAction)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingLinkCover(
                        url = preview?.coverUrl,
                        target = target,
                        circle = isCircle
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinkTypeBadge(badgeText)
                            Text(
                                text = titleText,
                                color = MelodiaColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = subtitleText,
                            color = MelodiaColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (isSong) {
                        MelodiaIconButton(
                            onClick = onEnqueueNext,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = "下一首播放",
                                tint = MelodiaColors.TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    MelodiaButton(
                        onClick = onPrimaryAction,
                        shape = RoundedCornerShape(PillRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MelodiaColors.Accent,
                            contentColor = MelodiaColors.OnAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                            .height(32.dp)
                    ) {
                        Text(
                            text = if (isSong) "插播" else "打开",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    MelodiaIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = MelodiaColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkTypeBadge(label: String) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .background(MelodiaColors.AccentText.copy(alpha = 0.1f), shape)
            .border(BorderStroke(0.5.dp, MelodiaColors.AccentText), shape)
            .padding(horizontal = MelodiaSpacing.xs, vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = MelodiaColors.AccentText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun FloatingLinkCover(url: String?, target: SongLinkTarget, circle: Boolean) {
    val context = LocalContext.current
    val shape = if (circle) CircleShape else RoundedCornerShape(RadiusCompact)
    val model = remember(url) {
        url?.takeIf { it.isNotBlank() }?.let {
            val sized = if ("param=" in it) it else "$it?param=120y120"
            ImageRequest.Builder(context).data(sized).size(120, 120).build()
        }
    }
    val fallbackIcon = when (target) {
        is SongLinkTarget.Song -> Icons.Rounded.MusicNote
        is SongLinkTarget.Playlist -> if (target.isAlbum) Icons.Rounded.Album else Icons.AutoMirrored.Rounded.QueueMusic
        is SongLinkTarget.Artist -> Icons.Rounded.Person
    }
    val fallback = @Composable {
        Box(
            modifier = Modifier.fillMaxSize().background(MelodiaColors.CoverPlaceholder),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = MelodiaColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Box(modifier = Modifier.size(44.dp).clip(shape)) {
        if (model == null) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { fallback() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
