package com.lin0721.linmusic.desktop.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.OutputAudioInfo
import com.lin0721.linmusic.core.model.PlaybackQuality
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.model.playbackQualityLabel
import com.lin0721.linmusic.core.model.qualityBadgeOf
import com.lin0721.linmusic.core.model.resolveQualityLevel
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlin.math.roundToInt

internal const val QUALITY_MISSING_TEXT = "—"
private const val QUALITY_ANIM_MS = 220

private val BadgeShape = RoundedCornerShape(3.dp)
private val TooltipCardShape = RoundedCornerShape(6.dp)

internal data class QualityBadgeVisual(
    val label: String,
    val colorArgb: Long?
)

private data class QualityBadgeRenderState(
    val quality: PlaybackQuality,
    val visual: QualityBadgeVisual
)

internal fun resolveQualityBadgeVisual(quality: PlaybackQuality?): QualityBadgeVisual? {
    val q = quality ?: return null
    val label = playbackQualityLabel(q) ?: return null
    val resolvedLevel = resolveQualityLevel(q.declaredLevel, q.stream)
    return QualityBadgeVisual(
        label = label,
        colorArgb = qualityBadgeOf(resolvedLevel)?.colorArgb
    )
}

internal fun formatCodecText(codec: String?): String =
    codec?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: QUALITY_MISSING_TEXT

internal fun formatSampleRateText(sampleRateHz: Int?): String {
    val hz = sampleRateHz?.takeIf { it > 0 } ?: return QUALITY_MISSING_TEXT
    if (hz % 1000 == 0) return "${hz / 1000} kHz"
    val whole = hz / 1000
    val frac = (hz % 1000).toString().padStart(3, '0').trimEnd('0')
    return "$whole.$frac kHz"
}

internal fun formatBitDepthText(bitDepth: Int?): String =
    bitDepth?.takeIf { it > 0 }?.let { "$it bit" } ?: QUALITY_MISSING_TEXT

internal fun formatBitrateText(bitrateBps: Int?): String =
    bitrateBps?.takeIf { it > 0 }?.let { "${(it / 1000.0).roundToInt().coerceAtLeast(1)} kbps" } ?: QUALITY_MISSING_TEXT

internal fun formatChannelsText(channels: Int?): String = when (channels?.takeIf { it > 0 }) {
    null -> QUALITY_MISSING_TEXT
    1 -> "1 声道 (单声道)"
    2 -> "2 声道 (立体声)"
    else -> "$channels 声道"
}

internal fun formatOutputDeviceText(deviceName: String?): String =
    deviceName?.trim()?.takeIf { it.isNotEmpty() } ?: QUALITY_MISSING_TEXT

internal fun formatOutputFormatText(output: OutputAudioInfo?): String {
    if (output == null) return QUALITY_MISSING_TEXT
    val rate = output.sampleRateHz?.takeIf { it > 0 }?.let(::formatSampleRateText)
    val format = output.format?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        rate != null && format != null -> "$rate / $format"
        rate != null -> rate
        format != null -> format
        else -> QUALITY_MISSING_TEXT
    }
}

internal fun formatResampledText(resampled: Boolean?): String = when (resampled) {
    true -> "是"
    false -> "否"
    null -> QUALITY_MISSING_TEXT
}

internal fun formatDeclaredLevelText(declaredLevel: String?): String =
    declaredLevel?.trim()?.takeIf { it.isNotEmpty() }?.let(::getQualityDisplayName) ?: QUALITY_MISSING_TEXT

