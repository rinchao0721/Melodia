package com.lin0721.linmusic.feature.player.share

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.lyricLineKey
import com.lin0721.linmusic.core.share.LyricPick
import com.lin0721.linmusic.core.share.LyricShareSelection
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

private val ChunkShape = RoundedCornerShape(16.dp)
private val SelectedInk = Color(0xFF1A1A1A)

// 原文与翻译各自一块，可分别点选
@Composable
internal fun LyricSelectPage(
    lyrics: List<LyricLine>,
    pick: LyricPick,
    tint: Color?,
    onToggle: (index: Int, translation: Boolean) -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit
) {
    val surface = tint?.let { lerp(it, Color.Black, 0.35f) } ?: Color(0xFF1E2A30)
    val rows = remember(lyrics) { lyrics.withIndex().filter { LyricShareSelection.isSelectable(it.value) } }
    val listState = rememberLazyListState()
    LaunchedEffect(rows) {
        val first = pick.lineIndices.minOrNull() ?: return@LaunchedEffect
        val position = rows.indexOfFirst { it.index == first }
        if (position > 1) listState.scrollToItem(position - 1)
    }

    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(surface)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.sm, vertical = MelodiaSpacing.sm)) {
            MelodiaIconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "关闭", tint = Color.White)
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("选择歌词", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "已选择 ${pick.lineCount} 行歌词",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(rows, key = { lyricLineKey(it.index, it.value) }) { (index, line) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SelectChunk(line.text.trim(), index in pick.originals, 22.sp, FontWeight.ExtraBold) {
                        onToggle(index, false)
                    }
                    line.translation?.takeIf { it.isNotBlank() }?.let {
                        SelectChunk(it.trim(), index in pick.translations, 15.sp, FontWeight.SemiBold) {
                            onToggle(index, true)
                        }
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = MelodiaSpacing.md),
            contentAlignment = Alignment.Center
        ) {
            MelodiaButton(
                onClick = onContinue,
                enabled = !pick.isEmpty,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = SelectedInk,
                    disabledContainerColor = Color.White.copy(alpha = 0.3f),
                    disabledContentColor = SelectedInk.copy(alpha = 0.6f)
                ),
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp)
            ) {
                Text("继续", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SelectChunk(text: String, selected: Boolean, size: TextUnit, weight: FontWeight, onClick: () -> Unit) {
    val container by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "chunkBg")
    val ink by animateColorAsState(if (selected) SelectedInk else Color.White, label = "chunkInk")
    Text(
        text,
        color = ink,
        fontSize = size,
        lineHeight = size * 1.35f,
        fontWeight = weight,
        modifier = Modifier.fillMaxWidth()
            .pressable(MelodiaPress.Row, ChunkShape, onClick = onClick)
            .background(container, ChunkShape)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
