package com.lin0721.linmusic.feature.recognition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.SurfaceLight
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.TimerWarningRed
import com.lin0721.linmusic.feature.recognition.domain.RecognitionMode

private data class ModeOption(
    val mode: RecognitionMode,
    val label: String,
    val description: String,
    val icon: ImageVector
)

private val ModeOptions = listOf(
    ModeOption(RecognitionMode.MICROPHONE, "麦克风", "识别身边外放的声音", Icons.Rounded.Mic),
    ModeOption(RecognitionMode.PLAYBACK, "内录", "识别手机里其他 App 正在放的歌", Icons.Rounded.PhoneAndroid)
)

// 聆听中顶部的方式切换，切换即按新方式重新开始
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecognitionModeSwitch(
    selected: RecognitionMode,
    onSelect: (RecognitionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ModeOptions.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.mode == selected,
                onClick = { if (option.mode != selected) onSelect(option.mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ModeOptions.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Color.White,
                    activeContentColor = BackgroundDark,
                    activeBorderColor = SurfaceDark,
                    inactiveContainerColor = SurfaceDark,
                    inactiveContentColor = TextGray,
                    inactiveBorderColor = SurfaceDark
                ),
                icon = { Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
            ) {
                Text(text = option.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// 进入识别页时的方式选择，点选即开始；不选直接关闭时由调用方一并关闭识别页
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecognitionModeChooserSheet(
    lastMode: RecognitionMode?,
    onChoose: (RecognitionMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceDark,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = MelodiaSpacing.md, end = MelodiaSpacing.md, bottom = MelodiaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "选择识别方式", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = "点选后立即开始，识别中也可以在顶部切换", color = TextGray, fontSize = 13.sp)
            Spacer(Modifier.height(MelodiaSpacing.xs))
            ModeOptions.forEach { option ->
                ModeOptionCard(option = option, isLastUsed = option.mode == lastMode, onClick = { onChoose(option.mode) })
            }
            Text(
                text = "内录需要授权屏幕录制；部分 App 禁止被内录，会录到静音",
                color = TextGray,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ModeOptionCard(option: ModeOption, isLastUsed: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(InfoCardRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .pressable(style = MelodiaPress.Card, shape = shape, role = Role.Button, onClick = onClick)
            .background(SurfaceLight, shape)
            .padding(horizontal = MelodiaSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(NeteaseRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(option.icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = option.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (isLastUsed) {
                    Spacer(Modifier.width(MelodiaSpacing.sm))
                    Surface(shape = RoundedCornerShape(10.dp), color = NeteaseRed.copy(alpha = 0.2f)) {
                        Text(
                            text = "上次使用",
                            color = TimerWarningRed,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = MelodiaSpacing.sm, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(text = option.description, color = TextGray, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextGray)
    }
}
