package com.lin0721.linmusic.desktop.di

import com.lin0721.linmusic.desktop.ui.shell.DesktopAppDependencies
import com.lin0721.linmusic.desktop.ui.shell.PageViewModelFactory
import com.lin0721.linmusic.desktop.ui.shell.SongActionSources
import androidx.lifecycle.ViewModel
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
        playerViewModel = get(),
        searchViewModel = get(),
        commonSettingsViewModel = get(),
        desktopSettingsViewModel = get(),
        lxPluginSettingsViewModel = get(),
        plugins = get(),
        pluginPages = get(),
        playerCards = get(),
        playbackController = get(),
        downloader = get(),
        desktopPreferences = get(),
        settingsPreferences = get(),
        sessionMonitor = get(),
        browserLocator = get(),
        updateChecker = get(),
        songActions = SongActionSources(likes = get(), collect = get()),
        localMusic = get(),
        cloudMusic = get(),
        recognition = get(),
        pageViewModels = object : PageViewModelFactory {
            override fun <T : ViewModel> create(modelClass: KClass<T>): T = koin.get(modelClass)
        }
    )
}
