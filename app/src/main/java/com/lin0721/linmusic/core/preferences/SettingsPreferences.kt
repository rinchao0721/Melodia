package com.lin0721.linmusic.core.preferences

import android.content.Context
import com.lin0721.linmusic.BuildConfig
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 使用 preferencesDataStore 进行设置项持久化
private val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

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
        // 缓存开关 KEY，默认 true
        private val KEY_STREAM_CACHE_ENABLED = booleanPreferencesKey("stream_cache_enabled")
        // 音频缓存上限大小 KEY，默认 512MB
        private val KEY_AUDIO_CACHE_MAX_SIZE = longPreferencesKey("audio_cache_max_size")
        // 是否使用真实 IP 伪装，默认 false
        private val KEY_USE_REAL_IP = booleanPreferencesKey("use_real_ip")
        // 自定义真实 IP 值，默认空串 ""
        private val KEY_REAL_IP_VALUE = stringPreferencesKey("real_ip_value")

        // 自动播放推荐新歌，默认 true
        private val KEY_AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        // 与其他应用同时播放，默认 false
        private val KEY_PLAY_WITH_OTHER_APPS = booleanPreferencesKey("play_with_other_apps")
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
        prefs[KEY_STREAM_CACHE_ENABLED] ?: true
    }

    suspend fun saveStreamCacheEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_STREAM_CACHE_ENABLED] = enabled
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
}

