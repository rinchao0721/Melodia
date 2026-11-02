package com.lin0721.linmusic.plugin.api

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
}

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
