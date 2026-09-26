package com.lin0721.linmusic.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.lin0721.linmusic.core.log.AppLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "PreferencesStores"

// 同一文件只允许存在一个 DataStore 实例，按路径全局缓存；文件位置由平台决定
object PreferencesStores {
    const val USER = "user_prefs"
    const val SETTINGS = "settings_prefs"
    const val XEAPI_KEY = "xeapi_key_prefs"
    const val SEARCH_HISTORY = "search_history_prefs"

    private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

    fun get(file: File): DataStore<Preferences> = stores.getOrPut(file.absolutePath) {
        PreferenceDataStoreFactory.create(
            // 损坏时回退空数据而非崩溃
            corruptionHandler = ReplaceFileCorruptionHandler { ex ->
                AppLogger.e(TAG, "偏好数据损坏，已重置为默认值：${file.name}", ex)
                emptyPreferences()
            },
            produceFile = { file }
        )
    }
}
