package com.lin0721.linmusic.plugin.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.io.File

// 次版本只增不改，破坏性变更才提升主版本
object PluginApiVersion {
    const val MAJOR = 1
    const val MINOR = 9
}

// 原生插件入口，需要公开的无参构造函数；回调都不在主线程
interface MelodiaPlugin {
    // 在这里注册扩展；抛出异常视为加载失败
    fun onLoad(context: PluginContext)

    // 停用或删除时调用一次，之后 context 不再可用
    fun onUnload() {}
}

// 宿主拒绝调用或调用失败，message 可以直接展示
class PluginApiException(message: String) : Exception(message)

interface PluginContext {
    val pluginId: String
    val extensions: PluginExtensions
    val player: PlayerApi

    // API 1.3 起提供
    val spectrum: SpectrumApi
    val library: LibraryApi
    val netease: NeteaseApi
    val storage: PluginStorage
    val settings: PluginSettings
    val ui: PluginUi
    val logger: PluginLogger

    // 随插件停用取消，未捕获的异常计入插件错误
    val scope: CoroutineScope

    // 插件私有目录，升级时保留，删除插件时清空
    val dataDirectory: File

    // 包内 native/windows-x64/ 下的动态库；要在插件代码里调用 System.load，库才会绑定到插件的类加载器
    fun nativeLibrary(fileName: String): File?
}

interface PluginLogger {
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)
    fun error(message: String, error: Throwable? = null)
}

interface PluginStorage {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun remove(key: String)
}

// 值为 Boolean、Number 或 String；key 是清单 settings 里的 id
interface PluginSettings {
    suspend fun get(key: String): Any?
    suspend fun all(): Map<String, Any?>
}

interface PluginUi {
    suspend fun toast(text: String)

    // 合并进页面状态；值可以是 String、Number、Boolean、null、List 或 Map
    suspend fun setState(pageId: String, patch: Map<String, Any?>)

    // 打开内容源里的歌单
    suspend fun openContent(sourceId: String, contentId: String, title: String = "")

    // 用系统浏览器打开 https 网址，每次先经用户确认，拒绝时抛 PluginApiException；需要 API 1.4
    suspend fun openUrl(url: String)
}

interface PlayerApi {
    suspend fun current(): NowPlayingSong?
    suspend fun state(): PlaybackState
    suspend fun play()
    suspend fun pause()
    suspend fun next()
    suspend fun previous()
    suspend fun enqueue(song: QueueSong)
}

interface SpectrumApi {
    // 需要 playback.read 权限，缺权限或系统不支持时收集会抛 PluginApiException
    // 收集时才采集，约 30 帧/秒；来不及处理的帧会被丢弃，无声时停发
    fun frames(): Flow<SpectrumFrame>
}

// 取自实际输出，随音量变化；数组由所有订阅者共用，不要修改，要保留就复制一份
class SpectrumFrame(
    // 从本次采集开始算起
    val timeMs: Long,
    // 64 个对数频段（20 Hz–16 kHz），范围 0–1
    val bands: FloatArray,
    // 最近 1024 个单声道采样，范围 -1–1
    val waveform: FloatArray
)

interface LibraryApi {
    suspend fun likedSongIds(): Set<Long>
    suspend fun playlists(): List<LibraryPlaylist>
}

// 方法与沙盒的 netease.call 一一对应，同样经过权限、限频和用户确认
interface NeteaseApi {
    suspend fun songDetails(ids: List<Long>): List<NeteaseTrack>
    suspend fun lyric(id: Long): List<NeteaseLyricLine>
    suspend fun search(
        keyword: String,
        type: NeteaseSearchType = NeteaseSearchType.SONG,
        offset: Int = 0,
        limit: Int = 30
    ): NeteaseSearchPage
    suspend fun playlist(id: Long): NeteasePlaylist
    suspend fun album(id: Long): NeteasePlaylist
    suspend fun artist(id: Long): NeteaseArtist
    suspend fun artistTopSongs(id: Long): List<NeteaseTrack>
    suspend fun userPlaylists(): List<LibraryPlaylist>
    suspend fun userLikedSongs(): Set<Long>
    suspend fun likeSong(id: Long, like: Boolean)
    suspend fun createPlaylist(name: String, private: Boolean = false): NeteasePlaylist
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>)
    suspend fun removeTracks(playlistId: Long, trackIds: List<Long>)
    suspend fun subscribePlaylist(id: Long, subscribe: Boolean)
    suspend fun subscribeAlbum(id: Long, subscribe: Boolean)
    suspend fun followArtist(id: Long, follow: Boolean)
    suspend fun deletePlaylist(id: Long)
}
