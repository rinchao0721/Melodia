package com.lin0721.linmusic.desktop.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricAlign
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricSecondary
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricStyle
import com.lin0721.linmusic.desktop.app.RenderBackend
import com.lin0721.linmusic.desktop.app.SavedWindow
import com.lin0721.linmusic.desktop.app.WindowBounds
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import com.lin0721.linmusic.desktop.player.AUTO_AUDIO_DEVICE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class CloseAction { TRAY, EXIT }

// 音乐库形态：收起为窄条 / 默认侧栏 / 展开铺满中间区域
enum class LibraryMode { RAIL, DEFAULT, EXPANDED }

// 音乐库条目的显示方式
enum class LibraryViewMode { COMPACT_LIST, LIST, SMALL_GRID, LARGE_GRID }

// 桌面端独有设置；快捷键以 “动作=修饰键:键码” 分号拼接存储，空值表示用户已清除该键
class DesktopPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val STORE_NAME = "desktop_prefs"
        const val DEFAULT_LYRICS_VIEW_FONT_SIZE = 40
        const val DEFAULT_VOLUME = 100

        private val KEY_HOTKEYS = stringPreferencesKey("hotkeys")
        private val KEY_MEDIA_KEYS_ENABLED = booleanPreferencesKey("media_keys_enabled")
        private val KEY_CLOSE_ACTION = stringPreferencesKey("close_action")
        private val KEY_NOW_PLAYING_OPEN = booleanPreferencesKey("now_playing_panel_open")
        private val KEY_LIBRARY_MODE = stringPreferencesKey("library_mode")
        private val KEY_LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        private val KEY_LIBRARY_WIDTH = floatPreferencesKey("library_width")
        private val KEY_NOW_PLAYING_WIDTH = floatPreferencesKey("now_playing_width")
        private val KEY_AUDIO_DEVICE = stringPreferencesKey("audio_device")
        private val KEY_CACHE_ROOT = stringPreferencesKey("cache_root")
        private val KEY_CACHE_CLEANUP = stringPreferencesKey("cache_cleanup")
        private val KEY_VOLUME = intPreferencesKey("volume")
        private val KEY_LAST_AUDIBLE_VOLUME = intPreferencesKey("last_audible_volume")
        private val KEY_LYRICS_VIEW_FONT_SIZE = intPreferencesKey("lyrics_view_font_size")
        private val KEY_WINDOW_BOUNDS = stringPreferencesKey("window_bounds")
        private val KEY_WINDOW_MAXIMIZED = booleanPreferencesKey("window_maximized")
        private val KEY_RENDER_BACKEND = stringPreferencesKey("render_backend")
        private val KEY_DESKTOP_LYRIC_BOUNDS = stringPreferencesKey("desktop_lyric_bounds")
        private val KEY_DESKTOP_LYRIC_LOCKED = booleanPreferencesKey("desktop_lyric_locked")
        private val KEY_DESKTOP_LYRIC_FONT_SIZE = intPreferencesKey("desktop_lyric_font_size")
        private val KEY_DESKTOP_LYRIC_PLAYED_COLOR = stringPreferencesKey("desktop_lyric_played_color")
        private val KEY_DESKTOP_LYRIC_UNPLAYED_COLOR = stringPreferencesKey("desktop_lyric_unplayed_color")
        private val KEY_DESKTOP_LYRIC_SHADOW = booleanPreferencesKey("desktop_lyric_shadow")
        private val KEY_DESKTOP_LYRIC_BACKGROUND_ALPHA = intPreferencesKey("desktop_lyric_background_alpha")
        private val KEY_DESKTOP_LYRIC_ALIGN = stringPreferencesKey("desktop_lyric_align")
        private val KEY_DESKTOP_LYRIC_SECONDARY = stringPreferencesKey("desktop_lyric_secondary")
        private val KEY_DESKTOP_LYRIC_BOLD = booleanPreferencesKey("desktop_lyric_bold")
        private val KEY_DESKTOP_LYRIC_SHADOW_ALPHA = intPreferencesKey("desktop_lyric_shadow_alpha")
        private val KEY_DESKTOP_LYRIC_SHADOW_BLUR = intPreferencesKey("desktop_lyric_shadow_blur")
        private val KEY_DESKTOP_LYRIC_SHADOW_COLOR = stringPreferencesKey("desktop_lyric_shadow_color")
        private val KEY_MINI_PLAYER_POSITION = stringPreferencesKey("mini_player_position")
        private val KEY_MINI_PLAYER_STYLE = stringPreferencesKey("mini_player_style")
        private val KEY_MINI_PLAYER_ALWAYS_ON_TOP = booleanPreferencesKey("mini_player_always_on_top")
        private val KEY_MINI_PLAYER_SIZES = MiniPlayerStyle.entries.associateWith { stringPreferencesKey("mini_player_size_${it.name.lowercase()}") }
        private val KEY_MINI_LYRIC_FONT_SIZE = stringPreferencesKey("mini_lyric_font_size")
        private val KEY_MINI_LYRIC_SECONDARY = stringPreferencesKey("mini_lyric_secondary")
        private val KEY_MINI_LYRIC_KARAOKE = booleanPreferencesKey("mini_lyric_karaoke")
        private val KEY_MINI_LYRIC_SIDE_LINES = booleanPreferencesKey("mini_lyric_side_lines")
        private val KEY_MINI_LYRIC_ALIGN = stringPreferencesKey("mini_lyric_align")

        val DEFAULT_DESKTOP_LYRIC_STYLE = FloatingLyricStyle(fontSizeSp = 28)
    }

    val desktopLyricStyle: Flow<FloatingLyricStyle> = dataStore.data.map { prefs ->
        val default = DEFAULT_DESKTOP_LYRIC_STYLE
        FloatingLyricStyle(
            fontSizeSp = prefs[KEY_DESKTOP_LYRIC_FONT_SIZE] ?: default.fontSizeSp,
            playedColor = prefs[KEY_DESKTOP_LYRIC_PLAYED_COLOR] ?: default.playedColor,
            unplayedColor = prefs[KEY_DESKTOP_LYRIC_UNPLAYED_COLOR] ?: default.unplayedColor,
            shadow = prefs[KEY_DESKTOP_LYRIC_SHADOW] ?: default.shadow,
            backgroundAlpha = (prefs[KEY_DESKTOP_LYRIC_BACKGROUND_ALPHA] ?: default.backgroundAlpha).coerceIn(0, 100),
            align = FloatingLyricAlign.fromKey(prefs[KEY_DESKTOP_LYRIC_ALIGN]),
            secondary = FloatingLyricSecondary.fromKey(prefs[KEY_DESKTOP_LYRIC_SECONDARY]),
            bold = prefs[KEY_DESKTOP_LYRIC_BOLD] ?: default.bold,
            shadowAlpha = (prefs[KEY_DESKTOP_LYRIC_SHADOW_ALPHA] ?: default.shadowAlpha).coerceIn(0, 100),
            shadowBlurDp = (prefs[KEY_DESKTOP_LYRIC_SHADOW_BLUR] ?: default.shadowBlurDp).coerceAtLeast(0),
            shadowColor = prefs[KEY_DESKTOP_LYRIC_SHADOW_COLOR] ?: default.shadowColor
        )
    }.distinctUntilChanged()

    val desktopLyricLocked: Flow<Boolean> = dataStore.data.map { it[KEY_DESKTOP_LYRIC_LOCKED] ?: false }.distinctUntilChanged()

    suspend fun loadDesktopLyricBounds(): DesktopLyricBounds? =
        dataStore.data.first()[KEY_DESKTOP_LYRIC_BOUNDS]?.let(DesktopLyricBounds::decode)

    suspend fun saveDesktopLyricBounds(bounds: DesktopLyricBounds) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_BOUNDS] = bounds.encode() }
    }

    suspend fun saveDesktopLyricLocked(locked: Boolean) {
        dataStore.edit { it[KEY_DESKTOP_LYRIC_LOCKED] = locked }
    }

    suspend fun saveDesktopLyricStyle(style: FloatingLyricStyle) {
        dataStore.edit { prefs ->
            prefs[KEY_DESKTOP_LYRIC_FONT_SIZE] = style.fontSizeSp
            prefs[KEY_DESKTOP_LYRIC_PLAYED_COLOR] = style.playedColor
            prefs[KEY_DESKTOP_LYRIC_UNPLAYED_COLOR] = style.unplayedColor
            prefs[KEY_DESKTOP_LYRIC_SHADOW] = style.shadow
            prefs[KEY_DESKTOP_LYRIC_BACKGROUND_ALPHA] = style.backgroundAlpha.coerceIn(0, 100)
            prefs[KEY_DESKTOP_LYRIC_ALIGN] = style.align.key
            prefs[KEY_DESKTOP_LYRIC_SECONDARY] = style.secondary.key
            prefs[KEY_DESKTOP_LYRIC_BOLD] = style.bold
            prefs[KEY_DESKTOP_LYRIC_SHADOW_ALPHA] = style.shadowAlpha.coerceIn(0, 100)
            prefs[KEY_DESKTOP_LYRIC_SHADOW_BLUR] = style.shadowBlurDp.coerceAtLeast(0)
            prefs[KEY_DESKTOP_LYRIC_SHADOW_COLOR] = style.shadowColor
        }
    }

    val miniPlayerStyle: Flow<MiniPlayerStyle> =
        dataStore.data.map { MiniPlayerStyle.fromName(it[KEY_MINI_PLAYER_STYLE]) }.distinctUntilChanged()

    val miniPlayerAlwaysOnTop: Flow<Boolean> = dataStore.data.map { it[KEY_MINI_PLAYER_ALWAYS_ON_TOP] ?: true }.distinctUntilChanged()

    suspend fun saveMiniPlayerStyle(style: MiniPlayerStyle) {
        dataStore.edit { it[KEY_MINI_PLAYER_STYLE] = style.name }
    }

    suspend fun saveMiniPlayerAlwaysOnTop(onTop: Boolean) {
        dataStore.edit { it[KEY_MINI_PLAYER_ALWAYS_ON_TOP] = onTop }
    }

    val miniLyricSettings: Flow<MiniLyricSettings> = dataStore.data.map { prefs ->
        val default = MiniLyricSettings()
        MiniLyricSettings(
            fontSize = MiniLyricFontSize.fromName(prefs[KEY_MINI_LYRIC_FONT_SIZE]),
            secondary = MiniLyricSettings.secondaryFromKey(prefs[KEY_MINI_LYRIC_SECONDARY]),
            karaoke = prefs[KEY_MINI_LYRIC_KARAOKE] ?: default.karaoke,
            showSideLines = prefs[KEY_MINI_LYRIC_SIDE_LINES] ?: default.showSideLines,
            align = MiniLyricSettings.alignFromKey(prefs[KEY_MINI_LYRIC_ALIGN])
        )
    }.distinctUntilChanged()

    suspend fun saveMiniLyricSettings(settings: MiniLyricSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_MINI_LYRIC_FONT_SIZE] = settings.fontSize.name
            prefs[KEY_MINI_LYRIC_SECONDARY] = settings.secondary.key
            prefs[KEY_MINI_LYRIC_KARAOKE] = settings.karaoke
            prefs[KEY_MINI_LYRIC_SIDE_LINES] = settings.showSideLines
            prefs[KEY_MINI_LYRIC_ALIGN] = settings.align.key
        }
    }

    // 每种样式各存一份，缺失或损坏时用默认尺寸
    val miniPlayerSizes: Flow<Map<MiniPlayerStyle, MiniPlayerSize>> = dataStore.data.map { prefs ->
        MiniPlayerStyle.entries.associateWith { style ->
            prefs[KEY_MINI_PLAYER_SIZES.getValue(style)]?.let { MiniPlayerSize.decode(it, style) } ?: style.defaultSize
        }
    }.distinctUntilChanged()

    suspend fun saveMiniPlayerSize(style: MiniPlayerStyle, size: MiniPlayerSize) {
        dataStore.edit { it[KEY_MINI_PLAYER_SIZES.getValue(style)] = size.constrainedTo(style).encode() }
    }

    suspend fun loadMiniPlayerPosition(): MiniPlayerPosition? =
        dataStore.data.first()[KEY_MINI_PLAYER_POSITION]?.let(MiniPlayerPosition::decode)

    suspend fun saveMiniPlayerPosition(position: MiniPlayerPosition) {
        dataStore.edit { it[KEY_MINI_PLAYER_POSITION] = position.encode() }
    }

    val hotkeys: Flow<Map<HotkeyAction, HotkeyCombo?>> = dataStore.data.map { prefs ->
        val saved = parseHotkeys(prefs[KEY_HOTKEYS].orEmpty())
        HotkeyAction.entries.associateWith { action ->
            if (saved.containsKey(action)) saved[action] else HotkeyCombo.defaults[action]
        }
    }.distinctUntilChanged()

    val mediaKeysEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_MEDIA_KEYS_ENABLED] ?: true }.distinctUntilChanged()

    val closeAction: Flow<CloseAction> = dataStore.data.map { prefs ->
        prefs[KEY_CLOSE_ACTION]?.let { name -> CloseAction.entries.firstOrNull { it.name == name } } ?: CloseAction.TRAY
    }.distinctUntilChanged()

    val nowPlayingPanelOpen: Flow<Boolean> = dataStore.data.map { it[KEY_NOW_PLAYING_OPEN] ?: true }.distinctUntilChanged()

    val libraryMode: Flow<LibraryMode> = dataStore.data.map { prefs ->
        prefs[KEY_LIBRARY_MODE]?.let { name -> LibraryMode.entries.firstOrNull { it.name == name } } ?: LibraryMode.DEFAULT
    }.distinctUntilChanged()

    val libraryViewMode: Flow<LibraryViewMode> = dataStore.data.map { prefs ->
        prefs[KEY_LIBRARY_VIEW_MODE]?.let { name -> LibraryViewMode.entries.firstOrNull { it.name == name } } ?: LibraryViewMode.LIST
    }.distinctUntilChanged()

    // 拖动调整过的栏宽（dp），未调整过为 null
    val libraryWidth: Flow<Float?> = dataStore.data.map { it[KEY_LIBRARY_WIDTH] }.distinctUntilChanged()

    val nowPlayingWidth: Flow<Float?> = dataStore.data.map { it[KEY_NOW_PLAYING_WIDTH] }.distinctUntilChanged()

    // 音频输出设备的 mpv 设备名，缺省跟随系统默认
    // 全屏歌词字号（sp）：与移动端共用的同名偏好默认值只适合手机，桌面端单独存
    val lyricsViewFontSize: Flow<Int> = dataStore.data.map { it[KEY_LYRICS_VIEW_FONT_SIZE] ?: DEFAULT_LYRICS_VIEW_FONT_SIZE }.distinctUntilChanged()

    val audioDevice: Flow<String> = dataStore.data.map { it[KEY_AUDIO_DEVICE] ?: AUTO_AUDIO_DEVICE }.distinctUntilChanged()

    // 下次启动才生效
    val renderBackend: Flow<RenderBackend> = dataStore.data.map { RenderBackend.fromName(it[KEY_RENDER_BACKEND]) }.distinctUntilChanged()

    // 所选的缓存目录，缺省为默认位置；下次启动才生效
    val cacheRoot: Flow<String?> = dataStore.data.map { it[KEY_CACHE_ROOT]?.takeIf(String::isNotBlank) }.distinctUntilChanged()

    // 切换缓存目录后待删除的旧缓存根目录
    val pendingCacheCleanup: Flow<String?> = dataStore.data.map { it[KEY_CACHE_CLEANUP]?.takeIf(String::isNotBlank) }.distinctUntilChanged()

    suspend fun saveCacheRelocation(selected: String?, cleanup: String?) {
        dataStore.edit { prefs ->
            if (selected != null) prefs[KEY_CACHE_ROOT] = selected else prefs.remove(KEY_CACHE_ROOT)
            if (cleanup != null) prefs[KEY_CACHE_CLEANUP] = cleanup else prefs.remove(KEY_CACHE_CLEANUP)
        }
    }

    suspend fun clearPendingCacheCleanup() {
        dataStore.edit { it.remove(KEY_CACHE_CLEANUP) }
    }

    // 0 表示静音
    val volume: Flow<Int> = dataStore.data.map { (it[KEY_VOLUME] ?: DEFAULT_VOLUME).coerceIn(0, 100) }.distinctUntilChanged()

    // 静音前的音量，取消静音时恢复到它
    val lastAudibleVolume: Flow<Int> = dataStore.data.map { (it[KEY_LAST_AUDIBLE_VOLUME] ?: DEFAULT_VOLUME).coerceIn(1, 100) }.distinctUntilChanged()

    suspend fun saveVolume(volume: Int, lastAudible: Int) {
        dataStore.edit {
            it[KEY_VOLUME] = volume.coerceIn(0, 100)
            it[KEY_LAST_AUDIBLE_VOLUME] = lastAudible.coerceIn(1, 100)
        }
    }

    suspend fun saveHotkeys(hotkeys: Map<HotkeyAction, HotkeyCombo?>) {
        dataStore.edit { prefs ->
            prefs[KEY_HOTKEYS] = hotkeys.entries.joinToString(";") { (action, combo) -> "${action.name}=${combo?.encode().orEmpty()}" }
        }
    }

    suspend fun saveMediaKeysEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MEDIA_KEYS_ENABLED] = enabled }
    }

    suspend fun saveCloseAction(action: CloseAction) {
        dataStore.edit { it[KEY_CLOSE_ACTION] = action.name }
    }

    suspend fun saveRenderBackend(backend: RenderBackend) {
        dataStore.edit { it[KEY_RENDER_BACKEND] = backend.name }
    }

    suspend fun saveNowPlayingPanelOpen(open: Boolean) {
        dataStore.edit { it[KEY_NOW_PLAYING_OPEN] = open }
    }

    suspend fun saveLibraryMode(mode: LibraryMode) {
        dataStore.edit { it[KEY_LIBRARY_MODE] = mode.name }
    }

    suspend fun saveLibraryViewMode(mode: LibraryViewMode) {
        dataStore.edit { it[KEY_LIBRARY_VIEW_MODE] = mode.name }
    }

    suspend fun saveLibraryWidth(widthDp: Float) {
        dataStore.edit { it[KEY_LIBRARY_WIDTH] = widthDp }
    }

    suspend fun saveAudioDevice(name: String) {
        dataStore.edit { it[KEY_AUDIO_DEVICE] = name }
    }

    suspend fun saveLyricsViewFontSize(size: Int) {
        dataStore.edit { it[KEY_LYRICS_VIEW_FONT_SIZE] = size }
    }

    suspend fun saveNowPlayingWidth(widthDp: Float) {
        dataStore.edit { it[KEY_NOW_PLAYING_WIDTH] = widthDp }
    }

    suspend fun loadWindow(): SavedWindow = dataStore.data.first().let { prefs ->
        SavedWindow(
            bounds = prefs[KEY_WINDOW_BOUNDS]?.let(WindowBounds::decode),
            maximized = prefs[KEY_WINDOW_MAXIMIZED] ?: false
        )
    }

    // bounds 为 null 时保留已存的浮动位置与大小
    suspend fun saveWindow(bounds: WindowBounds?, maximized: Boolean) {
        dataStore.edit { prefs ->
            if (bounds != null) prefs[KEY_WINDOW_BOUNDS] = bounds.encode()
            prefs[KEY_WINDOW_MAXIMIZED] = maximized
        }
    }

    // 解析失败的条目忽略，回落到默认值
    private fun parseHotkeys(raw: String): Map<HotkeyAction, HotkeyCombo?> {
        if (raw.isBlank()) return emptyMap()
        val result = mutableMapOf<HotkeyAction, HotkeyCombo?>()
        raw.split(';').forEach { entry ->
            val (name, value) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val action = HotkeyAction.entries.firstOrNull { it.name == name } ?: return@forEach
            if (value.isEmpty()) {
                result[action] = null
            } else {
                HotkeyCombo.decode(value)?.let { result[action] = it }
            }
        }
        return result
    }
}
