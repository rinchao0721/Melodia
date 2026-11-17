package com.lin0721.linmusic.desktop.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lin0721.linmusic.core.navigation.FrameRetention
import com.lin0721.linmusic.core.ui.components.CollectPromptEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

// 最多保留现场的非根栈帧数
private const val MAX_RETAINED_FRAMES = 8

// 按栈帧保存页面现场：滚动位置等 rememberSaveable 状态，以及页面专属 ViewModel
@Stable
class DesktopFrameHost(
    private val saveableStateHolder: SaveableStateHolder,
    private val liveEntryIds: () -> Set<Long>,
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit,
    private val onCollectPrompt: (CollectPromptEvent) -> Unit = {}
) {
    private val retention = FrameRetention(MAX_RETAINED_FRAMES)
    private val stores = HashMap<Long, ViewModelStore>()
    private val toastJobs = HashMap<Long, MutableList<Job>>()

    @Composable
    fun Provide(entry: DesktopEntry, content: @Composable () -> Unit) {
        DisposableEffect(entry.id) {
            retention.enter(entry.id, evictable = entry.route != DesktopRoute.Home)
            onDispose {
                retention.leave(entry.id)
                // 等本次组合释放完成再清理，避免抢在 SaveableStateProvider 离开保存之前
                scope.launch { reconcile() }
            }
        }
        saveableStateHolder.SaveableStateProvider(entry.id, content)
    }

    // 取该栈帧自己的 ViewModel；toast 与收藏提示在栈帧存活期间持续转发，页面离开组合后异步结果不丢
    fun <T : ViewModel> viewModelFor(
        entryId: Long,
        modelClass: KClass<T>,
        toasts: (T) -> Flow<String>,
        collectPrompts: (T) -> Flow<CollectPromptEvent> = { emptyFlow() },
        create: () -> T
    ): T {
        val store = stores.getOrPut(entryId) { ViewModelStore() }
        val factory = viewModelFactory {
            addInitializer(modelClass) {
                create().also { viewModel ->
                    val jobs = toastJobs.getOrPut(entryId) { mutableListOf() }
                    jobs += scope.launch { toasts(viewModel).collect(onToast) }
                    jobs += scope.launch { collectPrompts(viewModel).collect(onCollectPrompt) }
                }
            }
        }
        return ViewModelProvider.create(store, factory)[modelClass]
    }

    // 释放已出栈、或超出保留上限的栈帧
    fun reconcile() {
        retention.release(liveEntryIds()).forEach { id ->
            saveableStateHolder.removeState(id)
            toastJobs.remove(id)?.forEach { it.cancel() }
            stores.remove(id)?.clear()
        }
    }
}
