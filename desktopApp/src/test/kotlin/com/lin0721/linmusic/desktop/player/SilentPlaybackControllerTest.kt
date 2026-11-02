package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SilentPlaybackControllerTest {

    private fun item(id: Long) = QueueItem(id, "歌曲$id", "歌手", "")

    private fun controllerWith(vararg ids: Long, start: Int = 0) = SilentPlaybackController().apply {
        playQueue(ids.map(::item), start, null, null)
    }

    @Test
    fun reportsUnavailableAndHasNoAudioCapabilities() {
        val controller = SilentPlaybackController()

        assertEquals(SilentPlaybackController.UNAVAILABLE_MESSAGE, controller.unavailableReason)
        assertTrue(controller.audioDevices.value.isEmpty())
        assertEquals(AUTO_AUDIO_DEVICE, controller.audioDevice.value)
        controller.setVolume(10)
        assertEquals(100, controller.volume.value)
    }

    @Test
    fun everyPlayAttemptEmitsTheUnavailableMessage() = runTest {
        val controller = SilentPlaybackController()
        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { controller.messages.collect { messages += it } }

        controller.resume()
        controller.playQueue(listOf(item(1)), 0, null, null)
        controller.pause()
        controller.resume()

        assertEquals(List(2) { SilentPlaybackController.UNAVAILABLE_MESSAGE }, messages)
        job.cancel()
    }

    @Test
    fun playQueueSetsCurrentTrackAndNeighbours() {
        val controller = controllerWith(1, 2, 3, start = 1)

        assertEquals(1, controller.currentIndex.value)
        assertEquals("2", controller.nowPlaying.value?.mediaId)
        assertEquals(1L, controller.previousQueueItem.value?.songId)
        assertEquals(3L, controller.nextQueueItem.value?.songId)
        assertTrue(controller.isPlaying.value)
    }

    @Test
    fun startIndexIsClampedAndEmptyQueueIgnored() {
        val controller = controllerWith(1, 2, start = 9)
        assertEquals(1, controller.currentIndex.value)

        controller.playQueue(emptyList(), 0, null, null)
        assertEquals(2, controller.queue.value.size)
    }

    @Test
    fun stepsWrapAroundQueue() {
        val controller = controllerWith(1, 2, 3, start = 2)

        controller.playNext()
        assertEquals(0, controller.currentIndex.value)

        controller.skipToPrevious()
        assertEquals(2, controller.currentIndex.value)
    }

    @Test
    fun addToPlayNextInsertsAfterCurrent() {
        val controller = controllerWith(1, 2, start = 0)

        controller.addToPlayNext(listOf(item(9)))

        assertEquals(listOf(1L, 9L, 2L), controller.queue.value.map { it.songId })
        assertEquals(9L, controller.nextQueueItem.value?.songId)
    }

    @Test
    fun removingBeforeCurrentKeepsSameTrack() {
        val controller = controllerWith(1, 2, 3, start = 2)

        controller.removeFromQueue(0)

        assertEquals(1, controller.currentIndex.value)
        assertEquals(3L, controller.currentQueueItem.value?.songId)
    }

    @Test
    fun removingCurrentMovesToFollowingTrack() {
        val controller = controllerWith(1, 2, 3, start = 1)

        controller.removeFromQueue(1)

        assertEquals("3", controller.nowPlaying.value?.mediaId)
    }

    @Test
    fun removingLastTrackClearsQueue() {
        val controller = controllerWith(1)

        controller.removeFromQueue(0)

        assertTrue(controller.queue.value.isEmpty())
        assertNull(controller.nowPlaying.value)
        assertEquals(-1, controller.currentIndex.value)
        assertFalse(controller.isPlaying.value)
    }

    @Test
    fun movingKeepsCurrentTrackSelected() {
        val controller = controllerWith(1, 2, 3, start = 0)

        controller.moveInQueue(0, 2)

        assertEquals(listOf(2L, 3L, 1L), controller.queue.value.map { it.songId })
        assertEquals(2, controller.currentIndex.value)
    }

    @Test
    fun playModesRotateAndToggle() {
        val controller = SilentPlaybackController()

        // 模式按钮轮转：列表循环 → 单曲循环 → 顺序 → 列表循环 → 单曲循环 → 随机 → 顺序
        controller.rotatePlayMode()
        assertEquals(PlayMode.SINGLE_LOOP, controller.playMode.value)
        controller.toggleRepeat()
        assertEquals(PlayMode.SEQUENCE, controller.playMode.value)
        controller.rotatePlayMode()
        assertEquals(PlayMode.LIST_LOOP, controller.playMode.value)
        controller.rotatePlayMode()
        controller.rotatePlayMode()
        assertEquals(PlayMode.SHUFFLE, controller.playMode.value)
        controller.rotatePlayMode()
        assertEquals(PlayMode.SEQUENCE, controller.playMode.value)

        // 随机开关记忆：开启随机后关闭，回到进入随机前的模式
        controller.toggleShuffle()
        assertEquals(PlayMode.SHUFFLE, controller.playMode.value)
        controller.toggleShuffle()
        assertEquals(PlayMode.SEQUENCE, controller.playMode.value)
    }

    @Test
    fun seekNeverGoesNegative() {
        val controller = controllerWith(1)

        controller.seekTo(-500)

        assertEquals(0L, controller.currentPosition.value)
    }
}
