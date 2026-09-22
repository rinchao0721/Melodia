package com.lin0721.linmusic.core.preferences

import android.content.Context
import com.lin0721.linmusic.BuildConfig
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "SettingsPreferences"

// 使用 preferencesDataStore 进行设置项持久化
private val Context.settingsDataStore by preferencesDataStore(
    name = "settings_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        AppLogger.e(TAG, "设置数据损坏，已重置为默认值", ex)
        emptyPreferences()
    }
)

class SettingsPreferences(private val context: Context) {

    companion object {
        // Wi-Fi 播放音质 KEY，默认 "lossless"
        private val KEY_WIFI_QUALITY = stringPreferencesKey("wifi_quality")
        // 移动网络播放音质 KEY，默认 "standard"
        private val KEY_MOBILE_QUALITY = stringPreferencesKey("mobile_quality")
        // 新建歌单是否默认设为隐私模式
        private val KEY_DEFAULT_PLAYLIST_PRIVATE = booleanPreferencesKey("default_playlist_private")
        // 默认搜索源
        private val KEY_DEFAULT_SEARCH_SOURCE = stringPreferencesKey("default_search_source")
        // 缓存开关 KEY，默认 false
        private val KEY_STREAM_CACHE_ENABLED = booleanPreferencesKey("stream_cache_enabled")
        // 音频缓存上限大小 KEY，默认 512MB
        private val KEY_AUDIO_CACHE_MAX_SIZE = longPreferencesKey("audio_cache_max_size")
        // 自定义下载目录（SAF tree Uri）
        private val KEY_DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_uri")
        // 下载时是否附带歌词
        private val KEY_DOWNLOAD_LYRICS_ENABLED = booleanPreferencesKey("download_lyrics_enabled")
        // 是否使用真实 IP 伪装，默认 false
        private val KEY_USE_REAL_IP = booleanPreferencesKey("use_real_ip")
        // 自定义真实 IP 值，默认空串 ""
        private val KEY_REAL_IP_VALUE = stringPreferencesKey("real_ip_value")

        // 自动播放推荐新歌，默认 true
        private val KEY_AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        // 与其他应用同时播放，默认 false
        private val KEY_PLAY_WITH_OTHER_APPS = booleanPreferencesKey("play_with_other_apps")
        // 外部音视频停止后自动恢复，默认 true
        private val KEY_RESUME_AFTER_EXTERNAL_INTERRUPTION = booleanPreferencesKey("resume_after_external_interruption")
        // 默认播放顺序，默认 "loop" (列表循环)
        // 仅 Wi-Fi 网络下联网播放，默认 false
        private val KEY_WIFI_ONLY_PLAY = booleanPreferencesKey("wifi_only_play")
        // 流量播放警告提示，默认 true
        private val KEY_MOBILE_ALERT = booleanPreferencesKey("mobile_alert")
        // 使用代理服务器，默认 false
        private val KEY_USE_PROXY = booleanPreferencesKey("use_proxy")
        // 启用桌面悬浮歌词，默认 false
        private val KEY_SHOW_DESKTOP_LRC = booleanPreferencesKey("show_desktop_lrc")
        // 启用系统锁屏显示，默认 true
        private val KEY_SHOW_LOCKSCREEN = booleanPreferencesKey("show_lockscreen")
        // 车载模式蓝牙自动启动，默认 false
        private val KEY_CAR_MODE = booleanPreferencesKey("car_mode")
        // 底栏是否显示创建歌单快捷入口，默认 true
        private val KEY_SHOW_CREATE_ENTRY = booleanPreferencesKey("show_create_entry")
        // 悬浮歌词字体大小，默认 14sp
        private val KEY_LYRIC_TEXT_SIZE = intPreferencesKey("lyric_text_size")
        // 悬浮歌词颜色，默认 "#FFFFFF"
        private val KEY_LYRIC_TEXT_COLOR = stringPreferencesKey("lyric_text_color")
        // 日志级别 KEY，默认 debug 包 "DEBUG"、release 包 "WARN"
        private val KEY_LOG_LEVEL = stringPreferencesKey("log_level")
        // 启动时自动检查更新，默认 true
        private val KEY_AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
        // 是否接收测试版（beta/rc）更新推送，默认 false 只接收正式版
        private val KEY_ALLOW_PRERELEASE_CHANNEL = booleanPreferencesKey("allow_prerelease_channel")
        // 用户主动忽略的更新版本 tag，默认空串表示未忽略任何版本
        private val KEY_IGNORED_UPDATE_TAG = stringPreferencesKey("ignored_update_tag")
        // 全屏歌词字体大小，默认 22sp
        private val KEY_FULL_SCREEN_LYRIC_TEXT_SIZE = intPreferencesKey("full_screen_lyric_text_size")
        // 全屏歌词对齐方式，默认 "left"
        private val KEY_FULL_SCREEN_LYRIC_ALIGNMENT = stringPreferencesKey("full_screen_lyric_alignment")
        // 全屏歌词是否显示双语翻译，默认 true
        private val KEY_FULL_SCREEN_LYRIC_SHOW_TRANSLATION = booleanPreferencesKey("full_screen_lyric_show_translation")
        // 全屏歌词副文本展示模式 ("translation", "roma", "none")，默认 "translation"
        private val KEY_FULL_SCREEN_LYRIC_SECONDARY_MODE = stringPreferencesKey("full_screen_lyric_secondary_mode")
        // 逐字歌词流光动效，默认 true
        private val KEY_FULL_SCREEN_KARAOKE_ADVANCED_EFFECT = booleanPreferencesKey("full_screen_karaoke_advanced_effect")
        // 启用 SuperLyric 实时歌词，默认 false
        private val KEY_SUPER_LYRIC_ENABLED = booleanPreferencesKey("super_lyric_enabled")
        // 启用 LyricInfo 系统歌词注入，默认 true
        private val KEY_LYRIC_INFO_ENABLED = booleanPreferencesKey("lyric_info_enabled")
        // 启用车载蓝牙歌词 (AVRCP)，默认 false
        private val KEY_BLUETOOTH_LYRIC_ENABLED = booleanPreferencesKey("bluetooth_lyric_enabled")
        // 启用 Lyricon 词幕协议，默认 false
        private val KEY_LYRICON_ENABLED = booleanPreferencesKey("lyricon_enabled")
    }

