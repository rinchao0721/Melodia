package com.lin0721.linmusic.desktop.platform.fallback

import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.platform.AutoStartService
import com.lin0721.linmusic.desktop.platform.BrowserLocator
import com.lin0721.linmusic.desktop.platform.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import com.lin0721.linmusic.desktop.platform.MediaSessionService
import com.lin0721.linmusic.desktop.platform.TaskbarThumbnailService
import com.lin0721.linmusic.desktop.platform.WindowEdge
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.dsl.module
import java.awt.Point
import java.awt.Window

// 尚未适配的系统：平台能力全部为空实现，界面据此隐藏对应入口
val fallbackPlatformModule = module {
    single<MediaSessionService> { NoopMediaSession() }
    single<GlobalHotkeyService> { NoopGlobalHotkeys() }
    single<AutoStartService> { UnsupportedAutoStart() }
    single<WindowEffects> { NoopWindowEffects() }
    single<TaskbarThumbnailService> { NoopTaskbarThumbnail() }
    single<BrowserLocator> { BrowserLocator { null } }
}

internal class NoopTaskbarThumbnail : TaskbarThumbnailService {
    override fun attach(window: Window) = Unit
    override suspend fun bind(controller: PlaybackController) = Unit
    override fun detach() = Unit
}

internal class NoopMediaSession : MediaSessionService {
    override val available: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override fun start(): Boolean = false
    override fun setEnabled(value: Boolean) = Unit
    override suspend fun bind(controller: PlaybackController, playerViewModel: PlayerViewModel) = Unit
    override fun shutdown() = Unit
}

internal class NoopGlobalHotkeys : GlobalHotkeyService {
    override val isSupported: Boolean = false
    override var onAction: ((HotkeyAction) -> Unit)? = null
    override val failed: StateFlow<Set<HotkeyAction>> = MutableStateFlow(emptySet<HotkeyAction>()).asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
    override fun apply(custom: Map<HotkeyAction, HotkeyCombo?>, mediaKeys: Boolean) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
}

internal class UnsupportedAutoStart : AutoStartService {
    override val unavailableReason: String = "当前系统暂不支持"
    override fun isEnabled(): Boolean = false
    override fun setEnabled(enabled: Boolean): Boolean = false
}

internal class NoopWindowEffects : WindowEffects {
    override val supportsClickThrough: Boolean = false
    override fun install(window: Window) = Unit
    override fun uninstall(window: Window) = Unit
    override fun applyFrame(window: Window, maximized: Boolean, borderRgb: Int) = Unit
    override fun systemBorderAccent(): Int? = null
    override fun systemAppsUseLightTheme(): Boolean = false
    override fun observeSystemTheme(onChanged: () -> Unit): () -> Unit = {}
    override fun setClickThrough(window: Window, enabled: Boolean) = Unit
    override fun beginSystemMove(window: Window): Boolean = false
    override fun beginSystemResize(window: Window, edge: WindowEdge): Boolean = false
    override fun cursorPhysicalPosition(): Point? = null
}
