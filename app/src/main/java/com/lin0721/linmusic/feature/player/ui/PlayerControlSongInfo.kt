package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.PersonalFmModeOrb
import com.lin0721.linmusic.core.ui.interaction.copyOnLongClick
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.podcast.ui.PodcastPlayerState

// 私人FM 模式胶囊入口：左侧展示当前模式极光圆点，点击打开模式选择弹层
@Composable
fun PersonalFmModePill(
    mode: PersonalFmMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillBackground = MelodiaColors.TextPrimary.copy(
        alpha = if (MelodiaColors.IsDark) 0.12f else 0.08f
    )
    Row(
        modifier = modifier
            .pressable(MelodiaPress.Pill, shape = CircleShape) { onClick() }
            .clip(CircleShape)
            .background(pillBackground)
            .padding(start = 5.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PersonalFmModeOrb(mode = mode)
        Text(
            text = "${mode.displayName}模式",
            color = MelodiaColors.TextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = "切换FM模式",
            tint = MelodiaColors.TextPrimary.copy(alpha = 0.75f),
            modifier = Modifier.size(14.dp)
        )
    }
}

// 封面下方的歌名歌手行，右侧带收藏按钮；私人FM 上下文时在歌名上方展示模式胶囊
@Composable
fun SongInfo(
    title: String,
    artist: String,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onArtistClick: (() -> Unit)? = null,
    onAlbumClick: (() -> Unit)? = null,
    // 播客节目：♡ 直接订阅所属电台
    podcast: PodcastPlayerState = PodcastPlayerState(),
    onToggleSubscribe: () -> Unit = {},
    isPersonalFm: Boolean = false,
    personalFmMode: PersonalFmMode = PersonalFmMode.DEFAULT,
    onPersonalFmModeClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.md)) {
            if (isPersonalFm && onPersonalFmModeClick != null) {
                PersonalFmModePill(
                    mode = personalFmMode,
                    onClick = onPersonalFmModeClick,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            SongTitleText(title, onAlbumClick)
            Spacer(modifier = Modifier.height(4.dp))
            SongArtistText(artist, onArtistClick)
        }
        SongLikeButton(isLiked, onToggleLike, podcast, onToggleSubscribe, Modifier.offset(x = 10.dp))
    }
}

// 单行时过长滚动显示，多行时按行数截断
@Composable
fun SongTitleText(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    lines: Int = 1
) {
    Text(
        text = title,
        color = MelodiaColors.TextPrimary,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        maxLines = lines,
        overflow = if (lines > 1) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier
            .then(if (lines == 1) Modifier.basicMarquee() else Modifier)
            .copyOnLongClick(title, "歌名", onClick = { onClick?.invoke() })
    )
}

@Composable
fun SongArtistText(
    artist: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp
) {
    Text(
        text = artist,
        color = MelodiaColors.TextPrimary.copy(alpha = 0.75f),
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.copyOnLongClick(artist, "歌手", onClick = { onClick?.invoke() })
    )
}

@Composable
fun SongLikeButton(
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    podcast: PodcastPlayerState,
    onToggleSubscribe: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (podcast.isPodcast) {
        if (podcast.canSubscribe) {
            MelodiaIconButton(onClick = onToggleSubscribe, modifier = modifier) {
                Icon(
                    if (podcast.subscribed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (podcast.subscribed) "取消订阅电台" else "订阅电台",
                    tint = MelodiaColors.TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    } else MelodiaIconButton(onClick = onToggleLike, modifier = modifier) {
        Icon(
            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = null,
            tint = MelodiaColors.TextPrimary,
            modifier = Modifier.size(28.dp)
        )
    }
}
