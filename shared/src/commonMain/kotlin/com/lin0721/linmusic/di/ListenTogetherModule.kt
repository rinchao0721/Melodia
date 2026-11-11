package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.listentogether.ListenTogetherSession
import com.lin0721.linmusic.core.listentogether.RoomTrackResolver
import com.lin0721.linmusic.core.listentogether.data.ListenTogetherApi
import com.lin0721.linmusic.core.listentogether.data.ListenTogetherRepository
import com.lin0721.linmusic.core.listentogether.data.ListenTogetherRepositoryImpl
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
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
                    .map { QueueItem(it.id, it.name, it.ar.joinToString("/") { a -> a.name }, it.al.picUrl) }
            }
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
