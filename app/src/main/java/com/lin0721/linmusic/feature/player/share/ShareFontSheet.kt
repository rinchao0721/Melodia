package com.lin0721.linmusic.feature.player.share

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.share.ui.ShareCardTypography
import com.lin0721.linmusic.core.share.ui.ShareCardTypographyOptions
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 歌词字体：字形、字号倍率、字重、对齐，改动即时反映在预览上
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareFontSheet(
    typography: ShareCardTypography,
    onChange: (ShareCardTypography) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = MelodiaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
        ) {
            Text(
                "歌词字体",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = MelodiaSpacing.lg)
            )
            OptionRow("字体") {
                ShareCardTypographyOptions.presetFonts.forEach { option ->
                    OptionChip(option.label, null, option.family == typography.family) {
                        onChange(typography.copy(family = option.family))
                    }
                }
            }
            OptionRow("字号") {
                ShareCardTypographyOptions.scales.forEach { scale ->
                    OptionChip(ShareCardTypographyOptions.scaleLabel(scale), null, scale == typography.scale) {
                        onChange(typography.copy(scale = scale))
                    }
                }
            }
            OptionRow("字重") {
                ShareCardTypographyOptions.weights.forEach { (label, weight) ->
                    OptionChip(label, null, weight == typography.weight) { onChange(typography.copy(weight = weight)) }
                }
            }
            OptionRow("对齐") {
                ShareCardTypographyOptions.aligns.forEach { (label, align) ->
                    OptionChip(label, null, align == typography.align) { onChange(typography.copy(align = align)) }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = MelodiaSpacing.lg)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = MelodiaSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}
