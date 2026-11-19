package com.lin0721.linmusic.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.ui.badge.DraggableUnreadBadge
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.ContentSwitchDurationMs
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.uiTween
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import com.lin0721.linmusic.feature.message.ui.ConversationPreviewState
import com.lin0721.linmusic.feature.message.ui.PrivateConversationRow
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.ChevronDown
import com.lin0721.linmusic.core.ui.icons.ChevronRight
import com.lin0721.linmusic.core.ui.icons.Download
import com.lin0721.linmusic.core.ui.icons.Insights

private val SidebarHorizontalPadding = 20.dp
private val AccountCardShape = RoundedCornerShape(20.dp)

 // 侧边栏
@Composable
fun ProfileSidebar(
    userProfile: UserProfile?,
    accounts: List<UserProfile>,
    showMessageSection: Boolean = true,
    privateUnreadCount: Int = 0,
    conversationPreview: ConversationPreviewState = ConversationPreviewState(),
    onDismissUnread: () -> Unit = {},
    onDismissConversationUnread: (PrivateConversation) -> Unit = {},
    onLogin: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (uid: Long) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToProfile: (uid: Long) -> Unit,
    onNavigateToRecentPlay: () -> Unit,
    onNavigateToListenData: () -> Unit,
    onNavigateToCloud: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToMessage: () -> Unit,
    onOpenPrivateChat: (uid: Long) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // 需要账号的入口，未登录时改为弹登录
    val withLogin: (() -> Unit) -> () -> Unit = { navigate ->
        {
            if (userProfile != null) {
                navigate()
            } else {
                onDismiss()
                onLogin()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(MelodiaColors.ShellFill)
            .statusBarsPadding() // 避开状态栏
            .verticalScroll(rememberScrollState())
            .padding(bottom = MelodiaSpacing.lg)
    ) {
        AccountCard(
            userProfile = userProfile,
            accounts = accounts,
            onLogin = {
                onDismiss()
                onLogin()
            },
            onAddAccount = {
                onDismiss()
                onAddAccount()
            },
            onSwitchAccount = onSwitchAccount,
            onNavigateToProfile = onNavigateToProfile
        )

        Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

        // 菜单区。每项跳二级页，收起抽屉由调用方负责
        SidebarMenuItem(
            icon = MelodiaIcons.History,
            title = "最近播放",
            onClick = withLogin(onNavigateToRecentPlay)
        )
        SidebarMenuItem(
            icon = MelodiaIcons.Insights,
            title = "听歌数据",
            onClick = withLogin(onNavigateToListenData)
        )
        SidebarMenuItem(
            icon = MelodiaIcons.Cloud,
            title = "我的云盘",
            onClick = withLogin(onNavigateToCloud)
        )
        SidebarMenuItem(
            icon = MelodiaIcons.Download,
            title = "下载管理",
            onClick = withLogin(onNavigateToDownloads)
        )
        SidebarMenuItem(
            icon = MelodiaIcons.Settings,
            title = "设置和隐私",
            onClick = onNavigateToSettings
        )

        if (showMessageSection) {
            HorizontalDivider(
                color = MelodiaColors.Overlay.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = SidebarHorizontalPadding, vertical = MelodiaSpacing.sm)
            )

            MessageSection(
                isLoggedIn = userProfile != null,
                unreadCount = privateUnreadCount,
                preview = conversationPreview,
                onDismissUnread = onDismissUnread,
                onDismissConversationUnread = onDismissConversationUnread,
                onOpenMessages = withLogin(onNavigateToMessage),
                onOpenPrivateChat = onOpenPrivateChat
            )
        }
    }
}

// 头部账户卡片：默认折叠，展开后列出其他账户与添加入口
@Composable
private fun AccountCard(
    userProfile: UserProfile?,
    accounts: List<UserProfile>,
    onLogin: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (uid: Long) -> Unit,
    onNavigateToProfile: (uid: Long) -> Unit
) {
    var expanded by rememberSaveable(userProfile?.uid) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = uiTween(ContentSwitchDurationMs, easing = FastOutSlowInEasing),
        label = "AccountCardArrow"
    )
    val otherAccounts = remember(accounts, userProfile?.uid) { accounts.filter { it.uid != userProfile?.uid } }

    Column(
        modifier = Modifier
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            .fillMaxWidth()
            .clip(AccountCardShape)
            .background(MelodiaColors.CardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(MelodiaPress.Row) {
                    if (userProfile != null) onNavigateToProfile(userProfile.uid) else onLogin()
                }
                .padding(start = MelodiaSpacing.md, end = MelodiaSpacing.xs, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountAvatar(avatarUrl = userProfile?.avatarUrl, size = 52.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userProfile?.nickname ?: "未登录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MelodiaColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (userProfile != null) "查看个人资料" else "点击登录",
                    fontSize = 13.sp,
                    color = MelodiaColors.TextSecondary
                )
            }
            if (userProfile != null) {
                MelodiaIconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = MelodiaIcons.ChevronDown,
                        contentDescription = if (expanded) "收起账户列表" else "展开账户列表",
                        tint = MelodiaColors.TextSecondary,
                        modifier = Modifier.rotate(arrowRotation)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
            }
        }

        AnimatedVisibility(
            visible = expanded && userProfile != null,
            enter = fadeIn(uiTween(ContentSwitchDurationMs, easing = FastOutSlowInEasing)) +
                expandVertically(uiTween(ContentSwitchDurationMs, easing = FastOutSlowInEasing)),
            exit = fadeOut(uiTween(ContentSwitchDurationMs, easing = FastOutSlowInEasing)) +
                shrinkVertically(uiTween(ContentSwitchDurationMs, easing = FastOutSlowInEasing))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    color = MelodiaColors.Overlay.copy(alpha = 0.08f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = MelodiaSpacing.md)
                )
                otherAccounts.forEach { account ->
                    AccountRow(onClick = { onSwitchAccount(account.uid) }) {
                        AccountAvatar(avatarUrl = account.avatarUrl, size = 32.dp)
                        Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                        Text(
                            text = account.nickname,
                            fontSize = 15.sp,
                            color = MelodiaColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                val canAddAccount = accounts.size < UserPreferences.MAX_ACCOUNTS
                AccountRow(
                    modifier = Modifier.alpha(if (canAddAccount) 1f else 0.45f),
                    onClick = {
                        if (canAddAccount) {
                            onAddAccount()
                        } else {
                            ToastManager.showToast("最多只能登录 ${UserPreferences.MAX_ACCOUNTS} 个账户")
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MelodiaColors.Fill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MelodiaIcons.Plus,
                            contentDescription = null,
                            tint = MelodiaColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                    Text(
                        text = "添加帐号",
                        fontSize = 15.sp,
                        color = MelodiaColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
            }
        }
    }
}

@Composable
private fun AccountRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(horizontal = MelodiaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun AccountAvatar(avatarUrl: String?, size: Dp) {
    if (avatarUrl.isNullOrBlank()) {
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
            model = "$avatarUrl?param=200y200",
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MelodiaColors.Fill),
            contentScale = ContentScale.Crop
        )
    }
}

// 消息区：标题进消息页，下面最近几条私信直达聊天页
@Composable
private fun MessageSection(
    isLoggedIn: Boolean,
    unreadCount: Int,
    preview: ConversationPreviewState,
    onDismissUnread: () -> Unit,
    onDismissConversationUnread: (PrivateConversation) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenPrivateChat: (uid: Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pressable(MelodiaPress.Row, onClick = onOpenMessages)
            .padding(horizontal = SidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "消息",
            color = MelodiaColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = MelodiaIcons.ChevronRight,
            contentDescription = null,
            tint = MelodiaColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (isLoggedIn && unreadCount > 0) {
            DraggableUnreadBadge(
                count = unreadCount,
                onDismiss = onDismissUnread,
                containerColor = MelodiaColors.Accent,
                contentColor = MelodiaColors.OnAccent
            )
        }
    }

    val hint = when {
        !isLoggedIn -> "登录后查看私信"
        preview.items.isNotEmpty() -> null
        preview.failed -> "私信加载失败，稍后再试"
        preview.loaded -> "暂无私信"
        else -> null
    }
    if (hint != null) {
        Text(
            text = hint,
            color = MelodiaColors.HintText,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = SidebarHorizontalPadding, vertical = MelodiaSpacing.sm)
        )
    } else if (isLoggedIn) {
        preview.items.forEach { conversation ->
            PrivateConversationRow(
                item = conversation,
                onClick = { onOpenPrivateChat(conversation.user.uid) },
                onDismissUnread = { onDismissConversationUnread(conversation) }
            )
        }
    }
}

// 菜单项组件 (SidebarMenuItem)
@Composable
private fun SidebarMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(horizontal = SidebarHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MelodiaColors.TextPrimary,
            modifier = Modifier.size(26.dp) // 微调图标尺寸
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            color = MelodiaColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp, // 稍微拉开字间距
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = MelodiaIcons.ChevronRight,
            contentDescription = null,
            tint = MelodiaColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
