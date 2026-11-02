package com.lin0721.linmusic.feature.newworks.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.newworks.domain.NewWorksRelease
import com.lin0721.linmusic.feature.newworks.domain.typeLabel

private const val DISABLED_ALPHA = 0.38f

// 新发布卡片的「更多」菜单：添加到音乐库 / 加入播放队列 / 加入歌单 / 下载（暂不可用）/ 分享
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewWorksReleaseOptionsSheet(
    release: NewWorksRelease,
    inLibrary: Boolean,
    onDismiss: () -> Unit,
    onAddToLibrary: () -> Unit,
    onAddToPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubcomposeAsyncImage(
                    model = "${release.coverUrl}?param=150y150",
                    contentDescription = release.title,
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() },
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(MelodiaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${release.typeLabel} · ${release.artistName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MelodiaColors.Divider,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = MelodiaSpacing.sm)
            )

            OptionRow(
                if (inLibrary) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (inLibrary) "从音乐库移除" else "添加到音乐库"
            ) {
                onDismiss()
                onAddToLibrary()
            }
            OptionRow(Icons.AutoMirrored.Rounded.QueueMusic, "加入播放队列") {
                onDismiss()
                onAddToPlayNext()
            }
            OptionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "加入歌单") {
                onDismiss()
                onAddToPlaylist()
            }
            OptionRow(Icons.Rounded.FileDownload, "下载", enabled = false) {}
            OptionRow(Icons.Rounded.Share, "分享") {
                onDismiss()
                shareRelease(context, release)
            }
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, enabled: Boolean = true, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            fontSize = 16.sp
        )
    }
}

private fun shareRelease(context: android.content.Context, release: NewWorksRelease) {
    val path = if (release.isAlbum) "album" else "song"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${release.title} https://music.163.com/$path?id=${release.id}")
    }
    context.startActivity(Intent.createChooser(intent, "分享${release.typeLabel}"))
}
