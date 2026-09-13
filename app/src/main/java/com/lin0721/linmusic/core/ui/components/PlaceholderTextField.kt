package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

// 带占位符的输入框，支持单行与多行，替代各处手写的 BasicTextField + 条件 Text 组合
@Composable
fun PlaceholderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small,
    focusRequester: FocusRequester? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    borderColor: Color? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    placeholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val baseModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
        .background(containerColor)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            focusRequester?.requestFocus()
            keyboardController?.show()
        }

    val boxModifier = if (singleLine) {
        baseModifier
            .height(44.dp)
            .padding(horizontal = 14.dp)
    } else {
        baseModifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
    }

    val contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart

    Box(
        modifier = boxModifier,
        contentAlignment = contentAlignment
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = placeholderColor, fontSize = 14.sp)
        }
        val textFieldModifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = textColor, fontSize = 14.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = textFieldModifier,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines
        )
    }
}
