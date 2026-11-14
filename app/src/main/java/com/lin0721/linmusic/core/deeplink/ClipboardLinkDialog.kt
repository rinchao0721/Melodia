package com.lin0721.linmusic.core.deeplink

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 打开应用时发现剪贴板里有网易云链接，先亮出这首歌再问一句，避免误触
@Composable
fun ClipboardLinkDialog(
    link: ClipboardLink,
    track: Track?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val song = link.target as? SongLinkTarget.Song
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (song != null) "剪贴板里的这首歌" else "剪贴板里的链接",
                color = MelodiaColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (song == null) {
                Text(
                    "要在 Melodia 里打开这个链接吗？\n${link.text.take(120)}",
                    color = MelodiaColors.TextSecondary,
                    fontSize = 14.sp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinkCover(url = track?.al?.picUrl)
                    Spacer(Modifier.width(MelodiaSpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track?.name ?: "读取中…",
                            color = MelodiaColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            track?.ar?.joinToString(" / ") { it.name }.orEmpty(),
                            color = MelodiaColors.TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            MelodiaTextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MelodiaColors.Accent)
            ) {
                Text(if (song != null) "播放" else "打开", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            MelodiaTextButton(onClick = onDismiss) {
                Text("不用", color = MelodiaColors.TextPrimary)
            }
        },
        containerColor = MelodiaColors.Surface,
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun LinkCover(url: String?) {
    val context = LocalContext.current
    val model = remember(url) {
        url?.takeIf { it.isNotBlank() }?.let {
            val sized = if ("param=" in it) it else "$it?param=200y200"
            ImageRequest.Builder(context).data(sized).size(200, 200).build()
        }
    }
    if (model == null) {
        Spacer(Modifier.size(56.dp))
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}
