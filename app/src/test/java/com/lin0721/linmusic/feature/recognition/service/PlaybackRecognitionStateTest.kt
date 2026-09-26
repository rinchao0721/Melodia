package com.lin0721.linmusic.feature.recognition.service

import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress
import com.lin0721.linmusic.feature.recognition.ui.RecognitionFailedReason
import com.lin0721.linmusic.feature.recognition.ui.RecognitionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecognitionStateTest {

    @Test
    fun `打开结果请求只能被消费一次`() {
        val state = PlaybackRecognitionState()
        assertFalse(state.consumeOpenResult())

        state.requestOpenResult()
        assertTrue(state.pendingOpenResult.value)
        assertTrue(state.consumeOpenResult())
        assertFalse(state.consumeOpenResult())
        assertFalse(state.pendingOpenResult.value)
    }

    @Test
    fun `仅聆听中视为正在运行且结局会被保留`() {
        val state = PlaybackRecognitionState()
        assertFalse(state.isRunning)

        state.update(RecognitionUiState.Listening(RecognitionProgress()))
        assertTrue(state.isRunning)

        val failed = RecognitionUiState.Failed(RecognitionFailedReason.SILENT, RecognitionProgress())
        state.update(failed)
        assertFalse(state.isRunning)
        assertEquals(failed, state.state.value)
    }
}
