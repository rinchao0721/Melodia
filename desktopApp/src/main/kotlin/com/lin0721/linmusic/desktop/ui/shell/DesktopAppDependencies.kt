package com.lin0721.linmusic.desktop.ui.shell

import com.lin0721.linmusic.core.plugin.PluginRepository
import androidx.lifecycle.ViewModel
import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.auth.SessionMonitor
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.update.UpdateChecker
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.data.download.DesktopSongDownloader
import com.lin0721.linmusic.desktop.data.recognition.DesktopRecognition
import com.lin0721.linmusic.desktop.platform.BrowserLocator
import com.lin0721.linmusic.desktop.player.DesktopPlaybackController
import com.lin0721.linmusic.desktop.ui.cloud.CloudMusicActions
import com.lin0721.linmusic.desktop.ui.local.LocalMusicActions
import com.lin0721.linmusic.desktop.ui.settings.DesktopSettingsViewModel
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.music.ui.MusicViewModel
import com.lin0721.linmusic.feature.newworks.ui.NewWorksViewModel
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.podcast.ui.PodcastHomeViewModel
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import com.lin0721.linmusic.feature.settings.ui.CommonSettingsViewModel
import com.lin0721.linmusic.feature.settings.ui.LxPluginSettingsViewModel
import kotlin.reflect.KClass

// 页面专属 ViewModel 的创建入口，每个栈帧各取一份
interface PageViewModelFactory {
    fun <T : ViewModel> create(modelClass: KClass<T>): T
}

// 队列等面板里收藏、喜欢歌曲用到的仓储
class SongActionSources(
    val likes: SongLikeRepository,
    val collect: SongCollectDelegate
)

// 应用壳需要的全部依赖，启动时构造一次后显式向下传递
class DesktopAppDependencies(
    val homeViewModel: HomeViewModel,
    val musicViewModel: MusicViewModel,
    val podcastViewModel: PodcastHomeViewModel,
    val newWorksViewModel: NewWorksViewModel,
    val libraryViewModel: LibraryViewModel,
    val loginViewModel: LoginViewModel,
    val playerViewModel: PlayerViewModel,
    val searchViewModel: SearchViewModel,
    val commonSettingsViewModel: CommonSettingsViewModel,
    val desktopSettingsViewModel: DesktopSettingsViewModel,
    val lxPluginSettingsViewModel: LxPluginSettingsViewModel,
    val plugins: PluginRepository,
    val playbackController: DesktopPlaybackController,
    val downloader: DesktopSongDownloader,
    val desktopPreferences: DesktopPreferences,
    val settingsPreferences: SettingsPreferences,
    val sessionMonitor: SessionMonitor,
    val browserLocator: BrowserLocator,
    val updateChecker: UpdateChecker,
    val songActions: SongActionSources,
    val localMusic: LocalMusicActions,
    val cloudMusic: CloudMusicActions,
    val recognition: DesktopRecognition,
    val pageViewModels: PageViewModelFactory
)
