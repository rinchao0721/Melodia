package com.lin0721.linmusic.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.EntryHeartGradient
import com.lin0721.linmusic.core.ui.theme.EntryHotGradient
import com.lin0721.linmusic.core.ui.theme.EntryRadarGradient
import com.lin0721.linmusic.core.ui.theme.EntryRoamingGradient
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.feature.home.data.PersonalizedPlaylist
import com.lin0721.linmusic.feature.home.domain.ToplistInfo

// 一个功能入口。这几个功能没有对应的封面资源，一律用策展色表达，
// 不再从推荐歌单里借图——借来的封面跟点进去的功能毫无关系。
internal data class ForYouEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val onClick: () -> Unit
)

// 依赖具体歌单 id 的入口在拿不到 id 时直接不出现，避免出现点进去是无关内容的死入口
@Composable
internal fun rememberForYouEntries(
    toplists: List<ToplistInfo>,
    recommendPlaylists: List<PersonalizedPlaylist>,
    onHotlistClick: (Long) -> Unit,
    onIntelligenceClick: () -> Unit,
    onRadarClick: (Long) -> Unit,
    onRoamingClick: () -> Unit
): List<ForYouEntry> {
    val hotlist = remember(toplists) {
        toplists.firstOrNull { it.name.contains("热") } ?: toplists.firstOrNull()
    }

    // 只认名字里带「雷达」的歌单，匹配不上就不放这个入口
    val radarPlaylist = remember(recommendPlaylists) {
        recommendPlaylists.firstOrNull { it.name.contains("雷达") }
    }

    return remember(hotlist, radarPlaylist) {
        buildList {
            hotlist?.let { list ->
                add(
                    ForYouEntry(
                        key = "hot",
                        title = "热歌榜",
                        subtitle = "最热音乐随时听",
                        icon = Icons.Rounded.Whatshot,
                        gradient = EntryHotGradient,
                        onClick = { onHotlistClick(list.id) }
                    )
                )
            }
            add(
                ForYouEntry(
                    key = "heart",
                    title = "心动模式",
                    subtitle = "智能红心电台",
                    icon = Icons.Rounded.Favorite,
                    gradient = EntryHeartGradient,
                    onClick = onIntelligenceClick
                )
            )
            radarPlaylist?.let { list ->
                add(
                    ForYouEntry(
                        key = "radar",
                        title = "私人雷达",
                        subtitle = "根据喜好定制",
                        icon = Icons.Rounded.TrackChanges,
                        gradient = EntryRadarGradient,
                        onClick = { onRadarClick(list.id) }
                    )
                )
            }
            add(
                ForYouEntry(
                    key = "roaming",
                    title = "音乐漫游",
                    subtitle = "无限探索相似歌曲",
                    icon = Icons.Rounded.Shuffle,
                    gradient = EntryRoamingGradient,
                    onClick = onRoamingClick
                )
            )
        }
    }
}

// 为你定制：功能入口横滑，卡宽与服务端货架一致
@Composable
internal fun ForYouSection(
    entries: List<ForYouEntry>,
    cardWidth: Dp,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        HomeSectionTitle(text = "为你定制", modifier = Modifier.padding(contentPadding))
        LazyRow(
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(HomeCardGap)
        ) {
            items(entries, key = { it.key }) { entry ->
                ForYouCard(entry = entry, modifier = Modifier.width(cardWidth))
            }
        }
    }
}

// 仿 Mix 封面：渐变底 + 底部白色名牌条
@Composable
private fun ForYouCard(entry: ForYouEntry, modifier: Modifier = Modifier) {
    Column(modifier = modifier.pressable(MelodiaPress.Card) { entry.onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(RadiusCompact))
                .background(Brush.linearGradient(entry.gradient))
        ) {
            // 左上打一束高光，纯色块不至于平成一张色纸
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset.Zero,
                            radius = 300f
                        )
                    )
            )

            // 同一个图标放大压在右上角当水印，越出的部分由卡片圆角裁掉
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 24.dp, y = (-16).dp)
            )

            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .padding(10.dp)
                    .size(20.dp)
                    .align(Alignment.TopStart)
            )

            Text(
                text = entry.title,
                color = entry.gradient.last(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 14.dp)
                    .fillMaxWidth(0.85f)
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Text(
            text = entry.subtitle,
            color = MelodiaColors.TextSecondary,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}
