package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.styledCardRadius
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private const val ENTER_MS = 250

// 正在播放面板里各信息卡的统一外壳：圆角底 + 标题 + 内容；backdrop 画在内容之下，用于带着色背景的卡片
@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(styledCardRadius(8.dp))).background(DesktopColors.CardSurface)) {
        backdrop?.invoke(this)
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = DesktopColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                headerTrailing?.invoke(this)
            }
            content()
        }
    }
}

// 卡片出现时淡入并把下方内容平滑让位，避免整块突然顶开
@Composable
fun InfoCardEnter(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = fadeIn(uiTween(ENTER_MS)) + expandVertically(uiTween(ENTER_MS))
    ) { content() }
}
