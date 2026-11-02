package com.lin0721.linmusic.desktop.player.spectrum

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.windows.audio.LoopbackBlock
import com.lin0721.linmusic.desktop.platform.windows.audio.WasapiLoopbackCapture
import com.lin0721.linmusic.desktop.platform.windows.audio.loopbackBlocks
import com.lin0721.linmusic.desktop.platform.windows.audio.openPlaybackLoopback
import com.lin0721.linmusic.plugin.api.PluginApiException
import com.lin0721.linmusic.plugin.api.SpectrumFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn

private const val TAG = "SpectrumHub"

private const val STOP_TIMEOUT_MS = 1_000L
private const val RETRY_DELAY_MS = 10_000L

// 无声超过约半秒后停发
private const val QUIET_FRAMES = 15

// 所有订阅共用一路采集，第一个订阅时启动，最后一个取消后停止；慢的订阅者丢帧，不拖累采集
class SpectrumHub internal constructor(
    blocks: () -> Flow<LoopbackBlock>,
    scope: CoroutineScope,
    supported: Boolean
) {
    constructor() : this(
        blocks = { loopbackBlocks { ole32 -> openPlaybackLoopback(ole32, ProcessHandle.current().pid()) } },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        supported = WasapiLoopbackCapture.isSupported
    )

    val frames: Flow<SpectrumFrame> = if (supported) {
        analyzed(blocks)
            .retryWhen { cause, _ ->
                if (cause is CancellationException) return@retryWhen false
                AppLogger.w(TAG, "频谱采集失败，稍后重试", cause)
                delay(RETRY_DELAY_MS)
                true
            }
            .conflate()
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS))
    } else {
        flow { throw PluginApiException("当前系统不支持频谱") }
    }

    private fun analyzed(blocks: () -> Flow<LoopbackBlock>): Flow<SpectrumFrame> = flow {
        var analyzer: SpectrumAnalyzer? = null
        var quiet = 0
        blocks().collect { block ->
            val current = analyzer?.takeIf { it.sampleRate == block.sampleRate }
                ?: SpectrumAnalyzer(block.sampleRate).also { analyzer = it }
            for (result in current.process(block.samples)) {
                quiet = if (result.bands.all { it == 0f }) quiet + 1 else 0
                if (quiet <= QUIET_FRAMES) emit(SpectrumFrame(result.timeMs, result.bands, result.waveform))
            }
        }
    }
}
