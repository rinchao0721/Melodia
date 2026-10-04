package com.lin0721.linmusic.core.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.lin0721.linmusic.core.ui.theme.ToastBackground
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius

object ToastManager {
    private val _toastFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val toastFlow = _toastFlow.asSharedFlow()

    fun showToast(message: String) {
        _toastFlow.tryEmit(message)
    }
}

@Composable
fun CustomToast(message: String) {
    Row(
        modifier = Modifier
            .wrapContentWidth()
            .widthIn(max = 400.dp)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(PillRadius))
            .background(ToastBackground)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(PillRadius))
            .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.matchParentSize()
            )
            Image(
                painter = painterResource(id = R.drawable.ic_notification),
                contentDescription = null,
                modifier = Modifier.matchParentSize()
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
