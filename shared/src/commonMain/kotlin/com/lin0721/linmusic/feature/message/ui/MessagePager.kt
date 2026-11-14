package com.lin0721.linmusic.feature.message.ui

import com.lin0721.linmusic.feature.message.domain.MessagePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface MessageListState<out T> {
    // 尚未请求过，切到对应 Tab 时才触发首次加载
    data object Idle : MessageListState<Nothing>

    data object Loading : MessageListState<Nothing>

    data class Success<T>(
        val items: List<T>,
        val hasMore: Boolean,
        val cursor: Long,
        val isLoadingMore: Boolean = false
    ) : MessageListState<T>

    data class Error(val message: String) : MessageListState<Nothing>
}

// 单个 Tab 的游标分页状态机；scope 由 ViewModel 传入，跨平台共用
class MessagePager<T>(
    private val scope: CoroutineScope,
    private val initialCursor: Long,
    private val fetch: (cursor: Long) -> Flow<Result<MessagePage<T>>>,
    private val keyOf: (T) -> Any,
    private val errorMessage: (Throwable) -> String,
    private val onToast: suspend (String) -> Unit,
    private val onPageLoaded: (List<T>) -> Unit = {}
) {
    private val _state = MutableStateFlow<MessageListState<T>>(MessageListState.Idle)
    val state: StateFlow<MessageListState<T>> = _state.asStateFlow()

    private var job: Job? = null

    fun loadIfIdle() {
        if (_state.value is MessageListState.Idle) refresh()
    }

    fun refresh() {
        job?.cancel()
        _state.value = MessageListState.Loading
        job = scope.launch {
            fetch(initialCursor).first()
                .onSuccess { page ->
                    _state.value = MessageListState.Success(
                        items = page.items.distinctBy(keyOf),
                        hasMore = page.hasMore,
                        cursor = page.nextCursor
                    )
                    onPageLoaded(page.items)
                }
                .onFailure { _state.value = MessageListState.Error(errorMessage(it)) }
        }
    }

    // 保留当前列表重拉首页，用于会话摘要/未读变化时无感更新；未加载过或正在加载时不动
    fun refreshSilently() {
        if (_state.value !is MessageListState.Success) return
        job?.cancel()
        job = scope.launch {
            fetch(initialCursor).first()
                .onSuccess { page ->
                    _state.value = MessageListState.Success(
                        items = page.items.distinctBy(keyOf),
                        hasMore = page.hasMore,
                        cursor = page.nextCursor
                    )
                    onPageLoaded(page.items)
                }
                .onFailure {
                    val latest = _state.value as? MessageListState.Success ?: return@onFailure
                    _state.value = latest.copy(isLoadingMore = false)
                }
        }
    }

    fun loadMore() {
        val current = _state.value as? MessageListState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return
        _state.value = current.copy(isLoadingMore = true)
        job = scope.launch {
            fetch(current.cursor).first()
                .onSuccess { page ->
                    val latest = _state.value as? MessageListState.Success ?: return@onSuccess
                    val knownKeys = latest.items.mapTo(HashSet(), keyOf)
                    _state.value = latest.copy(
                        items = latest.items + page.items.filter { keyOf(it) !in knownKeys }.distinctBy(keyOf),
                        hasMore = page.hasMore,
                        cursor = page.nextCursor,
                        isLoadingMore = false
                    )
                    onPageLoaded(page.items)
                }
                .onFailure {
                    onToast(errorMessage(it))
                    val latest = _state.value as? MessageListState.Success ?: return@onFailure
                    _state.value = latest.copy(isLoadingMore = false)
                }
        }
    }
}
