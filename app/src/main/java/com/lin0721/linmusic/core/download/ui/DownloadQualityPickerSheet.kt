package com.lin0721.linmusic.core.download.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.download.satisfies
import com.lin0721.linmusic.core.model.DOWNLOAD_QUALITY_LEVELS
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.model.qualityRank
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import org.koin.compose.koinInject

// VIP 与 SVIP 专属档位
private val VIP_TIERS = setOf("lossless", "hires")
private val SVIP_TIERS = setOf("jyeffect", "sky", "jymaster")

private fun requiredPlanLabel(quality: String): String? = when {
    quality in SVIP_TIERS -> "SVIP"
    quality in VIP_TIERS -> "VIP"
    else -> null
}

@Composable
@ReadOnlyComposable
private fun requiredPlanColor(quality: String): Color = if (quality in SVIP_TIERS) MelodiaColors.Vip else MelodiaColors.Accent

// 各档位的格式与规格说明
private fun qualityDescription(quality: String): String = when (quality) {
    "standard" -> "128kbps · 体积最小"
    "higher" -> "192kbps"
    "exhigh" -> "最高 320kbps"
    "lossless" -> "FLAC · 最高 48kHz/16bit"
    "hires" -> "FLAC · 最高 192kHz/24bit"
    "jyeffect" -> "高清环绕声效"
    "sky" -> "沉浸式空间音频"
    "jymaster" -> "母带级 · 最高 192kHz/24bit"
    else -> ""
}

// 下载音质选择弹窗
// 单曲模式传 songId：标出已下载音质，选择已满足的档位不再重复入队；
// 批量模式传 batchSongIds：按档位提示将下载与将跳过的数量
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualityPickerSheet(
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    songId: Long? = null,
    maxDownloadLevel: String? = null,
    headline: String? = null,
    supportingText: String? = null,
    batchSongIds: List<Long>? = null
) {
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
    val maxRank = maxDownloadLevel?.let(::qualityRank)?.takeIf { it >= 0 }
    val downloadPreferences: DownloadPreferences = koinInject()

    val singleRecord: DownloadRecord? = if (songId != null) {
        val record by downloadPreferences.downloadedRecordFor(songId).collectAsStateWithLifecycle(initialValue = null)
        record
    } else {
        null
    }

    // 批量模式一次性读取已下载记录，null 表示仍在校验
    val batchRecords: List<DownloadRecord>? = if (batchSongIds != null) {
        val records by produceState<List<DownloadRecord>?>(initialValue = null, batchSongIds) {
            value = downloadPreferences.findVerifiedRecords(batchSongIds)
        }
        records
    } else {
        null
    }
    val batchTotal = batchSongIds?.distinct()?.size ?: 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = MelodiaSpacing.md, end = MelodiaSpacing.md, bottom = MelodiaSpacing.lg)
        ) {
            Column(modifier = Modifier.padding(horizontal = MelodiaSpacing.sm)) {
                Text(
                    text = "选择下载音质",
                    color = MelodiaColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                val context = listOfNotNull(
                    headline?.takeIf { it.isNotBlank() },
                    supportingText?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (context.isNotEmpty()) {
                    Spacer(Modifier.height(MelodiaSpacing.xs))
                    Text(
                        text = context,
                        color = MelodiaColors.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (batchSongIds != null) {
                    Spacer(Modifier.height(MelodiaSpacing.xs))
                    Text(
                        text = "已下载同等或更高音质的歌曲会自动跳过",
                        color = MelodiaColors.TextSecondary.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(MelodiaSpacing.sm))

            DOWNLOAD_QUALITY_LEVELS.forEach { quality ->
                val planLabel = requiredPlanLabel(quality)
                val needsHigherPlan = if (maxRank != null) qualityRank(quality) > maxRank else planLabel != null
                val coveredBySingle = singleRecord?.satisfies(quality) == true
                val batchDownloaded = batchRecords?.count { it.satisfies(quality) }

                QualityRow(
                    title = getQualityDisplayName(quality),
                    description = qualityDescription(quality),
                    status = when {
                        singleRecord?.quality == quality -> RowStatus("已下载", highlighted = true)
                        coveredBySingle -> RowStatus("已有更高音质")
                        batchSongIds == null -> null
                        batchDownloaded == null -> RowStatus("统计中…")
                        batchTotal in 1..batchDownloaded -> RowStatus("已全部下载", highlighted = true)
                        batchDownloaded > 0 -> RowStatus("将下载 ${batchTotal - batchDownloaded} 首 · 跳过 $batchDownloaded 首")
                        else -> null
                    },
                    planBadge = if (needsHigherPlan) planLabel ?: "会员" else null,
                    planColor = requiredPlanColor(quality),
                    dimmed = coveredBySingle && singleRecord?.quality != quality,
                    onClick = {
                        if (coveredBySingle) {
                            ToastManager.showToast("已下载${getQualityDisplayName(singleRecord?.quality ?: quality)}，无需重复下载")
                            onDismiss()
                        } else {
                            onQualitySelected(quality)
                        }
                    }
                )
            }
        }
    }
}

// 档位右侧的下载状态，highlighted 以绿色描边标签展示
private data class RowStatus(val text: String, val highlighted: Boolean = false)

@Composable
private fun QualityRow(
    title: String,
    description: String,
    status: RowStatus?,
    planBadge: String?,
    planColor: Color,
    dimmed: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = MelodiaSpacing.sm, vertical = 10.dp)
            .alpha(if (dimmed) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = MelodiaColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (planBadge != null) {
                    Spacer(Modifier.width(6.dp))
                    OutlinedBadge(text = planBadge, color = planColor)
                }
            }
            if (description.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    color = MelodiaColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        if (status != null) {
            Spacer(Modifier.width(MelodiaSpacing.sm))
            if (status.highlighted) {
                OutlinedBadge(text = status.text, color = MelodiaColors.Success)
            } else {
                Text(
                    text = status.text,
                    color = MelodiaColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OutlinedBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
