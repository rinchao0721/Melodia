package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.auth.AccountSwitcher
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.icons.Check
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// 平台品牌色与短名，账号徽标和首次启动页共用
internal object PlatformBrand {
    val NeteaseRed = Color(0xFFE60026)
    val KugouBlue = Color(0xFF2979FF)

    fun color(platform: MusicPlatform): Color = when (platform) {
        MusicPlatform.KUGOU -> KugouBlue
        else -> NeteaseRed
    }

    fun label(platform: MusicPlatform): String = when (platform) {
        MusicPlatform.KUGOU -> "酷狗"
        else -> "网易云"
    }
}

// 跨平台切换会清空播放队列，需先确认
internal fun requiresQueueClearConfirm(currentPlatform: MusicPlatform, targetPlatform: MusicPlatform): Boolean =
    currentPlatform != targetPlatform

internal fun StoredAccount.displayName(): String =
    nickname.ifBlank { "${PlatformBrand.label(platform)}用户 $effectivePlatformUid" }

// 网易图片走缩略参数，其他平台原样
internal fun accountAvatarModel(account: StoredAccount?): String? {
    val url = account?.avatarUrl?.takeIf { it.isNotBlank() } ?: return null
    return if (account.platform == MusicPlatform.NETEASE && !url.contains('?')) "$url?param=200y200" else url
}

sealed interface PendingAccountSwitch {
    val targetPlatform: MusicPlatform

    data class ToAccount(val account: StoredAccount) : PendingAccountSwitch {
        override val targetPlatform: MusicPlatform get() = account.platform
    }

    data object ToGuest : PendingAccountSwitch {
        override val targetPlatform: MusicPlatform get() = MusicPlatform.NETEASE
    }
}

// 账号菜单的切换动作：跨平台先弹确认，同平台直接切
@Stable
class AccountSwitchController internal constructor(
    private val scope: CoroutineScope,
    private val accountSwitcher: AccountSwitcher,
    private val currentPlatform: State<MusicPlatform>
) {
    var pending by mutableStateOf<PendingAccountSwitch?>(null)
        private set

    fun requestAccount(account: StoredAccount) = request(PendingAccountSwitch.ToAccount(account))

    fun requestGuest() = request(PendingAccountSwitch.ToGuest)

    fun confirm() {
        val target = pending ?: return
        pending = null
        execute(target)
    }

    fun cancel() {
        pending = null
    }

    private fun request(target: PendingAccountSwitch) {
        if (requiresQueueClearConfirm(currentPlatform.value, target.targetPlatform)) {
            pending = target
        } else {
            execute(target)
        }
    }

    private fun execute(target: PendingAccountSwitch) {
        scope.launch {
            when (target) {
                is PendingAccountSwitch.ToAccount -> {
                    if (accountSwitcher.switchToAccount(target.account.accountId)) {
                        ToastManager.showToast("已切换至 ${target.account.displayName()}")
                    }
                }
                PendingAccountSwitch.ToGuest -> {
                    if (accountSwitcher.browseAsGuest()) {
                        ToastManager.showToast("已切换为网易云游客")
                    }
                }
            }
        }
    }
}

@Composable
fun rememberAccountSwitchController(): AccountSwitchController {
    val accountSwitcher: AccountSwitcher = koinInject()
    val userPreferences: UserPreferences = koinInject()
    val scope = rememberCoroutineScope()
    val currentPlatform = userPreferences.currentPlatform.collectAsStateWithLifecycle(initialValue = MusicPlatform.NETEASE)
    val latestPlatform = rememberUpdatedState(currentPlatform.value)
    return remember(accountSwitcher, scope) { AccountSwitchController(scope, accountSwitcher, latestPlatform) }
}

