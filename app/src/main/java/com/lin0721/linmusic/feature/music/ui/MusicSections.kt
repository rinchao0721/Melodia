package com.lin0721.linmusic.feature.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.feature.music.domain.StyleArtistItem
import com.lin0721.linmusic.feature.music.domain.StylePlaylistItem

// 曲风页各段共用的标题
@Composable
fun MusicSectionTitle(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MusicEdgePadding, end = MusicEdgePadding, top = 19.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        trailing?.let {
            Text(text = it, color = TextGray, fontSize = 11.5.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// 热门歌单：封面已自带 imageView 查询串，拼参数前必须判断
@Composable
fun MusicPlaylistRow(playlists: List<StylePlaylistItem>, onClick: (StylePlaylistItem) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = MusicEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(playlists, key = { index, item -> "${item.id}_$index" }) { _, item ->
            Column(modifier = Modifier.width(126.dp).pressable(MelodiaPress.Card) { onClick(item) }) {
                SubcomposeAsyncImage(
                    model = item.coverUrl.withStyleCoverParam("300y300"),
                    contentDescription = item.name,
                    modifier = Modifier.size(126.dp).clip(RoundedCornerShape(RadiusCompact)),
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() }
                )
                Text(
                    text = item.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = item.playCount.toPlayCountText(),
                    color = TextGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// 必听单曲：带序号，点击整段入队从该首起播
@Composable
fun MusicSongList(songs: List<Track>, onPlayAt: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        songs.forEachIndexed { index, track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayAt(index) }
                    .padding(horizontal = MusicEdgePadding, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    color = TextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.width(18.dp)
                )
                SubcomposeAsyncImage(
                    model = track.al.picUrl.withStyleCoverParam("120y120"),
                    contentDescription = track.name,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(RadiusCompact)),
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() }
                )
                Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(
                        text = track.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.ar.joinToString("/") { it.name },
                        color = TextGray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }
}

// 代表歌手：圆形头像，取 1:1 图避免裁脸
@Composable
fun MusicArtistRow(artists: List<StyleArtistItem>, onClick: (StyleArtistItem) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = MusicEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(artists, key = { index, item -> "${item.id}_$index" }) { _, item ->
            Column(
                modifier = Modifier.width(76.dp).pressable(MelodiaPress.Card) { onClick(item) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = item.picUrl.withStyleCoverParam("200y200"),
                    contentDescription = item.name,
                    modifier = Modifier.size(76.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = item.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp)
                )
                if (item.musicSize > 0) {
                    Text(
                        text = "${item.musicSize} 首",
                        color = TextGray,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun MusicSectionLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// 曲风歌单封面自带 ?imageView=… 查询串，无脑追加会拼出两个问号的非法地址
internal fun String.withStyleCoverParam(param: String): String =
    if (contains('?')) this else "$this?param=$param"

private fun Long.toPlayCountText(): String = when {
    this >= 100_000_000 -> "${this / 100_000_000} 亿次播放"
    this >= 10_000 -> "${this / 10_000} 万次播放"
    this <= 0 -> ""
    else -> "$this 次播放"
}
