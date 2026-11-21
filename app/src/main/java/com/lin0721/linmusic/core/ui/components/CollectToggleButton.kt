package com.lin0721.linmusic.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.icons.AddCircle
import com.lin0721.linmusic.core.ui.icons.CheckCircleFilled
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.theme.uiTween
import org.koin.compose.koinInject

private const val COLLECT_TOGGLE_DURATION_MS = 280
private const val ADD_ICON_ROTATION = 90f

// ⊕/✓ 交叉过渡：⊕ 旋转淡出、✓ 淡入，取消时反向
@Composable
fun CollectToggleIcon(
    collected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    collectedTint: Color = MaterialTheme.colorScheme.primary
) {
    val progress by animateFloatAsState(
        targetValue = if (collected) 1f else 0f,
        animationSpec = uiTween(COLLECT_TOGGLE_DURATION_MS),
        label = "collectToggle"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = MelodiaIcons.AddCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = ADD_ICON_ROTATION * progress
                    alpha = 1f - progress
                }
        )
        Icon(
            imageVector = MelodiaIcons.CheckCircleFilled,
            contentDescription = null,
            tint = collectedTint,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 0.8f + 0.2f * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = progress
                }
        )
    }
}

@Composable
fun CollectToggleButton(
    collected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    collectedTint: Color = MaterialTheme.colorScheme.primary,
    onLongClick: (() -> Unit)? = null
) {
    MelodiaIconButton(onClick = onClick, modifier = modifier, onLongClick = onLongClick) {
        CollectToggleIcon(
            collected = collected,
            size = iconSize,
            tint = tint,
            collectedTint = collectedTint
        )
    }
}

// 设置项“常驻显示已喜欢标记”，关掉后列表行不再显示 ⊕/✓
@Composable
fun rememberShowLikedBadge(): Boolean {
    val settingsPreferences: SettingsPreferences = koinInject()
    val flow = remember(settingsPreferences) { settingsPreferences.alwaysShowLikedBadge }
    val show by flow.collectAsState(initial = true)
    return show
}
