package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Close

private val CloseButtonSize = 32.dp

// 右侧栏覆盖面板的统一头部：标题、可选的右侧附加内容、始终可见的关闭按钮
@Composable
fun OverlayPanelHeader(
    title: String,
    closeDescription: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = DesktopColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        trailing()
        IconButton(onClick = onClose, modifier = Modifier.size(CloseButtonSize)) {
            Icon(MelodiaIcons.Close, closeDescription, tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
        }
    }
}
