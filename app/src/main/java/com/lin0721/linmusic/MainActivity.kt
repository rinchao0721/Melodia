package com.lin0721.linmusic

import com.lin0721.linmusic.feature.settings.ui.AndroidPluginPageUi
import com.lin0721.linmusic.core.plugin.PluginPageHost
import com.lin0721.linmusic.core.plugin.PluginCardContext
import com.lin0721.linmusic.core.plugin.PlayerCardSource
import com.lin0721.linmusic.core.plugin.LocalPluginCards
import com.lin0721.linmusic.core.plugin.PluginRepository
import com.lin0721.linmusic.core.plugin.LocalPlayerLayouts
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import com.lin0721.linmusic.core.preferences.ThemeSettings
import com.lin0721.linmusic.core.ui.skin.SkinImageCodec
import com.lin0721.linmusic.core.ui.skin.SkinRepository
import com.lin0721.linmusic.core.ui.skin.ActiveSkin
import com.lin0721.linmusic.core.ui.skin.withBackdrop
import com.lin0721.linmusic.core.ui.theme.LocalSkinBackdrop
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaPalette
import com.lin0721.linmusic.core.ui.theme.ThemeRevealHost
import com.lin0721.linmusic.feature.recognition.service.ACTION_OPEN_RECOGNITION_RESULT
import com.lin0721.linmusic.feature.recognition.service.PlaybackRecognitionState
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lin0721.linmusic.core.player.FloatingLyricService
import com.lin0721.linmusic.core.player.floatinglyric.ACTION_OPEN_LYRIC_SETTINGS
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricRequests
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.UiScale
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaTheme
import com.lin0721.linmusic.core.ui.theme.UiMotion
import com.lin0721.linmusic.core.ui.theme.scaledBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {

    private val settingsPreferences: SettingsPreferences by inject()
    private val skinRepository: SkinRepository by inject()
    private val pluginRepository: PluginRepository by inject()
    private val playerCardSource: PlayerCardSource by inject()
    private val pluginPageHost: PluginPageHost by inject()
    private val skinImageCodec: SkinImageCodec by inject()
    private val playbackRecognitionState: PlaybackRecognitionState by inject()
    private val floatingLyricRequests: FloatingLyricRequests by inject()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // 本次创建时已应用的缩放百分比；偏好里的值与它不一致就重建界面
    private var appliedUiScalePercent = UiScale.DEFAULT_PERCENT

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // Activity 创建时 Koin 已由 Application 启动；读取失败按不缩放处理
        appliedUiScalePercent = runCatching {
            runBlocking { GlobalContext.get().get<SettingsPreferences>().uiScalePercent.first() }
        }.getOrDefault(UiScale.DEFAULT_PERCENT)
        val factor = UiScale.toFactor(appliedUiScalePercent)
        if (factor != 1f) applyOverrideConfiguration(newBase.resources.configuration.scaledBy(factor))
    }

    // 覆盖配置里的 dp 尺寸是创建时的快照，旋转等配置变化后会过期，重建以按新配置重新换算
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (appliedUiScalePercent != UiScale.DEFAULT_PERCENT) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开启宽色域，提升暗部与渐变渲染位深
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
        }
        applySystemBarStyle(isDark = true)
        requestNotificationPermissionIfNeeded()
        handleLaunchIntent(intent)

        // 监听悬浮歌词开关
        lifecycleScope.launch {
            settingsPreferences.showDesktopLrc.collectLatest { enabled ->
                if (enabled) {
                    startFloatingLyricOrRequestPermission()
                } else {
                    stopService(Intent(this@MainActivity, FloatingLyricService::class.java))
                }
            }
        }

        // 设置页松手后写入新缩放，重建界面让它落到 Activity 配置上
        lifecycleScope.launch {
            settingsPreferences.uiScalePercent.collect { percent ->
                if (percent != appliedUiScalePercent) recreate()
            }
        }

        // 首帧即按已存主题渲染，避免先以深色绘制再跳变
        val initialThemeSettings = runCatching {
            runBlocking { settingsPreferences.themeSettings.first() }
        }.getOrDefault(ThemeSettings())
        val initialSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val initialDark = initialThemeSettings.mode.isDark(initialSystemDark)
        skinRepository.updateSystemDark(initialSystemDark)
        val initialSkin = runCatching { runBlocking { skinRepository.load(initialThemeSettings.skinId(initialDark), initialDark) } }.getOrNull()

        setContent {
            val themeSettings by settingsPreferences.themeSettings.collectAsState(initial = initialThemeSettings)
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) { skinRepository.updateSystemDark(systemDark) }
            val activeSkinFlow = remember { skinRepository.activeSkin.withBackdrop(skinImageCodec) }
            val activeSkin by activeSkinFlow.collectAsState(initial = ActiveSkin(initialSkin, null))
            val uiAnimationsEnabled by settingsPreferences.uiAnimationsEnabled.collectAsState(initial = true)
            val uiMotion = remember { UiMotion() }
            LaunchedEffect(uiAnimationsEnabled) { uiMotion.enabled = uiAnimationsEnabled }
            val pluginLayouts by pluginRepository.layouts.collectAsState()
            val pluginCards = remember { PluginCardContext(playerCardSource, pluginPageHost, AndroidPluginPageUi) }
            CompositionLocalProvider(
                LocalUiMotion provides uiMotion,
                LocalPlayerLayouts provides pluginLayouts.roots,
                LocalPluginCards provides pluginCards
            ) {
                MelodiaTheme(themeSettings, activeSkin.skin, activeSkin.backdrop) {
                    val palette = LocalMelodiaPalette.current
                    // 系统栏图标与窗口底色随主题深浅切换；启动页底色仍固定深色
                    LaunchedEffect(palette.isDark, palette.pane) {
                        applySystemBarStyle(palette.isDark)
                        window.setBackgroundDrawable(ColorDrawable(palette.pane.toArgb()))
                    }
                    ThemeRevealHost(themeKey = palette to LocalSkinBackdrop.current, enabled = uiMotion.enabled) {
                        MelodiaApp()
                    }
                }
            }
        }
    }

    private fun applySystemBarStyle(isDark: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (isDark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent)
    }

    // 识曲结果通知与悬浮歌词设置按钮拉起：交给界面层打开对应页面
    private fun handleLaunchIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_RECOGNITION_RESULT -> playbackRecognitionState.requestOpenResult()
            ACTION_OPEN_LYRIC_SETTINGS -> floatingLyricRequests.requestOpenSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val enabled = settingsPreferences.showDesktopLrc.first()
            if (enabled && android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                startService(Intent(this@MainActivity, FloatingLyricService::class.java))
            }
        }
    }

    // Android 13+ 需要运行时授权才能显示系统通知
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 有悬浮窗权限则直接启动桌面歌词，否则需要前往系统设置授权
    private fun startFloatingLyricOrRequestPermission() {
        if (android.provider.Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingLyricService::class.java))
            return
        }
        android.widget.Toast.makeText(
            this,
            "请开启悬浮窗权限以显示桌面歌词",
            android.widget.Toast.LENGTH_LONG
        ).show()
        startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
        )
    }
}
