package com.lin0721.linmusic.core.download.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DownloadedGreen
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.SvipGold
import org.koin.compose.koinInject

// 可选下载音质档位
private val DOWNLOAD_QUALITIES = listOf(
    "standard", "higher", "exhigh", "lossless", "hires", "jyeffect", "sky", "jymaster"
)

// VIP 与 SVIP 专属档位
private val VIP_TIERS = setOf("lossless", "hires")
private val SVIP_TIERS = setOf("jyeffect", "sky", "jymaster")

private fun qualityRank(level: String): Int = DOWNLOAD_QUALITIES.indexOf(level)

private fun requiredPlanLabel(quality: String): String? = when {
    quality in SVIP_TIERS -> "SVIP"
    quality in VIP_TIERS -> "VIP"
    else -> null
}

private fun requiredPlanColor(quality: String): Color = if (quality in SVIP_TIERS) SvipGold else NeteaseRed

// 下载音质选择弹窗
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualityPickerSheet(
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    songId: Long? = null,
    maxDownloadLevel: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxRank = maxDownloadLevel?.let(::qualityRank)?.takeIf { it >= 0 }

    val downloadedQuality: String? = if (songId != null) {
        val downloadPreferences: DownloadPreferences = koinInject()
        val quality by downloadPreferences.downloadedQualityFor(songId).collectAsStateWithLifecycle(initialValue = null)
        quality
    } else {
        null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundDark,
        shape = BottomSheetShape,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = MelodiaSpacing.xs)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(DragHandleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "选择下载音质",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )
            DOWNLOAD_QUALITIES.forEach { quality ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQualitySelected(quality) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = getQualityDisplayName(quality),
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (quality == downloadedQuality) {
                        Box(
                            modifier = Modifier
                                .border(1.dp, DownloadedGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "已下载",
                                color = DownloadedGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(MelodiaSpacing.xs))
                    }
                    val planLabel = requiredPlanLabel(quality)
                    val needsHigherPlan = if (maxRank != null) {
                        qualityRank(quality) > maxRank
                    } else {
                        planLabel != null
                    }
                    if (needsHigherPlan) {
                        val color = requiredPlanColor(quality)
                        Box(
                            modifier = Modifier
                                .border(1.dp, color, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = planLabel ?: "会员",
                                color = color,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
