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
    val fluidCloudLyricEnabled by viewModel.fluidCloudLyricEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSizeSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }

    val sizeLabel = "${lyricTextSize} sp"
    val colorLabel = when (lyricTextColor) {
        "#FFFFFF" -> "白色"
        "#E03E3E" -> "网易红"
        "#10B981" -> "绿色"
        else -> lyricTextColor
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
        ) {
            item {
                SettingsGroupCard("悬浮歌词") {
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
                SettingsGroupCard("外部与系统歌词") {
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
                        title = "OPPO 流体云歌词（测试）",
                        subtitle = "通过 Android 16 实时更新通知，在 ColorOS 16 流体云 / 状态栏胶囊中显示当前歌词",
                        checked = fluidCloudLyricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !FluidCloudLyricNotifier.isSupported()) {
                                android.widget.Toast.makeText(
                                    context,
                                    "需要 Android 16 及以上系统（ColorOS 16+）",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return@SettingsSwitchRow
                            }
                            viewModel.updateFluidCloudLyricEnabled(enabled)
                            // 用户在系统里关闭过「实时更新」时，引导到授权页重新开启，否则通知只会以普通形式出现
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
