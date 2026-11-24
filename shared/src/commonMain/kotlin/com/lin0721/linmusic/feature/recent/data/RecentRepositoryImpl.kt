package com.lin0721.linmusic.feature.recent.data

import com.lin0721.linmusic.core.cache.OfflineFallback
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import com.lin0721.linmusic.feature.recent.domain.formatClockTime
import com.lin0721.linmusic.feature.recent.domain.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

// 服务端忽略 limit 一次下发全量（歌曲曾实测 300 条 / 460KB），列表侧统一截断
private const val MAX_RECORDS = 100

class RecentRepositoryImpl(
    private val apiService: RecentApi,
    private val playbackPreferences: PlaybackPreferences,
    private val offline: OfflineFallback
) : RecentRepository {

    override fun getRecentSongs(): Flow<Result<List<RecentSong>>> = apiFlow(
        request = { apiService.getRecentSongs() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { response -> response.data!!.list.take(MAX_RECORDS).map { it.toDomain() } }
    )

    private fun remoteRecentPlaylists(): Flow<Result<List<RecentPlaylist>>> =
        offline.cached("recent_playlists_remote") {
            apiFlow(
                request = { apiService.getRecentPlaylists() },
                isSuccess = { it.isSuccess && it.data != null },
                code = { it.code },
                transform = { response -> response.data!!.list.take(MAX_RECORDS).map { it.toDomain() } }
            )
        }

    // 服务端不记录本客户端的歌单播放，本地记录与服务端列表按播放时间合并去重
    override fun getRecentPlaylists(): Flow<Result<List<RecentPlaylist>>> = flow {
        val local = runCatching { playbackPreferences.recentPlaylists.first() }
            .getOrDefault(emptyList())
            .filter { it.platform == MusicPlatform.NETEASE }
            .map {
                RecentPlaylist(
                    id = it.id,
                    name = it.name,
                    coverUrl = it.coverUrl,
                    creatorName = "",
                    playTime = it.playTime,
                    playedAtText = formatClockTime(it.playTime)
                )
            }
        val remote = remoteRecentPlaylists().first()
        if (remote.isFailure && local.isEmpty()) {
            emit(remote)
            return@flow
        }
        val merged = (local + remote.getOrDefault(emptyList()))
            .sortedByDescending { it.playTime }
            .distinctBy { it.id }
            .take(MAX_RECORDS)
        emit(Result.success(merged))
    }

    override fun getRecentAlbums(): Flow<Result<List<RecentAlbum>>> = apiFlow(
        request = { apiService.getRecentAlbums() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { response -> response.data!!.list.take(MAX_RECORDS).map { it.toDomain() } }
    )
}
