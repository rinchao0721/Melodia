package com.lin0721.linmusic.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.feature.profile.domain.ProfileUserInfo

@Composable
fun ProfileHeaderSection(
    userInfo: ProfileUserInfo,
    isSelf: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onFollowClick: () -> Unit,
    onFollowsClick: () -> Unit,
    onFollowedsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .padding(horizontal = 20.dp, vertical = MelodiaSpacing.md)
    ) {
        // 用户资料行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = "${userInfo.avatarUrl}?param=200y200",
                contentDescription = userInfo.nickname,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = userInfo.nickname,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 金色等级徽标
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(PillRadius))
                            .background(Color(0xFFE8B45C).copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Lv.${userInfo.level}",
                            color = Color(0xFFE8B45C),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (userInfo.signature.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = userInfo.signature,
                        color = TextGray,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 统计行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MelodiaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.clickable(onClick = onFollowsClick),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "${userInfo.followsCount}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "关注",
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            Column(
                modifier = Modifier.clickable(onClick = onFollowedsClick),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "${userInfo.followedsCount}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "粉丝",
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "${userInfo.playlistCount}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "歌单",
                    color = TextGray,
                    fontSize = 12.sp
                )
            }
        }

        // 关注操作按钮（非本人时展示）
        if (!isSelf) {
            Spacer(modifier = Modifier.height(MelodiaSpacing.md))
            Box(
                modifier = Modifier
                    .pressable(
                        style = MelodiaPress.Action,
                        shape = RoundedCornerShape(PillRadius),
                        onClick = onFollowClick
                    )
                    .border(
                        border = if (userInfo.isFollowedByMe) BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        else BorderStroke(0.dp, Color.Transparent),
                        shape = RoundedCornerShape(PillRadius)
                    )
                    .background(if (userInfo.isFollowedByMe) Color.Transparent else MaterialTheme.colorScheme.primary)
                    .padding(horizontal = MelodiaSpacing.md, vertical = 6.dp)
            ) {
                Text(
                    text = if (userInfo.isFollowedByMe) "已关注" else "关注",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(MelodiaSpacing.md))

        // 分割线
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 1.dp
        )

        // 分页 Tab 栏：视觉样式对齐 ArtistTabBar，指示器选中态高亮
        ProfileTabBar(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected
        )
    }
}

// 头部 Tab 切换栏（样式对齐 ArtistTabBar）
@Composable
private fun ProfileTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("歌单", "动态", "听歌排行")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .pressable(MelodiaPress.Tab) { onTabSelected(index) }
                    .padding(vertical = MelodiaSpacing.xs)
            ) {
                Text(
                    text = title,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(28.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}
