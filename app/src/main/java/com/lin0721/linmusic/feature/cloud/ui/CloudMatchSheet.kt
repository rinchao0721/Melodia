package com.lin0721.linmusic.feature.cloud.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.PlaceholderTextField
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.TextGray

private val ResultsMaxHeight = 360.dp

// 结果区随打字反复切换，用快速纯淡入淡出；不用位移/DataEnterSpec 那套生长动画——
// 那是给一次性数据可视化用的，套在高频重绘的搜索结果上会显得抖动
private val MatchContentFadeSpec = tween<Float>(160, easing = FastOutSlowInEasing)

private enum class MatchContentState { IDLE, SEARCHING, NO_RESULTS, RESULTS }

private fun CloudOverlay.Match.contentState(): MatchContentState = when {
    isSearching -> MatchContentState.SEARCHING
    query.isBlank() -> MatchContentState.IDLE
    results.isEmpty() -> MatchContentState.NO_RESULTS
    else -> MatchContentState.RESULTS
}

// 重新匹配搜索面板：复用现有 cloudSearch(type=1) 能力，不新写搜索接口
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudMatchSheet(
    overlay: CloudOverlay.Match,
    onQueryChange: (String) -> Unit,
    onSelect: (Track) -> Unit,
    onCancelConfirm: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundDark,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Text(
                text = "重新匹配「${overlay.song.name}」",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = MelodiaSpacing.sm)
            )
            PlaceholderTextField(
                value = overlay.query,
                onValueChange = onQueryChange,
                placeholder = "搜索正版曲目名或歌手",
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

            AnimatedContent(
                targetState = overlay.contentState(),
                transitionSpec = { fadeIn(MatchContentFadeSpec) togetherWith fadeOut(MatchContentFadeSpec) },
                label = "cloud_match_content"
            ) { state ->
                when (state) {
                    MatchContentState.IDLE -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(icon = Icons.Rounded.Search, title = "输入歌曲名或歌手开始搜索")
                        }
                    }

                    MatchContentState.SEARCHING -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }

                    MatchContentState.NO_RESULTS -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(icon = Icons.Rounded.LibraryMusic, title = "没有找到匹配的曲目")
                        }
                    }

                    MatchContentState.RESULTS -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = ResultsMaxHeight),
                            contentPadding = PaddingValues(bottom = MelodiaSpacing.sm)
                        ) {
                            items(overlay.results, key = { it.id }) { track ->
                                MatchCandidateRow(track = track, onClick = { onSelect(track) })
                            }
                        }
                    }
                }
            }
        }
    }

    val confirmTarget = overlay.confirmTarget
    if (confirmTarget != null) {
        AlertDialog(
            onDismissRequest = { if (!overlay.isMatching) onCancelConfirm() },
            title = { Text("确认匹配", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "确定要把「${overlay.song.name}」关联到「${confirmTarget.name}」吗？",
                    color = TextGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                MelodiaTextButton(
                    onClick = onConfirm,
                    enabled = !overlay.isMatching,
                    colors = ButtonDefaults.textButtonColors(contentColor = NeteaseRed)
                ) {
                    Text(if (overlay.isMatching) "匹配中..." else "是的", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                MelodiaTextButton(onClick = onCancelConfirm, enabled = !overlay.isMatching) {
                    Text("取消", color = Color.White)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(10.dp)
        )
    }
}

@Composable
private fun MatchCandidateRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = track.al.picUrl.ifEmpty { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() },
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(RadiusCompact))
        )
        Column(modifier = Modifier.padding(start = MelodiaSpacing.sm).weight(1f)) {
            Text(
                text = track.name,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.ar.joinToString(" / ") { it.name },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
