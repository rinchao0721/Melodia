package com.lin0721.linmusic.desktop.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlin.math.max
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Check
import com.lin0721.linmusic.core.ui.icons.ChevronDown

private val PopupWidth = 292.dp
private val PopupShape = RoundedCornerShape(10.dp)
private val PopupPadding = PaddingValues(10.dp)
private val GridGap = 8.dp
private val CardHeight = 72.dp
private val CardRadius = 10.dp
private val CardShape = RoundedCornerShape(CardRadius)
private val CheckBadgeSize = 16.dp
private val CheckIconSize = 11.dp
private val RingGap = 2.dp
private val RingStroke = 2.dp
private const val HOVER_OVERLAY_ALPHA = 0.14f
private const val HOVER_ANIMATION_MS = 120

// 模式胶囊左侧的极光小圆球，取当前模式的底色与高光/暗角渐变
@Composable
fun PersonalFmModeOrb(
    mode: PersonalFmMode,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    val base = Color(mode.baseColor)
    val light = Color(mode.lightColor)
    val dark = Color(mode.darkColor)
    Canvas(modifier.size(size).clip(CircleShape)) {
        val diameter = this.size.minDimension
        drawRect(base)
        val lightCenter = Offset(diameter * 0.22f, diameter * 0.22f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(light.copy(alpha = 0.95f), light.copy(alpha = 0f)),
                center = lightCenter,
                radius = diameter * 0.72f
            ),
            radius = diameter * 0.72f,
            center = lightCenter
        )
        val darkCenter = Offset(diameter * 0.80f, diameter * 0.80f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(dark.copy(alpha = 0.92f), dark.copy(alpha = 0f)),
                center = darkCenter,
                radius = diameter * 0.72f
            ),
            radius = diameter * 0.72f,
            center = darkCenter
        )
    }
}

// 正在播放顶栏使用的半透明胶囊入口：极光圆球 + 「XX模式」 + 下拉箭头
@Composable
fun PersonalFmModePill(
    mode: PersonalFmMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier
            .clip(CircleShape)
            .background(if (hovered) DesktopColors.Selected else DesktopColors.Fill)
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        PersonalFmModeOrb(mode = mode, size = 18.dp)
        Text(
            text = "${mode.displayName}模式",
            color = DesktopColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        Icon(
            imageVector = MelodiaIcons.ChevronDown,
            contentDescription = "切换FM模式",
            tint = DesktopColors.TextPrimary.copy(alpha = 0.8f),
            modifier = Modifier.size(14.dp)
        )
    }
}

// 私人FM模式选择悬浮弹层：3列×2行极光渐变小卡片
@Composable
fun PersonalFmModePopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    currentMode: PersonalFmMode,
    onSelectMode: (PersonalFmMode) -> Unit,
    offset: DpOffset = DpOffset(0.dp, 6.dp)
) {
    DesktopMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        offset = offset,
        width = PopupWidth,
        shape = PopupShape,
        contentPadding = PopupPadding
    ) {
        PersonalFmModeGrid(
            currentMode = currentMode,
            onSelectMode = { mode ->
                onDismiss()
                onSelectMode(mode)
            }
        )
    }
}

// 3列×2行极光渐变模式卡片网格，供独立弹层与二级菜单共用
@Composable
fun PersonalFmModeGrid(
    currentMode: PersonalFmMode,
    onSelectMode: (PersonalFmMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GridGap)
    ) {
        PersonalFmMode.entries.chunked(3).forEach { rowModes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GridGap)
            ) {
                rowModes.forEach { mode ->
                    PersonalFmModeAuroraCard(
                        mode = mode,
                        selected = mode == currentMode,
                        onClick = { onSelectMode(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalFmModeAuroraCard(
    mode: PersonalFmMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val hoverAlpha by animateFloatAsState(
        targetValue = if (hovered) HOVER_OVERLAY_ALPHA else 0f,
        animationSpec = uiTween(HOVER_ANIMATION_MS),
        label = "fmModeHover"
    )
    val accentColor = DesktopColors.Accent
    val base = Color(mode.baseColor)
    val light = Color(mode.lightColor)
    val dark = Color(mode.darkColor)

    Box(
        modifier = modifier
            .height(CardHeight)
            .then(
                if (selected) {
                    Modifier.drawBehind {
                        val gapPx = RingGap.toPx()
                        val strokePx = RingStroke.toPx()
                        // 外圈留出 2dp 间隙后再画主题色描边，不挤压卡片本身尺寸
                        val expand = gapPx + strokePx / 2f
                        val corner = CardRadius.toPx() + expand
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(-expand, -expand),
                            size = Size(size.width + expand * 2f, size.height + expand * 2f),
                            cornerRadius = CornerRadius(corner, corner),
                            style = Stroke(width = strokePx)
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CardShape)
                .hoverable(source)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val blobRadius = max(w, h) * 0.78f
                drawRect(base)
                val lightCenter = Offset(w * 0.14f, h * 0.08f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(light.copy(alpha = 0.88f), light.copy(alpha = 0f)),
                        center = lightCenter,
                        radius = blobRadius
                    ),
                    radius = blobRadius,
                    center = lightCenter
                )
                val darkCenter = Offset(w * 0.86f, h * 0.92f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(dark.copy(alpha = 0.86f), dark.copy(alpha = 0f)),
                        center = darkCenter,
                        radius = blobRadius * 0.95f
                    ),
                    radius = blobRadius * 0.95f,
                    center = darkCenter
                )
                if (hoverAlpha > 0f) {
                    drawRect(Color.White.copy(alpha = hoverAlpha))
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(CheckBadgeSize)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MelodiaIcons.Check,
                        contentDescription = "当前模式",
                        tint = base,
                        modifier = Modifier.size(CheckIconSize)
                    )
                }
            }
            Text(
                text = mode.displayName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            )
        }
    }
}
