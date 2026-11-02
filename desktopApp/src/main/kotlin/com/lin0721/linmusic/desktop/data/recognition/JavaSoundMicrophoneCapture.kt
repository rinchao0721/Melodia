package com.lin0721.linmusic.desktop.data.recognition

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import com.lin0721.linmusic.feature.recognition.engine.AudioCapture
import com.lin0721.linmusic.feature.recognition.engine.Downsampler
import com.lin0721.linmusic.feature.recognition.engine.RECOGNITION_SAMPLE_RATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

private const val TAG = "JavaSoundMicrophoneCapture"

private val CANDIDATE_RATES = listOf(48_000f, 44_100f, 16_000f, RECOGNITION_SAMPLE_RATE.toFloat())
private val CANDIDATE_CHANNELS = listOf(1, 2)

// 系统默认录音设备；Windows 关闭麦克风隐私权限时读到的是全零，最终表现为静音结局
class JavaSoundMicrophoneCapture : AudioCapture {

    override fun capture(): Flow<FloatArray> = flow {
        val line = openLine()
        try {
            line.start()
            val format = line.format
            val channels = format.channels
            val frameSize = format.frameSize
            val bigEndian = format.isBigEndian
            val downsampler = Downsampler(format.sampleRate.toInt(), RECOGNITION_SAMPLE_RATE)
            // 每次读约 100 ms，取消后能及时退出阻塞读
            val framesPerRead = (format.sampleRate / 10).toInt()
            val bytes = ByteArray(framesPerRead * frameSize)
            val mono = FloatArray(framesPerRead)
            while (currentCoroutineContext().isActive) {
                val read = line.read(bytes, 0, bytes.size)
                val frames = read / frameSize
                if (frames <= 0) continue
                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) {
                        val offset = frame * frameSize + channel * 2
                        val lo = bytes[if (bigEndian) offset + 1 else offset].toInt() and 0xFF
                        val hi = bytes[if (bigEndian) offset else offset + 1].toInt()
                        sum += ((hi shl 8) or lo).toShort() / 32768f
                    }
                    mono[frame] = sum / channels
                }
                val output = downsampler.process(mono, frames)
                if (output.isNotEmpty()) emit(output)
            }
        } finally {
            runCatching { line.stop() }
            line.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun openLine(): TargetDataLine {
        var busy: Exception? = null
        for (rate in CANDIDATE_RATES) {
            for (channels in CANDIDATE_CHANNELS) {
                val format = AudioFormat(rate, 16, channels, true, false)
                val info = DataLine.Info(TargetDataLine::class.java, format)
                if (!AudioSystem.isLineSupported(info)) continue
                try {
                    val line = AudioSystem.getLine(info) as TargetDataLine
                    // 缓冲半秒，避免偶发卡顿时底层溢出丢样
                    line.open(format, (rate / 2).toInt() * format.frameSize)
                    AppLogger.d(TAG, "麦克风录音配置 rate=$rate channels=$channels")
                    return line
                } catch (e: LineUnavailableException) {
                    busy = e
                } catch (e: SecurityException) {
                    throw RecognitionException(RecognitionFailure.PERMISSION_DENIED, "无权访问麦克风", e)
                } catch (e: IllegalArgumentException) {
                    AppLogger.d(TAG, "录音配置不受支持 rate=$rate channels=$channels", e)
                }
            }
        }
        if (busy != null) {
            throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "麦克风被占用", busy)
        }
        throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "没有可用的麦克风")
    }
}
