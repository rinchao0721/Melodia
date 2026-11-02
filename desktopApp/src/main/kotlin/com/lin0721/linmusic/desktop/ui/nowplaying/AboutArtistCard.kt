package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.format.formatCompactCount
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.sizedCoverUrl
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.player.presentation.descPreview
import com.lin0721.linmusic.feature.player.presentation.initialAboutPage
import com.lin0721.linmusic.feature.player.presentation.validAboutArtists
import com.lin0721.linmusic.feature.player.ui.ArtistCardItem
import kotlinx.coroutines.launch

private val HeroHeight = 200.dp
private const val HERO_REQUEST_PX = 800
// 箭头垂直居中于封面区
private val ArrowTop = (HeroHeight - ScrollArrowSize) / 2

// 关于艺人：歌曲有多位歌手时可左右切换，切换后下方的“更多专辑”“相似艺人”随之刷新
@Composable
fun AboutArtistCard(
    artists: List<ArtistCardItem>,
    selectedIndex: Int,
    onSelectArtist: (Int) -> Unit,
    onToggleFollow: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val valid = remember(artists) { validAboutArtists(artists) }
    if (valid.isEmpty()) return
    if (valid.size == 1) {
        ArtistPage(valid.first(), onToggleFollow, modifier)
        return
    }

    val pagerState = rememberPagerState(initialPage = initialAboutPage(valid, artists, selectedIndex)) { valid.size }
    val scope = rememberCoroutineScope()
    val motion = LocalUiMotion.current
    val turnPage: suspend (Int) -> Unit = { page ->
        if (motion.enabled) pagerState.animateScrollToPage(page) else pagerState.scrollToPage(page)
    }
    LaunchedEffect(pagerState.currentPage, valid) {
        val target = valid.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val originalIndex = artists.indexOfFirst { it.artistId == target.artistId }
        if (originalIndex >= 0) onSelectArtist(originalIndex)
    }

    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.hoverable(hoverSource)) {
            // 桌面端没有横滑手势，翻页只走两侧箭头
            HorizontalPager(state = pagerState, userScrollEnabled = false, modifier = Modifier.fillMaxWidth()) { page ->
                ArtistPage(valid[page], onToggleFollow)
            }
            ScrollArrow(
                revealed = hovered && pagerState.currentPage > 0,
                left = true,
                description = "上一位歌手",
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = ArrowTop)
            ) { scope.launch { turnPage(pagerState.currentPage - 1) } }
            ScrollArrow(
                revealed = hovered && pagerState.currentPage < valid.lastIndex,
                left = false,
                description = "下一位歌手",
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = ArrowTop)
            ) { scope.launch { turnPage(pagerState.currentPage + 1) } }
        }
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(valid.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    Modifier.size(if (selected) 6.dp else 5.dp).clip(CircleShape)
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.25f))
                )
            }
        }
    }
}

@Composable
private fun ArtistPage(item: ArtistCardItem, onToggleFollow: (Long) -> Unit, modifier: Modifier = Modifier) {
    val navigator = LocalDesktopNavigator.current
    val detail = item.artistDetail ?: return
    val heroUrl = detail.cover.ifEmpty { detail.avatar }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DesktopColors.CardSurface)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { navigator.openArtist(item.artistId, detail.name) }
    ) {
        Box(Modifier.fillMaxWidth().height(HeroHeight).background(DesktopColors.CoverPlaceholder)) {
            SubcomposeAsyncImage(
                model = sizedCoverUrl(heroUrl, HERO_REQUEST_PX),
                contentDescription = detail.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 顶部压一层渐变，保证标题在亮图上也可读
            Box(
                Modifier.fillMaxWidth().height(60.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)))
            )
            Text(
                "关于艺人",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            )
        }
        Column(Modifier.padding(16.dp).animateContentSize(uiSpring())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        detail.name,
                        color = DesktopColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.fansCount?.let { "${formatCompactCount(it)}粉丝" } ?: "--粉丝",
                        color = DesktopColors.TextGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                FollowButton(item.isFollowed) { onToggleFollow(item.artistId) }
            }
            if (detail.briefDesc.isNotBlank()) {
                val preview = descPreview(detail.briefDesc, expanded)
                val emphasis = remember { SpanStyle(color = Color.White, fontWeight = FontWeight.Bold) }
                Text(
                    remember(preview, expanded) {
                        buildAnnotatedString {
                            append(preview.text)
                            if (preview.canToggle) {
                                append(" ")
                                withStyle(emphasis) { append(if (expanded) "收起" else "查看更多") }
                            }
                        }
                    },
                    color = DesktopColors.TextGray,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        .then(if (preview.canToggle) Modifier.clickable { expanded = !expanded } else Modifier)
                )
            }
        }
    }
}

// 登录态由调用方在 onToggleFollow 里判断，按钮本身只负责展示与点击
@Composable
private fun FollowButton(followed: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.TextPrimary)
    ) {
        Text(if (followed) "已关注" else "关注", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