// 跨平台切换确认弹窗
@Composable
fun AccountSwitchConfirmDialog(controller: AccountSwitchController) {
    val target = controller.pending ?: return
    val targetName = when (target) {
        is PendingAccountSwitch.ToAccount -> "${PlatformBrand.label(target.account.platform)} · ${target.account.displayName()}"
        PendingAccountSwitch.ToGuest -> "网易云 · 游客"
    }
    AlertDialog(
        onDismissRequest = controller::cancel,
        title = { Text("切换到 $targetName", color = MelodiaColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = { Text("切换后将清空当前播放队列", color = MelodiaColors.TextSecondary, fontSize = 14.sp) },
        confirmButton = {
            MelodiaTextButton(
                onClick = controller::confirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MelodiaColors.Accent)
            ) {
                Text("切换", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            MelodiaTextButton(onClick = controller::cancel) {
                Text("取消", color = MelodiaColors.TextPrimary)
            }
        },
        containerColor = MelodiaColors.Surface,
        shape = RoundedCornerShape(10.dp)
    )
}

// 添加账号第一步：选平台
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountPlatformSheet(
    showKugou: Boolean,
    onDismiss: () -> Unit,
    onPick: (MusicPlatform) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                .padding(bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "添加账号",
                color = MelodiaColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = MelodiaSpacing.lg, vertical = MelodiaSpacing.sm)
            )
            val platforms = if (showKugou) listOf(MusicPlatform.NETEASE, MusicPlatform.KUGOU) else listOf(MusicPlatform.NETEASE)
            platforms.forEach { platform ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressable(MelodiaPress.Row) { onPick(platform) }
                        .padding(horizontal = MelodiaSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(PlatformBrand.color(platform))
                    )
                    Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                    Text(
                        text = "登录${platform.displayName}",
                        color = MelodiaColors.TextPrimary,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// 带平台徽标的圆形头像
@Composable
fun PlatformAccountAvatar(
    account: StoredAccount?,
    size: Dp,
    platform: MusicPlatform? = account?.platform
) {
    Box(modifier = Modifier.size(size)) {
        val model = accountAvatarModel(account)
        if (model == null) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MelodiaColors.Overlay.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MelodiaIcons.Person,
                    contentDescription = null,
                    tint = MelodiaColors.TextSecondary,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MelodiaColors.Fill),
                contentScale = ContentScale.Crop
            )
        }
        if (platform != null) {
            val badge = (size * 0.32f).coerceAtLeast(10.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badge)
                    .clip(CircleShape)
                    .background(PlatformBrand.color(platform))
                    .border(1.5.dp, MelodiaColors.CardSurface, CircleShape)
            )
        }
    }
}

// 跨平台统一账号列表：游客项（可选）、全部账号、添加账号
@Composable
fun UnifiedAccountList(
    accounts: List<StoredAccount>,
    currentAccountId: AccountId?,
    showGuestEntry: Boolean,
    onSelectAccount: (StoredAccount) -> Unit,
    onSelectGuest: () -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 32.dp,
    rowPadding: PaddingValues = PaddingValues(horizontal = MelodiaSpacing.md),
    onLogout: ((StoredAccount) -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showGuestEntry) {
            UnifiedAccountRow(
                avatar = { PlatformAccountAvatar(account = null, size = avatarSize, platform = MusicPlatform.NETEASE) },
                title = "网易云 · 游客",
                subtitle = null,
                isCurrent = currentAccountId == null,
                rowPadding = rowPadding,
                onClick = onSelectGuest
            )
        }
        accounts.forEach { account ->
            val isCurrent = account.accountId == currentAccountId
            UnifiedAccountRow(
                avatar = { PlatformAccountAvatar(account = account, size = avatarSize) },
                title = account.displayName(),
                subtitle = PlatformBrand.label(account.platform),
                isCurrent = isCurrent,
                rowPadding = rowPadding,
                onClick = { if (!isCurrent) onSelectAccount(account) },
                trailing = onLogout?.let { logout ->
                    {
                        MelodiaTextButton(
                            onClick = { logout(account) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("退出", fontSize = 12.sp, color = MelodiaColors.AccentText)
                        }
                    }
                }
            )
        }
        UnifiedAccountRow(
            avatar = {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(MelodiaColors.Fill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MelodiaIcons.Plus,
                        contentDescription = null,
                        tint = MelodiaColors.TextPrimary,
                        modifier = Modifier.size(avatarSize * 0.56f)
                    )
                }
            },
            title = "添加账号",
            subtitle = null,
            isCurrent = false,
            rowPadding = rowPadding,
            onClick = onAddAccount
        )
    }
}

@Composable
private fun UnifiedAccountRow(
    avatar: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    isCurrent: Boolean,
    rowPadding: PaddingValues,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 56.dp else 52.dp)
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(rowPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        avatar()
        Spacer(modifier = Modifier.width(MelodiaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = MelodiaColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MelodiaColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
        if (isCurrent) {
            Icon(
                imageVector = MelodiaIcons.Check,
                contentDescription = "当前账号",
                tint = MelodiaColors.AccentText,
                modifier = Modifier.size(20.dp)
            )
        }
        trailing?.invoke()
    }
}
