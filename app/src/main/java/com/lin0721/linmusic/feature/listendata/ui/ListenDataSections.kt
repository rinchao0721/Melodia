package com.lin0721.linmusic.feature.listendata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.DataEnterStaggerFraction
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.feature.listendata.domain.DayDuration
import com.lin0721.linmusic.feature.listendata.domain.FriendListening
import com.lin0721.linmusic.feature.listendata.domain.Highlight
import com.lin0721.linmusic.feature.listendata.domain.TimePeriod
import com.lin0721.linmusic.feature.listendata.domain.TopArtist
import com.lin0721.linmusic.feature.listendata.domain.YearStat
import com.lin0721.linmusic.feature.listendata.domain.toHours
import com.lin0721.linmusic.feature.listendata.domain.toHoursText

private val CardShape = RoundedCornerShape(10.dp)
private val CardColor = Color.White.copy(alpha = 0.06f)
private val TrackColor = Color.White.copy(alpha = 0.06f)
// 柱体最高值，横轴标签的高度由 Row 自适应撑开，不能并进固定高度里
private val BarMaxHeight = 72.dp
private val BarMinHeight = 3.dp

// 背景墙取前若干张封面，再多在遮罩下也分辨不出，徒增图片请求
private const val CoverWallCount = 6

// 柱顶数值与柱体之间的呼吸。
private val BarValueGap = 2.dp

// 把总进度换算成组内第 index 项的进度：前 DataEnterStaggerFraction 用于错开各项起始，
// 余下比例是单项自身的生长时长，于是首项先动、末项收尾时整组同时结束
internal fun itemProgress(total: Float, index: Int, count: Int): Float {
    if (count <= 1) return total
    val start = DataEnterStaggerFraction * index / (count - 1)
    val span = 1f - DataEnterStaggerFraction
    return ((total - start) / span).coerceIn(0f, 1f)
}

// 数值按进度递增；进度未走完时向下取整，避免提前显示终值
internal fun Int.byProgress(progress: Float): Int = (this * progress).toInt()

// ======================= 通用小件 =======================

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MelodiaSpacing.lg, bottom = MelodiaSpacing.sm),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        if (!trailing.isNullOrBlank()) {
            Text(text = trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RowScope.StatCard(
    label: String,
    value: String,
    unit: String,
    caption: String? = null,
    icon: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(CardShape)
            .background(CardColor)
            .padding(MelodiaSpacing.sm + MelodiaSpacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(MelodiaSpacing.xs))
            }
            Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Spacer(Modifier.height(MelodiaSpacing.xxs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(MelodiaSpacing.xs))
            Text(
                text = unit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        if (!caption.isNullOrBlank()) {
            Text(text = caption, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

// ======================= 概览 =======================

// 本周期听过的封面拼成背景墙。单张被裁成竖条，靠重遮罩压成有色调层次的暗背景，
// 底部渐隐到页面底色，避免和下方卡片之间出现硬边
@Composable
private fun BoxScope.CoverWall(urls: List<String>) {
    if (urls.isEmpty()) return

    Row(modifier = Modifier.matchParentSize()) {
        urls.take(CoverWallCount).forEach { url ->
            SubcomposeAsyncImage(
                model = "$url?param=200y200",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = {},
                error = {},
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    // 顶部收进底色接住药丸行，中段留出封面色调，底部实色收尾接下方卡片
                    0f to BackgroundDark.copy(alpha = 0.95f),
                    0.35f to BackgroundDark.copy(alpha = 0.86f),
                    0.8f to BackgroundDark.copy(alpha = 0.93f),
                    1f to BackgroundDark
                )
            )
    )
}

@Composable
fun HeroSection(
    playMinutes: Int,
    beyondPercentText: String,
    achievementTitle: String,
    achievementSubtitle: String,
    listenDays: Int,
    songCount: Int,
    wallpaperUrls: List<String>,
    totalSeconds: Long,
    periodLabel: String,
    progress: Float
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            CoverWall(wallpaperUrls)

            Column(modifier = Modifier.padding(top = MelodiaSpacing.lg, bottom = MelodiaSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${periodLabel}收听",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (songCount > 0) {
                        Text(
                            text = " · 听过 $songCount 首",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(top = MelodiaSpacing.xxs)
                ) {
                    Text(
                        text = playMinutes.byProgress(progress).toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "分钟",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = MelodiaSpacing.xs, bottom = 7.dp)
                    )
                }

                if (beyondPercentText.isNotBlank()) {
                    val percent = beyondPercentText.filter { it.isDigit() }.toIntOrNull() ?: 0
                    Spacer(Modifier.height(MelodiaSpacing.sm))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TrackColor)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percent.coerceIn(0, 100) / 100f * progress)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        text = beyondPercentText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = MelodiaSpacing.xs)
                    )
                }
            }
        }

        if (achievementTitle.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MelodiaSpacing.md)
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(MelodiaSpacing.sm + MelodiaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(MelodiaSpacing.sm))
                Column {
                    Text(
                        text = achievementTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (achievementSubtitle.isNotBlank()) {
                        Text(
                            text = achievementSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.sm + MelodiaSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
        ) {
            StatCard(
                label = "听歌天数",
                value = listenDays.byProgress(progress).toString(),
                unit = "天"
            )
            StatCard(
                label = "累计收听",
                value = toHours(totalSeconds).toInt().byProgress(progress).toString(),
                unit = "小时"
            )
        }
    }
}

// ======================= 亮点 =======================

// 服务端编排好的三条亮点：最多收听 / 首个收藏 / 年代最远。文案直接用其原文，不自行拼接
@Composable
fun HighlightSection(highlights: List<Highlight>, periodLabel: String, onSongClick: (Long) -> Unit) {
    if (highlights.isEmpty()) return

    SectionTitle("${periodLabel}亮点")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm),
        contentPadding = PaddingValues(vertical = MelodiaSpacing.xxs)
    ) {
        items(highlights.size) { index ->
            val highlight = highlights[index]
            Column(
                modifier = Modifier
                    .width(132.dp)
                    .clip(CardShape)
                    .background(CardColor)
                    .pressable(MelodiaPress.Card) { onSongClick(highlight.songId) }
                    .padding(MelodiaSpacing.sm + MelodiaSpacing.xs)
            ) {
                SubcomposeAsyncImage(
                    model = "${highlight.coverUrl}?param=300y300",
                    contentDescription = highlight.songName,
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(RadiusCompact))
                )
                Spacer(Modifier.height(MelodiaSpacing.sm))
                Text(
                    text = highlight.label,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = highlight.songName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = MelodiaSpacing.xxs)
                )
                if (highlight.valueText.isNotBlank()) {
                    Text(
                        text = highlight.valueText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(top = MelodiaSpacing.xxs)
                    )
                }
            }
        }
    }
}

