package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 默认文案对应"新建歌单并收藏"，本地歌单新建/重命名传入自己的文案
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit,
    title: String = "新建歌单并收藏",
    confirmText: String = "创建并收藏",
    initialName: String = "",
    availablePlatforms: List<MusicPlatform> = emptyList(),
    onCreateForPlatform: ((name: String, platform: MusicPlatform) -> Unit)? = null
) {
    var nameInput by remember { mutableStateOf(initialName) }
    var selectedPlatform by remember(availablePlatforms) {
        mutableStateOf(availablePlatforms.firstOrNull() ?: MusicPlatform.NETEASE)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (availablePlatforms.size > 1 && onCreateForPlatform != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)) {
                        availablePlatforms.forEach { platform ->
                            val label = if (platform == MusicPlatform.KUGOU) "酷狗音乐" else "网易云音乐"
                            FilterChip(
                                selected = selectedPlatform == platform,
                                onClick = { selectedPlatform = platform },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(MelodiaSpacing.sm))
                }
                Text("请输入新歌单的名称：", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(MelodiaSpacing.sm))
                PlaceholderTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = "歌单名称"
                )
            }
        },
        confirmButton = {
            MelodiaTextButton(
                onClick = {
                    if (nameInput.isNotBlank()) {
                        if (onCreateForPlatform != null) {
                            onCreateForPlatform(nameInput, selectedPlatform)
                        } else {
                            onCreate(nameInput)
                        }
                        onDismiss()
                    } else {
                        ToastManager.showToast("名字不能为空哦！")
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            MelodiaTextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurface)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    )
}
