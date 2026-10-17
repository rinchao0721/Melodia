package com.lin0721.linmusic.di

import androidx.room.Room
import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.feature.localmusic.data.LocalCoverArtCache
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.feature.localmusic.data.LocalMusicApiImpl
import com.lin0721.linmusic.feature.localmusic.data.LocalMusicSettings
import com.lin0721.linmusic.feature.localmusic.data.PreferredLocalAudioRepository
import com.lin0721.linmusic.feature.localmusic.data.LocalAlbumAssociationStore
import com.lin0721.linmusic.feature.localmusic.data.batch.LocalMatchCoverRepository
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchLocalMatchRepository
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchAlbumMatchStore
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchAlbumDetailApi
import com.lin0721.linmusic.feature.localmusic.data.batch.BatchAlbumOnlineRepository
import retrofit2.Retrofit
import com.lin0721.linmusic.feature.localmusic.data.LocalPlaylistRepository
import com.lin0721.linmusic.feature.localmusic.data.db.LocalMusicDatabase
import com.lin0721.linmusic.feature.localmusic.data.lyrics.LocalLyricsReader
import com.lin0721.linmusic.feature.localmusic.data.legacy.LegacyImportedMusicStore
import com.lin0721.linmusic.feature.localmusic.data.scan.LocalMusicImporter
import com.lin0721.linmusic.feature.localmusic.data.tags.LocalTagEditor
import com.lin0721.linmusic.feature.localmusic.data.scan.MediaStoreScanner
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val localMusicModule = module {
    single {
        Room.databaseBuilder(androidContext(), LocalMusicDatabase::class.java, LocalMusicDatabase.NAME).build()
    }
    single { get<LocalMusicDatabase>().localTrackDao() }
    single { get<LocalMusicDatabase>().localPlaylistDao() }
    single { LocalPlaylistRepository(dao = get()) }
    single { LegacyImportedMusicStore(androidContext()) }
    single { LocalMusicSettings(androidContext()) }
    single { MediaStoreScanner(androidContext()) }
    single { LocalMusicImporter(androidContext()) }
    single {
        LocalLibraryRepository(
            context = androidContext(),
            dao = get(),
            scanner = get(),
            importer = get(),
            downloadPreferences = get(),
            legacyImportedStore = get(),
            settings = get(),
            coverArtCache = get()
        )
    }
    single { LocalCoverArtCache(androidContext()) }
    single { LocalLyricsReader(androidContext(), dao = get()) }
    single { LocalTagEditor(androidContext()) }
    single { PreferredLocalAudioRepository(androidContext(), library = get()) }
    single { LocalAlbumAssociationStore(androidContext()) }
    single { BatchAlbumMatchStore(androidContext()) }
    single<BatchAlbumDetailApi> { get<Retrofit>().create(BatchAlbumDetailApi::class.java) }
    single { BatchAlbumOnlineRepository(search = get(), api = get(), contentFilter = get()) }
    single { LocalMatchCoverRepository(playerRepository = get()) }
    single { BatchLocalMatchRepository(androidContext(), preferredAudio = get(), search = get(), covers = get(), library = get()) }
    single<LocalMusicApi> { LocalMusicApiImpl(coverArtCache = get(), lyricsReader = get(), preferredAudio = get()) }
}
