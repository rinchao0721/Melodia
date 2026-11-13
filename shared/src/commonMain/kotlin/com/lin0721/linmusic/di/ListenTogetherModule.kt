package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.listentogether.ListenTogetherSession
import com.lin0721.linmusic.core.listentogether.RoomTrackResolver
import com.lin0721.linmusic.core.listentogether.data.ListenTogetherApi
import com.lin0721.linmusic.core.listentogether.data.ListenTogetherRepository
import com.lin0721.linmusic.core.listentogether.data.ListenTogetherRepositoryImpl
import com.lin0721.linmusic.core.listentogether.data.RoomSongSource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.core.module.Module
import org.koin.dsl.module
import retrofit2.Retrofit

// 一起听依赖注入；playerDispatcher 由各端给出可安全操作播放器的调度器（Android 为 Main）
fun listenTogetherModule(playerDispatcher: CoroutineDispatcher): Module = module {
    single<ListenTogetherApi> { get<Retrofit>().create(ListenTogetherApi::class.java) }
    single<ListenTogetherRepository> { ListenTogetherRepositoryImpl(get()) }
    single<RoomTrackResolver> {
        val playerRepository = get<PlayerRepository>()
        RoomTrackResolver { songIds ->
            if (songIds.isEmpty()) {
                emptyList()
            } else {
                playerRepository.getSongDetails(songIds).first().getOrNull().orEmpty()
                    .filter { it.id > 0L }
                    .map { it.toQueueItem() }
            }
        }
    }
    // 点歌来源：喜欢列表按红心时间倒序取前若干首补详情；搜索走云搜索单曲
    single<RoomSongSource> {
        val playerRepository = get<PlayerRepository>()
        val songLikeRepository = get<SongLikeRepository>()
        val searchRepository = get<SearchRepository>()
        val userPreferences = get<UserPreferences>()
        object : RoomSongSource {
            override suspend fun liked(limit: Int): List<QueueItem> {
                val uid = userPreferences.userProfile.first()?.uid ?: return emptyList()
                val ids = songLikeRepository.getLikedSongIds(uid).first().getOrThrow().filter { it > 0L }.take(limit)
                if (ids.isEmpty()) return emptyList()
                val tracks = playerRepository.getSongDetails(ids).first().getOrThrow().associateBy { it.id }
                return ids.mapNotNull { tracks[it] }.map { it.toQueueItem() }
            }

            override suspend fun search(keyword: String, limit: Int): List<QueueItem> =
                searchRepository.search(keyword, SearchType.SONG, limit = limit).first().getOrThrow().items
                    .filterIsInstance<SearchResultItem.SongItem>()
                    .map { it.track.toQueueItem() }
        }
    }
    single {
        val userPreferences = get<UserPreferences>()
        ListenTogetherSession(
            repository = get(),
            controller = get<PlaybackController>(),
            resolver = get(),
            selfUserId = { userPreferences.userProfile.first()?.uid ?: 0L },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            playerDispatcher = playerDispatcher
        )
    }
}

private fun Track.toQueueItem() = QueueItem(id, name, ar.joinToString("/") { it.name }, al.picUrl)
