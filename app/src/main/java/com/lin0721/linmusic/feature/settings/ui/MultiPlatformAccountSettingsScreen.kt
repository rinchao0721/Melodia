package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.components.AccountSwitchConfirmDialog
import com.lin0721.linmusic.core.ui.components.AddAccountPlatformSheet
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.UnifiedAccountList
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.core.ui.components.displayName
import com.lin0721.linmusic.core.ui.components.rememberAccountSwitchController
import com.lin0721.linmusic.core.kugou.KugouClientMode
import com.lin0721.linmusic.core.ui.components.KugouLoginBottomSheet
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.icons.Check
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 多平台账号管理设置子页：跨平台统一账号列表，及酷狗总开关与客户端模式切换
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPlatformAccountSettingsView(viewModel: SettingsViewModel) {
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val currentAccountId by viewModel.currentAccountId.collectAsStateWithLifecycle()
    val activeNetease by viewModel.activeNeteaseAccount.collectAsStateWithLifecycle()
    val kugouPlatformEnabled by viewModel.kugouPlatformEnabled.collectAsStateWithLifecycle()
    val kugouClientMode by viewModel.kugouClientMode.collectAsStateWithLifecycle()
    val switchController = rememberAccountSwitchController()

    var showAddAccountPicker by remember { mutableStateOf(false) }
    var showNeteaseLoginSheet by remember { mutableStateOf(false) }
    var showNeteaseWebLogin by remember { mutableStateOf(false) }
    var showKugouLoginSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    val hasNeteaseAccount = allAccounts.any { it.platform == MusicPlatform.NETEASE }
    val hasKugouAccount = allAccounts.any { it.platform == MusicPlatform.KUGOU }

    SettingsPageColumn {
        item {
            SettingsGroupCard(SettingsSubMenu.ACCOUNT.sectionTitles[0]) {
                UnifiedAccountList(
                    accounts = allAccounts,
                    currentAccountId = currentAccountId,
                    showGuestEntry = activeNetease == null,
                    onSelectAccount = switchController::requestAccount,
                    onSelectGuest = switchController::requestGuest,
                    onAddAccount = { showAddAccountPicker = true },
                    avatarSize = 40.dp,
                    rowPadding = PaddingValues(0.dp),
                    onLogout = { account -> viewModel.logoutPlatformAccount(account.accountId, account.displayName()) }
                )
            }
        }

        item {
            SettingsGroupCard(SettingsSubMenu.ACCOUNT.sectionTitles[1]) {
                SettingsSwitchRow(
                    title = "启用酷狗音乐平台",
                    subtitle = "开启多平台搜索与酷狗播放解析能力；关闭仅暂停调用，不清除已登录账号",
                    checked = kugouPlatformEnabled,
                    onCheckedChange = viewModel::updateKugouPlatformEnabled
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsRow(
                    title = "酷狗客户端模式",
                    subtitle = when (kugouClientMode) {
                        KugouClientMode.LITE -> "概念版 (LITE · appid 3116)"
                        KugouClientMode.STANDARD -> "标准版 (STANDARD · appid 1005)"
                    },
                    onClick = { showModeSheet = true }
                )
            }
        }
    }

    AccountSwitchConfirmDialog(switchController)

    if (showAddAccountPicker) {
        AddAccountPlatformSheet(
            showKugou = kugouPlatformEnabled,
            onDismiss = { showAddAccountPicker = false },
            onPick = { platform ->
                showAddAccountPicker = false
                if (platform == MusicPlatform.KUGOU) showKugouLoginSheet = true else showNeteaseLoginSheet = true
            }
        )
    }

    if (showNeteaseLoginSheet) {
        LoginBottomSheet(
            onDismiss = { showNeteaseLoginSheet = false },
            onWebLogin = {
                showNeteaseLoginSheet = false
                showNeteaseWebLogin = true
            },
            onLoginSuccess = { cookies ->
                showNeteaseLoginSheet = false
                viewModel.handleLoginSuccess(cookies)
            },
            addAccount = hasNeteaseAccount
        )
    }

    if (showNeteaseWebLogin) {
        Dialog(
            onDismissRequest = { showNeteaseWebLogin = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            WebViewLoginScreen(
                onClose = { showNeteaseWebLogin = false },
                onLoginSuccess = { cookies ->
                    showNeteaseWebLogin = false
                    viewModel.handleLoginSuccess(cookies)
                },
                addAccount = hasNeteaseAccount
            )
        }
    }

    if (showKugouLoginSheet) {
        KugouLoginBottomSheet(
            onDismiss = { showKugouLoginSheet = false },
            onLoginSuccess = { account ->
                showKugouLoginSheet = false
                ToastManager.showToast("已登录酷狗：${account.nickname.ifBlank { account.effectivePlatformUid }}")
            },
            addAccount = hasKugouAccount
        )
    }

    if (showModeSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showModeSheet = false },
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
                    text = "选择酷狗客户端模式",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                )
                listOf(
                    Triple(KugouClientMode.LITE, "概念版 (LITE)", "appid 3116，默认模式，日常签到与播放兼容最佳"),
                    Triple(KugouClientMode.STANDARD, "标准版 (STANDARD)", "appid 1005，标准酷狗客户端协议")
                ).forEach { (mode, title, desc) ->
                    val selected = kugouClientMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateKugouClientMode(mode)
                                showModeSheet = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MelodiaColors.AccentText else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = desc,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = MelodiaIcons.Check,
                                contentDescription = null,
                                tint = MelodiaColors.AccentText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
