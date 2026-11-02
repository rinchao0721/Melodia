package com.lin0721.linmusic.feature.home.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.coverBase
import com.lin0721.linmusic.core.ui.theme.FallbackBase
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.extractBaseColorFromUrl
import com.lin0721.linmusic.core.ui.theme.immersiveMidFill
import com.lin0721.linmusic.core.ui.theme.uiSpring
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.feature.home.data.PersonalizedPlaylist
import com.lin0721.linmusic.feature.home.domain.ToplistInfo
import java.time.LocalDate

// 封面取色后再压暗，保证白字在任意封面色上都看得清
private const val SPOTLIGHT_DARKEN_FRACTION = 0.35f

// 曲目串最多拼几首，再多也会被两行省略截掉
private const val SPOTLIGHT_TRACK_PREVIEW_COUNT = 8

// 并排时「为你定制」最多占整行的比例，余下留给聚焦卡
private const val FOR_YOU_SIDE_MAX_FRACTION = 0.6f

private val SpotlightCoverSize = 64.dp

// 每日推荐聚焦卡 + 为你定制。平板横屏两者并排，其余断点上下排列
@Composable
fun HomeHighlightsSection(
    dailySongs: List<DailySong>,
    toplists: List<ToplistInfo>,
    recommendPlaylists: List<PersonalizedPlaylist>,
    onDailyOpen: () -> Unit,
    onDailyPlay: () -> Unit,
    onHotlistClick: (Long) -> Unit,
    onIntelligenceClick: () -> Unit,
    onRadarClick: (Long) -> Unit,
    onRoamingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = rememberForYouEntries(
        toplists = toplists,
        recommendPlaylists = recommendPlaylists,
        onHotlistClick = onHotlistClick,
        onIntelligenceClick = onIntelligenceClick,
        onRadarClick = onRadarClick,
        onRoamingClick = onRoamingClick
    )
    val sideBySide = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded &&
        LocalMelodiaOrientationClass.current == MelodiaOrientationClass.Landscape

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardWidth = rememberHomeCardWidth(maxWidth)

        if (sideBySide && dailySongs.isNotEmpty()) {
            val forYouWidth = min(
                HomeEdgePadding + cardWidth * entries.size + HomeCardGap * entries.size,
                maxWidth * FOR_YOU_SIDE_MAX_FRACTION
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = HomeSectionSpacing)) {
                // 卡片拉到与右侧入口方块等高，两列底边对齐
                DailySpotlightSection(
                    songs = dailySongs,
                    onOpen = onDailyOpen,
                    onPlay = onDailyPlay,
                    cardMinHeight = cardWidth,
                    modifier = Modifier.weight(1f).padding(start = HomeEdgePadding)
                )
                ForYouSection(
                    entries = entries,
                    cardWidth = cardWidth,
                    contentPadding = PaddingValues(start = HomeCardGap + 8.dp, end = HomeEdgePadding),
                    modifier = Modifier.width(forYouWidth)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (dailySongs.isNotEmpty()) {
                    DailySpotlightSection(
                        songs = dailySongs,
                        onOpen = onDailyOpen,
                        onPlay = onDailyPlay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = HomeSectionSpacing)
                            .padding(horizontal = HomeEdgePadding)
                    )
                }
                ForYouSection(
                    entries = entries,
                    cardWidth = cardWidth,
                    contentPadding = PaddingValues(horizontal = HomeEdgePadding),
                    modifier = Modifier.fillMaxWidth().padding(top = HomeSectionSpacing)
                )
            }
        }
    }
}

@Composable
private fun DailySpotlightSection(
    songs: List<DailySong>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    cardMinHeight: Dp = 0.dp
) {
    Column(modifier = modifier) {
        HomeSectionTitle(text = "今日为你推荐")
        DailySpotlightCard(songs = songs, onOpen = onOpen, onPlay = onPlay, minHeight = cardMinHeight)
    }
}

// 底色取自首曲封面
@Composable
private fun DailySpotlightCard(
    songs: List<DailySong>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    minHeight: Dp
) {
    val context = LocalContext.current
    val coverUrl = songs.firstOrNull()?.al?.picUrl.orEmpty()
    var baseColor by remember { mutableStateOf(FallbackBase) }
    LaunchedEffect(coverUrl) {
        if (coverUrl.isNotBlank()) {
            baseColor = extractBaseColorFromUrl(context, coverUrl)
        }
    }
    // 浅色下改用与播放页一致的浓色中调，避免向黑混色在白底上发脏
    val isDark = MelodiaColors.IsDark
    val containerColor by animateColorAsState(
        targetValue = coverBase(CoverGroup.CARDS, baseColor)?.let { if (isDark) lerp(it, Color.Black, SPOTLIGHT_DARKEN_FRACTION) else immersiveMidFill(it) }
            ?: MelodiaColors.ShellFill,
        animationSpec = uiSpring(),
        label = "daily_spotlight_color"
    )
    val today = remember { LocalDate.now() }
    val countColor = MelodiaColors.OnCoverCard
    val trackLine = remember(songs, countColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = countColor, fontWeight = FontWeight.Bold)) {
                append("${songs.size} 首")
            }
            songs.take(SPOTLIGHT_TRACK_PREVIEW_COUNT).forEach { song ->
                append(" · ")
                append(song.name)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .pressable(MelodiaPress.Card) { onOpen() }
            .clip(RoundedCornerShape(RadiusCompact))
            .background(containerColor)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubcomposeAsyncImage(
                model = coverUrl.withCoverParam("200y200"),
                contentDescription = null,
                modifier = Modifier
                    .size(SpotlightCoverSize)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() }
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = "每日推荐",
                    color = MelodiaColors.OnCoverCard,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "根据你的口味 · ${today.monthValue}月${today.dayOfMonth}日",
                    color = MelodiaColors.OnCoverCard.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = trackLine,
                color = MelodiaColors.OnCoverCard.copy(alpha = 0.75f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            MelodiaIconButton(
                onClick = onPlay,
                style = MelodiaPress.Transport,
                containerColor = MelodiaColors.OnCoverCard
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放每日推荐",
                    tint = MelodiaColors.OnCoverCardInverse,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
