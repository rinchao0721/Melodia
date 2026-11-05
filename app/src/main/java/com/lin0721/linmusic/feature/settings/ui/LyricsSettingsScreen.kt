package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricAlign
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricPalette
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricSecondary
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricShadow
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricStyle
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.feature.player.ui.LyricCapsuleSlider
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettingsView(viewModel: SettingsViewModel) {
    val showDesktopLrc by viewModel.showDesktopLrc.collectAsStateWithLifecycle()
    val floatingStyle by viewModel.floatingLyricStyle.collectAsStateWithLifecycle()
    val floatingLocked by viewModel.floatingLyricLocked.collectAsStateWithLifecycle()
    val superLyricEnabled by viewModel.superLyricEnabled.collectAsStateWithLifecycle()
    val lyricInfoEnabled by viewModel.lyricInfoEnabled.collectAsStateWithLifecycle()
    val bluetoothLyricEnabled by viewModel.bluetoothLyricEnabled.collectAsStateWithLifecycle()
    val lyriconEnabled by viewModel.lyriconEnabled.collectAsStateWithLifecycle()
    val fullScreenLyricTextSize by viewModel.fullScreenLyricTextSize.collectAsStateWithLifecycle()
    val fullScreenLyricAlignment by viewModel.fullScreenLyricAlignment.collectAsStateWithLifecycle()
    val fullScreenKaraokeAdvancedEffect by viewModel.fullScreenKaraokeAdvancedEffect.collectAsStateWithLifecycle()
    val fluidCloudLyricEnabled by viewModel.fluidCloudLyricEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var floatingSheet by remember { mutableStateOf<FloatingSheet?>(null) }
    var showAlignmentSheet by remember { mutableStateOf(false) }

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
                    val isExpanded = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded
                    val fontSizeRange = if (isExpanded) 20f..40f else 16f..32f
                    val displayFontSize = fullScreenLyricTextSize.coerceIn(
                        fontSizeRange.start.roundToInt(),
                        fontSizeRange.endInclusive.roundToInt()
                    )

                    LaunchedEffect(fontSizeRange) {
                        if (fullScreenLyricTextSize !in fontSizeRange.start.roundToInt()..fontSizeRange.endInclusive.roundToInt()) {
                            viewModel.updateFullScreenLyricTextSize(displayFontSize)
                        }
                    }

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
                                text = "${displayFontSize} sp",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LyricCapsuleSlider(
                            value = displayFontSize,
                            onValueChange = { viewModel.updateFullScreenLyricTextSize(it) },
                            valueRange = fontSizeRange
                        )
                    }
                    HorizontalDivider(color = MelodiaColors.Divider)
                    SettingsRow(
                        title = "歌词对齐方式",
                        subtitle = alignmentLabel,
                        onClick = { showAlignmentSheet = true }
                    )
                    HorizontalDivider(color = MelodiaColors.Divider)
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
                    if (showDesktopLrc) {
                        HorizontalDivider(color = MelodiaColors.Divider)
                        SettingsSwitchRow(
                            title = "锁定悬浮歌词",
                            subtitle = "锁定后不可拖动与点击，可在播放通知中解锁",
                            checked = floatingLocked,
                            onCheckedChange = { viewModel.updateFloatingLyricLocked(it) }
                        )
                        FloatingSheet.entries.filterNot { it.shadowDetail }.forEach { sheet ->
                            HorizontalDivider(color = MelodiaColors.Divider)
                            SettingsRow(
                                title = sheet.title,
                                subtitle = sheet.currentLabel(floatingStyle),
                                onClick = { floatingSheet = sheet }
                            )
                        }
                        HorizontalDivider(color = MelodiaColors.Divider)
                        SettingsSwitchRow(
                            title = "文字阴影",
                            subtitle = "浅色壁纸上也能看清歌词",
                            checked = floatingStyle.shadow,
                            onCheckedChange = { on -> viewModel.updateFloatingLyricStyle { it.copy(shadow = on) } }
                        )
                        if (floatingStyle.shadow) {
                            FloatingSheet.entries.filter { it.shadowDetail }.forEach { sheet ->
                                HorizontalDivider(color = MelodiaColors.Divider)
                                SettingsRow(
                                    title = sheet.title,
                                    subtitle = sheet.currentLabel(floatingStyle),
                                    onClick = { floatingSheet = sheet }
                                )
                            }
                        }
                        HorizontalDivider(color = MelodiaColors.Divider)
                        SettingsSwitchRow(
                            title = "粗体",
                            subtitle = "关闭后使用常规字重",
                            checked = floatingStyle.bold,
                            onCheckedChange = { on -> viewModel.updateFloatingLyricStyle { it.copy(bold = on) } }
                        )
                    }
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
                    HorizontalDivider(color = MelodiaColors.Divider)
                    SettingsSwitchRow(
                        title = "LyricInfo 系统歌词（测试）",
                        subtitle = "向系统媒体会话元数据注入整轨歌词 JSON，供锁屏岛等插件读取",
                        checked = lyricInfoEnabled,
                        onCheckedChange = { viewModel.updateLyricInfoEnabled(it) }
                    )
                    HorizontalDivider(color = MelodiaColors.Divider)
                    SettingsSwitchRow(
                        title = "车载蓝牙歌词（测试）",
                        subtitle = "播放时将当前行歌词实时同步至蓝牙设备标题栏，暂停时恢复原曲名",
                        checked = bluetoothLyricEnabled,
                        onCheckedChange = { viewModel.updateBluetoothLyricEnabled(it) }
                    )
                    HorizontalDivider(color = MelodiaColors.Divider)
                    SettingsSwitchRow(
                        title = "Lyricon 词幕投屏（测试）",
                        subtitle = "向 Lyricon 独立词幕服务同步当前播放曲目与歌词进度",
                        checked = lyriconEnabled,
                        onCheckedChange = { viewModel.updateLyriconEnabled(it) }
                    )
                    HorizontalDivider(color = MelodiaColors.Divider)
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
                dragHandle = { MelodiaDragHandle() }
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

        floatingSheet?.let { sheet ->
            FloatingOptionSheet(
                sheet = sheet,
                style = floatingStyle,
                onSelect = { transform ->
                    viewModel.updateFloatingLyricStyle(transform)
                    floatingSheet = null
                },
                onDismiss = { floatingSheet = null }
            )
        }
    }
}

