package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.AuthRepositoryImpl
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.comment.data.CommentRepositoryImpl
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStore
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStoreImpl
import com.lin0721.linmusic.core.player.data.PlaybackRepositoryImpl
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepositoryImpl
import com.lin0721.linmusic.core.userartist.UserArtistRepositoryImpl
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepositoryImpl
import com.lin0721.linmusic.feature.artist.data.ArtistRepositoryImpl
import com.lin0721.linmusic.feature.cloud.data.CloudRepositoryImpl
import com.lin0721.linmusic.feature.create.data.CreateRepositoryImpl
import com.lin0721.linmusic.feature.home.data.HomeRepositoryImpl
import com.lin0721.linmusic.feature.library.data.LibraryRepositoryImpl
import com.lin0721.linmusic.feature.listendata.data.ListenDataRepositoryImpl
import com.lin0721.linmusic.feature.music.data.MusicRepositoryImpl
import com.lin0721.linmusic.feature.newworks.data.NewWorksRepositoryImpl
import com.lin0721.linmusic.feature.player.data.PlayerRepositoryImpl
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepositoryImpl
import com.lin0721.linmusic.feature.playlist.domain.CreatePlaylistAndAddSongUseCase
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.playlist.domain.UpdatePlaylistCoverUseCase
import com.lin0721.linmusic.feature.podcast.data.PodcastRepositoryImpl
import com.lin0721.linmusic.feature.profile.data.ProfileRepositoryImpl
import com.lin0721.linmusic.feature.recent.data.RecentRepositoryImpl
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import com.lin0721.linmusic.feature.search.data.SearchRepositoryImpl
import com.lin0721.linmusic.feature.settings.data.SettingsRepositoryImpl
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files

// 共享层只靠平台补齐这几项就能在纯 JVM 上组装完整的网络与仓储依赖
class SharedModulesResolveTest {

    @Test
    fun `网络与仓储模块在桌面端可完整解析`() {
        val dir: File = Files.createTempDirectory("melodia-prefs").toFile()
        fun store(name: String) = PreferencesStores.get(File(dir, "$name.preferences_pb"))
        val platformModule = module {
            single { UserPreferences(store(PreferencesStores.USER)) }
            single { SettingsPreferences(store(PreferencesStores.SETTINGS)) }
            single { SearchHistoryPreferences(store(PreferencesStores.SEARCH_HISTORY)) }
            single<XeapiKeyStore> { XeapiKeyStoreImpl(store(PreferencesStores.XEAPI_KEY)) }
            single { ContentFilter(get()) }
            single { ResourceProvider() }
            single<NetworkStateProvider> { NetworkStateProvider { true } }
        }
        val koin = koinApplication { modules(platformModule, networkModule, repositoryModule) }.koin
        try {
            listOf(
                AuthRepositoryImpl::class, HomeRepositoryImpl::class, MusicRepositoryImpl::class,
                PodcastRepositoryImpl::class, SearchRepositoryImpl::class, LibraryRepositoryImpl::class,
                RecentRepositoryImpl::class, CloudRepositoryImpl::class, ListenDataRepositoryImpl::class,
                NewWorksRepositoryImpl::class, ArtistRepositoryImpl::class, CommentRepositoryImpl::class,
                SongLikeRepositoryImpl::class, UserPlaylistRepositoryImpl::class, UserArtistRepositoryImpl::class,
                PlaylistRepositoryImpl::class, PlaybackRepositoryImpl::class, PlayerRepositoryImpl::class,
                SettingsRepositoryImpl::class, CreateRepositoryImpl::class, CreatePlaylistAndAddSongUseCase::class,
                UpdatePlaylistCoverUseCase::class, SongCollectDelegate::class, SyncProfileAfterLoginUseCase::class,
                LoadLikedSongIdsUseCase::class, PlaylistMutationBus::class, ProfileRepositoryImpl::class,
            ).forEach { assertNotNull(it.simpleName, koin.get<Any>(it)) }
        } finally {
            koin.close()
            dir.deleteRecursively()
        }
    }
}
