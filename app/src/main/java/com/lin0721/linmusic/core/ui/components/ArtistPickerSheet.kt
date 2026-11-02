package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.launch

data class ArtistPickerEntry(
    val id: Long,
    val name: String,
    val avatarUrl: String? = null
)

// 多歌手合唱时的歌手选择面板：点名字/“歌手”入口不再只能进第一位歌手主页
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPickerSheet(
    artists: List<ArtistPickerEntry>,
    onArtistClick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Text(
                text = "选择歌手",
                color = MelodiaColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            )
            artists.forEach { artist ->
                EntityRow(
                    data = EntityRowData(
                        id = artist.id,
                        title = artist.name,
                        coverUrl = artist.avatarUrl,
                        coverShape = EntityCoverShape.Circle
                    ),
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismiss()
                            onArtistClick(artist.id)
                        }
                    }
                )
            }
        }
    }
}
