package com.lin0721.linmusic.feature.cloud.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.theme.MelodiaColors

// 删除云盘歌曲二次确认，视觉范式对齐 PlayQueueSheet 的清空队列弹窗，不做撤销缓冲
@Composable
fun CloudDeleteConfirmDialog(
    songName: String,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("删除云盘歌曲", color = MelodiaColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = { Text("确定要删除「$songName」吗？删除后无法恢复。", color = MelodiaColors.TextSecondary, fontSize = 14.sp) },
        confirmButton = {
            MelodiaTextButton(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (isDeleting) "删除中..." else "是的", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            MelodiaTextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("取消", color = MaterialTheme.colorScheme.onSurface)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    )
}
