package com.lin0721.linmusic.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.EntryDailyGradient
import com.lin0721.linmusic.core.ui.theme.EntryHeartGradient
import com.lin0721.linmusic.core.ui.theme.EntryHotGradient
import com.lin0721.linmusic.core.ui.theme.EntryRadarGradient
import com.lin0721.linmusic.core.ui.theme.EntryRoamingGradient
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.feature.home.data.PersonalizedPlaylist
import com.lin0721.linmusic.feature.home.domain.ToplistInfo

// 平板下卡片略微放大，手机不变
private val EntryCardSizeCompact = 132.dp
private val EntryCardSizeExpanded = 160.dp

// 一个功能入口。这几个功能没有对应的封面资源，一律用策展色表达，
// 不再从推荐歌单里借图——借来的封面跟点进去的功能毫无关系。
private data class ForYouEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val onClick: () -> Unit
)

// 为你推荐：功能入口横排。依赖具体歌单 id 的入口在拿不到 id 时直接不出现，
// 避免出现点进去是无关内容的死入口。
@Composable
fun ForYouSection(
    dailySongs: List<DailySong>,
    toplists: List<ToplistInfo>,
    recommendPlaylists: List<PersonalizedPlaylist>,
    onDailyRecommendClick: () -> Unit,
    onHotlistClick: (Long) -> Unit,
    onIntelligenceClick: () -> Unit,
    onRadarClick: (Long) -> Unit,
    onRoamingClick: () -> Unit
) {
    val entryCardSize = if (LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded) {
        EntryCardSizeExpanded
    } else {
        EntryCardSizeCompact
    }

    val hotlist = remember(toplists) {
        toplists.firstOrNull { it.name.contains("热") } ?: toplists.firstOrNull()
    }

    // 只认名字里带「雷达」的歌单，匹配不上就不放这个入口
    val radarPlaylist = remember(recommendPlaylists) {
        recommendPlaylists.firstOrNull { it.name.contains("雷达") }
    }

    val entries = remember(dailySongs, hotlist, radarPlaylist) {
        buildList {
            add(
                ForYouEntry(
                    key = "daily",
                    title = "每日推荐",
                    subtitle = if (dailySongs.isEmpty()) "每天 6 点更新" else "${dailySongs.size} 首专属好歌",
                    icon = Icons.Rounded.DateRange,
                    gradient = EntryDailyGradient,
                    onClick = onDailyRecommendClick
                )
            )
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

    Column(modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.lg)) {
        Text(
            text = "为你推荐",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = HomeEdgePadding, end = HomeEdgePadding, bottom = 13.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = HomeEdgePadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.key }) { entry ->
                Box(
                    modifier = Modifier
                        .size(entryCardSize)
                        .pressable(MelodiaPress.Card) { entry.onClick() }
                        .clip(RoundedCornerShape(PillRadius))
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

                    // 同一个图标放大压在右下角当水印，越出的部分由卡片圆角裁掉，做出纵深
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .size(94.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 24.dp, y = 20.dp)
                    )

                    Column(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                        Column {
                            Text(
                                text = entry.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = entry.subtitle,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 10.5.sp,
                                lineHeight = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
