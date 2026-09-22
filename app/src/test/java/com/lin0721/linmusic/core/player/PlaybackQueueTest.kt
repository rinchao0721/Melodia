package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {

    private fun item(id: Long) = QueueItem(songId = id, title = "歌曲$id", artist = "歌手$id", coverUrl = "cover/$id")

    private fun queueOf(vararg ids: Long, startIndex: Int = 0): PlaybackQueue =
        PlaybackQueue().apply { replaceAll(ids.map { item(it) }, startIndex) }

    private fun PlaybackQueue.ids(): List<Long> = items.value.map { it.songId }

    // ======================= 下标推算 =======================

    @Test
    fun `空队列的前后下标均为-1`() {
        val queue = PlaybackQueue()
        assertEquals(-1, queue.nextIndex())
        assertEquals(-1, queue.previousIndex())
    }

    @Test
    fun `下一首在末尾时回绕到队首`() {
        val queue = queueOf(1, 2, 3, startIndex = 2)
        assertEquals(0, queue.nextIndex())
    }

    @Test
    fun `上一首在队首时回绕到末尾`() {
        val queue = queueOf(1, 2, 3, startIndex = 0)
        assertEquals(2, queue.previousIndex())
    }

    @Test
    fun `队列不足两首时按模式取下一首返回null`() {
        val queue = queueOf(1)
        assertNull(queue.nextItemByMode())
    }

    @Test
    fun `单曲循环下按模式取下一首仍是当前曲目`() {
        val queue = queueOf(1, 2, 3, startIndex = 1)
        queue.setPlayMode(PlayMode.SINGLE_LOOP)
        assertEquals(2L, queue.nextItemByMode()?.songId)
    }

    @Test
    fun `列表循环下按模式取下一首会回绕`() {
        val queue = queueOf(1, 2, 3, startIndex = 2)
        queue.setPlayMode(PlayMode.LIST_LOOP)
        assertEquals(1L, queue.nextItemByMode()?.songId)
    }

    // ======================= 移除 =======================

    @Test
    fun `移除当前项之前的曲目会让当前下标前移且无需重播`() {
        val queue = queueOf(1, 2, 3, 4, startIndex = 2)
        val replayIndex = queue.removeAt(0)
        assertEquals(-1, replayIndex)
        assertEquals(1, queue.currentIndex.value)
        assertEquals(listOf(2L, 3L, 4L), queue.ids())
    }

    @Test
    fun `移除当前项之后的曲目不影响当前下标`() {
        val queue = queueOf(1, 2, 3, 4, startIndex = 1)
        val replayIndex = queue.removeAt(3)
        assertEquals(-1, replayIndex)
        assertEquals(1, queue.currentIndex.value)
    }

    @Test
    fun `移除当前项会返回需重播的下标`() {
        val queue = queueOf(1, 2, 3, startIndex = 1)
        val replayIndex = queue.removeAt(1)
        assertEquals(1, replayIndex)
        assertEquals(1, queue.currentIndex.value)
        assertEquals(listOf(1L, 3L), queue.ids())
    }

    @Test
    fun `移除末位的当前项后下标收敛到新的末位`() {
        val queue = queueOf(1, 2, 3, startIndex = 2)
        val replayIndex = queue.removeAt(2)
        assertEquals(1, replayIndex)
        assertEquals(1, queue.currentIndex.value)
    }

    @Test
    fun `队列仅剩一首时拒绝移除`() {
        val queue = queueOf(1)
        assertEquals(-1, queue.removeAt(0))
        assertEquals(listOf(1L), queue.ids())
    }

    @Test
    fun `越界移除不改变队列`() {
        val queue = queueOf(1, 2, 3)
        assertEquals(-1, queue.removeAt(5))
        assertEquals(-1, queue.removeAt(-1))
        assertEquals(listOf(1L, 2L, 3L), queue.ids())
    }

    // ======================= 拖动排序 =======================

    @Test
    fun `原地拖动视为无变化`() {
        val queue = queueOf(1, 2, 3)
        assertFalse(queue.move(1, 1))
    }

    @Test
    fun `越界拖动被拒绝`() {
        val queue = queueOf(1, 2, 3)
        assertFalse(queue.move(0, 5))
        assertFalse(queue.move(-1, 1))
        assertEquals(listOf(1L, 2L, 3L), queue.ids())
    }

    @Test
    fun `拖动当前项时下标跟随到目标位置`() {
        val queue = queueOf(1, 2, 3, 4, startIndex = 1)
        assertTrue(queue.move(1, 3))
        assertEquals(3, queue.currentIndex.value)
        assertEquals(listOf(1L, 3L, 4L, 2L), queue.ids())
    }

    @Test
    fun `把当前项之前的曲目向后拖过当前项时当前下标前移`() {
        val queue = queueOf(1, 2, 3, 4, startIndex = 2)
        assertTrue(queue.move(0, 3))
        assertEquals(1, queue.currentIndex.value)
        // 当前项仍是原来的 3
        assertEquals(3L, queue.currentItem()?.songId)
    }

    @Test
    fun `把当前项之后的曲目向前拖到当前项之前时当前下标后移`() {
        val queue = queueOf(1, 2, 3, 4, startIndex = 1)
        assertTrue(queue.move(3, 0))
        assertEquals(2, queue.currentIndex.value)
        assertEquals(2L, queue.currentItem()?.songId)
    }

    @Test
    fun `拖动完全在当前项之后时当前下标不变`() {
        val queue = queueOf(1, 2, 3, 4, startIndex = 0)
        assertTrue(queue.move(2, 3))
        assertEquals(0, queue.currentIndex.value)
        assertEquals(1L, queue.currentItem()?.songId)
    }

    // ======================= 插播 =======================

    @Test
    fun `插播插入到当前项之后`() {
        val queue = queueOf(1, 2, 3, startIndex = 0)
        queue.insertNext(listOf(item(9)))
        assertEquals(listOf(1L, 9L, 2L, 3L), queue.ids())
        // 当前下标不动，仍指向原曲目
        assertEquals(0, queue.currentIndex.value)
        assertEquals(1L, queue.currentItem()?.songId)
    }

    @Test
    fun `插播同时写入原始队列以支持随机模式还原`() {
        val queue = queueOf(1, 2, 3, startIndex = 0)
        queue.insertNext(listOf(item(9)))
        assertEquals(listOf(1L, 9L, 2L, 3L), queue.original.map { it.songId })
    }

    @Test
    fun `插播空列表不产生变化`() {
        val queue = queueOf(1, 2)
        queue.insertNext(emptyList())
        assertEquals(listOf(1L, 2L), queue.ids())
    }

    // ======================= 播放模式切换 =======================

    @Test
    fun `切到随机模式会把当前曲目提到队首`() {
        val queue = queueOf(1, 2, 3, 4, 5, startIndex = 3)
        val current = queue.currentItem()
        queue.applyMode(PlayMode.SHUFFLE, current)
        assertEquals(0, queue.currentIndex.value)
        assertEquals(4L, queue.ids().first())
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), queue.ids().toSet())
    }

    @Test
    fun `从随机切回列表循环会还原原始顺序并重新定位当前曲目`() {
        val queue = queueOf(1, 2, 3, 4, 5, startIndex = 3)
        val current = queue.currentItem()
        queue.applyMode(PlayMode.SHUFFLE, current)
        queue.applyMode(PlayMode.LIST_LOOP, current)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), queue.ids())
        assertEquals(3, queue.currentIndex.value)
    }

    @Test
    fun `切到单曲循环不重排队列`() {
        val queue = queueOf(1, 2, 3, startIndex = 1)
        val current = queue.currentItem()
        queue.applyMode(PlayMode.SINGLE_LOOP, current)
        assertEquals(listOf(1L, 2L, 3L), queue.ids())
        assertEquals(1, queue.currentIndex.value)
        assertEquals(PlayMode.SINGLE_LOOP, queue.playMode.value)
    }

    @Test
    fun `随机模式下整体替换队列会把起始曲目提到队首`() {
        val queue = PlaybackQueue()
        queue.setPlayMode(PlayMode.SHUFFLE)
        queue.replaceAll((1L..5L).map { item(it) }, startIndex = 2)
        assertEquals(0, queue.currentIndex.value)
        assertEquals(3L, queue.ids().first())
    }

    // ======================= 只留当前 =======================

    @Test
    fun `只保留当前项时队列收缩为一首`() {
        val queue = queueOf(1, 2, 3, startIndex = 1)
        val kept = queue.keepOnlyCurrent()
        assertEquals(2L, kept?.songId)
        assertEquals(listOf(2L), queue.ids())
        assertEquals(0, queue.currentIndex.value)
    }

    @Test
    fun `无当前项时只保留当前会清空队列`() {
        val queue = PlaybackQueue()
        queue.restore(listOf(item(1), item(2)), index = 0, context = null)
        queue.setCurrentIndex(-1)
        assertNull(queue.keepOnlyCurrent())
        assertTrue(queue.isEmpty)
        assertEquals(-1, queue.currentIndex.value)
    }

    // ======================= 漫游快照 =======================

    @Test
    fun `还原快照时按歌曲ID重新定位当前曲目`() {
        val queue = queueOf(1, 2, 3, 4, startIndex = 2)
        queue.takeSnapshot()
        // 模拟漫游：截断并追加新曲目
        queue.appendAfter(2, listOf(item(7), item(8)))
        assertEquals(listOf(1L, 2L, 3L, 7L, 8L), queue.ids())

        queue.restoreSnapshot()
        assertEquals(listOf(1L, 2L, 3L, 4L), queue.ids())
        // 当前播放的仍是 3，在还原后的队列中下标为 2
        assertEquals(2, queue.currentIndex.value)
    }

    @Test
    fun `还原快照时同步还原播放上下文`() {
        val queue = queueOf(1, 2, 3, startIndex = 0)
        queue.setPlayContext("我喜欢的音乐")
        queue.takeSnapshot()

        // 进入心动模式，切换上下文
        queue.setPlayContext("intelligence")
        queue.replaceAll(listOf(item(1), item(9)), startIndex = 0)
        assertEquals("intelligence", queue.playContext.value)

        // 还原快照，上下文应自动恢复为原歌单
        queue.restoreSnapshot()
        assertEquals("我喜欢的音乐", queue.playContext.value)
    }

    @Test
    fun `无快照时还原不产生变化`() {
        val queue = queueOf(1, 2, 3, startIndex = 1)
        queue.restoreSnapshot()
        assertEquals(listOf(1L, 2L, 3L), queue.ids())
        assertEquals(1, queue.currentIndex.value)
    }

    // ======================= 恢复与替换 =======================

    @Test
    fun `恢复时越界下标被收敛到合法范围`() {
        val queue = PlaybackQueue()
        queue.restore(listOf(item(1), item(2)), index = 99, context = "playlist_1")
        assertEquals(1, queue.currentIndex.value)
        assertEquals("playlist_1", queue.playContext.value)
    }

    @Test
    fun `恢复空列表不产生变化`() {
        val queue = queueOf(1, 2)
        queue.restore(emptyList(), index = 0, context = "x")
        assertEquals(listOf(1L, 2L), queue.ids())
        assertNull(queue.playContext.value)
    }

    @Test
    fun `收缩为单曲会同时重置原始队列`() {
        val queue = queueOf(1, 2, 3, startIndex = 2)
        queue.replaceWithSingle(item(9))
        assertEquals(listOf(9L), queue.ids())
        assertEquals(listOf(9L), queue.original.map { it.songId })
        assertEquals(0, queue.currentIndex.value)
    }

    // 随机模式下的持久化与恢复
    @Test
    fun `随机模式下保存与恢复的组合应精确复原正在播放的曲目`() {
        val queue = queueOf(1, 2, 3, 4, 5, startIndex = 0)
        queue.applyMode(PlayMode.SHUFFLE, queue.currentItem())
        // 模拟随机模式下已经切到了打乱队列里的第4首
        queue.setCurrentIndex(3)
        val playingSongId = queue.currentItem()!!.songId

        val savedOriginal = queue.original
        val savedIndex = queue.currentIndexInOriginal()

        // 模拟应用重启后新建实例恢复
        val restored = PlaybackQueue()
        restored.setPlayMode(PlayMode.SHUFFLE)
        restored.restore(savedOriginal, savedIndex, context = null)

        assertEquals(playingSongId, restored.currentItem()?.songId)
    }
}
