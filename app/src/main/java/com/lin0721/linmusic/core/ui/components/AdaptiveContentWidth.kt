package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass

// Expanded 断点下纯文字/列表类内容居中限宽，避免一行文字拉到平板整屏宽度视线来回扫；
// Compact 下等价于 fillMaxWidth()，零视觉变化
private val DefaultMaxContentWidth = 680.dp

@Composable
fun AdaptiveContentWidth(
    modifier: Modifier = Modifier,
    maxWidth: Dp = DefaultMaxContentWidth,
    content: @Composable BoxScope.() -> Unit
) {
    val windowSizeClass = LocalMelodiaWindowSizeClass.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = if (windowSizeClass == MelodiaWindowSizeClass.Expanded) {
                Modifier.widthIn(max = maxWidth).fillMaxHeight()
            } else {
                Modifier.fillMaxWidth().fillMaxHeight()
            },
            content = content
        )
    }
}