// ======================= 每日时长 =======================

@Composable
fun DailyChartSection(
    days: List<DayDuration>,
    progress: Float,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit
) {
    if (days.isEmpty()) return
    val maxMinutes = days.maxOf { it.minutes }.coerceAtLeast(1)
    val selected = selectedIndex?.let { days.getOrNull(it) }

    // 选中时标题右侧顶替成该日详情，省去在细柱子上做气泡定位
    SectionTitle(
        text = "每日时长",
        trailing = selected?.let { "${it.fullLabel} · ${it.minutes} 分钟" }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        // 月视图柱子多，间距压到 2dp 才不至于让柱体细得看不见
        horizontalArrangement = Arrangement.spacedBy(if (days.size > 10) 2.dp else MelodiaSpacing.xs)
    ) {
        days.forEachIndexed { index, day ->
            val grow = itemProgress(progress, index, days.size)
            val ratio = day.minutes.toFloat() / maxMinutes * grow
            val isPeak = day.minutes == maxMinutes
            val isSelected = selectedIndex == index
            // 选中态接管强调色；未选中时把其余柱子压暗，只留选中那根跳出来
            val barColor = when {
                isSelected -> MaterialTheme.colorScheme.primary
                selectedIndex != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                isPeak -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    // 柱子本身只有几 dp 宽，整列（含横轴标签）都做成命中区
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(if (isSelected) null else index) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                val showValue = if (selectedIndex == null) isPeak else isSelected
                Text(
                    text = "${day.minutes.byProgress(grow)}分",
                    color = if (showValue) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .wrapContentWidth(unbounded = true)
                        .padding(bottom = BarValueGap)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BarMaxHeight * ratio + BarMinHeight)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(barColor)
                )
                Text(
                    text = day.label,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    // 11sp 以下「日」这类笔画密的字在横轴上会糊成方块
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    // 月视图单根柱子只有几 dp 宽，标签须允许溢出，否则两位数日期会被截成一位
                    modifier = Modifier
                        .padding(top = MelodiaSpacing.xs)
                        .wrapContentWidth(unbounded = true)
                )
            }
        }
    }
}

// ======================= 时段偏好 =======================

// 六个时段各自的意象图标，未知 key 退回通用时钟
private fun periodIcon(key: String): ImageVector = when (key) {
    "early_morning" -> Icons.Rounded.Bedtime
    "morning" -> Icons.Rounded.WbSunny
    "noon" -> Icons.Rounded.LightMode
    "afternoon" -> Icons.Rounded.WbTwilight
    "night" -> Icons.Rounded.NightsStay
    "deep_night" -> Icons.Rounded.DarkMode
    else -> Icons.Rounded.Schedule
}

