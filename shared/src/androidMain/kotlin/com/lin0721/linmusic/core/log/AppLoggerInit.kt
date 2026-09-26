package com.lin0721.linmusic.core.log

import android.content.Context
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.get
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

fun AppLogger.init(context: Context) {
    val settings = SettingsPreferences(PreferencesStores.get(context, PreferencesStores.SETTINGS))
    init(File(context.cacheDir, "logs")) { runBlocking { settings.logLevel.first() } }
}
