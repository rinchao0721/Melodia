package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.interaction.pressScale
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadiusLarge


 // 侧边栏
@Composable
fun ProfileSidebar(
    userProfile: UserProfile,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToProfile: (uid: Long) -> Unit,
    onNavigateToRecentPlay: () -> Unit,
    onNavigateToListenData: () -> Unit,
    onNavigateToCloud: () -> Unit,
    onNavigateToMessage: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(BackgroundDark)
            .statusBarsPadding() // 避开状态栏
    ) {
        // 1. 头部：用户资料区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(MelodiaPress.Row) {
                    onNavigateToProfile(userProfile.uid)
                }
                .padding(horizontal = 20.dp, vertical = MelodiaSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            AsyncImage(
                model = "${userProfile.avatarUrl}?param=200y200",
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 昵称与查看资料
            Column {
                Text(
                    text = userProfile.nickname,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "查看个人资料",
                    fontSize = 13.sp,
                    color = TextGray
                )
            }
        }

        // 极细分割线
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.1f),
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 菜单区。每项跳二级页，进入前先收起抽屉
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            SidebarMenuItem(
                icon = Icons.Outlined.History,
                title = "最近播放",
                onClick = { onDismiss(); onNavigateToRecentPlay() }
            )
            SidebarMenuItem(
                icon = Icons.Outlined.Insights,
                title = "听歌数据",
                onClick = { onDismiss(); onNavigateToListenData() }
            )
            SidebarMenuItem(
                icon = Icons.Outlined.CloudQueue,
                title = "我的云盘",
                onClick = { onDismiss(); onNavigateToCloud() }
            )
            SidebarMenuItem(
                icon = Icons.Outlined.Notifications,
                title = "消息",
                onClick = { onDismiss(); onNavigateToMessage() }
            )
            SidebarMenuItem(
                icon = Icons.Outlined.WorkspacePremium,
                title = "账号与会员",
                onClick = { onDismiss(); onNavigateToAccount() }
            )
            SidebarMenuItem(
                icon = Icons.Outlined.Settings,
                title = "设置和隐私",
                onClick = { onDismiss(); onNavigateToSettings() }
            )
        }

        // 3. 底部
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Surface 自带 onClick，插不进 clickable，缩放走 pressScale 并共用 interactionSource
            val interactionSource = remember { MutableInteractionSource() }

            Surface(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .pressScale(MelodiaPress.Action, interactionSource),
                shape = RoundedCornerShape(PillRadiusLarge),
                color = Color.Transparent,
                border = BorderStroke(1.dp, NeteaseRed.copy(alpha = 0.6f)),
                interactionSource = interactionSource
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = NeteaseRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "退出登录",
                        color = NeteaseRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(26.dp) // 微调图标尺寸
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp, // 稍微拉开字间距
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextGray,
            modifier = Modifier.size(20.dp)
        )
    }
}
