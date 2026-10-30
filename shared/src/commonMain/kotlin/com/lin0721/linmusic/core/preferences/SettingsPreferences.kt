package com.lin0721.linmusic.core.preferences

import com.lin0721.linmusic.core.AppEnvironment
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.CrossfadePolicy
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricAlign
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricPalette
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricSecondary
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricShadow
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricStyle
import com.lin0721.linmusic.core.ui.skin.BUILTIN_SKIN_ID
import com.lin0721.linmusic.core.ui.theme.ThemeAccents
import com.lin0721.linmusic.core.ui.theme.parseRgbHex
import com.lin0721.linmusic.core.ui.theme.toRgbHex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val TAG = "SettingsPreferences"

// 未设置时的默认值，由各平台决定
class SettingsPreferences(
    private val dataStore: DataStore<Preferences>,
    private val streamCacheDefault: Boolean = false
) {

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
        // 硬件音频解码（音频卸载），默认 false
        private val KEY_AUDIO_OFFLOAD = booleanPreferencesKey("audio_offload")
        // 外部音视频停止后自动恢复，默认 true
        private val KEY_RESUME_AFTER_EXTERNAL_INTERRUPTION = booleanPreferencesKey("resume_after_external_interruption")
        // 切歌交叉淡化，默认 false
        private val KEY_CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        // 自动切歌交叉淡化时长 (ms)，默认 3000
        private val KEY_CROSSFADE_DURATION_MS = intPreferencesKey("crossfade_duration_ms")
        // 智能过渡（跳过首尾静音），默认 false
        private val KEY_SMART_CROSSFADE_ENABLED = booleanPreferencesKey("smart_crossfade_enabled")
        // 音量平衡（整曲响度归一化），默认 false
        private val KEY_LOUDNESS_NORMALIZATION_ENABLED = booleanPreferencesKey("loudness_normalization_enabled")
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
        // 平板点击播放条时播放页直接全屏展开，默认 false（侧栏展开）
        private val KEY_PANEL_DEFAULT_FULLSCREEN = booleanPreferencesKey("panel_default_fullscreen")
        private val KEY_PLAYER_PAGE_MODE = stringPreferencesKey("player_page_mode")
        private val KEY_SIDE_PLAYER_PINNED = booleanPreferencesKey("side_player_pinned")
        private val KEY_SIDE_PLAYER_POSITION = stringPreferencesKey("side_player_position")
        // 平板全局沉浸，默认 false
        private val KEY_TABLET_IMMERSIVE_MODE = booleanPreferencesKey("tablet_immersive_mode")
        // 界面缩放百分比，默认 100
        private val KEY_UI_SCALE_PERCENT = intPreferencesKey("ui_scale_percent")
        // 主题模式与深浅两套主色，默认深色网易红
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_THEME_DARK_ACCENT = stringPreferencesKey("theme_dark_accent")
        private val KEY_THEME_LIGHT_ACCENT = stringPreferencesKey("theme_light_accent")
        private val KEY_THEME_DARK_CUSTOM_COLOR = stringPreferencesKey("theme_dark_custom_color")
        private val KEY_THEME_LIGHT_CUSTOM_COLOR = stringPreferencesKey("theme_light_custom_color")
        // 旧版深浅共用的皮肤，首次按模式保存时拆到两个槽位
        private val KEY_THEME_SKIN_LEGACY = stringPreferencesKey("theme_skin")
        private val KEY_THEME_SKIN_DARK = stringPreferencesKey("theme_skin_dark")
        private val KEY_THEME_SKIN_LIGHT = stringPreferencesKey("theme_skin_light")
        const val PLAYER_PAGE_MODE_AUTO = "auto"
        const val PLAYER_PAGE_MODE_FULLSCREEN = "fullscreen"
        const val PLAYER_PAGE_MODE_SIDE = "side"
        const val SIDE_PLAYER_POSITION_RIGHT = "right"
        const val SIDE_PLAYER_POSITION_LEFT = "left"
        // 悬浮歌词字体大小，默认 14sp
        private val KEY_LYRIC_TEXT_SIZE = intPreferencesKey("lyric_text_size")
        // 悬浮歌词颜色，默认 "#FFFFFF"
        private val KEY_LYRIC_TEXT_COLOR = stringPreferencesKey("lyric_text_color")
        // 悬浮歌词其余样式；字号与已播色沿用上面两个旧键
        private val KEY_FLOATING_LYRIC_UNPLAYED_COLOR = stringPreferencesKey("floating_lyric_unplayed_color")
        private val KEY_FLOATING_LYRIC_SHADOW = booleanPreferencesKey("floating_lyric_shadow")
        private val KEY_FLOATING_LYRIC_BACKGROUND_ALPHA = intPreferencesKey("floating_lyric_background_alpha")
        private val KEY_FLOATING_LYRIC_ALIGN = stringPreferencesKey("floating_lyric_align")
        private val KEY_FLOATING_LYRIC_SECONDARY = stringPreferencesKey("floating_lyric_secondary")
        private val KEY_FLOATING_LYRIC_BOLD = booleanPreferencesKey("floating_lyric_bold")
        private val KEY_FLOATING_LYRIC_SHADOW_ALPHA = intPreferencesKey("floating_lyric_shadow_alpha")
        private val KEY_FLOATING_LYRIC_SHADOW_BLUR = intPreferencesKey("floating_lyric_shadow_blur")
        private val KEY_FLOATING_LYRIC_SHADOW_COLOR = stringPreferencesKey("floating_lyric_shadow_color")
        private val KEY_FLOATING_LYRIC_LOCKED = booleanPreferencesKey("floating_lyric_locked")
        // 悬浮窗位置与宽度（px），未保存过时为空
        private val KEY_FLOATING_LYRIC_BOUNDS = stringPreferencesKey("floating_lyric_bounds")
        // 日志级别 KEY，默认 debug 包 "DEBUG"、release 包 "WARN"
        private val KEY_LOG_LEVEL = stringPreferencesKey("log_level")
        // 启动时自动检查更新，默认 true
        private val KEY_AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
        // 是否接收测试版（beta/rc）更新推送，默认 false 只接收正式版
        private val KEY_ALLOW_PRERELEASE_CHANNEL = booleanPreferencesKey("allow_prerelease_channel")
        // 界面过渡动画开关，默认 true；不影响歌词与音频淡入淡出
        private val KEY_UI_ANIMATIONS_ENABLED = booleanPreferencesKey("ui_animations_enabled")
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
        // 逐字歌词字词呼吸光晕动效，默认 false
        private val KEY_FULL_SCREEN_KARAOKE_GLOW_EFFECT = booleanPreferencesKey("full_screen_karaoke_glow_effect")
        private val KEY_AMLL_LYRICS_ENABLED = booleanPreferencesKey("amll_lyrics_enabled")
        private val KEY_QQ_LYRICS_ENABLED = booleanPreferencesKey("qq_lyrics_enabled")
        private val KEY_NETEASE_LYRICS_ENABLED = booleanPreferencesKey("netease_lyrics_enabled")
        // 全屏歌词行与行间距 (dp)，默认 24
        private val KEY_FULL_SCREEN_LYRIC_LINE_SPACING = intPreferencesKey("full_screen_lyric_line_spacing")
        // 全屏歌词原文与翻译/罗马音间距 (dp)，默认 6
        private val KEY_FULL_SCREEN_LYRIC_SECONDARY_SPACING = intPreferencesKey("full_screen_lyric_secondary_spacing")
        // 全屏歌词自动隐藏控制组件，默认 false
        private val KEY_FULL_SCREEN_LYRIC_AUTO_HIDE_CONTROLS = booleanPreferencesKey("full_screen_lyric_auto_hide_controls")
        private val KEY_FULL_SCREEN_LYRIC_CENTER_FIRST_LINE = booleanPreferencesKey("full_screen_lyric_center_first_line")
        // 全屏播放页信息卡片顺序与显隐，格式见 FullPlayerCardLayout
        private val KEY_FULL_PLAYER_CARD_LAYOUT = stringPreferencesKey("full_player_card_layout")
        // 底栏项目排序与显隐，格式见 BottomBarLayout
        private val KEY_BOTTOM_BAR_LAYOUT = stringPreferencesKey("bottom_bar_layout")
        // 底栏仅显示图标模式，默认 false
        private val KEY_BOTTOM_BAR_ICON_ONLY = booleanPreferencesKey("bottom_bar_icon_only")
        // 启动默认打开音乐库，默认 false
        private val KEY_START_IN_LIBRARY = booleanPreferencesKey("start_in_library")
        // 启用 SuperLyric 实时歌词，默认 false
        private val KEY_SUPER_LYRIC_ENABLED = booleanPreferencesKey("super_lyric_enabled")
        // 启用 LyricInfo 系统歌词注入，默认 true
        private val KEY_LYRIC_INFO_ENABLED = booleanPreferencesKey("lyric_info_enabled")
        // 启用车载蓝牙歌词 (AVRCP)，默认 false
        private val KEY_BLUETOOTH_LYRIC_ENABLED = booleanPreferencesKey("bluetooth_lyric_enabled")
        // 启用 Lyricon 词幕协议，默认 false
        private val KEY_LYRICON_ENABLED = booleanPreferencesKey("lyricon_enabled")
        // 启用状态栏歌词胶囊 (Android 16 实时更新通知)，默认 false
        private val KEY_FLUID_CLOUD_LYRIC_ENABLED = booleanPreferencesKey("fluid_cloud_lyric_enabled")
        // 播放页小歌词显隐，默认 true
        private val KEY_SHOW_MINI_LYRIC = booleanPreferencesKey("show_mini_lyric")
        // 播放页显示实际音质，默认 false
        private val KEY_SHOW_PLAYBACK_QUALITY = booleanPreferencesKey("show_playback_quality")
    }

    // Wi-Fi 音质设置 Flow
    val wifiQuality: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_WIFI_QUALITY] ?: "lossless"
    }

    suspend fun saveWifiQuality(quality: String) {
        dataStore.edit { prefs ->
            prefs[KEY_WIFI_QUALITY] = quality
        }
    }

    // 移动网络音质设置 Flow
    val mobileQuality: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_MOBILE_QUALITY] ?: "standard"
    }

    suspend fun saveMobileQuality(quality: String) {
        dataStore.edit { prefs ->
            prefs[KEY_MOBILE_QUALITY] = quality
        }
    }

    // 默认隐私歌单配置 Flow
    val defaultPlaylistPrivate: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_PLAYLIST_PRIVATE] ?: false
    }

    suspend fun saveDefaultPlaylistPrivate(private: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_PLAYLIST_PRIVATE] = private
        }
    }

    // 默认搜索源 Flow
    val defaultSearchSource: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_SEARCH_SOURCE] ?: "netease"
    }

    suspend fun saveDefaultSearchSource(source: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_SEARCH_SOURCE] = source
        }
    }

    // 缓存开关设置 Flow
    val streamCacheEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_STREAM_CACHE_ENABLED] ?: streamCacheDefault
    }

    suspend fun saveStreamCacheEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_STREAM_CACHE_ENABLED] = enabled
        }
    }

    // 自定义下载目录 Uri（SAF tree Uri 字符串），未设置时为 null，代表用默认的存储根目录 Melodia/
    val downloadFolderUri: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_FOLDER_URI]
    }

    suspend fun saveDownloadFolderUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_DOWNLOAD_FOLDER_URI] = uri
            else prefs.remove(KEY_DOWNLOAD_FOLDER_URI)
        }
    }

    // 下载歌词配置 Flow
    val downloadLyricsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_LYRICS_ENABLED] ?: true
    }

    suspend fun saveDownloadLyricsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_LYRICS_ENABLED] = enabled
        }
    }

    // 音频缓存最大容量设置 Flow
    val audioCacheMaxSize: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_CACHE_MAX_SIZE] ?: (500 * 1024 * 1024L) // 默认 500MB
    }

    suspend fun saveAudioCacheMaxSize(size: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_AUDIO_CACHE_MAX_SIZE] = size
        }
    }

    // 是否使用真实 IP 伪装 Flow
    val useRealIp: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_USE_REAL_IP] ?: false
    }

    suspend fun saveUseRealIp(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_USE_REAL_IP] = enabled
        }
    }

    // 自定义真实 IP 值 Flow
    val realIpValue: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_REAL_IP_VALUE] ?: ""
    }

    suspend fun saveRealIpValue(ip: String) {
        dataStore.edit { prefs ->
            prefs[KEY_REAL_IP_VALUE] = ip
        }
    }

    // 自动播放推荐新歌 Flow
    val autoPlayNext: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_PLAY_NEXT] ?: true
    }

    suspend fun saveAutoPlayNext(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_PLAY_NEXT] = enabled
        }
    }

    // 与其他应用同时播放 Flow
    val playWithOtherApps: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PLAY_WITH_OTHER_APPS] ?: false
    }

    suspend fun savePlayWithOtherApps(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_PLAY_WITH_OTHER_APPS] = enabled
        }
    }

    // 硬件音频解码 Flow
    val audioOffload: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_OFFLOAD] ?: false
    }

    // 与切歌交叉淡化互斥
    suspend fun saveAudioOffload(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUDIO_OFFLOAD] = enabled
            if (enabled) prefs[KEY_CROSSFADE_ENABLED] = false
        }
    }

    // 外部音视频停止后自动恢复 Flow
    val resumeAfterExternalInterruption: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_RESUME_AFTER_EXTERNAL_INTERRUPTION] ?: true
    }

    suspend fun saveResumeAfterExternalInterruption(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_RESUME_AFTER_EXTERNAL_INTERRUPTION] = enabled
        }
    }

    // 切歌交叉淡化开关 Flow
    val crossfadeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CROSSFADE_ENABLED] ?: false
    }

    // 与硬件音频解码互斥：淡化时两台播放器同时出声，会争用唯一的卸载通道
    suspend fun saveCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CROSSFADE_ENABLED] = enabled
            if (enabled) prefs[KEY_AUDIO_OFFLOAD] = false
        }
    }

    // 自动切歌交叉淡化时长 Flow
    val crossfadeDurationMs: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CROSSFADE_DURATION_MS] ?: CrossfadePolicy.DEFAULT_DURATION_MS
    }

    suspend fun saveCrossfadeDurationMs(durationMs: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_CROSSFADE_DURATION_MS] = CrossfadePolicy.normalizeDurationMs(durationMs)
        }
    }

    // 智能过渡开关 Flow
    val smartCrossfadeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SMART_CROSSFADE_ENABLED] ?: false
    }

    suspend fun saveSmartCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SMART_CROSSFADE_ENABLED] = enabled
        }
    }

    // 音量平衡开关 Flow
    val loudnessNormalizationEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LOUDNESS_NORMALIZATION_ENABLED] ?: false
    }

    suspend fun saveLoudnessNormalizationEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_LOUDNESS_NORMALIZATION_ENABLED] = enabled
        }
    }

    // 仅 Wi-Fi 网络下联网播放 Flow
    val wifiOnlyPlay: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_WIFI_ONLY_PLAY] ?: false
    }

    suspend fun saveWifiOnlyPlay(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WIFI_ONLY_PLAY] = enabled
        }
    }

    // 流量播放警告提示 Flow
    val mobileAlert: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_MOBILE_ALERT] ?: true
    }

    suspend fun saveMobileAlert(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_MOBILE_ALERT] = enabled
        }
    }

    // 使用代理服务器 Flow
    val useProxy: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_USE_PROXY] ?: false
    }

    suspend fun saveUseProxy(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_USE_PROXY] = enabled
        }
    }

    // 启用桌面悬浮歌词 Flow
    val showDesktopLrc: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_DESKTOP_LRC] ?: false
    }

    suspend fun saveShowDesktopLrc(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_DESKTOP_LRC] = enabled
        }
    }

    // 启用系统锁屏显示 Flow
    val showLockscreen: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_LOCKSCREEN] ?: true
    }

    suspend fun saveShowLockscreen(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_LOCKSCREEN] = enabled
        }
    }

    // 车载模式蓝牙自动启动 Flow
    val carMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CAR_MODE] ?: false
    }

    suspend fun saveCarMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CAR_MODE] = enabled
        }
    }

    // 底栏项目配置 Flow
    val bottomBarLayout: Flow<List<BottomBarItemSetting>> = dataStore.data.map { prefs ->
        BottomBarLayout.decode(prefs[KEY_BOTTOM_BAR_LAYOUT], prefs[KEY_SHOW_CREATE_ENTRY])
    }

    suspend fun saveBottomBarLayout(layout: List<BottomBarItemSetting>) {
        val normalized = BottomBarLayout.normalize(layout)
        val createVisible = normalized.firstOrNull { it.item == BottomBarItem.CREATE }?.visible ?: true
        dataStore.edit { prefs ->
            prefs[KEY_BOTTOM_BAR_LAYOUT] = BottomBarLayout.encode(normalized)
            prefs[KEY_SHOW_CREATE_ENTRY] = createVisible
        }
    }

    // 底栏仅显示图标 Flow
    val bottomBarIconOnly: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BOTTOM_BAR_ICON_ONLY] ?: false
    }

    suspend fun saveBottomBarIconOnly(iconOnly: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_BOTTOM_BAR_ICON_ONLY] = iconOnly
        }
    }

    // 启动默认打开音乐库 Flow
    val startInLibrary: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_START_IN_LIBRARY] ?: false
    }

    suspend fun saveStartInLibrary(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_START_IN_LIBRARY] = enabled
        }
    }

    // 底栏创建歌单快捷入口 Flow
    val showCreateEntry: Flow<Boolean> = dataStore.data.map { prefs ->
        val layout = BottomBarLayout.decode(prefs[KEY_BOTTOM_BAR_LAYOUT], prefs[KEY_SHOW_CREATE_ENTRY])
        layout.firstOrNull { it.item == BottomBarItem.CREATE }?.visible ?: true
    }

    suspend fun saveShowCreateEntry(enabled: Boolean) {
        dataStore.edit { prefs ->
            val layout = BottomBarLayout.decode(prefs[KEY_BOTTOM_BAR_LAYOUT], prefs[KEY_SHOW_CREATE_ENTRY])
            val updated = layout.map {
                if (it.item == BottomBarItem.CREATE) it.copy(visible = enabled) else it
            }
            prefs[KEY_BOTTOM_BAR_LAYOUT] = BottomBarLayout.encode(updated)
            prefs[KEY_SHOW_CREATE_ENTRY] = enabled
        }
    }

    // 播放页模式：auto 按方向（竖屏全屏式、横屏侧边式）、fullscreen、side；未设置时沿用旧「默认全屏」开关
    val playerPageMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_PLAYER_PAGE_MODE]
            ?: if (prefs[KEY_PANEL_DEFAULT_FULLSCREEN] == true) PLAYER_PAGE_MODE_FULLSCREEN else PLAYER_PAGE_MODE_AUTO
    }

    suspend fun savePlayerPageMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_PLAYER_PAGE_MODE] = mode
        }
    }

    // 侧边播放页是否钉住；关闭后返回键直接收起
    val sidePlayerPinned: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SIDE_PLAYER_PINNED] ?: true
    }

    suspend fun saveSidePlayerPinned(pinned: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SIDE_PLAYER_PINNED] = pinned
        }
    }

    // 平板侧边播放页停靠在哪一侧
    val sidePlayerPosition: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SIDE_PLAYER_POSITION] ?: SIDE_PLAYER_POSITION_RIGHT
    }

    suspend fun saveSidePlayerPosition(position: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SIDE_PLAYER_POSITION] = position
        }
    }

    // 平板全局沉浸 Flow
    val tabletImmersiveMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_TABLET_IMMERSIVE_MODE] ?: false
    }

    suspend fun saveTabletImmersiveMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_TABLET_IMMERSIVE_MODE] = enabled
        }
    }

    // 界面缩放百分比 Flow
    val uiScalePercent: Flow<Int> = dataStore.data.map { prefs ->
        UiScale.normalizePercent(prefs[KEY_UI_SCALE_PERCENT] ?: UiScale.DEFAULT_PERCENT)
    }

    suspend fun saveUiScalePercent(percent: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_UI_SCALE_PERCENT] = UiScale.normalizePercent(percent)
        }
    }

    val themeSettings: Flow<ThemeSettings> = dataStore.data.map { prefs ->
        ThemeSettings(
            mode = ThemeMode.fromKey(prefs[KEY_THEME_MODE]),
            darkAccentId = prefs[KEY_THEME_DARK_ACCENT] ?: ThemeAccents.DEFAULT_ID,
            lightAccentId = prefs[KEY_THEME_LIGHT_ACCENT] ?: ThemeAccents.DEFAULT_ID,
            darkCustomHex = prefs[KEY_THEME_DARK_CUSTOM_COLOR],
            lightCustomHex = prefs[KEY_THEME_LIGHT_CUSTOM_COLOR],
            darkSkinId = prefs[KEY_THEME_SKIN_DARK] ?: prefs[KEY_THEME_SKIN_LEGACY] ?: BUILTIN_SKIN_ID,
            lightSkinId = prefs[KEY_THEME_SKIN_LIGHT] ?: prefs[KEY_THEME_SKIN_LEGACY] ?: BUILTIN_SKIN_ID
        )
    }.distinctUntilChanged()

    suspend fun saveThemeSkin(isDark: Boolean, skinId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_SKIN_LEGACY]?.let { legacy ->
                if (prefs[KEY_THEME_SKIN_DARK] == null) prefs[KEY_THEME_SKIN_DARK] = legacy
                if (prefs[KEY_THEME_SKIN_LIGHT] == null) prefs[KEY_THEME_SKIN_LIGHT] = legacy
                prefs.remove(KEY_THEME_SKIN_LEGACY)
            }
            prefs[if (isDark) KEY_THEME_SKIN_DARK else KEY_THEME_SKIN_LIGHT] = skinId
        }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.key
        }
    }

    suspend fun saveThemeAccent(isDark: Boolean, accentId: String) {
        dataStore.edit { prefs ->
            prefs[if (isDark) KEY_THEME_DARK_ACCENT else KEY_THEME_LIGHT_ACCENT] = accentId
        }
    }

    // 保存自定义色并切换到自定义主色；非法色值直接忽略
    suspend fun saveThemeCustomColor(isDark: Boolean, hex: String) {
        val normalized = parseRgbHex(hex)?.toRgbHex() ?: return
        dataStore.edit { prefs ->
            prefs[if (isDark) KEY_THEME_DARK_CUSTOM_COLOR else KEY_THEME_LIGHT_CUSTOM_COLOR] = normalized
            prefs[if (isDark) KEY_THEME_DARK_ACCENT else KEY_THEME_LIGHT_ACCENT] = ThemeAccents.CUSTOM_ID
        }
    }

    // 悬浮歌词完整样式；旧版只有 70% 黑底，背景默认值与之保持一致
    val floatingLyricStyle: Flow<FloatingLyricStyle> = dataStore.data.map { prefs ->
        FloatingLyricStyle(
            fontSizeSp = prefs[KEY_LYRIC_TEXT_SIZE] ?: 14,
            playedColor = prefs[KEY_LYRIC_TEXT_COLOR] ?: FloatingLyricPalette.DEFAULT_PLAYED,
            unplayedColor = prefs[KEY_FLOATING_LYRIC_UNPLAYED_COLOR] ?: FloatingLyricPalette.DEFAULT_UNPLAYED,
            shadow = prefs[KEY_FLOATING_LYRIC_SHADOW] ?: true,
            backgroundAlpha = (prefs[KEY_FLOATING_LYRIC_BACKGROUND_ALPHA] ?: 70).coerceIn(0, 100),
            align = FloatingLyricAlign.fromKey(prefs[KEY_FLOATING_LYRIC_ALIGN]),
            secondary = FloatingLyricSecondary.fromKey(prefs[KEY_FLOATING_LYRIC_SECONDARY]),
            bold = prefs[KEY_FLOATING_LYRIC_BOLD] ?: true,
            shadowAlpha = (prefs[KEY_FLOATING_LYRIC_SHADOW_ALPHA] ?: FloatingLyricShadow.DEFAULT_ALPHA).coerceIn(0, 100),
            shadowBlurDp = (prefs[KEY_FLOATING_LYRIC_SHADOW_BLUR] ?: FloatingLyricShadow.DEFAULT_BLUR_DP).coerceAtLeast(0),
            shadowColor = prefs[KEY_FLOATING_LYRIC_SHADOW_COLOR] ?: FloatingLyricShadow.DEFAULT_COLOR
        )
    }.distinctUntilChanged()

    suspend fun saveFloatingLyricStyle(style: FloatingLyricStyle) {
        dataStore.edit { prefs ->
            prefs[KEY_LYRIC_TEXT_SIZE] = style.fontSizeSp
            prefs[KEY_LYRIC_TEXT_COLOR] = style.playedColor
            prefs[KEY_FLOATING_LYRIC_UNPLAYED_COLOR] = style.unplayedColor
            prefs[KEY_FLOATING_LYRIC_SHADOW] = style.shadow
            prefs[KEY_FLOATING_LYRIC_BACKGROUND_ALPHA] = style.backgroundAlpha.coerceIn(0, 100)
            prefs[KEY_FLOATING_LYRIC_ALIGN] = style.align.key
            prefs[KEY_FLOATING_LYRIC_SECONDARY] = style.secondary.key
            prefs[KEY_FLOATING_LYRIC_BOLD] = style.bold
            prefs[KEY_FLOATING_LYRIC_SHADOW_ALPHA] = style.shadowAlpha.coerceIn(0, 100)
            prefs[KEY_FLOATING_LYRIC_SHADOW_BLUR] = style.shadowBlurDp.coerceAtLeast(0)
            prefs[KEY_FLOATING_LYRIC_SHADOW_COLOR] = style.shadowColor
        }
    }

    val floatingLyricLocked: Flow<Boolean> = dataStore.data.map { it[KEY_FLOATING_LYRIC_LOCKED] ?: false }.distinctUntilChanged()

    suspend fun saveFloatingLyricLocked(locked: Boolean) {
        dataStore.edit { it[KEY_FLOATING_LYRIC_LOCKED] = locked }
    }

    // 格式 "x,y,width"，解析失败视为未保存
    suspend fun loadFloatingLyricBounds(): Triple<Int, Int, Int>? {
        val raw = dataStore.data.first()[KEY_FLOATING_LYRIC_BOUNDS] ?: return null
        val parts = raw.split(',').map { it.trim().toIntOrNull() ?: return null }
        if (parts.size != 3 || parts[2] <= 0) return null
        return Triple(parts[0], parts[1], parts[2])
    }

    suspend fun saveFloatingLyricBounds(x: Int, y: Int, width: Int) {
        dataStore.edit { it[KEY_FLOATING_LYRIC_BOUNDS] = "$x,$y,$width" }
    }

    // 日志级别 Flow，取值为 AppLogger.LogLevel 的 name（DEBUG/INFO/WARN/ERROR）
    val logLevel: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LOG_LEVEL] ?: if (AppEnvironment.isDebug) "DEBUG" else "WARN"
    }

    suspend fun saveLogLevel(level: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LOG_LEVEL] = level
        }
    }

    // 启动时自动检查更新 Flow
    val autoCheckUpdateEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CHECK_UPDATE] ?: true
    }

    suspend fun saveAutoCheckUpdateEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_CHECK_UPDATE] = enabled
        }
    }

    // 接收测试版更新通道 Flow
    val allowPrereleaseChannel: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ALLOW_PRERELEASE_CHANNEL] ?: false
    }

    suspend fun saveAllowPrereleaseChannel(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ALLOW_PRERELEASE_CHANNEL] = enabled
        }
    }

    // 界面过渡动画开关 Flow
    val uiAnimationsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_UI_ANIMATIONS_ENABLED] ?: true
    }

    suspend fun saveUiAnimationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_UI_ANIMATIONS_ENABLED] = enabled
        }
    }

    // 被用户忽略的更新版本 tag Flow
    val ignoredUpdateTag: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_IGNORED_UPDATE_TAG] ?: ""
    }

    suspend fun saveIgnoredUpdateTag(tag: String) {
        dataStore.edit { prefs ->
            prefs[KEY_IGNORED_UPDATE_TAG] = tag
        }
    }

    // 全屏歌词字号设置 Flow
    val fullScreenLyricTextSize: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_TEXT_SIZE] ?: 22
    }

    suspend fun saveFullScreenLyricTextSize(size: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_TEXT_SIZE] = size
        }
    }

    // 全屏歌词对齐方式 Flow
    val fullScreenLyricAlignment: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_ALIGNMENT] ?: "left"
    }

    suspend fun saveFullScreenLyricAlignment(alignment: String) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_ALIGNMENT] = alignment
        }
    }

    // 全屏歌词是否显示双语翻译 Flow
    val fullScreenLyricShowTranslation: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_SHOW_TRANSLATION] ?: true
    }

    suspend fun saveFullScreenLyricShowTranslation(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_SHOW_TRANSLATION] = show
        }
    }

    // 全屏歌词副文本展示模式 Flow（"translation"：翻译，"roma"：罗马音，"none"：仅原词）
    val fullScreenLyricSecondaryMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_SECONDARY_MODE] ?: if (prefs[KEY_FULL_SCREEN_LYRIC_SHOW_TRANSLATION] == false) "none" else "translation"
    }

    suspend fun saveFullScreenLyricSecondaryMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_SECONDARY_MODE] = mode
            prefs[KEY_FULL_SCREEN_LYRIC_SHOW_TRANSLATION] = mode != "none"
        }
    }

    // 网易歌词源开关，默认启用。
    val neteaseLyricsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NETEASE_LYRICS_ENABLED] ?: true
    }

    suspend fun saveNeteaseLyricsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_NETEASE_LYRICS_ENABLED] = enabled }
    }

    // QQ 歌词源开关，默认启用。
    val qqLyricsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_QQ_LYRICS_ENABLED] ?: true
    }

    suspend fun saveQqLyricsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_QQ_LYRICS_ENABLED] = enabled }
    }

    // AMLL 歌词源开关，默认启用。
    val amllLyricsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AMLL_LYRICS_ENABLED] ?: true
    }

    suspend fun saveAmllLyricsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AMLL_LYRICS_ENABLED] = enabled }
    }

    // 逐字歌词流光动效 Flow
    val fullScreenKaraokeAdvancedEffect: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_KARAOKE_ADVANCED_EFFECT] ?: true
    }

    suspend fun saveFullScreenKaraokeAdvancedEffect(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_KARAOKE_ADVANCED_EFFECT] = enabled
        }
    }

    // 逐字歌词字词呼吸光晕动效 Flow，默认 false
    val fullScreenKaraokeGlowEffect: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_KARAOKE_GLOW_EFFECT] ?: false
    }

    suspend fun saveFullScreenKaraokeGlowEffect(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_KARAOKE_GLOW_EFFECT] = enabled
        }
    }

    // 全屏歌词行与行间距 Flow
    val fullScreenLyricLineSpacing: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_LINE_SPACING] ?: 24
    }

    suspend fun saveFullScreenLyricLineSpacing(spacing: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_LINE_SPACING] = spacing
        }
    }

    // 全屏歌词原文与翻译/罗马音间距 Flow
    val fullScreenLyricSecondarySpacing: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_SECONDARY_SPACING] ?: 6
    }

    suspend fun saveFullScreenLyricSecondarySpacing(spacing: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_SECONDARY_SPACING] = spacing
        }
    }

    // 全屏歌词自动隐藏控制组件 Flow
    val fullScreenLyricAutoHideControls: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_AUTO_HIDE_CONTROLS] ?: false
    }

    suspend fun saveFullScreenLyricAutoHideControls(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_AUTO_HIDE_CONTROLS] = enabled
        }
    }

    // 全屏歌词首行居中 Flow（默认关闭，沿用原样式）
    val fullScreenLyricCenterFirstLine: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FULL_SCREEN_LYRIC_CENTER_FIRST_LINE] ?: false
    }

    suspend fun saveFullScreenLyricCenterFirstLine(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_LYRIC_CENTER_FIRST_LINE] = enabled
        }
    }

    // 全屏播放页信息卡片顺序与显隐 Flow
    val fullPlayerCardLayout: Flow<List<FullPlayerCardSetting>> = dataStore.data.map { prefs ->
        FullPlayerCardLayout.decode(prefs[KEY_FULL_PLAYER_CARD_LAYOUT])
    }

    suspend fun saveFullPlayerCardLayout(layout: List<FullPlayerCardSetting>) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_PLAYER_CARD_LAYOUT] =
                PlayerCardEntries.withPluginTokens(FullPlayerCardLayout.encode(layout), prefs[KEY_FULL_PLAYER_CARD_LAYOUT])
        }
    }

    // 含插件卡片的原始配置，格式见 PlayerCardEntries
    val fullPlayerCardRaw: Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_FULL_PLAYER_CARD_LAYOUT] }

    suspend fun saveFullPlayerCardEntries(entries: List<PlayerCardEntry>) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_PLAYER_CARD_LAYOUT] = PlayerCardEntries.encode(entries, prefs[KEY_FULL_PLAYER_CARD_LAYOUT])
        }
    }

    // SuperLyric 实时歌词 Flow
    val superLyricEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SUPER_LYRIC_ENABLED] ?: false
    }

    suspend fun saveSuperLyricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SUPER_LYRIC_ENABLED] = enabled
        }
    }

    // LyricInfo 系统歌词注入 Flow
    val lyricInfoEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LYRIC_INFO_ENABLED] ?: true
    }

    suspend fun saveLyricInfoEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_LYRIC_INFO_ENABLED] = enabled
        }
    }

    // 车载蓝牙歌词 (AVRCP) Flow
    val bluetoothLyricEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BLUETOOTH_LYRIC_ENABLED] ?: false
    }

    suspend fun saveBluetoothLyricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_BLUETOOTH_LYRIC_ENABLED] = enabled
        }
    }

    // Lyricon 词幕协议 Flow
    val lyriconEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LYRICON_ENABLED] ?: false
    }

    suspend fun saveLyriconEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_LYRICON_ENABLED] = enabled
        }
    }

    // 状态栏歌词胶囊 Flow
    val fluidCloudLyricEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FLUID_CLOUD_LYRIC_ENABLED] ?: false
    }

    suspend fun saveFluidCloudLyricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_FLUID_CLOUD_LYRIC_ENABLED] = enabled
        }
    }

    // 播放页小歌词显隐 Flow
    val showMiniLyric: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_MINI_LYRIC] ?: true
    }

    suspend fun saveShowMiniLyric(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_MINI_LYRIC] = show
        }
    }

    // 播放页实际音质显隐 Flow
    val showPlaybackQuality: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_PLAYBACK_QUALITY] ?: false
    }

    suspend fun saveShowPlaybackQuality(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_PLAYBACK_QUALITY] = show
        }
    }
}

