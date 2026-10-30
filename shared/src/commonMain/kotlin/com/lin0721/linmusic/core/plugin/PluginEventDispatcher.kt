package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaybackController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// 把播放状态变化转成插件事件；只有运行中的插件订阅了事件时才连接播放器
class PluginEventDispatcher(
    private val runtime: PluginRuntime,
    private val playback: () -> PlaybackController?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val progressIntervalMs: Long = PROGRESS_INTERVAL_MS
) {
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            runtime.status
                .map { runtime.subscribedEvents() }
                .distinctUntilChanged()
                .collectLatest { events ->
                    if (events.isEmpty()) return@collectLatest
                    // 播放器需在主线程创建
                    val controller = withContext(mainDispatcher) { playback() } ?: return@collectLatest
                    observe(controller, events)
                }
        }
    }

    private suspend fun observe(controller: PlaybackController, events: Set<PluginEvent>) = coroutineScope {
        if (PluginEvent.SONG_CHANGED in events) {
            launch {
                controller.nowPlaying
                    .distinctUntilChanged { old, new -> old?.mediaId == new?.mediaId }
                    .drop(1)
                    .collect { runtime.emitAll(PluginEvent.SONG_CHANGED, songJson(it)) }
            }
        }
        if (PluginEvent.PLAY_STATE_CHANGED in events) {
            launch {
                controller.isPlaying
                    .drop(1)
                    .collect { playing -> runtime.emitAll(PluginEvent.PLAY_STATE_CHANGED, buildJsonObject { put("playing", playing) }) }
            }
        }
        if (PluginEvent.PROGRESS in events) {
            launch {
                while (true) {
                    delay(progressIntervalMs)
                    if (!controller.isPlaying.value) continue
                    runtime.emitAll(
                        PluginEvent.PROGRESS,
                        buildJsonObject {
                            put("positionMs", controller.currentPosition.value)
                            put("durationMs", controller.duration.value)
                        }
                    )
                }
            }
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 1_000L

        fun songJson(nowPlaying: NowPlaying?): JsonElement = nowPlaying?.let {
            buildJsonObject {
                put("id", it.mediaId)
                it.songId?.takeIf { id -> id > 0 }?.let { id -> put("songId", id) }
                put("title", it.title)
                put("artist", it.artist)
                put("cover", it.artworkUri)
            }
        } ?: JsonNull
    }
}
