package com.lin0721.linmusic.di

import androidx.room.Room
import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.feature.localmusic.data.LocalCoverArtCache
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.feature.localmusic.data.LocalMusicApiImpl
import com.lin0721.linmusic.feature.localmusic.data.LocalMusicSettings
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
        Room.databaseBuilder(androidContext(), LocalMusicDatabase::class.java, LocalMusicDatabase.NAME)
            .addMigrations(LocalMusicDatabase.MIGRATION_5_6)
            .build()
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
    single<LocalMusicApi> { LocalMusicApiImpl(coverArtCache = get(), lyricsReader = get()) }
}
