package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.PlaybackQuality
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.model.qualityBadgeOf
import com.lin0721.linmusic.core.model.resolveQualityLevel
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlin.math.roundToInt

private const val QUALITY_MISSING_TEXT = "—"

// 播放页音质徽标展示快照：文案与完整音质详情
data class PlaybackQualityDisplay(
    val quality: PlaybackQuality,
    val label: String
)

// 音质详情弹窗中的单条参数行
data class PlaybackQualityDetailItem(
    val label: String,
    val value: String,
    val accentColorArgb: Long? = null
)

// 将编码 MIME 或短名转为可读展示文案
internal fun formatCodecLabel(raw: String?): String? {
    val normalized = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return when (normalized) {
        "audio/flac", "audio/x-flac", "flac", "x-flac" -> "FLAC"
        "audio/alac", "alac" -> "ALAC"
        "audio/mpeg", "mp3" -> "MP3"
        "audio/mp4a-latm", "aac", "mp4a" -> "AAC"
        "audio/vorbis", "vorbis" -> "Vorbis"
        "audio/opus", "opus" -> "Opus"
        "audio/raw", "raw" -> "PCM"
        "audio/wav", "audio/x-wav", "audio/vnd.wave", "wav", "x-wav", "vnd.wave" -> "WAV"
        "audio/aiff", "audio/x-aiff", "aiff", "x-aiff" -> "AIFF"
        "audio/ape", "audio/x-ape", "ape", "x-ape" -> "APE"
        "audio/eac3-joc", "eac3-joc" -> "E-AC-3 JOC"
        "audio/eac3", "eac3" -> "E-AC-3"
        "audio/ac3", "ac3" -> "AC-3"
        "audio/ac4", "ac4" -> "AC-4"
        "audio/true-hd", "audio/truehd", "true-hd", "truehd" -> "Dolby TrueHD"
        else -> normalized.removePrefix("audio/").uppercase()
    }
}

internal fun formatSampleRateLabel(sampleRateHz: Int?): String? {
    val hz = sampleRateHz?.takeIf { it > 0 } ?: return null
    if (hz < 1000) return "$hz Hz"
    if (hz % 1000 == 0) return "${hz / 1000} kHz"
    val whole = hz / 1000
    val frac = (hz % 1000).toString().padStart(3, '0').trimEnd('0')
    return "$whole.$frac kHz"
}

internal fun formatBitDepthLabel(bitDepth: Int?): String? =
    bitDepth?.takeIf { it > 0 }?.let { "$it bit" }

internal fun formatBitrateLabel(bitrateBps: Int?): String? =
    bitrateBps?.takeIf { it > 0 }?.let { "${(it / 1000.0).roundToInt().coerceAtLeast(1)} kbps" }

internal fun formatChannelCountLabel(channels: Int?): String? =
    channels?.takeIf { it > 0 }?.let {
        when (it) {
            1 -> "单声道"
            2 -> "立体声"
            else -> "$it 声道"
        }
    }

// 构建音质详情弹窗展示条目列表，缺失项统一展示 "—"
fun buildPlaybackQualityDetailItems(quality: PlaybackQuality): List<PlaybackQualityDetailItem> {
    val resolvedLevel = resolveQualityLevel(quality.declaredLevel, quality.stream)
    val resolvedColorArgb = resolvedLevel?.let { qualityBadgeOf(it)?.colorArgb }
    val declaredLevel = quality.declaredLevel?.trim()?.takeIf { it.isNotEmpty() }
    val stream = quality.stream

    return listOf(
        PlaybackQualityDetailItem(
            label = "判定档位",
            value = resolvedLevel?.let(::getQualityDisplayName) ?: QUALITY_MISSING_TEXT,
            accentColorArgb = resolvedColorArgb
        ),
        PlaybackQualityDetailItem(
            label = "接口声明档位",
            value = declaredLevel?.let(::getQualityDisplayName) ?: QUALITY_MISSING_TEXT
        ),
        PlaybackQualityDetailItem(
            label = "编码",
            value = formatCodecLabel(stream?.codec) ?: QUALITY_MISSING_TEXT
        ),
        PlaybackQualityDetailItem(
            label = "采样率",
            value = formatSampleRateLabel(stream?.sampleRateHz) ?: QUALITY_MISSING_TEXT
        ),
        PlaybackQualityDetailItem(
            label = "位深",
            value = formatBitDepthLabel(stream?.bitDepth) ?: QUALITY_MISSING_TEXT
        ),
        PlaybackQualityDetailItem(
            label = "码率",
            value = formatBitrateLabel(stream?.bitrateBps) ?: QUALITY_MISSING_TEXT
        ),
        PlaybackQualityDetailItem(
            label = "声道",
            value = formatChannelCountLabel(stream?.channels) ?: QUALITY_MISSING_TEXT
        ),
        PlaybackQualityDetailItem(
            label = "输出设备",
            value = quality.output?.deviceName?.trim()?.takeIf { it.isNotEmpty() } ?: QUALITY_MISSING_TEXT
        )
    )
}

// 播放页音质详情底部弹窗：展示当前流解码参数与输出设备
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackQualityDetailSheet(
    quality: PlaybackQuality,
    onDismiss: () -> Unit
) {
    val items = remember(quality) { buildPlaybackQualityDetailItems(quality) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nestedScrollConnection = remember(sheetState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheetState.targetValue == SheetValue.Expanded && available.y < 0) {
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "音质详情",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MelodiaColors.Overlay.copy(alpha = 0.05f))
            ) {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MelodiaSpacing.md, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = MelodiaSpacing.md)
                        )
                        Text(
                            text = item.value,
                            color = item.accentColorArgb?.let(::Color) ?: MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = MelodiaSpacing.md),
                            color = MelodiaColors.Divider
                        )
                    }
                }
            }
        }
    }
}