// 悬浮歌词各项样式的选项弹层，选项与当前值都从样式对象读写
// shadowDetail 为真的项只在开启文字阴影后显示
private enum class FloatingSheet(val title: String, val shadowDetail: Boolean = false) {
    SIZE("悬浮歌词字号"),
    PLAYED_COLOR("已播颜色"),
    UNPLAYED_COLOR("未播颜色"),
    SECONDARY("第二行"),
    ALIGN("对齐方式"),
    BACKGROUND("背景不透明度"),
    SHADOW_ALPHA("阴影强度", shadowDetail = true),
    SHADOW_BLUR("阴影模糊", shadowDetail = true),
    SHADOW_COLOR("阴影颜色", shadowDetail = true);

    val isColor: Boolean get() = this == PLAYED_COLOR || this == UNPLAYED_COLOR || this == SHADOW_COLOR

    fun options(): List<Pair<String, String>> = when (this) {
        SIZE -> FloatingLyricFontSizes.map { it.toString() to "$it sp" }
        PLAYED_COLOR -> FloatingLyricPalette.played.map { it.hex to it.label }
        UNPLAYED_COLOR -> FloatingLyricPalette.unplayed.map { it.hex to it.label }
        SECONDARY -> FloatingLyricSecondary.entries.map { it.key to it.label }
        ALIGN -> FloatingLyricAlign.entries.map { it.key to it.label }
        BACKGROUND -> FloatingLyricBackgroundAlphas.map { it.toString() to "$it%" }
        SHADOW_ALPHA -> FloatingLyricShadow.alphas.map { it.toString() to "$it%" }
        SHADOW_BLUR -> FloatingLyricShadow.blursDp.map { it.toString() to FloatingLyricShadow.blurLabel(it) }
        SHADOW_COLOR -> FloatingLyricShadow.colors.map { it.hex to it.label }
    }

    fun currentKey(style: FloatingLyricStyle): String = when (this) {
        SIZE -> style.fontSizeSp.toString()
        PLAYED_COLOR -> style.playedColor
        UNPLAYED_COLOR -> style.unplayedColor
        SECONDARY -> style.secondary.key
        ALIGN -> style.align.key
        BACKGROUND -> style.backgroundAlpha.toString()
        SHADOW_ALPHA -> style.shadowAlpha.toString()
        SHADOW_BLUR -> style.shadowBlurDp.toString()
        SHADOW_COLOR -> style.shadowColor
    }

    fun currentLabel(style: FloatingLyricStyle): String {
        val key = currentKey(style)
        return options().firstOrNull { it.first.equals(key, ignoreCase = true) }?.second ?: key
    }

    // 选项 key 均由 options() 生成，数字解析失败时保持原样式
    fun apply(style: FloatingLyricStyle, key: String): FloatingLyricStyle = when (this) {
        SIZE -> key.toIntOrNull()?.let { style.copy(fontSizeSp = it) } ?: style
        PLAYED_COLOR -> style.copy(playedColor = key)
        UNPLAYED_COLOR -> style.copy(unplayedColor = key)
        SECONDARY -> style.copy(secondary = FloatingLyricSecondary.fromKey(key))
        ALIGN -> style.copy(align = FloatingLyricAlign.fromKey(key))
        BACKGROUND -> key.toIntOrNull()?.let { style.copy(backgroundAlpha = it) } ?: style
        SHADOW_ALPHA -> key.toIntOrNull()?.let { style.copy(shadowAlpha = it) } ?: style
        SHADOW_BLUR -> key.toIntOrNull()?.let { style.copy(shadowBlurDp = it) } ?: style
        SHADOW_COLOR -> style.copy(shadowColor = key)
    }
}

private val FloatingLyricFontSizes = listOf(12, 14, 16, 18, 20, 22, 24)
private val FloatingLyricBackgroundAlphas = listOf(0, 20, 40, 60, 70, 80)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingOptionSheet(
    sheet: FloatingSheet,
    style: FloatingLyricStyle,
    onSelect: ((FloatingLyricStyle) -> FloatingLyricStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentKey = sheet.currentKey(style)
    val isColor = sheet.isColor
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
                .navigationBarsPadding()
                .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "选择${sheet.title}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )
            sheet.options().forEach { (key, label) ->
                val isSelected = key.equals(currentKey, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect { sheet.apply(it, key) } }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isColor) {
                            Box(
                                Modifier
                                    .padding(end = 12.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, MelodiaColors.Overlay.copy(alpha = 0.3f), CircleShape)
                                    .background(Color((FloatingLyricPalette.parseArgb(key) ?: 0xFFFFFFFFL).toInt()))
                            )
                        }
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
