package com.lin0721.linmusic.feature.settings.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaSwitch
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 定义多级菜单类型。subtitle 是子页面前两个设置项标题的预览，仅用于主菜单入口展示
enum class SettingsSubMenu(val title: String, val subtitle: String, val icon: ImageVector) {
    PLAYBACK_DOWNLOAD("播放与下载", "自动播放推荐新歌、边听边存", Icons.Outlined.PlayCircleOutline),
    AUDIO_QUALITY("音质", "Wi-Fi 环境播放音质、移动网络环境播放音质", Icons.Outlined.HighQuality),
    PRIVACY("隐私设置", "", Icons.Outlined.PrivacyTip),
    STORAGE("储存空间", "清理应用缓存、最大音频缓存上限", Icons.Outlined.Storage),
    NETWORK("网络设置", "仅 Wi-Fi 网络下联网播放、流量播放警告提示", Icons.Outlined.Wifi),
    EXTENSIONS("扩展", "启用系统锁屏显示、车载模式蓝牙自动启动", Icons.Outlined.Extension),
    LYRICS("歌词设置", "启用桌面悬浮歌词、悬浮歌词字号", Icons.Outlined.Subtitles),
    ABOUT("关于", "检查更新、自动检查更新", Icons.Outlined.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var activeSubMenu by remember { mutableStateOf<SettingsSubMenu?>(null) }

    BackHandler(enabled = activeSubMenu != null) {
        activeSubMenu = null
    }
    
    // 监听 Toast 提示消息
    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collectLatest { msg ->
            ToastManager.showToast(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeSubMenu?.title ?: "设置",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    MelodiaIconButton(
                        onClick = {
                            if (activeSubMenu != null) {
                                activeSubMenu = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 使用 AnimatedContent 实现主菜单和子菜单的平滑切换动画
            AnimatedContent(
                targetState = activeSubMenu,
                transitionSpec = {
                    if (targetState != null) {
                        // 进入子菜单：从右往左滑入
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        // 返回主菜单：从左向右滑入
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                label = "SettingsMenuTransition"
            ) { subMenu ->
                if (subMenu == null) {
                    // 主设置菜单
                    MainSettingsMenu(
                        onNavigate = { activeSubMenu = it },
                        onBack = onBack,
                        viewModel = viewModel
                    )
                } else {
                    // 各分类子菜单内容
                    SubMenuContent(
                        subMenu = subMenu,
                        viewModel = viewModel,
                        context = context
                    )
                }
            }
        }
    }
}

// 主设置列表菜单
@Composable
private fun MainSettingsMenu(
    onNavigate: (SettingsSubMenu) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MelodiaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp, top = 8.dp)
    ) {
        // 多级设置菜单入口组
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "常规设置",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = MelodiaSpacing.xs, bottom = MelodiaSpacing.sm)
                )
                SettingsSubMenu.values().forEach { item ->
                    SettingsMainMenuRow(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        onClick = { onNavigate(item) }
                    )
                }
            }
        }

        // 底部账户操作按钮组
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MelodiaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 退出登录按钮
                MelodiaButton(
                    onClick = {
                        viewModel.executeLogout {
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(PillRadiusLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "退出登录",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// 统一子菜单内容分发器
@Composable
private fun SubMenuContent(
    subMenu: SettingsSubMenu,
    viewModel: SettingsViewModel,
    context: Context
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MelodiaSpacing.md)
    ) {
        when (subMenu) {
            SettingsSubMenu.PLAYBACK_DOWNLOAD -> PlaybackDownloadSettingsView(viewModel)
            SettingsSubMenu.AUDIO_QUALITY -> AudioQualitySettingsView(viewModel)
            SettingsSubMenu.PRIVACY -> PrivacySettingsView(viewModel)
            SettingsSubMenu.STORAGE -> StorageSettingsView(viewModel, context)
            SettingsSubMenu.NETWORK -> NetworkSettingsView(viewModel)
            SettingsSubMenu.EXTENSIONS -> ExtensionsSettingsView(viewModel)
            SettingsSubMenu.LYRICS -> LyricsSettingsView(viewModel)
            SettingsSubMenu.ABOUT -> AboutSettingsView(viewModel)
        }
    }
}

// ─── 辅助卡片及布局小组件 (不带 private，以便同包子模块访问) ───

@Composable
fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = MelodiaSpacing.xs, bottom = MelodiaSpacing.sm)
        )
        Column(content = content)
    }
}

@Composable
private fun SettingsMainMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(MelodiaSpacing.md))
        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.sm)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.sm)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.md)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        MelodiaSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

fun getBindingIcon(type: Int): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        1 -> Icons.Default.PhoneAndroid
        10 -> Icons.Default.ChatBubbleOutline
        20 -> Icons.Default.AccountCircle
        else -> Icons.Default.Link
    }
}
