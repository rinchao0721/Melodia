package com.lin0721.linmusic.plugin.api

import androidx.compose.runtime.Composable

// id 必须已在清单 contributes 里声明，否则抛 IllegalArgumentException
interface PluginExtensions {
    fun audioSource(id: String, source: AudioSource)
    fun lyricsSource(id: String, source: LyricsSource)
    fun contentSource(id: String, source: ContentSource)
    fun command(id: String, handler: suspend (args: Map<String, Any?>) -> Unit)

    // 页面按钮触发；open:<页面 id> 在页面打开时调用，settingsChanged 在设置改动时调用
    fun action(name: String, handler: suspend (args: Map<String, Any?>) -> Unit)

    // 只收到清单 events 里声明的事件
    fun playbackListener(listener: PlaybackListener)

    // API 1.1 起提供
    fun backdropSource(id: String, source: BackdropSource)

    // API 1.2 起提供；对应清单 pages 里 compose 为 true 的页面，在独立的窗口层渲染，出错只影响这个页面
    fun composePage(id: String, content: @Composable () -> Unit)
}

// 窗口背景的逐帧来源；帧率与遮罩写在清单 backdrops 里，窗口隐藏时不调用
interface BackdropSource {
    // 帧宽上限，高度按窗口比例计算，宿主拉伸铺满
    val maxWidth: Int get() = 960

    // 在后台线程调用，要在一帧时间内返回；把不透明的 ARGB 写进 frame.pixels
    fun render(frame: BackdropFrame)
}

class BackdropFrame(val width: Int, val height: Int, val timeMs: Long, val pixels: IntArray)

interface AudioSource {
    // 返回 http(s) 地址，没有结果返回 null
    suspend fun resolve(query: TrackQuery, quality: String): String?
}

interface LyricsSource {
    suspend fun fetch(query: TrackQuery): LyricsResult?
}

interface ContentSource {
    suspend fun search(keyword: String, offset: Int, limit: Int): List<ContentTrack> = emptyList()
    suspend fun playlist(id: String): ContentPlaylist?
    suspend fun resolve(trackId: String, quality: String): String?
}

interface PlaybackListener {
    fun onSongChanged(song: NowPlayingSong?) {}
    fun onPlayStateChanged(playing: Boolean) {}
    fun onProgress(positionMs: Long, durationMs: Long) {}
}
