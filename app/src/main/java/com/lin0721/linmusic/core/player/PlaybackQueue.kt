package com.lin0721.linmusic.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 播放队列的内存模型：维护原始顺序与实际播放顺序、当前下标、播放模式与播放来源
class PlaybackQueue {

    // 用户选中时的原始顺序，随机播放时作为还原基准
    private var originalItems: List<QueueItem> = emptyList()

    // 实际的播放顺序
    private var playItems: List<QueueItem> = emptyList()

    // 进入漫游前的队列快照
    private var snapshotItems: List<QueueItem> = emptyList()
    private var snapshotIndex: Int = -1

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.LIST_LOOP)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _playContext = MutableStateFlow<String?>(null)
    val playContext: StateFlow<String?> = _playContext.asStateFlow()

    val size: Int get() = playItems.size

    val isEmpty: Boolean get() = playItems.isEmpty()

    val original: List<QueueItem> get() = originalItems

    fun itemAt(index: Int): QueueItem? = playItems.getOrNull(index)

    fun currentItem(): QueueItem? = playItems.getOrNull(_currentIndex.value)

    // 当前曲目在原始（未打乱）顺序中的下标，供持久化使用：随机模式下 currentIndex 是打乱后 playItems 里的下标，
    // 跟 original 的顺序对不上，必须转换成 original 里的下标才能在下次恢复时精确定位回同一首歌
    fun currentIndexInOriginal(): Int {
        val current = currentItem() ?: return _currentIndex.value
        val idx = originalItems.indexOfFirst { it.songId == current.songId }
        return if (idx >= 0) idx else _currentIndex.value
    }

    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
    }

    fun setPlayContext(context: String?) {
        _playContext.value = context
    }

    fun setCurrentIndex(index: Int) {
        _currentIndex.value = index
    }

    fun nextIndex(): Int = nextIndexFrom(_currentIndex.value)

    fun nextIndexFrom(index: Int): Int = if (playItems.isEmpty()) -1 else (index + 1) % playItems.size

    fun previousIndex(): Int {
        if (playItems.isEmpty()) return -1
        val cur = _currentIndex.value
        return if (cur - 1 < 0) playItems.size - 1 else cur - 1
    }

    // 按当前播放模式推算下一首曲目，队列不足两首时返回 null
    fun nextItemByMode(): QueueItem? {
        val total = playItems.size
        if (total <= 1) return null
        val nextIdx = when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> _currentIndex.value
            else -> (_currentIndex.value + 1) % total
        }
        return playItems.getOrNull(nextIdx)
    }

    // 从持久化数据恢复队列；index 是当前曲目在 items（原始顺序）里的下标。
    // 随机模式下要按当前 playMode 重新打乱，否则 playItems 会是未打乱的原始顺序，跟随机模式的语义不符
    fun restore(items: List<QueueItem>, index: Int, context: String?) {
        if (items.isEmpty()) return
        originalItems = items
        val safeIndex = index.coerceIn(0, items.size - 1)
        if (_playMode.value == PlayMode.SHUFFLE) {
            playItems = shufflePreservingCurrent(items, safeIndex)
            _currentIndex.value = 0
        } else {
            playItems = items
            _currentIndex.value = safeIndex
        }
        _items.value = playItems
        _playContext.value = context
    }

    // 整体替换队列，随机模式下打乱并把起始曲目提到首位
    fun replaceAll(items: List<QueueItem>, startIndex: Int) {
        if (items.isEmpty()) return
        originalItems = items
        if (_playMode.value == PlayMode.SHUFFLE) {
            playItems = shufflePreservingCurrent(items, startIndex)
            _currentIndex.value = 0
        } else {
            playItems = items
            _currentIndex.value = startIndex.coerceIn(0, items.size - 1)
        }
        _items.value = playItems
    }

    // 队列收缩为单曲
    fun replaceWithSingle(item: QueueItem) {
        originalItems = listOf(item)
        playItems = listOf(item)
        _currentIndex.value = 0
        _items.value = playItems
    }

    // 插播到“下一首”位置
    fun insertNext(items: List<QueueItem>) {
        if (items.isEmpty()) return
        val curIndex = _currentIndex.value
        val targetInsertIndex = curIndex + 1

        // 插入到当前的活动播放队列中
        val mutablePlay = playItems.toMutableList()
        mutablePlay.addAll(targetInsertIndex.coerceIn(0, mutablePlay.size), items)
        playItems = mutablePlay

        // 同步插入到原始队列中以支持防丢
        val currentItem = playItems.getOrNull(curIndex)
        val mutableOrig = originalItems.toMutableList()
        if (currentItem != null) {
            val origIdx = mutableOrig.indexOfFirst { it.songId == currentItem.songId }
            if (origIdx >= 0) {
                mutableOrig.addAll(origIdx + 1, items)
            } else {
                mutableOrig.addAll(items)
            }
        } else {
            mutableOrig.addAll(items)
        }
        originalItems = mutableOrig

        _items.value = playItems
    }

    // 移除指定项，返回需要重新起播的下标，无需重播时返回 -1
    fun removeAt(index: Int): Int {
        if (index < 0 || index >= playItems.size || playItems.size <= 1) return -1
        val removedItem = playItems[index]
        val mutablePlay = playItems.toMutableList()
        mutablePlay.removeAt(index)
        playItems = mutablePlay

        val mutableOrig = originalItems.toMutableList()
        val origIdx = mutableOrig.indexOfFirst { it.songId == removedItem.songId }
        if (origIdx >= 0) mutableOrig.removeAt(origIdx)
        originalItems = mutableOrig

        var replayIndex = -1
        when {
            index < _currentIndex.value -> _currentIndex.value -= 1
            index == _currentIndex.value -> {
                val newIdx = _currentIndex.value.coerceAtMost(playItems.size - 1)
                _currentIndex.value = newIdx
                replayIndex = newIdx
            }
        }
        _items.value = playItems
        return replayIndex
    }

    // 拖动排序，返回是否真的发生了变化
    fun move(from: Int, to: Int): Boolean {
        if (from == to) return false
        if (from < 0 || from >= playItems.size || to < 0 || to >= playItems.size) return false
        val mutable = playItems.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        playItems = mutable
        originalItems = mutable.toList()

        val cur = _currentIndex.value
        _currentIndex.value = when (cur) {
            from -> to
            in (minOf(from, to)..maxOf(from, to)) ->
                if (from < to) cur - 1 else cur + 1
            else -> cur
        }
        _items.value = playItems
        return true
    }

    // 切换播放模式并按新模式重排实际播放顺序
    fun applyMode(newMode: PlayMode, currentItem: QueueItem?) {
        _playMode.value = newMode
        if (currentItem == null || originalItems.isEmpty()) return
        when (newMode) {
            PlayMode.SHUFFLE -> {
                val origIdx = originalItems.indexOfFirst { it.songId == currentItem.songId }.coerceAtLeast(0)
                playItems = shufflePreservingCurrent(originalItems, origIdx)
                _currentIndex.value = 0
            }
            PlayMode.LIST_LOOP -> {
                playItems = originalItems
                _currentIndex.value = originalItems.indexOfFirst { it.songId == currentItem.songId }.coerceAtLeast(0)
            }
            PlayMode.SINGLE_LOOP -> return
        }
        _items.value = playItems
    }

    // 只保留当前播放项，返回被保留的项；当前无播放项时清空队列并返回 null
    fun keepOnlyCurrent(): QueueItem? {
        val curIndex = _currentIndex.value
        val currentTrackItem = if (curIndex in playItems.indices) playItems[curIndex] else null
        if (currentTrackItem != null) {
            originalItems = listOf(currentTrackItem)
            playItems = listOf(currentTrackItem)
            _currentIndex.value = 0
            _items.value = playItems
        } else {
            originalItems = emptyList()
            playItems = emptyList()
            _currentIndex.value = -1
            _items.value = emptyList()
        }
        return currentTrackItem
    }

    // 截断到指定下标后追加新曲目，用于漫游续播
    fun appendAfter(index: Int, items: List<QueueItem>) {
        if (items.isEmpty()) return
        val newQueue = originalItems.take(index + 1) + items
        originalItems = newQueue
        playItems = newQueue
        _items.value = playItems
    }

    // 备份当前队列，供退出漫游时还原
    fun takeSnapshot() {
        snapshotItems = originalItems
        snapshotIndex = _currentIndex.value
    }

    // 还原备份的队列，并把当前正在播放的曲目定位到新队列中
    fun restoreSnapshot() {
        if (snapshotItems.isEmpty()) return
        originalItems = snapshotItems
        val currentTrackItem = playItems.getOrNull(_currentIndex.value)
        val newIndex = if (currentTrackItem != null) {
            val idx = snapshotItems.indexOfFirst { it.songId == currentTrackItem.songId }
            if (idx != -1) idx else snapshotIndex.coerceIn(0, snapshotItems.size - 1)
        } else {
            snapshotIndex.coerceIn(0, snapshotItems.size - 1)
        }

        if (_playMode.value == PlayMode.SHUFFLE) {
            playItems = shufflePreservingCurrent(snapshotItems, newIndex)
            _currentIndex.value = 0
        } else {
            playItems = snapshotItems
            _currentIndex.value = newIndex
        }

        _items.value = playItems
        snapshotItems = emptyList()
        snapshotIndex = -1
    }

    // 打乱其余曲目并把当前曲目固定在首位，避免切到随机模式时当前歌曲被换掉
    private fun shufflePreservingCurrent(items: List<QueueItem>, currentIndex: Int): List<QueueItem> {
        if (items.size <= 1) return items
        val mutable = items.toMutableList()
        val safeIdx = currentIndex.coerceIn(0, mutable.size - 1)
        val current = mutable.removeAt(safeIdx)
        for (i in mutable.size - 1 downTo 1) {
            val j = (0..i).random()
            mutable[i] = mutable[j].also { mutable[j] = mutable[i] }
        }
        mutable.add(0, current)
        return mutable
    }
}
