package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.ui.components.AuroraCardBackground
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.toAuroraPalette
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.launch
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Check

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalFmModeSheet(
    currentMode: PersonalFmMode,
    onSelectMode: (PersonalFmMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val recommendModes = remember { PersonalFmMode.entries.filter { !it.isScene } }
    val sceneModes = remember { PersonalFmMode.entries.filter { it.isScene } }

    fun handleSelect(mode: PersonalFmMode) {
        onSelectMode(mode)
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MelodiaSpacing.md)
                .padding(bottom = MelodiaSpacing.lg)
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(
                    text = "私人FM 模式",
                    color = MelodiaColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "切换后从下一首开始生效",
                    color = MelodiaColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            ModeGroupTitle("推荐方式")
            ModeAuroraRow(
                modes = recommendModes,
                currentMode = currentMode,
                onSelect = ::handleSelect
            )

            Spacer(modifier = Modifier.height(10.dp))

            ModeGroupTitle("场景")
            ModeAuroraRow(
                modes = sceneModes,
                currentMode = currentMode,
                onSelect = ::handleSelect
            )
        }
    }
}

@Composable
private fun ModeGroupTitle(text: String) {
    Text(
        text = text,
        color = MelodiaColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun ModeAuroraRow(
    modes: List<PersonalFmMode>,
    currentMode: PersonalFmMode,
    onSelect: (PersonalFmMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        modes.forEach { mode ->
            ModeAuroraCard(
                mode = mode,
                selected = mode == currentMode,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeAuroraCard(
    mode: PersonalFmMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardRadius = InfoCardRadius
    val outerShape = RoundedCornerShape(cardRadius + 4.dp)
    val innerShape = RoundedCornerShape(cardRadius)

    Box(
        modifier = modifier
            .then(
                if (selected) Modifier.border(2.5.dp, MelodiaColors.Accent, outerShape)
                else Modifier
            )
            .padding(5.dp)
            .pressable(MelodiaPress.Card) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(innerShape)
        ) {
            AuroraCardBackground(
                palette = mode.toAuroraPalette(),
                modifier = Modifier.matchParentSize()
            )

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MelodiaIcons.Check,
                        contentDescription = "已选择",
                        tint = MelodiaColors.Accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Text(
                    text = mode.displayName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mode.description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
