package com.lin0721.linmusic.feature.cloud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.feature.cloud.domain.CloudQuota
import com.lin0721.linmusic.feature.cloud.domain.formatWholeGigabytes

private val HeroHeight = 108.dp
private const val HeroMinRedWidthRatio = 0.12f
private const val HeroMinDarkWidthRatio = 0.3f

// 容量巨型色块海报：红块宽度按已用占比拉伸，内叠大号数字；深灰块承载文字说明
@Composable
fun CloudStorageHeroBlock(quota: CloudQuota, modifier: Modifier = Modifier) {
    // 两块都要留最小宽度：红块太窄大号数字会被裁掉；深灰块在容量用满（usedRatio=1）时
    // 权重会变成 0，Modifier.weight 要求 > 0，否则直接抛 IllegalArgumentException 闪退
    val redWeight = quota.usedRatio.coerceIn(HeroMinRedWidthRatio, 1f - HeroMinDarkWidthRatio)
    val darkWeight = 1f - redWeight

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HeroHeight)
            .clip(RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier
                .weight(redWeight)
                .fillMaxHeight()
                .background(NeteaseRed),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = "${formatWholeGigabytes(quota.usedBytes)}",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 44.sp,
                modifier = Modifier.padding(MelodiaSpacing.sm)
            )
        }
        Column(
            modifier = Modifier
                .weight(darkWeight)
                .fillMaxHeight()
                .background(SurfaceDark)
                .padding(horizontal = MelodiaSpacing.md),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "GB 已用 / ${formatWholeGigabytes(quota.maxBytes)}GB",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Text(
                text = "共 ${quota.totalCount} 首",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = MelodiaSpacing.xxs)
            )
        }
    }
}
