package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Delete

// SwipeToDismissBox 左滑删除的统一底层：主色底 + 右侧垃圾桶
// 皮肤带背景图时行底色透明，未滑动时不画，否则整片透出
@Composable
fun SwipeDeleteBackground(state: SwipeToDismissBoxState) {
    if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MelodiaColors.Accent)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(MelodiaIcons.Delete, contentDescription = null, tint = MelodiaColors.OnAccent)
    }
}