    // Wi-Fi 音质设置 Flow
    val wifiQuality: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_WIFI_QUALITY] ?: "lossless"
    }

    suspend fun saveWifiQuality(quality: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_WIFI_QUALITY] = quality
        }
    }

    // 移动网络音质设置 Flow
    val mobileQuality: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_MOBILE_QUALITY] ?: "standard"
    }

    suspend fun saveMobileQuality(quality: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_MOBILE_QUALITY] = quality
        }
    }

    // 默认隐私歌单配置 Flow
    val defaultPlaylistPrivate: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_PLAYLIST_PRIVATE] ?: false
    }

    suspend fun saveDefaultPlaylistPrivate(private: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_PLAYLIST_PRIVATE] = private
        }
    }

    // 默认搜索源 Flow
    val defaultSearchSource: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_SEARCH_SOURCE] ?: "netease"
    }

    suspend fun saveDefaultSearchSource(source: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_SEARCH_SOURCE] = source
        }
    }

    // 缓存开关设置 Flow
    val streamCacheEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_STREAM_CACHE_ENABLED] ?: false
    }

    suspend fun saveStreamCacheEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_STREAM_CACHE_ENABLED] = enabled
        }
    }

    // 自定义下载目录 Uri（SAF tree Uri 字符串），未设置时为 null，代表用默认的存储根目录 Melodia/
    val downloadFolderUri: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_FOLDER_URI]
    }

    suspend fun saveDownloadFolderUri(uri: String?) {
        context.settingsDataStore.edit { prefs ->
            if (uri != null) prefs[KEY_DOWNLOAD_FOLDER_URI] = uri
            else prefs.remove(KEY_DOWNLOAD_FOLDER_URI)
        }
    }

    // 下载歌词配置 Flow
    val downloadLyricsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_LYRICS_ENABLED] ?: false
    }

    suspend fun saveDownloadLyricsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_LYRICS_ENABLED] = enabled
        }
    }

    // 音频缓存最大容量设置 Flow
    val audioCacheMaxSize: Flow<Long> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_AUDIO_CACHE_MAX_SIZE] ?: (500 * 1024 * 1024L) // 默认 500MB
    }

    suspend fun saveAudioCacheMaxSize(size: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AUDIO_CACHE_MAX_SIZE] = size
        }
    }

    // 是否使用真实 IP 伪装 Flow
    val useRealIp: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_USE_REAL_IP] ?: false
    }

    suspend fun saveUseRealIp(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_USE_REAL_IP] = enabled
        }
    }

    // 自定义真实 IP 值 Flow
    val realIpValue: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_REAL_IP_VALUE] ?: ""
    }

    suspend fun saveRealIpValue(ip: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_REAL_IP_VALUE] = ip
        }
    }

    // 自动播放推荐新歌 Flow
    val autoPlayNext: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_AUTO_PLAY_NEXT] ?: true
    }

    suspend fun saveAutoPlayNext(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AUTO_PLAY_NEXT] = enabled
        }
    }

    // 与其他应用同时播放 Flow
    val playWithOtherApps: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_PLAY_WITH_OTHER_APPS] ?: false
    }

    suspend fun savePlayWithOtherApps(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_PLAY_WITH_OTHER_APPS] = enabled
        }
    }

    // 外部音视频停止后自动恢复 Flow
    val resumeAfterExternalInterruption: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_RESUME_AFTER_EXTERNAL_INTERRUPTION] ?: true
    }

    suspend fun saveResumeAfterExternalInterruption(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_RESUME_AFTER_EXTERNAL_INTERRUPTION] = enabled
        }
    }

    // 仅 Wi-Fi 网络下联网播放 Flow
    val wifiOnlyPlay: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_WIFI_ONLY_PLAY] ?: false
    }

    suspend fun saveWifiOnlyPlay(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_WIFI_ONLY_PLAY] = enabled
        }
    }

    // 流量播放警告提示 Flow
    val mobileAlert: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_MOBILE_ALERT] ?: true
    }

    suspend fun saveMobileAlert(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_MOBILE_ALERT] = enabled
        }
    }

    // 使用代理服务器 Flow
    val useProxy: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_USE_PROXY] ?: false
    }

    suspend fun saveUseProxy(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_USE_PROXY] = enabled
        }
    }

    // 启用桌面悬浮歌词 Flow
    val showDesktopLrc: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SHOW_DESKTOP_LRC] ?: false
    }

    suspend fun saveShowDesktopLrc(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SHOW_DESKTOP_LRC] = enabled
        }
    }

    // 启用系统锁屏显示 Flow
    val showLockscreen: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SHOW_LOCKSCREEN] ?: true
    }

    suspend fun saveShowLockscreen(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SHOW_LOCKSCREEN] = enabled
        }
    }

    // 车载模式蓝牙自动启动 Flow
    val carMode: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_CAR_MODE] ?: false
    }

    suspend fun saveCarMode(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_CAR_MODE] = enabled
        }
    }

    // 底栏创建歌单快捷入口 Flow
    val showCreateEntry: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SHOW_CREATE_ENTRY] ?: true
    }

    suspend fun saveShowCreateEntry(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SHOW_CREATE_ENTRY] = enabled
        }
    }

    // 悬浮歌词字号 Flow
    val lyricTextSize: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_LYRIC_TEXT_SIZE] ?: 14
    }

    suspend fun saveLyricTextSize(size: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LYRIC_TEXT_SIZE] = size
        }
    }

    // 悬浮歌词颜色 Flow
    val lyricTextColor: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_LYRIC_TEXT_COLOR] ?: "#FFFFFF"
    }

    suspend fun saveLyricTextColor(color: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LYRIC_TEXT_COLOR] = color
        }
    }

    // 日志级别 Flow，取值为 AppLogger.LogLevel 的 name（DEBUG/INFO/WARN/ERROR）
    val logLevel: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_LOG_LEVEL] ?: if (BuildConfig.DEBUG) "DEBUG" else "WARN"
    }

    suspend fun saveLogLevel(level: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LOG_LEVEL] = level
        }
    }

    // 启动时自动检查更新 Flow
    val autoCheckUpdateEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_AUTO_CHECK_UPDATE] ?: true
    }

    suspend fun saveAutoCheckUpdateEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AUTO_CHECK_UPDATE] = enabled
        }
    }

    // 接收测试版更新通道 Flow
    val allowPrereleaseChannel: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_ALLOW_PRERELEASE_CHANNEL] ?: false
    }

    suspend fun saveAllowPrereleaseChannel(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_ALLOW_PRERELEASE_CHANNEL] = enabled
        }
    }

    // 被用户忽略的更新版本 tag Flow
    val ignoredUpdateTag: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_IGNORED_UPDATE_TAG] ?: ""
    }

    suspend fun saveIgnoredUpdateTag(tag: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_IGNORED_UPDATE_TAG] = tag
        }
    }

    // 全屏歌词字号设置 Flow
    val fullScreenLyricTextSize: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_TEXT_SIZE] ?: 22
    }

    suspend fun saveFullScreenLyricTextSize(size: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_TEXT_SIZE] = size
        }
    }

    // 全屏歌词对齐方式 Flow
    val fullScreenLyricAlignment: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_ALIGNMENT] ?: "left"
    }

    suspend fun saveFullScreenLyricAlignment(alignment: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_ALIGNMENT] = alignment
        }
    }

    // 全屏歌词是否显示双语翻译 Flow
    val fullScreenLyricShowTranslation: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_SHOW_TRANSLATION] ?: true
    }

    suspend fun saveFullScreenLyricShowTranslation(show: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_SHOW_TRANSLATION] = show
        }
    }

    // 全屏歌词副文本展示模式 Flow（"translation"：翻译，"roma"：罗马音，"none"：仅原词）
    val fullScreenLyricSecondaryMode: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_SECONDARY_MODE] ?: if (prefs[KEY_FULL_SCREEN_LYRIC_SHOW_TRANSLATION] == false) "none" else "translation"
    }

    suspend fun saveFullScreenLyricSecondaryMode(mode: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_SECONDARY_MODE] = mode
            prefs[KEY_FULL_SCREEN_LYRIC_SHOW_TRANSLATION] = mode != "none"
        }
    }

    // 逐字歌词流光动效 Flow
    val fullScreenKaraokeAdvancedEffect: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_KARAOKE_ADVANCED_EFFECT] ?: true
    }

    suspend fun saveFullScreenKaraokeAdvancedEffect(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_KARAOKE_ADVANCED_EFFECT] = enabled
        }
    }

    // SuperLyric 实时歌词 Flow
    val superLyricEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SUPER_LYRIC_ENABLED] ?: false
    }

    suspend fun saveSuperLyricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SUPER_LYRIC_ENABLED] = enabled
        }
    }

    // LyricInfo 系统歌词注入 Flow
    val lyricInfoEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_LYRIC_INFO_ENABLED] ?: true
    }

    suspend fun saveLyricInfoEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LYRIC_INFO_ENABLED] = enabled
        }
    }

    // 车载蓝牙歌词 (AVRCP) Flow
    val bluetoothLyricEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_BLUETOOTH_LYRIC_ENABLED] ?: false
    }

    suspend fun saveBluetoothLyricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_BLUETOOTH_LYRIC_ENABLED] = enabled
        }
    }

    // Lyricon 词幕协议 Flow
    val lyriconEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_LYRICON_ENABLED] ?: false
    }

    suspend fun saveLyriconEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LYRICON_ENABLED] = enabled
        }
    }
}

