package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lin0721.linmusic.core.ui.interaction.pressScale
import com.lin0721.linmusic.core.ui.theme.MelodiaPress

// M3 Switch 的替代品：统一收口开关配色，反馈换成按压缩放
@Composable
fun MelodiaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
    )
) {
    val interactionSource = remember { MutableInteractionSource() }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.pressScale(MelodiaPress.Action, interactionSource),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource
    )
}
