package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform

// 头像菜单统一账号列表的一项；accountId 为 null 表示网易游客
internal data class AccountMenuEntry(
    val accountId: AccountId?,
    val platform: MusicPlatform,
    val displayName: String,
    val avatarUrl: String,
    val vipLabel: String,
    val isCurrent: Boolean
) {
    val isGuest: Boolean get() = accountId == null
}

internal val AccountMenuPlatforms = listOf(MusicPlatform.NETEASE, MusicPlatform.KUGOU)

// 无活跃网易账号时游客项置顶；酷狗关闭时隐藏酷狗账号
internal fun buildAccountMenuEntries(
    accounts: List<StoredAccount>,
    currentAccountId: AccountId?,
    hasActiveNetease: Boolean,
    kugouEnabled: Boolean
): List<AccountMenuEntry> {
    val visible = accounts.filter { it.platform in AccountMenuPlatforms && (kugouEnabled || it.platform != MusicPlatform.KUGOU) }
    val guest = if (!hasActiveNetease) {
        listOf(
            AccountMenuEntry(
                accountId = null,
                platform = MusicPlatform.NETEASE,
                displayName = "网易云 · 游客",
                avatarUrl = "",
                vipLabel = "",
                isCurrent = currentAccountId == null
            )
        )
    } else {
        emptyList()
    }
    return guest + visible.distinctBy { it.accountId }.map { account ->
        AccountMenuEntry(
            accountId = account.accountId,
            platform = account.platform,
            displayName = account.nickname.ifBlank { "${account.platform.displayName}用户 ${account.effectivePlatformUid}" },
            avatarUrl = account.avatarUrl,
            vipLabel = account.vipLabel,
            isCurrent = account.accountId == currentAccountId
        )
    }
}

// 跨平台切换会清空播放队列，需先确认
internal fun needsSwitchConfirm(target: AccountMenuEntry, currentPlatform: MusicPlatform): Boolean =
    !target.isCurrent && target.platform != currentPlatform

// 可添加账号的平台：未达上限且平台已启用
internal fun addableAccountPlatforms(accounts: List<StoredAccount>, kugouEnabled: Boolean): List<MusicPlatform> =
    AccountMenuPlatforms.filter { platform ->
        (kugouEnabled || platform != MusicPlatform.KUGOU) &&
            accounts.count { it.platform == platform } < UserPreferences.MAX_ACCOUNTS
    }

internal fun platformBrandColor(platform: MusicPlatform): Color = when (platform) {
    MusicPlatform.KUGOU -> KugouBrandColor
    else -> NeteaseBrandColor
}

private val NeteaseBrandColor = Color(0xFFE60026)
private val KugouBrandColor = Color(0xFF2979FF)

// 平台品牌色圆点，用作账号徽标与登录按钮标识
@Composable
internal fun PlatformBrandDot(platform: MusicPlatform, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(platformBrandColor(platform)))
}
