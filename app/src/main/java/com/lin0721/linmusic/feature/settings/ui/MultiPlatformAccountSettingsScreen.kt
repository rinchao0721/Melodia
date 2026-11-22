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
import com.lin0721.linmusic.core.auth.StoredAccount
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

// 多平台账号管理设置子页：分别管理网易云与酷狗账号，并支持酷狗总开关与客户端模式切换
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPlatformAccountSettingsView(viewModel: SettingsViewModel) {
    val neteaseAccounts by viewModel.neteaseAccounts.collectAsStateWithLifecycle()
    val activeNetease by viewModel.activeNeteaseAccount.collectAsStateWithLifecycle()
    val kugouAccounts by viewModel.kugouAccounts.collectAsStateWithLifecycle()
    val activeKugou by viewModel.activeKugouAccount.collectAsStateWithLifecycle()
    val kugouPlatformEnabled by viewModel.kugouPlatformEnabled.collectAsStateWithLifecycle()
    val kugouClientMode by viewModel.kugouClientMode.collectAsStateWithLifecycle()

    var showKugouLoginSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }

    SettingsPageColumn {
        item {
            SettingsGroupCard(SettingsSubMenu.ACCOUNT.sectionTitles[0]) {
                if (neteaseAccounts.isEmpty()) {
                    Text(
                        text = "当前未登录网易云账号，请从左侧边栏登录",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    neteaseAccounts.forEachIndexed { index, account ->
                        val isActive = activeNetease?.accountId == account.accountId
                        val displayName = account.nickname.ifBlank { "网易云用户 ${account.effectivePlatformUid}" }
                        PlatformAccountRow(
                            account = account,
                            isActive = isActive,
                            onSwitch = {
                                viewModel.switchPlatformAccount(account.accountId, displayName)
                            },
                            onLogout = {
                                viewModel.logoutPlatformAccount(account.accountId, displayName)
                            }
                        )
                        if (index < neteaseAccounts.lastIndex) {
                            HorizontalDivider(color = MelodiaColors.Divider)
                        }
                    }
                }
            }
        }

        item {
            SettingsGroupCard(SettingsSubMenu.ACCOUNT.sectionTitles[1]) {
                if (kugouAccounts.isEmpty()) {
                    Text(
                        text = "尚未登录酷狗音乐账号，登录后可使用酷狗收藏同步与 VIP 音源解析",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    kugouAccounts.forEachIndexed { index, account ->
                        val isActive = activeKugou?.accountId == account.accountId
                        val displayName = account.nickname.ifBlank { "酷狗用户 ${account.effectivePlatformUid}" }
                        PlatformAccountRow(
                            account = account,
                            isActive = isActive,
                            onSwitch = {
                                viewModel.switchPlatformAccount(account.accountId, displayName)
                            },
                            onLogout = {
                                viewModel.logoutPlatformAccount(account.accountId, displayName)
                            }
                        )
                        if (index < kugouAccounts.lastIndex) {
                            HorizontalDivider(color = MelodiaColors.Divider)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

                MelodiaButton(
                    onClick = { showKugouLoginSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MelodiaColors.Accent)
                ) {
                    Icon(
                        imageVector = MelodiaIcons.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MelodiaColors.OnAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (kugouAccounts.isEmpty()) "登录酷狗账号" else "添加酷狗账号",
                        color = MelodiaColors.OnAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            SettingsGroupCard(SettingsSubMenu.ACCOUNT.sectionTitles[2]) {
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

    if (showKugouLoginSheet) {
        KugouLoginBottomSheet(
            onDismiss = { showKugouLoginSheet = false },
            onLoginSuccess = { account ->
                showKugouLoginSheet = false
                ToastManager.showToast("已登录酷狗：${account.nickname.ifBlank { account.effectivePlatformUid }}")
            },
            addAccount = kugouAccounts.isNotEmpty()
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

@Composable
private fun PlatformAccountRow(
    account: StoredAccount,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (account.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = account.avatarUrl,
                contentDescription = account.nickname,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MelodiaColors.SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MelodiaIcons.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MelodiaColors.HintText
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = account.nickname.ifBlank { "用户 ${account.effectivePlatformUid}" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (account.vipLabel.isNotBlank()) {
                    Text(
                        text = account.vipLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MelodiaColors.AccentText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MelodiaColors.Accent.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (isActive) {
                    Text(
                        text = "当前",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MelodiaColors.OnAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MelodiaColors.Accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "UID ${account.effectivePlatformUid}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isActive) {
            MelodiaOutlinedButton(
                onClick = onSwitch,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MelodiaColors.SurfaceLight),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("切换", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        MelodiaTextButton(
            onClick = onLogout,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("退出", fontSize = 12.sp, color = MelodiaColors.AccentText)
        }
    }
}