// 播放栏音质标签：支持从无到有展开与档位切换平滑过渡，悬停显示流参数小卡片，点击弹出详情面板
@Composable
internal fun PlaybackQualityBadge(
    quality: PlaybackQuality?,
    modifier: Modifier = Modifier
) {
    val renderState = quality?.let { q ->
        resolveQualityBadgeVisual(q)?.let { QualityBadgeRenderState(quality = q, visual = it) }
    }
    var showDetail by remember { mutableStateOf(false) }
    val detailQuality = renderState?.quality
    LaunchedEffect(detailQuality == null) {
        if (detailQuality == null) showDetail = false
    }

    val motion = LocalUiMotion.current
    AnimatedContent(
        targetState = renderState,
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
            motion.transform(
                (fadeIn(tween(QUALITY_ANIM_MS)) togetherWith fadeOut(tween(QUALITY_ANIM_MS)))
                    .using(SizeTransform(clip = true) { _, _ -> tween(QUALITY_ANIM_MS) })
            )
        },
        contentKey = { it?.visual },
        label = "playbackQualityBadge"
    ) { target ->
        if (target != null) {
            val badgeColor = target.visual.colorArgb?.let(::Color) ?: DesktopColors.TextGray
            // 间距放在动画槽位内，无标签时收成 0 宽不留空白
            Box(Modifier.padding(end = 6.dp)) {
                DesktopTooltip(
                    tooltipContent = { PlaybackQualityHoverCard(target.quality) },
                    side = TooltipSide.Top
                ) {
                    Text(
                        text = target.visual.label,
                        color = badgeColor,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(BadgeShape)
                            .background(badgeColor.copy(alpha = 0.1f), BadgeShape)
                            .border(0.5.dp, badgeColor, BadgeShape)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { showDetail = true }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }

    if (showDetail && detailQuality != null) {
        PlaybackQualityDetailDialog(
            quality = detailQuality,
            onDismiss = { showDetail = false }
        )
    }
}

@Composable
private fun PlaybackQualityHoverCard(quality: PlaybackQuality) {
    val stream = quality.stream
    Column(
        Modifier
            .widthIn(min = 176.dp, max = 240.dp)
            .clip(TooltipCardShape)
            .background(DesktopColors.PopupSurface)
            .floatingOutline(TooltipCardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        QualityInfoRow("编码", formatCodecText(stream?.codec))
        QualityInfoRow("采样率", formatSampleRateText(stream?.sampleRateHz))
        QualityInfoRow("位深", formatBitDepthText(stream?.bitDepth))
        QualityInfoRow("码率", formatBitrateText(stream?.bitrateBps))
        QualityInfoRow("声道", formatChannelsText(stream?.channels))
    }
}

@Composable
private fun PlaybackQualityDetailDialog(
    quality: PlaybackQuality,
    onDismiss: () -> Unit
) {
    val stream = quality.stream
    val output = quality.output
    DesktopDialog(
        title = "音质详情",
        onDismiss = onDismiss,
        width = 380.dp,
        actions = {
            DialogButton("关闭", onClick = onDismiss, primary = true)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QualitySectionTitle("音频流")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityDetailRow("编码", formatCodecText(stream?.codec))
                QualityDetailRow("采样率", formatSampleRateText(stream?.sampleRateHz))
                QualityDetailRow("位深", formatBitDepthText(stream?.bitDepth))
                QualityDetailRow("码率", formatBitrateText(stream?.bitrateBps))
                QualityDetailRow("声道", formatChannelsText(stream?.channels))
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(DesktopColors.Divider))
            QualitySectionTitle("输出与来源")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityDetailRow("输出设备", formatOutputDeviceText(output?.deviceName))
                QualityDetailRow("输出采样率/格式", formatOutputFormatText(output))
                QualityDetailRow("是否重采样", formatResampledText(output?.resampled))
                QualityDetailRow("接口声明档位", formatDeclaredLevelText(quality.declaredLevel))
            }
        }
    }
}

@Composable
private fun QualitySectionTitle(text: String) {
    Text(
        text = text,
        color = DesktopColors.TextGray,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun QualityInfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = DesktopColors.TextGray,
            fontSize = 11.sp,
            maxLines = 1
        )
        Text(
            text = value,
            color = DesktopColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QualityDetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = DesktopColors.TextGray,
            fontSize = 13.sp,
            maxLines = 1
        )
        Text(
            text = value,
            color = DesktopColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
