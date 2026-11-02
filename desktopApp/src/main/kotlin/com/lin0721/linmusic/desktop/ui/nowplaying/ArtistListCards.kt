package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.player.presentation.artistAlbumsTitle
import kotlinx.coroutines.launch

private val AlbumWidth = 120.dp
private val ArtistWidth = 80.dp
private val ArtistAvatarSize = 64.dp
private const val SCROLL_FRACTION = 0.8f
private const val SCROLL_MS = 300

// 艺人的更多专辑：横向列表，点击进入专辑
@Composable
fun ArtistAlbumsCard(albums: List<ArtistAlbum>, artistName: String?, modifier: Modifier = Modifier) {
    val navigator = LocalDesktopNavigator.current
    InfoCard(artistAlbumsTitle(artistName), modifier) {
        HoverArrowRow(Modifier.padding(top = 12.dp)) {
            items(albums, key = { it.id }) { album ->
                Column(
                    Modifier.width(AlbumWidth).clip(RoundedCornerShape(8.dp)).pointerHoverIcon(PointerIcon.Hand)
                        .clickable { navigator.openAlbum(album.id, album.name) }
                        .padding(4.dp)
                ) {
                    Cover(album.picUrl, AlbumWidth - 8.dp, shape = RoundedCornerShape(8.dp))
                    Text(
                        album.name,
                        color = DesktopColors.TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// 相似艺人：圆形头像横向列表，点击进入歌手页
@Composable
fun SimilarArtistsCard(artists: List<ArtistInfo>, modifier: Modifier = Modifier) {
    val navigator = LocalDesktopNavigator.current
    InfoCard("探索类似艺人", modifier) {
        HoverArrowRow(Modifier.padding(top = 12.dp)) {
            items(artists, key = { it.id }) { artist ->
                Column(
                    Modifier.width(ArtistWidth).clip(RoundedCornerShape(8.dp)).pointerHoverIcon(PointerIcon.Hand)
                        .clickable { navigator.openArtist(artist.id, artist.name) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Cover(artist.avatarUrl, ArtistAvatarSize, shape = CircleShape)
                    Text(
                        artist.name,
                        color = DesktopColors.TextPrimary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp)
                    )
                }
            }
        }
    }
}

// 桌面端滚轮默认只滚竖向，横向列表靠悬停时两侧出现的箭头翻页
@Composable
private fun HoverArrowRow(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val motion = LocalUiMotion.current
    val scrollBy: (Int) -> Unit = { direction ->
        val distance = state.layoutInfo.viewportSize.width * SCROLL_FRACTION * direction
        scope.launch {
            if (motion.enabled) state.animateScrollBy(distance, motion.tween(SCROLL_MS)) else state.scrollBy(distance)
        }
    }
    Box(modifier.fillMaxWidth().hoverable(hoverSource)) {
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
        ScrollArrow(hovered && state.canScrollBackward, left = true, "向左", Modifier.align(Alignment.CenterStart)) { scrollBy(-1) }
        ScrollArrow(hovered && state.canScrollForward, left = false, "向右", Modifier.align(Alignment.CenterEnd)) { scrollBy(1) }
    }
}
