package com.lin0721.linmusic.feature.recognition.engine

import android.media.AudioFormat
import android.media.AudioRecord
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

private const val TAG = "AudioRecordStream"

private val CANDIDATE_RATES = listOf(RECOGNITION_SAMPLE_RATE, 16000, 48000, 44100)
private val CANDIDATE_ENCODINGS = listOf(AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT)

internal class OpenedRecord(val record: AudioRecord, val sampleRate: Int, val encoding: Int)

// 依次尝试 采样率 × 编码，返回第一个能初始化成功的组合；create 负责按具体音源构造 AudioRecord
internal fun openAudioRecord(
    label: String,
    create: (sampleRate: Int, encoding: Int, bufferSize: Int) -> AudioRecord
): OpenedRecord? {
    for (rate in CANDIDATE_RATES) {
        for (encoding in CANDIDATE_ENCODINGS) {
            val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, encoding)
            if (minBuffer <= 0) continue
            val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
            // 缓冲至少半秒，避免主线程偶发卡顿时底层溢出丢样
            val bufferSize = maxOf(minBuffer, rate / 2 * bytesPerSample)
            val record = try {
                create(rate, encoding, bufferSize)
            } catch (e: IllegalArgumentException) {
                AppLogger.d(TAG, "$label 录音配置不受支持 rate=$rate encoding=$encoding", e)
                continue
            } catch (e: UnsupportedOperationException) {
                AppLogger.d(TAG, "$label 录音配置不受支持 rate=$rate encoding=$encoding", e)
                continue
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                continue
            }
            AppLogger.d(TAG, "$label 录音配置 rate=$rate encoding=$encoding")
            return OpenedRecord(record, rate, encoding)
        }
    }
    return null
}

// 持续读取并抽样到 8 kHz 单声道 Float32，收集方取消时停止并释放 AudioRecord
internal fun OpenedRecord.asPcmFlow(): Flow<FloatArray> = flow {
    try {
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "录音被占用，未能启动")
        }
        val downsampler = Downsampler(sampleRate, RECOGNITION_SAMPLE_RATE)
        // 每次读约 100 ms，保证取消后能及时退出阻塞读
        val chunkSize = sampleRate / 10
        val floatBuffer = FloatArray(chunkSize)
        val shortBuffer = if (encoding == AudioFormat.ENCODING_PCM_16BIT) ShortArray(chunkSize) else null

        while (currentCoroutineContext().isActive) {
            val read = if (shortBuffer != null) {
                val n = record.read(shortBuffer, 0, chunkSize, AudioRecord.READ_BLOCKING)
                for (i in 0 until n.coerceAtLeast(0)) floatBuffer[i] = shortBuffer[i] / 32768f
                n
            } else {
                record.read(floatBuffer, 0, chunkSize, AudioRecord.READ_BLOCKING)
            }
            if (read < 0) {
                throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "录音读取失败: $read")
            }
            if (read == 0) continue
            val output = downsampler.process(floatBuffer, read)
            if (output.isNotEmpty()) emit(output)
        }
    } finally {
        runCatching { record.stop() }
        record.release()
    }
}.flowOn(Dispatchers.IO)
