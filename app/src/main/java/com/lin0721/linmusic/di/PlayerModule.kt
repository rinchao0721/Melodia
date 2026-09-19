package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.external.ExternalLyricCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val playerModule = module {
    single { PlayerManager(androidContext(), get(), get(), get(), get(), get(), get()) }
    single { ExternalLyricCoordinator(androidContext(), get(), get(), get()) }
}

