package com.lin0721.linmusic.feature.settings.ui

import com.lin0721.linmusic.core.plugin.PluginRepository
import org.koin.compose.koinInject
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.UiScale
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.feature.player.ui.LyricCapsuleSlider
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass

private val PlayerPageModes = listOf(
    SettingsPreferences.PLAYER_PAGE_MODE_AUTO to "自动",
    SettingsPreferences.PLAYER_PAGE_MODE_FULLSCREEN to "全屏式",
    SettingsPreferences.PLAYER_PAGE_MODE_SIDE to "侧边小窗式"
)

private val SidePlayerPositions = listOf(
    SettingsPreferences.SIDE_PLAYER_POSITION_RIGHT to "右侧",
    SettingsPreferences.SIDE_PLAYER_POSITION_LEFT to "左侧"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsSettingsView(viewModel: SettingsViewModel) {
    val showLockscreen by viewModel.showLockscreen.collectAsStateWithLifecycle()
    val carMode by viewModel.carMode.collectAsStateWithLifecycle()
    val bottomBarLayout by viewModel.bottomBarLayout.collectAsStateWithLifecycle()
    val bottomBarIconOnly by viewModel.bottomBarIconOnly.collectAsStateWithLifecycle()
    val startInLibrary by viewModel.startInLibrary.collectAsStateWithLifecycle()
    val playerPageMode by viewModel.playerPageMode.collectAsStateWithLifecycle()
    val sidePlayerPinned by viewModel.sidePlayerPinned.collectAsStateWithLifecycle()
    val sidePlayerPosition by viewModel.sidePlayerPosition.collectAsStateWithLifecycle()
    val tabletImmersiveMode by viewModel.tabletImmersiveMode.collectAsStateWithLifecycle()
    val uiScalePercent by viewModel.uiScalePercent.collectAsStateWithLifecycle()
    val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
    val uiAnimationsEnabled by viewModel.uiAnimationsEnabled.collectAsStateWithLifecycle()
    val isExpanded = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded
    val pluginRepository = koinInject<PluginRepository>()

    var showBottomBarSheet by remember { mutableStateOf(false) }
    var showPlayerModeSheet by remember { mutableStateOf(false) }
    var showSidePositionSheet by remember { mutableStateOf(false) }
    val playerModeLabel = PlayerPageModes.firstOrNull { it.first == playerPageMode }?.second ?: "自动"
    val sidePositionLabel = SidePlayerPositions.firstOrNull { it.first == sidePlayerPosition }?.second ?: "右侧"
    val playerModeSubtitle = when (playerPageMode) {
        SettingsPreferences.PLAYER_PAGE_MODE_FULLSCREEN -> "全屏式：点击展开，返回直接收起"
        SettingsPreferences.PLAYER_PAGE_MODE_SIDE -> "侧边小窗式：播放页固定在${sidePositionLabel}，与内容并排"
        else -> "自动：竖屏全屏式，横屏侧边小窗式"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
        ) {
            item {
                SettingsGroupCard(SettingsSubMenu.EXTENSIONS.sectionTitles[0]) {
                    SettingsSwitchRow(
                        title = "启用系统锁屏显示",
                        subtitle = "在锁屏界面展示播放控制器与歌词面板",
                        checked = showLockscreen,
                        onCheckedChange = { viewModel.updateShowLockscreen(it) }
                    )
                }
            }

            item {
                SettingsGroupCard(SettingsSubMenu.EXTENSIONS.sectionTitles[1]) {
                    SettingsSwitchRow(
                        title = "车载模式蓝牙自动启动",
                        subtitle = "连接车载蓝牙设备时自动恢复媒体播放",
                        checked = carMode,
                        onCheckedChange = { viewModel.updateCarMode(it) }
                    )
                }
            }

            item {
                SettingsGroupCard(SettingsSubMenu.EXTENSIONS.sectionTitles[2]) {
                    val enabledItemsSummary = remember(bottomBarLayout) {
                        bottomBarLayout.filter { it.visible }.joinToString("、") { it.item.title }
                    }
                    SettingsRow(
                        title = "底栏自定义",
                        subtitle = "当前启用：$enabledItemsSummary",
                        onClick = { showBottomBarSheet = true }
                    )
                    HorizontalDivider(color = MelodiaColors.Divider)
                    SettingsSwitchRow(
                        title = "启动默认打开音乐库",
                        subtitle = "冷启动时直接进入音乐库页面",
                        checked = startInLibrary,
                        onCheckedChange = { viewModel.updateStartInLibrary(it) }
                    )
                }
            }

            item {
                SettingsGroupCard(SettingsSubMenu.EXTENSIONS.sectionTitles[3]) {
                    SettingsRow(
                        title = "播放页模式：$playerModeLabel",
                        subtitle = playerModeSubtitle,
                        onClick = { showPlayerModeSheet = true }
                    )
                    if (playerPageMode != SettingsPreferences.PLAYER_PAGE_MODE_FULLSCREEN) {
                        HorizontalDivider(color = MelodiaColors.Divider)
                        SettingsRow(
                            title = "侧边播放页位置：$sidePositionLabel",
                            subtitle = "侧边播放页与迷你播放条停靠在屏幕${sidePositionLabel}",
                            onClick = { showSidePositionSheet = true }
                        )
                        HorizontalDivider(color = MelodiaColors.Divider)
                        SettingsSwitchRow(
                            title = "侧边播放页钉住",
                            subtitle = "关闭后按返回键直接收起侧边播放页",
                            checked = sidePlayerPinned,
                            onCheckedChange = { viewModel.updateSidePlayerPinned(it) }
                        )
                    }
                    if (isExpanded) {
                        HorizontalDivider(color = MelodiaColors.Divider)
                        SettingsSwitchRow(
                            title = "全局沉浸",
                            subtitle = "隐藏系统状态栏与导航栏，边缘滑动呼出",
                            checked = tabletImmersiveMode,
                            onCheckedChange = { viewModel.updateTabletImmersiveMode(it) }
                        )
                    }
                }
            }

            item {
                SettingsGroupCard(SettingsSubMenu.EXTENSIONS.sectionTitles[4]) {
                    ThemeSettingSection(
                        settings = themeSettings,
                        onModeChange = viewModel::updateThemeMode,
                        onAccentChange = viewModel::updateThemeAccent,
                        onCustomColorChange = viewModel::updateThemeCustomColor,
                    )
                    HorizontalDivider(color = MelodiaColors.Divider)
                    SkinSettingSection(settings = themeSettings, repository = viewModel.skins)
                    HorizontalDivider(color = MelodiaColors.Divider)
                    // 松手后才应用（会重建界面）；拖动中不应用，避免滑块在手指下移位
                    var pendingPercent by remember { mutableStateOf<Int?>(null) }
                    LaunchedEffect(uiScalePercent) { pendingPercent = null }
                    val displayPercent = pendingPercent ?: uiScalePercent
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
                            Text("界面缩放", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Text(
                                text = "$displayPercent%",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "整体缩放界面，松手后生效，界面会重新加载一次",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LyricCapsuleSlider(
                            value = displayPercent,
                            onValueChange = { pendingPercent = it },
                            valueRange = UiScale.MIN_PERCENT.toFloat()..UiScale.MAX_PERCENT.toFloat(),
                            startLabel = "${UiScale.MIN_PERCENT}%",
                            endLabel = "${UiScale.MAX_PERCENT}%",
                            onValueChangeFinished = {
                                pendingPercent?.let { viewModel.updateUiScalePercent(it) }
                            }
                        )
                    }
                    HorizontalDivider(color = MelodiaColors.Divider)
                    SettingsSwitchRow(
                        title = "界面过渡动画",
                        subtitle = "关闭后切页、面板展开收起等直接切换，不影响歌词滚动",
                        checked = uiAnimationsEnabled,
                        onCheckedChange = { viewModel.updateUiAnimationsEnabled(it) }
                    )
                }
            }

            item {
                SettingsGroupCard(SettingsSubMenu.EXTENSIONS.sectionTitles[5]) {
                    PluginSettingSection(repository = pluginRepository)
                }
            }
        }

        if (showPlayerModeSheet) {
            OptionPickerSheet(
                title = "选择播放页模式",
                options = PlayerPageModes,
                selected = playerPageMode,
                onSelect = viewModel::updatePlayerPageMode,
                onDismiss = { showPlayerModeSheet = false }
            )
        }

        if (showSidePositionSheet) {
            OptionPickerSheet(
                title = "选择侧边播放页位置",
                options = SidePlayerPositions,
                selected = sidePlayerPosition,
                onSelect = viewModel::updateSidePlayerPosition,
                onDismiss = { showSidePositionSheet = false }
            )
        }

        if (showBottomBarSheet) {
            BottomBarCustomizationSheet(
                layout = bottomBarLayout,
                iconOnly = bottomBarIconOnly,
                onLayoutChange = { viewModel.updateBottomBarLayout(it) },
                onIconOnlyChange = { viewModel.updateBottomBarIconOnly(it) },
                onDismiss = { showBottomBarSheet = false }
            )
        }
    }
}

// 单选列表底部弹窗，选中即保存并关闭
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionPickerSheet(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )
            options.forEach { (key, label) ->
                val isSelected = selected == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(key)
                            onDismiss()
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
