package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.preferences.ArtistAlbumFilterSettings

/** Both settings and artist-specific editors use the same validated input. */
@Composable
fun ArtistAlbumMinimumDialog(
    currentMinimum: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    description: String = "设为歌手专辑的默认最少曲数。0 首显示全部，已单独设置的歌手不受影响。",
    onUseDefault: (() -> Unit)? = null,
    defaultMinimum: Int = ArtistAlbumFilterSettings.DEFAULT_MIN_TRACKS
) {
    var input by remember(currentMinimum) { mutableStateOf(currentMinimum.toString()) }
    val minimum = input.toIntOrNull()?.takeIf {
        it in ArtistAlbumFilterSettings.MIN_TRACKS..ArtistAlbumFilterSettings.MAX_TRACKS
    }
    fun save() {
        minimum?.let {
            onSave(it)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("专辑最少曲数") },
        text = {
            Column {
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(3) },
                    label = { Text("最少曲数") },
                    suffix = { Text("首") },
                    singleLine = true,
                    isError = minimum == null,
                    supportingText = { Text("0 不限制曲数；可设置 ${ArtistAlbumFilterSettings.MIN_TRACKS}–${ArtistAlbumFilterSettings.MAX_TRACKS} 首") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() })
                )
                if (onUseDefault != null) {
                    MelodiaTextButton(onClick = {
                        onUseDefault()
                        onDismiss()
                    }) { Text("使用默认值（$defaultMinimum 首）") }
                }
            }
        },
        confirmButton = {
            MelodiaTextButton(onClick = ::save, enabled = minimum != null) { Text("保存") }
        },
        dismissButton = {
            MelodiaTextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
