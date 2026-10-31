package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.network.mapToAppError
import com.lin0721.linmusic.core.player.LocalRecentPlaylist
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricParser
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "PlaybackRepositoryImpl"

// 我喜欢的音乐（红心歌单）在歌单列表里的 specialType
internal const val LIKED_PLAYLIST_SPECIAL_TYPE = 5

// 心动模式只接受红心歌单：自建 / 收藏 / 云村歌单一律回 400「不支持该歌单类型」，
// playlistId 传 0 则回 400「歌单不存在」。所以未指定上下文时必须显式挑红心歌单，
// 不能拿歌单列表的第一项凑数（第一项往往是用户最近自建的歌单）。
internal fun pickIntelligencePlaylist(playlists: List<com.lin0721.linmusic.core.userplaylist.UserPlaylist>) =
    playlists.firstOrNull { it.specialType == LIKED_PLAYLIST_SPECIAL_TYPE }

class PlaybackRepositoryImpl(
    private val apiService: PlaybackApi,
    private val settingsPreferences: SettingsPreferences,
    private val userPreferences: UserPreferences,
    private val userPlaylistRepository: UserPlaylistRepository,
    private val playbackPreferences: PlaybackPreferences,
    private val contentFilter: ContentFilter,
    private val networkStateProvider: NetworkStateProvider,
    private val json: Json
) : PlaybackRepository {

    override fun getSongPlaybackInfo(songId: Long): Flow<Result<SongPlaybackInfo>> = apiFlow(
        request = {
            val quality = if (networkStateProvider.isWifiConnected()) {
                settingsPreferences.wifiQuality.first()
            } else {
                settingsPreferences.mobileQuality.first()
            }
            apiService.getSongUrl(body = SongUrlRequest(ids = "[$songId]", level = quality))
        },
        // 该歌曲可能需要开启 VIP 或版权受限：code=200 但 url 为空，也算失败
        isSuccess = { it.isSuccess && !it.data.firstOrNull()?.url.isNullOrBlank() },
        code = { it.code },
        transform = { response ->
            val item = response.data.first()
            SongPlaybackInfo(
                url = item.url.orEmpty(),
                isFreeTrial = item.isTrialAudio,
                level = item.level?.takeIf { it.isNotBlank() }
            )
        }
    )

    override fun getSongUrl(songId: Long): Flow<Result<String>> =
        getSongPlaybackInfo(songId).map { result -> result.map { it.url } }

    override fun getLyrics(songId: Long): Flow<Result<List<LyricLine>>> = apiFlow(
        request = {
            apiService.getLyrics(
                LyricRequest(id = songId, tv = -1, lv = -1, rv = -1, kv = -1, ytv = -1, yrv = -1)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            when {
                // 纯音乐返回带标识的单行；未收录返回空列表以隐藏卡片
                response.nolyric -> listOf(LyricLine(timeMs = 0, text = "纯音乐"))
                response.uncollected -> emptyList()
                else -> {
                    val yrcText = response.yrc?.lyric
                    val lrcText = response.lrc?.lyric
                    // 只识别独立的纯音乐标识，制作人员信息中的 Instrumental 不能覆盖正文。
                    val isInstrumental = isInstrumentalLyrics(yrcText) || isInstrumentalLyrics(lrcText)
                    if (isInstrumental) {
                        listOf(LyricLine(timeMs = 0, text = "纯音乐"))
                    } else {
                        val lines = if (!yrcText.isNullOrBlank()) {
                            val parsedYrc = LyricParser.parseYrc(yrcText)
                            if (parsedYrc.isNotEmpty()) parsedYrc else LyricParser.parseLrc(lrcText ?: "")
                        } else {
                            LyricParser.parseLrc(lrcText ?: "")
                        }
                        if (lines.isEmpty()) {
                            emptyList()
                        } else {
                            // 解析翻译歌词列表（优先使用 ytlrc，其次使用 tlyric）
                            val translationLines = LyricParser.parseLrc(response.ytlrc?.lyric ?: response.tlyric?.lyric ?: "")
                            // 解析罗马音歌词列表
                            val romaLines = LyricParser.parseLrc(response.romalrc?.lyric ?: "")
                            lines.map { line ->
                                // 寻找在 150ms 内与原词时间戳最接近的翻译行
                                val matchedTranslation = translationLines
                                    .filter { kotlin.math.abs(it.timeMs - line.timeMs) < 150 }
                                    .minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
                                    ?.text
                                // 寻找在 150ms 内与原词时间戳最接近的罗马音行
                                val matchedRoma = romaLines
                                    .filter { kotlin.math.abs(it.timeMs - line.timeMs) < 150 }
                                    .minByOrNull { kotlin.math.abs(it.timeMs - line.timeMs) }
                                    ?.text
                                line.copy(translation = matchedTranslation, roma = matchedRoma)
                            }
                        }
                    }
                }
            }
        }
    )

    override fun getRawLyrics(songId: Long): Flow<Result<String>> = apiFlow(
        request = {
            apiService.getLyrics(
                LyricRequest(id = songId, tv = -1, lv = -1, rv = -1, kv = -1, ytv = -1, yrv = -1)
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            when {
                response.nolyric -> "纯音乐"
                response.uncollected -> ""
                else -> {
                    val yrc = response.yrc?.lyric?.trim()?.takeIf { it.isNotEmpty() }
                    val lrc = response.lrc?.lyric?.trim()?.takeIf { it.isNotEmpty() }
                    val isInstrumental = isInstrumentalLyrics(yrc) || isInstrumentalLyrics(lrc)
                    if (isInstrumental) {
                        "纯音乐"
                    } else {
                        yrc ?: lrc ?: ""
                    }
                }
            }
        }
    )

    override fun getSongDetail(songId: Long): Flow<Result<Track>> = apiFlow(
        request = { apiService.getSongDetail(SongDetailRequest(c = """[{"id":$songId}]""")) },
        isSuccess = { it.isSuccess && it.songs.isNotEmpty() },
        code = { it.code },
        transform = { it.songs[0] }
    )

    override fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>> = apiFlow(
        request = { apiService.getSimiSongs(SimiSongRequest(songid = songId.toString())) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { contentFilter.filterBlockedArtists(it.songs) { song -> song.ar.map { a -> a.id } } }
    )

    // 未指定歌单上下文时（如首页心动模式入口），取当前歌单列表首位
    override fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>> = apiFlow(
        request = {
            val finalPlaylistId = if (playlistId != 0L) {
                playlistId
            } else {
                val uid = userPreferences.userProfile.first()?.uid
                    ?: throw AppError.BizError(-1, null)
                val playlists = userPlaylistRepository.getUserPlaylists(uid, limit = 1000)
                    .first().getOrThrow()
                pickIntelligencePlaylist(playlists)?.id
                    ?: throw AppError.BizError(-1, null)
            }
            apiService.getIntelligenceSongs(
                IntelligenceSongsRequest(
                    songId = songId.toString(),
                    playlistId = finalPlaylistId.toString(),
                    startMusicId = songId.toString(),
                    count = 20
                )
            )
        },
        isSuccess = { response -> response.isSuccess && response.data.any { it.songInfo != null } },
        code = { it.code },
        transform = { response ->
            contentFilter.filterBlockedArtists(response.data.mapNotNull { it.songInfo }) { it.ar.map { a -> a.id } }
        }
    )

    private val _playlistRecorded = MutableSharedFlow<Long>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val playlistRecorded: SharedFlow<Long> = _playlistRecorded.asSharedFlow()

    // 上一次已记录的歌单，同一歌单连续切歌不重复记录
    private var lastRecordedPlaylistId: Long = 0L

    // 歌单来源用歌单 id 作容器，专辑与无来源仍用 songId
    private fun containerIdOf(songId: Long, source: PlaySource?): Long =
        source?.takeIf { it.kind == PlaySource.Kind.PLAYLIST }?.id ?: songId

    // 服务端不记录本客户端的歌单播放，改在本地记录，记录完成后通知首页刷新
    private suspend fun recordPlaylistPlay(source: PlaySource) {
        runCatching {
            playbackPreferences.recordRecentPlaylist(
                LocalRecentPlaylist(source.id, source.name, source.coverUrl, System.currentTimeMillis())
            )
        }.onSuccess { _playlistRecorded.tryEmit(source.id) }
            .onFailure { AppLogger.w(TAG, "记录本地最近播放歌单失败 playlistId=${source.id}", it) }
    }

    // 歌曲开始播放时立即上报，进「最近播放」
    override fun reportStartPlay(songId: Long, source: PlaySource?): Flow<Result<Unit>> = flow {
        val containerId = containerIdOf(songId, source)
        val startplayLogs = json.encodeToString(
            listOf(StartPlayLogEntry(json = StartPlayLogJson(id = songId, content = "id=$containerId")))
        )
        if (source?.kind == PlaySource.Kind.PLAYLIST && source.id != lastRecordedPlaylistId) {
            lastRecordedPlaylistId = source.id
            recordPlaylistPlay(source)
        }
        val startRes = apiService.reportWeblog(WeblogRequest(logs = startplayLogs))
        AppLogger.i(TAG, "打卡上报 startplay 返回: songId=$songId container=$containerId code=${startRes.code} data=${startRes.data}")

        if (startRes.isSuccess) {
            emit(Result.success(Unit))
        } else {
            emit(Result.failure(AppError.BizError(startRes.code, startRes.data)))
        }
    }.catch { e ->
        AppLogger.e(TAG, "打卡上报 startplay 请求异常", e)
        emit(Result.failure(mapToAppError(e)))
    }

    // 离开歌曲（切歌/退出播放器）时上报实际播放时长，涨「听歌排行」计数
    override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?): Flow<Result<Unit>> = flow {
        val containerId = containerIdOf(songId, source)
        val playLogs = json.encodeToString(
            listOf(
                PlayLogEntry(
                    json = PlayLogJson(
                        id = songId,
                        sourceId = containerId,
                        time = playedSeconds,
                        content = "id=$containerId"
                    )
                )
            )
        )
        val playRes = apiService.reportWeblog(WeblogRequest(logs = playLogs))
        AppLogger.i(TAG, "打卡上报 play 返回: songId=$songId time=$playedSeconds code=${playRes.code} data=${playRes.data}")

        if (playRes.isSuccess) {
            emit(Result.success(Unit))
        } else {
            emit(Result.failure(AppError.BizError(playRes.code, playRes.data)))
        }
    }.catch { e ->
        AppLogger.e(TAG, "打卡上报 play 请求异常", e)
        emit(Result.failure(mapToAppError(e)))
    }
}

internal fun isInstrumentalLyrics(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val lines = LyricParser.parseYrc(text).ifEmpty { LyricParser.parseLrc(text) }
        .map { it.text.trim() }.filter { it.isNotEmpty() }
    return lines.isNotEmpty() && lines.all {
        it.equals("Instrumental", ignoreCase = true) ||
            it == "纯音乐" || it == "纯音乐，请欣赏" || it == "纯音乐,请欣赏"
    }
}
