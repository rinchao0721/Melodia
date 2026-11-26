package com.lin0721.linmusic.desktop.di

import com.lin0721.linmusic.desktop.platform.AutoStartService
import com.lin0721.linmusic.desktop.platform.BrowserLocator
import com.lin0721.linmusic.desktop.platform.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.MediaSessionService
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.platform.fallback.fallbackPlatformModule
import com.lin0721.linmusic.desktop.platform.windows.windowsPlatformModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.dsl.koinApplication

class PlatformModuleTest {

    @Test
    fun selectsWindowsModuleOnWindows() {
        assertSame(windowsPlatformModule, platformModuleFor("Windows 11"))
        assertSame(windowsPlatformModule, platformModuleFor("windows 10"))
    }

    @Test
    fun fallsBackOnOtherSystems() {
        assertSame(fallbackPlatformModule, platformModuleFor("Linux"))
        assertSame(fallbackPlatformModule, platformModuleFor("Mac OS X"))
        assertSame(fallbackPlatformModule, platformModuleFor(""))
    }

    // 两个模块都要能解析出全部平台服务，缺一个启动时才会暴露
    @Test
    fun bothModulesProvideEveryService() {
        listOf(windowsPlatformModule, fallbackPlatformModule).forEach { module ->
            val koin = koinApplication { modules(module) }.koin
            assertNotNull(koin.get<MediaSessionService>())
            assertNotNull(koin.get<GlobalHotkeyService>())
            assertNotNull(koin.get<AutoStartService>())
            assertNotNull(koin.get<WindowEffects>())
            assertNotNull(koin.get<BrowserLocator>())
            koin.close()
        }
    }

    @Test
    fun fallbackDisablesEverything() {
        val koin = koinApplication { modules(fallbackPlatformModule) }.koin
        assertFalse(koin.get<MediaSessionService>().start())
        assertFalse(koin.get<GlobalHotkeyService>().isSupported)
        assertNotNull(koin.get<AutoStartService>().unavailableReason)
        assertFalse(koin.get<WindowEffects>().supportsClickThrough)
        assertFalse(koin.get<WindowEffects>().supportsBorderlessFullscreen)
        assertNull(koin.get<BrowserLocator>().findChromiumBrowser())
        koin.close()
    }
}
