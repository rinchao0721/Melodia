package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.desktop.platform.native.AppPaths

import com.lin0721.linmusic.core.AppEnvironment
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private const val TAG = "DesktopLogging"

// 须先于 Koin 调用
object DesktopLogging {

    // gradle run 传入，安装包没有
    const val DEBUG_PROPERTY = "melodia.debug"

    fun install(
        logDir: File = AppPaths.current.logDir,
        savedLevel: () -> String = {
            val settings = SettingsPreferences(PreferencesStores.get(AppPaths.current.preferencesFile(PreferencesStores.SETTINGS)))
            runBlocking { settings.logLevel.first() }
        }
    ) {
        AppEnvironment.isDebug = System.getProperty(DEBUG_PROPERTY) == "true"
        AppLogger.init(logDir, savedLevel)
        DesktopCrashHandler.install()
        AppLogger.i(TAG, "启动 version=${AppInfo.version} debug=${AppEnvironment.isDebug}")
    }
}
