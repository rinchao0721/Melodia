package com.lin0721.linmusic.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.CrossfadePolicy
import com.lin0721.linmusic.core.preferences.FullPlayerCardLayout
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.ThemeMode
import com.lin0721.linmusic.core.preferences.ThemeSettings
import com.lin0721.linmusic.core.preferences.UiScale
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.core.ui.skin.SkinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 各端共有的本地设置项；平台专属的设置由各端自己的 ViewModel 负责
class CommonSettingsViewModel(
    private val settingsPreferences: SettingsPreferences,
    private val sourcePreferences: SourcePreferences,
    val skins: SkinRepository
) : ViewModel() {

    private fun <T> Flow<T>.asState(initial: T): StateFlow<T> = stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = initial
    )

    private fun launchSave(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    val wifiQuality = settingsPreferences.wifiQuality.asState("lossless")
    val crossfadeEnabled = settingsPreferences.crossfadeEnabled.asState(false)
    val crossfadeDurationMs = settingsPreferences.crossfadeDurationMs.asState(CrossfadePolicy.DEFAULT_DURATION_MS)
    val smartCrossfadeEnabled = settingsPreferences.smartCrossfadeEnabled.asState(false)
    val loudnessNormalizationEnabled = settingsPreferences.loudnessNormalizationEnabled.asState(false)
    val uiScalePercent = settingsPreferences.uiScalePercent.asState(UiScale.DEFAULT_PERCENT)
    val uiAnimationsEnabled = settingsPreferences.uiAnimationsEnabled.asState(true)
    val themeSettings = settingsPreferences.themeSettings.asState(ThemeSettings())

    val searchAggregationEnabled = sourcePreferences.searchAggregationEnabled.asState(false)
    val fallbackEnabled = sourcePreferences.fallbackEnabled.asState(false)
    val communityPriority = sourcePreferences.communityPriority.asState(false)
    val unmRemoteFallbackEnabled = sourcePreferences.unmRemoteFallbackEnabled.asState(false)
    val unmServerUrl = sourcePreferences.unmServerUrl.asState("")
    val unmAutoMatch = sourcePreferences.unmAutoMatch.asState(true)

    val streamCacheEnabled = settingsPreferences.streamCacheEnabled.asState(false)
    val audioCacheMaxSize = settingsPreferences.audioCacheMaxSize.asState(500 * 1024 * 1024L)

    val downloadFolderUri = settingsPreferences.downloadFolderUri.asState(null)
    val downloadLyricsEnabled = settingsPreferences.downloadLyricsEnabled.asState(true)

    val defaultPlaylistPrivate = settingsPreferences.defaultPlaylistPrivate.asState(false)
    val showDesktopLrc = settingsPreferences.showDesktopLrc.asState(false)
    val fullPlayerCardLayout: StateFlow<List<FullPlayerCardSetting>> =
        settingsPreferences.fullPlayerCardLayout.asState(FullPlayerCardLayout.DEFAULT)

    val logLevel = settingsPreferences.logLevel.asState(AppLogger.LogLevel.WARN.name)
    val autoCheckUpdateEnabled = settingsPreferences.autoCheckUpdateEnabled.asState(true)
    val allowPrereleaseChannel = settingsPreferences.allowPrereleaseChannel.asState(false)

    fun updateWifiQuality(quality: String) = launchSave { settingsPreferences.saveWifiQuality(quality) }

    fun updateCrossfadeEnabled(enabled: Boolean) = launchSave { settingsPreferences.saveCrossfadeEnabled(enabled) }

    fun updateCrossfadeDurationMs(durationMs: Int) = launchSave { settingsPreferences.saveCrossfadeDurationMs(durationMs) }

    fun updateSmartCrossfadeEnabled(enabled: Boolean) = launchSave { settingsPreferences.saveSmartCrossfadeEnabled(enabled) }

    fun updateLoudnessNormalizationEnabled(enabled: Boolean) = launchSave { settingsPreferences.saveLoudnessNormalizationEnabled(enabled) }

    fun updateUiScalePercent(percent: Int) = launchSave { settingsPreferences.saveUiScalePercent(percent) }

    fun updateUiAnimationsEnabled(enabled: Boolean) = launchSave { settingsPreferences.saveUiAnimationsEnabled(enabled) }

    fun updateThemeMode(mode: ThemeMode) = launchSave { settingsPreferences.saveThemeMode(mode) }

    fun updateThemeAccent(isDark: Boolean, accentId: String) = launchSave { settingsPreferences.saveThemeAccent(isDark, accentId) }

    fun updateThemeCustomColor(isDark: Boolean, hex: String) = launchSave { settingsPreferences.saveThemeCustomColor(isDark, hex) }

    fun updateSearchAggregationEnabled(enabled: Boolean) = launchSave { sourcePreferences.saveSearchAggregationEnabled(enabled) }

    fun updateFallbackEnabled(enabled: Boolean) = launchSave { sourcePreferences.saveFallbackEnabled(enabled) }

    fun updateCommunityPriority(enabled: Boolean) = launchSave { sourcePreferences.saveCommunityPriority(enabled) }

    fun updateUnmRemoteFallbackEnabled(enabled: Boolean) = launchSave { sourcePreferences.saveUnmRemoteFallbackEnabled(enabled) }

    fun updateUnmServerUrl(url: String) = launchSave { sourcePreferences.saveUnmServerUrl(url) }

    fun resetUnmServerUrl() = launchSave { sourcePreferences.resetUnmServerUrl() }

    fun updateUnmAutoMatch(enabled: Boolean) = launchSave { sourcePreferences.saveUnmAutoMatch(enabled) }

    fun updateStreamCacheEnabled(enabled: Boolean) = launchSave { settingsPreferences.saveStreamCacheEnabled(enabled) }

    fun updateAudioCacheMaxSize(size: Long) = launchSave { settingsPreferences.saveAudioCacheMaxSize(size) }

    // null 表示恢复默认目录
    fun updateDownloadFolderUri(uri: String?) = launchSave { settingsPreferences.saveDownloadFolderUri(uri) }

    fun updateDownloadLyricsEnabled(enabled: Boolean) = launchSave { settingsPreferences.saveDownloadLyricsEnabled(enabled) }

    fun updateDefaultPlaylistPrivate(private: Boolean) = launchSave { settingsPreferences.saveDefaultPlaylistPrivate(private) }

    fun updateShowDesktopLrc(enabled: Boolean) = launchSave { settingsPreferences.saveShowDesktopLrc(enabled) }

    fun updateFullPlayerCardLayout(layout: List<FullPlayerCardSetting>) = launchSave {
        settingsPreferences.saveFullPlayerCardLayout(layout)
    }

    // 立即生效，同时持久化供下次启动读取
    fun updateAutoCheckUpdateEnabled(enabled: Boolean) = launchSave { settingsPreferences.saveAutoCheckUpdateEnabled(enabled) }

    fun updateAllowPrereleaseChannel(enabled: Boolean) = launchSave { settingsPreferences.saveAllowPrereleaseChannel(enabled) }

    fun updateLogLevel(level: AppLogger.LogLevel) {
        AppLogger.setLevel(level)
        launchSave { settingsPreferences.saveLogLevel(level.name) }
    }
}
