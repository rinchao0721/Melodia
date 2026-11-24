package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.source.MusicPlatform

// 当前账号所在平台，内容平台与它相同时不显示平台标识
val LocalCurrentAccountPlatform = compositionLocalOf { MusicPlatform.NETEASE }

// 内容平台与当前账号平台不同时才带平台名前缀
@Composable
fun platformScopedLabel(platform: MusicPlatform, label: String): String =
    if (showsPlatformBadge(platform)) "${platform.badgeName}$label" else label

@Composable
fun showsPlatformBadge(platform: MusicPlatform?): Boolean =
    platform != null && platform != LocalCurrentAccountPlatform.current

enum class PlatformBadgeStyle { PILL, PILL_COMPACT }

@Composable
fun PlatformBadge(
    platform: MusicPlatform?,
    modifier: Modifier = Modifier,
    style: PlatformBadgeStyle = PlatformBadgeStyle.PILL_COMPACT
) {
    if (platform == null || !showsPlatformBadge(platform)) return
    val brand = Color(platform.brandColorArgb)
    when (style) {
        PlatformBadgeStyle.PILL -> Text(
            text = platform.badgeName,
            color = brand,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(brand.copy(alpha = 0.18f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        PlatformBadgeStyle.PILL_COMPACT -> Text(
            text = platform.badgeName,
            color = brand,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(brand.copy(alpha = 0.14f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

// 歌曲行内的来源标识：品牌色圆点加灰字
@Composable
fun PlatformSourceTag(platform: MusicPlatform?, modifier: Modifier = Modifier) {
    if (platform == null || !showsPlatformBadge(platform)) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(6.dp).background(Color(platform.brandColorArgb), CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(
            text = platform.badgeName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}
