package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val RowMinHeight = 56.dp

@Composable
internal fun SettingRow(
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = DesktopColors.TextGray,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = DesktopColors.TextPrimary, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = subtitleColor, fontSize = 12.sp)
        }
        trailing()
    }
}

@Composable
internal fun SettingSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = DesktopColors.OnAccent,
            checkedTrackColor = DesktopColors.Accent,
            uncheckedThumbColor = DesktopColors.TextGray,
            uncheckedTrackColor = DesktopColors.SurfaceLight
        )
    )
}

@Composable
internal fun SettingsSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = DesktopColors.TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 24.dp, bottom = 4.dp)
    )
}

@Composable
internal fun SettingsGroupTitle(text: String) {
    Text(text, color = DesktopColors.TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
}

// 胶囊下拉：当前项带勾，选中后立即关闭菜单
@Composable
internal fun <T> SettingsDropdown(
    options: List<T>,
    current: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    menuWidth: Dp = 168.dp,
    leading: (@Composable (T) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(16.dp))
                .background(DesktopColors.Surface)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = true }
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading(current)
                Spacer(Modifier.width(8.dp))
            }
            Text(label(current), color = DesktopColors.TextPrimary, fontSize = 12.sp)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }, width = menuWidth) {
            options.forEach { option ->
                SimpleMenuItem(
                    text = label(option),
                    leading = leading?.let { { it(option) } },
                    trailing = if (option == current) {
                        { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
internal fun SettingsActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(32.dp).pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DesktopColors.SurfaceLight),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (danger) DesktopColors.Accent else DesktopColors.TextPrimary,
            disabledContentColor = DesktopColors.TextGray.copy(alpha = 0.5f)
        )
    ) {
        Text(text, fontSize = 12.sp)
    }
}
