package com.lin0721.linmusic.core.source

// 第三方音源 Provider 统一接口
interface AudioSourceProvider {

    val platform: MusicPlatform

    // 根据歌曲元数据搜索匹配并返回可播放 URL
    suspend fun resolveUrl(
        songId: String = "",
        songName: String,
        artists: String,
        albumName: String?,
        durationMs: Long,
        quality: String,
        // songId 所属的平台，插件据此决定能否直接用这个 id
        origin: MusicPlatform = MusicPlatform.NETEASE,
        // 解析失败原因收集器（用于把「哪个平台为什么没成」回传给 UI），实现可忽略
        diagnostics: MutableList<String>? = null
    ): SourceResult?

    // 搜索歌曲
    suspend fun search(
        keyword: String,
        offset: Int = 0,
        limit: Int = 30
    ): List<ExternalTrack>
}
