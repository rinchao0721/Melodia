package com.lin0721.linmusic.feature.recognition.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

private const val TAG = "MicrophoneRecorder"

// 采集源抽象，便于识别流程脱离真机单测
fun interface AudioCapture {
    // 持续发射 8 kHz 单声道 Float32 块，直到收集方取消
    fun capture(): Flow<FloatArray>
}

// 麦克风采集：优先 8 kHz 直采，设备不支持时退回更高采样率再抽样
class MicrophoneRecorder(private val context: Context) : AudioCapture {

    private class OpenedRecord(val record: AudioRecord, val sampleRate: Int, val encoding: Int)

    @SuppressLint("MissingPermission") // 权限由界面层申请，未授权时 AudioRecord 抛 SecurityException 并在此转换
    override fun capture(): Flow<FloatArray> = flow {
        val opened = try {
            openRecord()
        } catch (e: SecurityException) {
            throw RecognitionException(RecognitionFailure.PERMISSION_DENIED, "麦克风权限未授予", e)
        } ?: throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "没有可用的录音配置")

        val record = opened.record
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "麦克风被占用，录音未启动")
            }
            val downsampler = Downsampler(opened.sampleRate, RECOGNITION_SAMPLE_RATE)
            // 每次读约 100 ms，保证取消后能及时退出阻塞读
            val chunkSize = opened.sampleRate / 10
            val floatBuffer = FloatArray(chunkSize)
            val shortBuffer = if (opened.encoding == AudioFormat.ENCODING_PCM_16BIT) ShortArray(chunkSize) else null

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

    // 依次尝试 音源 × 采样率 × 编码，返回第一个能初始化成功的组合
    private fun openRecord(): OpenedRecord? {
        for (source in candidateSources()) {
            for (rate in CANDIDATE_RATES) {
                for (encoding in CANDIDATE_ENCODINGS) {
                    val record = tryCreate(source, rate, encoding) ?: continue
                    AppLogger.d(TAG, "录音配置 source=$source rate=$rate encoding=$encoding")
                    return OpenedRecord(record, rate, encoding)
                }
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun tryCreate(source: Int, rate: Int, encoding: Int): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, encoding)
        if (minBuffer <= 0) return null
        val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
        // 缓冲至少半秒，避免主线程偶发卡顿时底层溢出丢样
        val bufferSize = maxOf(minBuffer, rate / 2 * bytesPerSample)
        val record = try {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, encoding, bufferSize)
        } catch (e: IllegalArgumentException) {
            AppLogger.d(TAG, "录音配置不受支持 source=$source rate=$rate encoding=$encoding", e)
            return null
        } catch (e: UnsupportedOperationException) {
            AppLogger.d(TAG, "录音配置不受支持 source=$source rate=$rate encoding=$encoding", e)
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        return record
    }

    // UNPROCESSED 需设备声明支持；VOICE_RECOGNITION 按兼容性要求关闭降噪与自动增益，最后兜底 MIC
    private fun candidateSources(): List<Int> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val unprocessedSupported =
            audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return buildList {
            if (unprocessedSupported) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }
    }

    private companion object {
        val CANDIDATE_RATES = listOf(RECOGNITION_SAMPLE_RATE, 16000, 48000, 44100)
        val CANDIDATE_ENCODINGS = listOf(AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT)
    }
}
