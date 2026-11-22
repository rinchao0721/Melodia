package com.lin0721.linmusic.desktop.di

import com.lin0721.linmusic.desktop.data.download.DesktopDownloadPreferences
import com.lin0721.linmusic.desktop.ui.shell.DesktopAppDependencies
import com.lin0721.linmusic.desktop.ui.shell.PageViewModelFactory
import com.lin0721.linmusic.desktop.ui.shell.SongActionSources
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.Koin
import kotlin.reflect.KClass

// 界面层只从这里拿依赖；页面专属 ViewModel 也经由这里的工厂按需创建
fun Koin.desktopAppDependencies(): DesktopAppDependencies {
    val koin = this
    return DesktopAppDependencies(
        homeViewModel = get(),
        musicViewModel = get(),
        podcastViewModel = get(),
        newWorksViewModel = get(),
        libraryViewModel = get(),
        loginViewModel = get(),
        kugouLoginViewModel = get(),
        kugouConfig = get(),
        playerViewModel = get(),
        searchViewModel = get(),
        commonSettingsViewModel = get(),
        desktopSettingsViewModel = get(),
        lxPluginSettingsViewModel = get(),
        plugins = get(),
        pluginPages = get(),
        pluginCommands = get(),
        playerCards = get(),
        playbackController = get(),
        lyricsResolver = get(),
        downloader = get(),
        downloadScanner = get(),
        downloadedQualities = get<DesktopDownloadPreferences>().downloadedQualities
            .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, emptyMap()),
        desktopPreferences = get(),
        settingsPreferences = get(),
        sessionMonitor = get(),
        browserLocator = get(),
        updateChecker = get(),
        songActions = SongActionSources(likes = get(), collect = get()),
        localMusic = get(),
        cloudMusic = get(),
        recognition = get(),
        songQuality = get(),
        playbackRepository = get(),
        songLinkRequests = get(),
        linkPreviewResolver = get(),
        listenTogether = get(),
        privateUnreadMonitor = get(),
        coverColorMemory = get(),
        userPreferences = get(),
        accountSwitcher = get(),
        lanLink = get(),
        remoteCoordinator = get(),
        pageViewModels = object : PageViewModelFactory {
            override fun <T : ViewModel> create(modelClass: KClass<T>): T = koin.get(modelClass)
        }
    )
}
