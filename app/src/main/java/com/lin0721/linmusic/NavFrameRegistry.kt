package com.lin0721.linmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.lin0721.linmusic.core.navigation.FrameRetention
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// 最多保留现场的非根栈帧数
private const val MAX_RETAINED_FRAMES = 8

// 挂在 Activity 上，旋转屏幕后栈帧的 ViewModel 仍然保留
class NavFrameStores : ViewModel() {
    private val stores = HashMap<Long, ViewModelStore>()

    fun storeFor(id: Long): ViewModelStore = stores.getOrPut(id) { ViewModelStore() }

    fun ids(): Set<Long> = stores.keys.toSet()

    fun clear(id: Long) {
        stores.remove(id)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

// 这些页面的 ViewModel 与具体 id 绑定，每个栈帧各用一份，返回时数据不被同类页面覆盖
private fun Screen.hasPageScopedViewModels(): Boolean = when (this) {
    is Screen.Playlist, is Screen.Artist, is Screen.Style, is Screen.Radio,
    is Screen.Profile, is Screen.FollowList, is Screen.PlaylistCategory,
    is Screen.PodcastSubscribed, is Screen.PodcastToplist, is Screen.PodcastCategory,
    is Screen.PrivateChat -> true
    else -> false
}

private fun Screen.isTabRoot(): Boolean = when (this) {
    Screen.Home, Screen.Search, Screen.Library,
    Screen.LocalMusic, Screen.Downloads, Screen.Cloud, Screen.MyProfile -> true
    else -> false
}

// 按栈帧保存页面现场：滚动位置等 rememberSaveable 状态，以及页面专属 ViewModel
@Stable
class NavFrameRegistry(
    private val saveableStateHolder: SaveableStateHolder,
    private val frameStores: NavFrameStores,
    private val liveEntryIds: () -> Set<Long>,
    private val scope: CoroutineScope
) {
    private val retention = FrameRetention(MAX_RETAINED_FRAMES)

    @Composable
    fun Provide(entry: NavEntry, content: @Composable () -> Unit) {
        DisposableEffect(entry.id) {
            retention.enter(entry.id, evictable = !entry.screen.isTabRoot())
            onDispose {
                retention.leave(entry.id)
                // 等本次组合释放完成再清理，避免抢在 SaveableStateProvider 离开保存之前
                scope.launch { reconcile() }
            }
        }
        val owner = if (entry.screen.hasPageScopedViewModels()) {
            remember(entry.id) { storeOwnerFor(entry.id) }
        } else {
            null
        }
        saveableStateHolder.SaveableStateProvider(entry.id) {
            if (owner == null) {
                content()
            } else {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
            }
        }
    }

    // 释放已出栈、或超出保留上限的栈帧
    fun reconcile() {
        val live = liveEntryIds()
        retention.release(live).forEach { release(it) }
        frameStores.ids()
            .filter { it !in live && !retention.isComposed(it) }
            .forEach { release(it) }
    }

    private fun release(id: Long) {
        saveableStateHolder.removeState(id)
        frameStores.clear(id)
    }

    private fun storeOwnerFor(id: Long): ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = frameStores.storeFor(id)
    }
}
