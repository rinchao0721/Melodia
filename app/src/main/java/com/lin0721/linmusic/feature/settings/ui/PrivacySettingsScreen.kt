package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

@Composable
fun PrivacySettingsView(viewModel: SettingsViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MelodiaSpacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "隐私设置开发中",
            color = MelodiaColors.TextSecondary,
            fontSize = 14.sp
        )
    }
}

