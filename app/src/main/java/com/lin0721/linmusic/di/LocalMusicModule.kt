package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.localmusic.ImportedMusicPreferences
import com.lin0721.linmusic.core.localmusic.LocalCoverArtCache
import com.lin0721.linmusic.core.localmusic.LocalMusicImporter
import com.lin0721.linmusic.core.localmusic.LocalMusicRepository
import org.koin.dsl.module

val localMusicModule = module {
    single { ImportedMusicPreferences(context = get()) }
    single { LocalMusicImporter(context = get(), importedMusicPreferences = get()) }
    single { LocalMusicRepository(context = get(), downloadPreferences = get(), importedMusicPreferences = get()) }
    single { LocalCoverArtCache(context = get()) }
}
