package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.core.ui.theme.styledButtonShape
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.DangerColor

private val DialogShape = RoundedCornerShape(8.dp)
private val FieldShape = RoundedCornerShape(4.dp)
private val ButtonShape = RoundedCornerShape(16.dp)

// 统一的紧凑弹窗：20dp 内边距、18sp 标题、右下角动作按钮
@Composable
fun DesktopDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 400.dp,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    // 平台默认宽度上限约 560dp，宽弹窗需关掉才能按 width 生效
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier.width(width).clip(DialogShape).background(DesktopColors.PopupSurface).floatingOutline(DialogShape).padding(20.dp)) {
            Text(title, color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

// primary 为主文字色底、反色字的主按钮，其余为灰色文字按钮；danger 用红色文字
@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val active = enabled && !loading
    Box(
        Modifier.clip(styledButtonShape(ButtonShape))
            .background(if (primary) DesktopColors.TextPrimary.copy(alpha = if (active) 1f else 0.4f) else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(16.dp),
                color = if (primary) DesktopColors.InverseText else DesktopColors.TextPrimary,
                strokeWidth = 2.dp
            )
        } else {
            val color = when {
                primary -> DesktopColors.InverseText
                danger -> DangerColor
                else -> if (enabled) DesktopColors.TextGray else DesktopColors.TextGray.copy(alpha = 0.5f)
            }
            Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DialogLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = DesktopColors.TextGray, fontSize = 12.sp, modifier = modifier.padding(bottom = 4.dp))
}

// 紧凑输入框：单行约 36dp，聚焦时描白边
@Composable
fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onEnter: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = TextStyle(color = DesktopColors.TextPrimary, fontSize = 14.sp),
        cursorBrush = SolidColor(DesktopColors.TextPrimary),
        modifier = modifier.fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (onEnter != null && event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    onEnter()
                    true
                } else {
                    false
                }
            },
        decorationBox = { inner ->
            Box(
                Modifier.clip(FieldShape).background(DesktopColors.Surface)
                    .border(1.dp, if (focused) DesktopColors.TextPrimary else Color.Transparent, FieldShape)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = DesktopColors.TextGray, fontSize = 14.sp)
                }
                inner()
            }
        }
    )
}
