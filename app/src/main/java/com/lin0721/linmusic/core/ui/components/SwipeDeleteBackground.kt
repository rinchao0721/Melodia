package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.NeteaseRed

// SwipeToDismissBox 左滑删除的统一底层：红底 + 右侧垃圾桶
@Composable
fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeteaseRed)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
    }
}
