package com.lin0721.linmusic.feature.recognition.engine

import com.lin0721.linmusic.feature.recognition.domain.AttemptStatus
import com.lin0721.linmusic.feature.recognition.domain.MatchAttempt
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionOutcome
import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.min

fun interface AudioMatcher {
    // 空列表表示该窗口未命中；网络等失败以异常抛出
    suspend fun match(fingerprint: String, durationSeconds: Int): List<RecognitionCandidate>
}

// 边录边识别：满 3 秒起匹配，之后每多 1 秒切一个新窗口，命中即停，最长录 8 秒。单次使用
class RecognitionSession(
    private val capture: AudioCapture,
    private val fingerprinter: FingerprintGenerator,
    private val matcher: AudioMatcher
) {

    private data class RecordingState(val samples: Int = 0, val finished: Boolean = false)

    private val _progress = MutableStateFlow(RecognitionProgress())
    val progress: StateFlow<RecognitionProgress> = _progress.asStateFlow()

    suspend fun run(): RecognitionOutcome = coroutineScope {
        val buffer = FloatArray(MAX_SAMPLES)
        val recording = MutableStateFlow(RecordingState())

        // 已写入的区间不再改写，读方看到 samples 递增后读取对应区间即可
        val recordJob = launch {
            var written = 0
            var levelFrom = 0
            try {
                capture.capture().collect { chunk ->
                    val count = min(chunk.size, MAX_SAMPLES - written)
                    chunk.copyInto(buffer, written, 0, count)
                    written += count
                    val newLevels = ArrayList<Float>()
                    while (levelFrom + SAMPLES_PER_LEVEL <= written) {
                        newLevels += PcmMath.rms(buffer, levelFrom, levelFrom + SAMPLES_PER_LEVEL)
                        levelFrom += SAMPLES_PER_LEVEL
                    }
                    _progress.update {
                        it.copy(
                            levels = if (newLevels.isEmpty()) it.levels else it.levels + newLevels,
                            recordedMs = written * 1000L / RECOGNITION_SAMPLE_RATE
                        )
                    }
                    recording.value = RecordingState(samples = written, finished = written >= MAX_SAMPLES)
                    if (written >= MAX_SAMPLES) cancel()
                }
            } finally {
                recording.update { it.copy(finished = true) }
            }
        }

        var silentWindows = 0
        for (index in 0 until MAX_WINDOWS) {
            val start = index * STEP_SAMPLES
            val end = start + WINDOW_SAMPLES
            val state = recording.first { it.samples >= end || it.finished }
            if (state.samples < end) break

            setAttempt(index, AttemptStatus.MATCHING)
            val normalized = PcmMath.normalize(buffer.copyOfRange(start, end))
            if (PcmMath.rms(normalized) < SILENCE_RMS_THRESHOLD) {
                silentWindows++
                setAttempt(index, AttemptStatus.SILENT)
                continue
            }

            val fingerprint = fingerprinter.generate(normalized)
            val candidates = matcher.match(fingerprint, WINDOW_SECONDS)
            if (candidates.isNotEmpty()) {
                setAttempt(index, AttemptStatus.HIT)
                recordJob.cancel()
                return@coroutineScope RecognitionOutcome.Found(candidates, start / RECOGNITION_SAMPLE_RATE)
            }
            setAttempt(index, AttemptStatus.MISSED)
        }

        recordJob.cancel()
        if (silentWindows == _progress.value.attempts.size && silentWindows > 0) {
            RecognitionOutcome.Silent
        } else {
            RecognitionOutcome.NotFound
        }
    }

    private fun setAttempt(index: Int, status: AttemptStatus) {
        _progress.update { current ->
            val attempt = MatchAttempt(startSecond = index * STEP_SAMPLES / RECOGNITION_SAMPLE_RATE, status = status)
            val attempts = if (index < current.attempts.size) {
                current.attempts.toMutableList().also { it[index] = attempt }
            } else {
                current.attempts + attempt
            }
            current.copy(attempts = attempts)
        }
    }

    companion object {
        const val WINDOW_SECONDS = 3
        const val MAX_SECONDS = 8
        const val LEVEL_COUNT = 40

        private const val WINDOW_SAMPLES = WINDOW_SECONDS * RECOGNITION_SAMPLE_RATE
        private const val STEP_SAMPLES = RECOGNITION_SAMPLE_RATE
        private const val MAX_SAMPLES = MAX_SECONDS * RECOGNITION_SAMPLE_RATE
        const val MAX_WINDOWS = (MAX_SAMPLES - WINDOW_SAMPLES) / STEP_SAMPLES + 1
        private const val SAMPLES_PER_LEVEL = MAX_SAMPLES / LEVEL_COUNT

        // 沿用 SPlayer-Next 的阈值，针对归一化后的 RMS
        private const val SILENCE_RMS_THRESHOLD = 0.005f
    }
}
