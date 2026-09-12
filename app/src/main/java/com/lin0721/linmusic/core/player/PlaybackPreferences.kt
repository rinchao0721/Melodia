package com.lin0721.linmusic.core.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "PlaybackPreferences"

private val Context.dataStore by preferencesDataStore(name = "playback_prefs")

data class PlaybackState(
    val songId: Long = -1,
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val lastPositionMs: Long = 0,
    val durationMs: Long = 0
)

data class QueueState(
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val playContext: String? = null
)

class PlaybackPreferences(private val context: Context) {

    companion object {
        private val KEY_SONG_ID = longPreferencesKey("last_song_id")
        private val KEY_TITLE = stringPreferencesKey("last_song_title")
        private val KEY_ARTIST = stringPreferencesKey("last_song_artist")
        private val KEY_COVER = stringPreferencesKey("last_song_cover")
        private val KEY_POSITION = longPreferencesKey("last_position_ms")
        private val KEY_DURATION = longPreferencesKey("last_duration_ms")
        private val KEY_PLAY_MODE = stringPreferencesKey("play_mode")
        private val KEY_QUEUE = stringPreferencesKey("play_queue")
        private val KEY_QUEUE_INDEX = intPreferencesKey("queue_index")
        private val KEY_PLAY_CONTEXT = stringPreferencesKey("play_context")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val playbackState: Flow<PlaybackState> = context.dataStore.data.map { prefs ->
        PlaybackState(
            songId = prefs[KEY_SONG_ID] ?: -1,
            title = prefs[KEY_TITLE] ?: "",
            artist = prefs[KEY_ARTIST] ?: "",
            coverUrl = prefs[KEY_COVER] ?: "",
            lastPositionMs = prefs[KEY_POSITION] ?: 0,
            durationMs = prefs[KEY_DURATION] ?: 0
        )
    }

    val playMode: Flow<PlayMode> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_PLAY_MODE]
        runCatching { PlayMode.valueOf(name ?: "") }
            .onFailure { AppLogger.w(TAG, "播放模式反序列化失败 value=$name", it) }
            .getOrDefault(PlayMode.LIST_LOOP)
    }.distinctUntilChanged()

    suspend fun savePlaybackState(state: PlaybackState) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SONG_ID] = state.songId
            prefs[KEY_TITLE] = state.title
            prefs[KEY_ARTIST] = state.artist
            prefs[KEY_COVER] = state.coverUrl
            prefs[KEY_POSITION] = state.lastPositionMs
            prefs[KEY_DURATION] = state.durationMs
        }
    }

    suspend fun savePlayMode(mode: PlayMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLAY_MODE] = mode.name
        }
    }

    val queueState: Flow<QueueState> = context.dataStore.data.map { prefs ->
        val queueJson = prefs[KEY_QUEUE]
        val queue = if (queueJson.isNullOrBlank()) emptyList()
                    else runCatching { json.decodeFromString<List<QueueItem>>(queueJson) }
                        .onFailure { AppLogger.w(TAG, "播放队列反序列化失败", it) }
                        .getOrDefault(emptyList())
        QueueState(
            queue = queue,
            currentIndex = prefs[KEY_QUEUE_INDEX] ?: -1,
            playContext = prefs[KEY_PLAY_CONTEXT]
        )
    }

    suspend fun saveQueueState(queue: List<QueueItem>, currentIndex: Int, playContext: String?) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUEUE] = json.encodeToString(queue)
            prefs[KEY_QUEUE_INDEX] = currentIndex
            if (playContext != null) prefs[KEY_PLAY_CONTEXT] = playContext
            else prefs.remove(KEY_PLAY_CONTEXT)
        }
    }
}
