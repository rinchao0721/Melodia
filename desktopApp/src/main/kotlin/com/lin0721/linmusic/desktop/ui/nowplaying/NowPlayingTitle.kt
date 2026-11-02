package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.desktop.ui.shell.DesktopNavigator
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.launch

private const val NO_ALBUM_MESSAGE = "暂无专辑信息"

// 跳转当前歌曲所属专辑；无云端歌曲或无专辑时提示
internal suspend fun openCurrentSongAlbum(songId: Long?, playerViewModel: PlayerViewModel, navigator: DesktopNavigator) {
    val album = songId?.takeIf { it > 0L }
        ?.let { playerViewModel.resolveSongDetail(it) }
        ?.al
        ?.takeIf { it.id > 0L }
    if (album == null) navigator.showMessage(NO_ALBUM_MESSAGE) else navigator.openAlbum(album.id, album.name)
}

// 点击歌曲名进入所属专辑，播客节目不可点
@Composable
internal fun NowPlayingSongTitle(
    track: NowPlaying,
    playerViewModel: PlayerViewModel,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1
) {
    val navigator = LocalDesktopNavigator.current
    val podcast by playerViewModel.podcastState.collectAsState()
    val scope = rememberCoroutineScope()
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val clickable = !podcast.isPodcast
    Text(
        track.title,
        color = DesktopColors.TextPrimary,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (clickable && hovered) TextDecoration.Underline else null,
        modifier = if (clickable) {
            modifier.clip(RoundedCornerShape(4.dp)).pointerHoverIcon(PointerIcon.Hand).hoverable(source)
                .clickable(interactionSource = source, indication = null) {
                    scope.launch { openCurrentSongAlbum(track.songId, playerViewModel, navigator) }
                }
        } else {
            modifier
        }
    )
}
