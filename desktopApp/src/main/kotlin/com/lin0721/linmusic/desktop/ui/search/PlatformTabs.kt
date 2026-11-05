package com.lin0721.linmusic.desktop.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.search.ui.ExternalSearchUiState
import com.lin0721.linmusic.feature.search.ui.tabBadge
import kotlinx.coroutines.flow.StateFlow

// 搜索结果页的平台切换 Tab，品牌色圆点加已加载条数，未切换过的平台不会发起请求
@Composable
internal fun PlatformTabs(
    platforms: List<MusicPlatform>,
    selected: MusicPlatform,
    externalResults: Map<MusicPlatform, StateFlow<ExternalSearchUiState>>,
    onSelect: (MusicPlatform) -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = platforms.indexOf(selected).coerceAtLeast(0),
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = DesktopColors.TextPrimary,
        edgePadding = 24.dp,
        divider = { HorizontalDivider(color = DesktopColors.Surface) }
    ) {
        platforms.forEach { platform ->
            val state = externalResults[platform]?.collectAsState()?.value
            val badge = state?.tabBadge()
            Tab(
                selected = platform == selected,
                onClick = { onSelect(platform) },
                selectedContentColor = DesktopColors.TextPrimary,
                unselectedContentColor = DesktopColors.TextGray,
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            Modifier
                                .size(6.dp)
                                .background(Color(platform.brandColorArgb), CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = platform.shortName,
                            fontSize = 14.sp,
                            fontWeight = if (platform == selected) FontWeight.Medium else FontWeight.Normal
                        )
                        if (badge != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = badge,
                                fontSize = 11.sp,
                                color = if (state is ExternalSearchUiState.Error) DesktopColors.AccentText else DesktopColors.TextGray
                            )
                        }
                    }
                }
            )
        }
    }
}
