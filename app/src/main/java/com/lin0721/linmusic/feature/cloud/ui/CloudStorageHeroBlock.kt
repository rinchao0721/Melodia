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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.cloud.domain.CloudQuota
import com.lin0721.linmusic.feature.cloud.domain.formatWholeGigabytes

private val HeroHeight = 108.dp
private val HeroMinRedWidthRatio = 0.12f

// 容量巨型色块海报：红块宽度按已用占比拉伸，内叠大号数字；深灰块承载文字说明
@Composable
fun CloudStorageHeroBlock(quota: CloudQuota, modifier: Modifier = Modifier) {
    // 已用占比过低时红块给个最小宽度，否则大号数字会被裁到看不清
    val redWeight = quota.usedRatio.coerceAtLeast(HeroMinRedWidthRatio)
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
                .background(MelodiaColors.Accent),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = "${formatWholeGigabytes(quota.usedBytes)}",
                color = MelodiaColors.OnAccent,
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
                .background(MelodiaColors.Surface)
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
