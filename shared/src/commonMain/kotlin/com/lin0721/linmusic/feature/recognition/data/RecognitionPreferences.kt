package com.lin0721.linmusic.feature.recognition.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.domain.RecognitionMode
import kotlinx.coroutines.flow.first

private const val TAG = "RecognitionPreferences"

// 识曲的轻量设置：上次选用的识别方式
class RecognitionPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_LAST_MODE = stringPreferencesKey("last_mode")
    }

    suspend fun lastMode(): RecognitionMode? {
        val raw = runCatching { dataStore.data.first()[KEY_LAST_MODE] }
            .onFailure { AppLogger.w(TAG, "读取上次识别方式失败", it) }
            .getOrNull()
        return RecognitionMode.entries.firstOrNull { it.name == raw }
    }

    suspend fun setLastMode(mode: RecognitionMode) {
        runCatching { dataStore.edit { it[KEY_LAST_MODE] = mode.name } }
            .onFailure { AppLogger.w(TAG, "保存上次识别方式失败", it) }
    }
}
