package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.PlaceholderTextField
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

private const val MAX_NAME_LENGTH = 40
private const val MAX_DESC_LENGTH = 1000

// "编辑歌单信息"底部半屏弹窗，支持同时修改歌单标题和简介
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistInfoDialog(
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nameInput by remember { mutableStateOf(initialName) }
    var descInput by remember { mutableStateOf(initialDescription) }

    val isChanged = nameInput.trim() != initialName.trim() || descInput.trim() != initialDescription.trim()
    val canSubmit = nameInput.isNotBlank() && isChanged

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = MelodiaSpacing.lg, vertical = MelodiaSpacing.md)
        ) {
            Text(
                text = "编辑歌单信息",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )

            // 歌单标题区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歌单名称",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Text(
                    text = "${nameInput.length}/$MAX_NAME_LENGTH",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
            PlaceholderTextField(
                value = nameInput,
                onValueChange = {
                    if (it.length <= MAX_NAME_LENGTH) {
                        nameInput = it
                    }
                },
                placeholder = "请输入歌单名称",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(MelodiaSpacing.md))

            // 歌单简介区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歌单简介",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Text(
                    text = "${descInput.length}/$MAX_DESC_LENGTH",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
            PlaceholderTextField(
                value = descInput,
                onValueChange = {
                    if (it.length <= MAX_DESC_LENGTH) {
                        descInput = it
                    }
                },
                placeholder = "添加歌单简介（选填）",
                modifier = Modifier.height(100.dp),
                singleLine = false,
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(MelodiaSpacing.lg))

            // 底部操作按钮区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MelodiaTextButton(onClick = onDismiss) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
                MelodiaButton(
                    onClick = {
                        val trimmedName = nameInput.trim()
                        if (trimmedName.isEmpty()) {
                            ToastManager.showToast("歌单名称不能为空")
                            return@MelodiaButton
                        }
                        if (!isChanged) {
                            onDismiss()
                            return@MelodiaButton
                        }
                        onConfirm(trimmedName, descInput.trim())
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
