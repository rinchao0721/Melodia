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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.player.external.FluidCloudLyricNotifier
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.player.ui.LyricCapsuleSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettingsView(viewModel: SettingsViewModel) {
    val showDesktopLrc by viewModel.showDesktopLrc.collectAsStateWithLifecycle()
    val lyricTextSize by viewModel.lyricTextSize.collectAsStateWithLifecycle()
    val lyricTextColor by viewModel.lyricTextColor.collectAsStateWithLifecycle()
    val superLyricEnabled by viewModel.superLyricEnabled.collectAsStateWithLifecycle()
    val lyricInfoEnabled by viewModel.lyricInfoEnabled.collectAsStateWithLifecycle()
    val bluetoothLyricEnabled by viewModel.bluetoothLyricEnabled.collectAsStateWithLifecycle()
    val lyriconEnabled by viewModel.lyriconEnabled.collectAsStateWithLifecycle()
    val fullScreenLyricTextSize by viewModel.fullScreenLyricTextSize.collectAsStateWithLifecycle()
    val fullScreenLyricAlignment by viewModel.fullScreenLyricAlignment.collectAsStateWithLifecycle()
    val fullScreenKaraokeAdvancedEffect by viewModel.fullScreenKaraokeAdvancedEffect.collectAsStateWithLifecycle()
    val fluidCloudLyricEnabled by viewModel.fluidCloudLyricEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSizeSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }
    var showAlignmentSheet by remember { mutableStateOf(false) }

    val sizeLabel = "${lyricTextSize} sp"
    val colorLabel = when (lyricTextColor) {
        "#FFFFFF" -> "白色"
        "#E03E3E" -> "网易红"
        "#10B981" -> "绿色"
        else -> lyricTextColor
    }
    val alignmentLabel = when (fullScreenLyricAlignment) {
        "center" -> "居中对齐"
        "right" -> "右对齐"
        else -> "左对齐"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
        ) {
            item {
                SettingsGroupCard(SettingsSubMenu.LYRICS.sectionTitles[0]) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("歌词字号大小", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Text(
                                text = "${fullScreenLyricTextSize} sp",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LyricCapsuleSlider(
                            value = fullScreenLyricTextSize,
                            onValueChange = { viewModel.updateFullScreenLyricTextSize(it) },
                            valueRange = 16f..32f
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsRow(
                        title = "歌词对齐方式",
                        subtitle = alignmentLabel,
                        onClick = { showAlignmentSheet = true }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSwitchRow(
                        title = "逐字歌词流光动效",
                        subtitle = "开启柔和渐变推进边缘",
                        checked = fullScreenKaraokeAdvancedEffect,
                        onCheckedChange = { viewModel.updateFullScreenKaraokeAdvancedEffect(it) }
                    )
                }
            }

            item {
                SettingsGroupCard(SettingsSubMenu.LYRICS.sectionTitles[1]) {
                    SettingsSwitchRow(
                        title = "启用桌面悬浮歌词",
                        subtitle = "返回桌面时以悬浮窗形态展示当前播放词句",
                        checked = showDesktopLrc,
                        onCheckedChange = { viewModel.updateShowDesktopLrc(it) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsRow(
                        title = "悬浮歌词字号",
                        subtitle = sizeLabel,
                        onClick = { showSizeSheet = true }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsRow(
                        title = "悬浮歌词颜色",
                        subtitle = colorLabel,
                        onClick = { showColorSheet = true }
                    )
                }
            }

            item {
                SettingsGroupCard(SettingsSubMenu.LYRICS.sectionTitles[2]) {
                    SettingsSwitchRow(
                        title = "SuperLyric 实时歌词（测试）",
                        subtitle = "通过系统 Binder 服务向状态栏或歌词插件广播实时歌词",
                        checked = superLyricEnabled,
                        onCheckedChange = { viewModel.updateSuperLyricEnabled(it) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSwitchRow(
                        title = "LyricInfo 系统歌词（测试）",
                        subtitle = "向系统媒体会话元数据注入整轨歌词 JSON，供锁屏岛等插件读取",
                        checked = lyricInfoEnabled,
                        onCheckedChange = { viewModel.updateLyricInfoEnabled(it) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSwitchRow(
                        title = "车载蓝牙歌词（测试）",
                        subtitle = "播放时将当前行歌词实时同步至蓝牙设备标题栏，暂停时恢复原曲名",
                        checked = bluetoothLyricEnabled,
                        onCheckedChange = { viewModel.updateBluetoothLyricEnabled(it) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSwitchRow(
                        title = "Lyricon 词幕投屏（测试）",
                        subtitle = "向 Lyricon 独立词幕服务同步当前播放曲目与歌词进度",
                        checked = lyriconEnabled,
                        onCheckedChange = { viewModel.updateLyriconEnabled(it) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSwitchRow(
                        title = "状态栏歌词胶囊（测试）",
                        subtitle = "Android 16+ 实时更新通知，支持 ColorOS 流体云、三星 Now Bar、Pixel 状态栏等",
                        checked = fluidCloudLyricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !FluidCloudLyricNotifier.isSupported()) {
                                android.widget.Toast.makeText(
                                    context,
                                    "需要 Android 16 及以上系统",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return@SettingsSwitchRow
                            }
                            viewModel.updateFluidCloudLyricEnabled(enabled)
                            if (enabled && !FluidCloudLyricNotifier.canPostPromoted(context)) {
                                FluidCloudLyricNotifier.buildManagePromotedIntent(context)?.let { intent ->
                                    runCatching { context.startActivity(intent) }
                                }
                            }
                        }
                    )
                }
            }
        }

        if (showAlignmentSheet) {
            val alignments = listOf(
                "left" to "左对齐",
                "center" to "居中对齐",
                "right" to "右对齐"
            )
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { showAlignmentSheet = false },
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
                        text = "选择歌词对齐方式",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                    )

                    alignments.forEach { (key, label) ->
                        val isSelected = fullScreenLyricAlignment == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateFullScreenLyricAlignment(key)
                                    showAlignmentSheet = false
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

        // 字号选择弹层
        if (showSizeSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSizeSheet = false },
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
                        text = "选择悬浮歌词字号",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                    )

                    val options = listOf(12, 14, 16, 18, 20)
                    options.forEach { size ->
                        val isSelected = lyricTextSize == size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateLyricTextSize(size)
                                    showSizeSheet = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${size} sp",
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

        // 颜色选择弹层
        if (showColorSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showColorSheet = false },
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
                        text = "选择悬浮歌词颜色",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                    )

                    val options = listOf(
                        "#FFFFFF" to "白色",
                        "#E03E3E" to "网易红",
                        "#10B981" to "绿色"
                    )
                    options.forEach { (colorCode, label) ->
                        val isSelected = lyricTextColor == colorCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateLyricTextColor(colorCode)
                                    showColorSheet = false
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
}
