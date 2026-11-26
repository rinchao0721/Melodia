package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.localmusic.data.LocalPlaylistRepository as SharedLocalPlaylistRepository
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Stable
class LocalLikeTarget(val isLiked: Boolean, val toggle: () -> Unit)

// 播放项属于本地曲库时返回本地喜欢状态，否则返回 null 走在线逻辑
@Composable
fun rememberLocalLikeTarget(localUri: String?): LocalLikeTarget? {
    val playlistRepository: SharedLocalPlaylistRepository = koinInject()
    val libraryRepository: LocalLibraryRepository = koinInject()
    val likedLocations by playlistRepository.likedLocations.collectAsStateWithLifecycle(initialValue = emptySet())
    val libraryLocations by remember(libraryRepository) {
        libraryRepository.tracks.map { list -> list.mapTo(HashSet()) { it.uri.toString() } }
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    val location = localUri?.takeIf { it.isNotBlank() && it in libraryLocations } ?: return null
    return LocalLikeTarget(isLiked = location in likedLocations) {
        scope.launch {
            runCatching { playlistRepository.toggleLiked(location) }
                .onSuccess { liked -> ToastManager.showToast(if (liked) "已添加到我喜欢的音乐" else "已从我喜欢的音乐中移除") }
                .onFailure {
                    AppLogger.e("LocalLikeTarget", "切换本地喜欢失败", it)
                    ToastManager.showToast("操作失败")
                }
        }
    }
}
