package com.lin0721.linmusic.feature.recognition.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.domain.RecognitionMode
import kotlinx.coroutines.flow.first

private const val TAG = "RecognitionPreferences"

private val Context.recognitionDataStore by preferencesDataStore(
    name = "recognition_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        AppLogger.e(TAG, "识曲设置数据损坏，已重置为默认值", ex)
        emptyPreferences()
    }
)

// 识曲的轻量设置：上次选用的识别方式，用于选择弹窗的「上次使用」标签
class RecognitionPreferences(private val context: Context) {

    companion object {
        private val KEY_LAST_MODE = stringPreferencesKey("last_mode")
    }

    suspend fun lastMode(): RecognitionMode? {
        val raw = runCatching { context.recognitionDataStore.data.first()[KEY_LAST_MODE] }
            .onFailure { AppLogger.w(TAG, "读取上次识别方式失败", it) }
            .getOrNull()
        return RecognitionMode.entries.firstOrNull { it.name == raw }
    }

    suspend fun setLastMode(mode: RecognitionMode) {
        runCatching { context.recognitionDataStore.edit { it[KEY_LAST_MODE] = mode.name } }
            .onFailure { AppLogger.w(TAG, "保存上次识别方式失败", it) }
    }
}