@Composable
fun TimePeriodSection(
    periods: List<TimePeriod>,
    progress: Float,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit
) {
    if (periods.isEmpty()) return
    // 按时长降序，最长的时段排在最上，弱化全天几乎为零的时段
    val sorted = periods.sortedByDescending { it.minutes }
    val maxMinutes = sorted.first().minutes.coerceAtLeast(1)
    val totalMinutes = sorted.sumOf { it.minutes }
    val selected = selectedIndex?.let { sorted.getOrNull(it) }

    SectionTitle(
        text = "时段偏好",
        trailing = selected?.let {
            // 全天为零时不显示占比，避免除零后给出 0% 的误导结论
            if (totalMinutes > 0) "${it.label}占 ${it.minutes * 100 / totalMinutes}%" else it.label
        }
    )
    sorted.forEachIndexed { index, period ->
        val grow = itemProgress(progress, index, sorted.size)
        val isSelected = selectedIndex == index
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSelect(if (isSelected) null else index) }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = periodIcon(period.key),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(MelodiaSpacing.xs + 2.dp))
            Text(
                text = period.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.width(32.dp)
            )
            Spacer(Modifier.width(MelodiaSpacing.sm))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TrackColor)
            ) {
                if (period.minutes > 0) {
                    val barColor = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        selectedIndex != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        period.minutes == maxMinutes -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(period.minutes.toFloat() / maxMinutes * grow)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
            }
            Spacer(Modifier.width(MelodiaSpacing.sm))
            Text(
                text = "${period.minutes.byProgress(grow)}分",
                color = if (isSelected || (selectedIndex == null && period.minutes == maxMinutes)) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 11.sp,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

// ======================= 最常听的歌手 =======================

@Composable
fun TopArtistsSection(artists: List<TopArtist>, onArtistClick: (Long) -> Unit) {
    if (artists.isEmpty()) return

    SectionTitle("最常听的歌手")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(vertical = MelodiaSpacing.xxs)
    ) {
        items(artists.size) { index ->
            val artist = artists[index]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .pressable(MelodiaPress.Card) { onArtistClick(artist.id) }
            ) {
                SubcomposeAsyncImage(
                    model = "${artist.coverUrl}?param=200y200",
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() },
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                )
                Text(
                    text = artist.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = MelodiaSpacing.xs)
                )
                Text(
                    text = artist.countText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// ======================= 听歌偏好 =======================

@Composable
fun PreferenceSection(genreName: String, ageLabel: String, languageName: String) {
    val items = listOf("曲风" to genreName, "年代" to ageLabel, "语种" to languageName)
        .filter { it.second.isNotBlank() }
    if (items.isEmpty()) return

    SectionTitle("听歌偏好")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
    ) {
        items.forEach { (label, value) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(CardShape)
                    .background(CardColor)
                    .padding(vertical = MelodiaSpacing.sm + MelodiaSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = MelodiaSpacing.xxs)
                )
            }
        }
    }
}

// ======================= 播客与会员 =======================

@Composable
fun PodcastVipSection(
    podcastMinutes: Int,
    podcastEpisodes: Int,
    vipMainText: String,
    vipSubText: String,
    progress: Float
) {
    val hasPodcast = podcastMinutes > 0 || podcastEpisodes > 0
    val hasVip = vipMainText.isNotBlank()
    if (!hasPodcast && !hasVip) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
    ) {
        if (hasPodcast) {
            StatCard(
                label = "播客",
                value = podcastMinutes.byProgress(progress).toString(),
                unit = "分钟",
                caption = "$podcastEpisodes 期节目",
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                }
            )
        }
        if (hasVip) {
            StatCard(
                label = "会员畅听",
                // 服务端给的是「48首」这类混排文本，拆出数字才能跟着进度递增
                value = (vipMainText.filter { it.isDigit() }.toIntOrNull() ?: 0)
                    .byProgress(progress).toString(),
                unit = vipMainText.filter { !it.isDigit() }.ifBlank { "首" },
                caption = vipSubText,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                }
            )
        }
    }
}

// ======================= 好友 =======================

@Composable
fun FriendsSection(friends: List<FriendListening>, keywords: List<String>) {
    if (friends.isEmpty() && keywords.isEmpty()) return

    SectionTitle("好友在听")
    friends.forEach { friend ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = MelodiaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = "${friend.avatarUrl}?param=100y100",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() },
                modifier = Modifier.size(36.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(MelodiaSpacing.sm + MelodiaSpacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.username,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (friend.playCount > 0) "${friend.songName} · 听了 ${friend.playCount} 次"
                    else friend.songName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (keywords.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = MelodiaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
        ) {
            keywords.take(3).forEach { keyword ->
                Text(
                    text = keyword,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = MelodiaSpacing.sm + MelodiaSpacing.xs, vertical = 5.dp)
                )
            }
        }
    }
}

// ======================= 历年收听 =======================

@Composable
fun YearStatsSection(stats: List<YearStat>, progress: Float) {
    if (stats.isEmpty()) return
    val maxDuration = stats.maxOf { it.durationSeconds }.coerceAtLeast(1)

    SectionTitle("历年收听")
    stats.forEachIndexed { index, stat ->
        val grow = itemProgress(progress, index, stats.size)
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = MelodiaSpacing.xs + 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stat.year.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${stat.playCount} 首 · ${toHoursText(stat.durationSeconds)} 小时",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(MelodiaSpacing.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(RadiusCompact / 2))
                    .background(TrackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(stat.durationSeconds.toFloat() / maxDuration * grow)
                        .height(7.dp)
                        .clip(RoundedCornerShape(RadiusCompact / 2))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
