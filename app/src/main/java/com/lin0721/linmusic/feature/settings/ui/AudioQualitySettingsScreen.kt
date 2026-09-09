package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualitySettingsView(viewModel: SettingsViewModel) {
    val wifiQuality by viewModel.wifiQuality.collectAsStateWithLifecycle()
    val mobileQuality by viewModel.mobileQuality.collectAsStateWithLifecycle()

    var qualityDialogTarget by remember { mutableStateOf<String?>(null) }

    // 渲染音质的子设置项
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        item {
            SettingsGroupCard("默认音质") {
                SettingsRow(
                    title = "Wi-Fi 环境播放音质",
                    subtitle = getQualityDisplayName(wifiQuality),
                    onClick = { qualityDialogTarget = "wifi" }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                SettingsRow(
                    title = "移动网络环境播放音质",
                    subtitle = getQualityDisplayName(mobileQuality),
                    onClick = { qualityDialogTarget = "mobile" }
                )
            }
        }

    }

    // 音质单选选择弹层
    val qualityTarget = qualityDialogTarget
    if (qualityTarget != null) {
        val qualities = listOf(
            "standard" to "标准音质",
            "exhigh" to "极高音质",
            "lossless" to "无损音质 (FLAC)",
            "hires" to "Hi-Res 无损",
            "jymaster" to "超清母带"
        )
        val isWifi = qualityTarget == "wifi"
        val activeQuality = if (isWifi) wifiQuality else mobileQuality
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { qualityDialogTarget = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
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
                    .navigationBarsPadding()
                    .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
            ) {
                Text(
                    text = if (isWifi) "选择 Wi-Fi 播放音质" else "选择移动网络播放音质",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                )

                qualities.forEach { (key, label) ->
                    val isSelected = activeQuality == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isWifi) {
                                    viewModel.updateWifiQuality(key)
                                } else {
                                    viewModel.updateMobileQuality(key)
                                }
                                qualityDialogTarget = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
