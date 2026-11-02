package com.lin0721.linmusic.desktop.data.recognition

import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.desktop.player.SilentPlaybackController
import com.lin0721.linmusic.feature.recognition.data.RecognitionHistoryPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionRepository
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionMode
import com.lin0721.linmusic.feature.recognition.engine.AudioCapture
import com.lin0721.linmusic.feature.recognition.engine.FingerprintGenerator
import com.lin0721.linmusic.feature.recognition.ui.RecognitionFailedReason
import com.lin0721.linmusic.feature.recognition.ui.RecognitionUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopRecognitionTest {

    private val candidate = RecognitionCandidate(
        songId = 42L, title = "晴天", artists = "周杰伦", album = "叶惠美",
        coverUrl = "", startTimeMs = 83_000L, durationMs = 269_000L
    )

    private val repository = object : RecognitionRepository {
        override suspend fun match(fingerprint: String, durationSeconds: Int) = listOf(candidate)
    }

    // 一次性推满 8 秒有声音频
    private val loudCapture = AudioCapture {
        flow { repeat(80) { emit(FloatArray(800) { i -> if (i % 2 == 0) 0.2f else -0.2f }) } }
    }

    private val silentForever = AudioCapture { flow { awaitCancellation() } }

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var history: RecognitionHistoryPreferences
    private lateinit var controller: SilentPlaybackController

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-recognition").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        history = RecognitionHistoryPreferences(PreferencesStores.get(File(dir, "history.preferences_pb")))
        controller = SilentPlaybackController()
        controller.playQueue(listOf(QueueItem(1L, "原曲", "歌手", "")), 0)
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun recognition(capture: AudioCapture) = DesktopRecognition(
        repository = repository,
        historyPreferences = history,
        preferences = RecognitionPreferences(PreferencesStores.get(File(dir, "prefs.preferences_pb"))),
        fingerprinter = FingerprintGenerator { "fp" },
        controller = controller,
        scope = scope,
        captureFactory = { capture }
    )

    private suspend fun awaitUntil(predicate: () -> Boolean) = withTimeout(5_000) {
        while (!predicate()) delay(20)
    }

    @Test
    fun foundRecordsHistoryAndRestoresPlaybackOnClose() = runBlocking {
        val recognition = recognition(loudCapture)
        assertTrue(controller.playWhenReady.value)

        recognition.start(RecognitionMode.MICROPHONE)
        assertFalse(controller.playWhenReady.value)
        awaitUntil { recognition.uiState.value is RecognitionUiState.Found }
        awaitUntil { recognition.history.value.isNotEmpty() }
        assertEquals(42L, recognition.history.value.first().songId)

        recognition.close()
        assertEquals(RecognitionUiState.Idle, recognition.uiState.value)
        assertTrue(controller.playWhenReady.value)
    }

    @Test
    fun playingDuringListeningStopsSessionAndKeepsNewSong() = runBlocking {
        val recognition = recognition(silentForever)
        recognition.start(RecognitionMode.MICROPHONE)
        assertTrue(recognition.uiState.value is RecognitionUiState.Listening)

        recognition.play(candidate)
        val state = recognition.uiState.value
        assertTrue(state is RecognitionUiState.Failed && state.reason == RecognitionFailedReason.STOPPED)
        assertEquals(42L, controller.nowPlaying.value?.songId)

        // 用户已点播，关闭时不再恢复原曲
        recognition.close()
        assertEquals(42L, controller.nowPlaying.value?.songId)
    }

    @Test
    fun cancelKeepsPlaybackPausedUntilClose() = runBlocking {
        val recognition = recognition(silentForever)
        recognition.start(RecognitionMode.MICROPHONE)
        recognition.cancel()
        assertEquals(RecognitionUiState.Idle, recognition.uiState.value)
        assertFalse(controller.playWhenReady.value)

        recognition.close()
        assertTrue(controller.playWhenReady.value)
    }
}
