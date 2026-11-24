package com.lin0721.linmusic.core.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "PlaybackPreferences"

data class PlaybackState(
    val songId: Long = -1,
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val lastPositionMs: Long = 0,
    val durationMs: Long = 0,
    val trackId: TrackId? = null
) {
    val effectiveTrackId: TrackId?
        get() = trackId ?: songId.takeIf { it > 0L }?.let(TrackId::netease)
}

data class QueueState(
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val playContext: String? = null,
    val playSource: PlaySource? = null
)

// 本地维护的最近播放歌单，服务端不会记录本客户端的歌单播放
@Serializable
data class LocalRecentPlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String = "",
    val playTime: Long,
    val platform: MusicPlatform = MusicPlatform.NETEASE,
    val rawId: String = "",
    val accountKey: String? = null,
    val kind: PlaySource.Kind = PlaySource.Kind.PLAYLIST
) {
    val effectiveRawId: String
        get() = rawId.trim().takeIf { it.isNotEmpty() } ?: id.toString()

    val dedupeKey: String
        get() = if (accountKey.isNullOrBlank()) {
            "${platform.key}:$effectiveRawId"
        } else {
            "$accountKey:${platform.key}:$effectiveRawId"
        }
}

// 最近播放过的私人FM曲目摘要，供非 FM 上下文时在首页卡片回显封面
@Serializable
data class RecentFmTrack(
    val songId: Long,
    val coverUrl: String = "",
    val title: String = "",
    val platform: MusicPlatform = MusicPlatform.NETEASE,
    val rawId: String = ""
) {
    val effectiveRawId: String
        get() = rawId.trim().takeIf { it.isNotEmpty() } ?: songId.toString()

    val dedupeKey: String
        get() = "${platform.key}:$effectiveRawId"

    val isValid: Boolean
        get() = if (platform == MusicPlatform.NETEASE && rawId.isBlank()) {
            songId > 0L
        } else {
            effectiveRawId.isNotBlank()
        }
}

class PlaybackPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_SONG_ID = longPreferencesKey("last_song_id")
        private val KEY_TRACK_ID = stringPreferencesKey("last_track_id")
        private val KEY_TITLE = stringPreferencesKey("last_song_title")
        private val KEY_ARTIST = stringPreferencesKey("last_song_artist")
        private val KEY_COVER = stringPreferencesKey("last_song_cover")
        private val KEY_POSITION = longPreferencesKey("last_position_ms")
        private val KEY_DURATION = longPreferencesKey("last_duration_ms")
        private val KEY_PLAY_MODE = stringPreferencesKey("play_mode")
        private val KEY_QUEUE = stringPreferencesKey("play_queue")
        private val KEY_QUEUE_INDEX = intPreferencesKey("queue_index")
        private val KEY_PLAY_CONTEXT = stringPreferencesKey("play_context")
        private val KEY_PLAY_SOURCE = stringPreferencesKey("play_source")
        private val KEY_RECENT_PLAYLISTS = stringPreferencesKey("local_recent_playlists")
        private val KEY_RECENT_FM_TRACKS = stringPreferencesKey("recent_fm_tracks")
        private const val MAX_LOCAL_RECENT_PLAYLISTS = 20
        private const val MAX_RECENT_FM_TRACKS = 3
        private val json = Json { ignoreUnknownKeys = true }
    }

    val recentPlaylists: Flow<List<LocalRecentPlaylist>> = dataStore.data.map { prefs ->
        decodeRecentPlaylists(prefs[KEY_RECENT_PLAYLISTS])
    }.distinctUntilChanged()

    val recentFmTracks: Flow<List<RecentFmTrack>> = dataStore.data.map { prefs ->
        decodeRecentFmTracks(prefs[KEY_RECENT_FM_TRACKS])
    }.distinctUntilChanged()

    // 同一歌单只留最新一条，按播放时间倒序保留最近 20 个（兼容多平台 rawId 去重）
    suspend fun recordRecentPlaylist(item: LocalRecentPlaylist) {
        dataStore.edit { prefs ->
            val merged = (listOf(item) + decodeRecentPlaylists(prefs[KEY_RECENT_PLAYLISTS]))
                .distinctBy { it.dedupeKey }
                .take(MAX_LOCAL_RECENT_PLAYLISTS)
            prefs[KEY_RECENT_PLAYLISTS] = json.encodeToString(merged)
        }
    }

    // 保留最近 3 首 FM 曲目，按平台+标识去重并优先保留本批新顺序；不足 3 首时用已有记录补齐
    suspend fun saveRecentFmTracks(tracks: List<RecentFmTrack>) {
        val incoming = tracks
            .filter { it.isValid }
            .distinctBy { it.dedupeKey }
        if (incoming.isEmpty()) return
        dataStore.edit { prefs ->
            val existing = decodeRecentFmTracks(prefs[KEY_RECENT_FM_TRACKS])
            val merged = (incoming + existing)
                .distinctBy { it.dedupeKey }
                .take(MAX_RECENT_FM_TRACKS)
            prefs[KEY_RECENT_FM_TRACKS] = json.encodeToString(merged)
        }
    }

    private fun decodeRecentPlaylists(raw: String?): List<LocalRecentPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<LocalRecentPlaylist>>(raw) }
            .onFailure { AppLogger.w(TAG, "本地最近播放歌单反序列化失败", it) }
            .getOrDefault(emptyList())
    }

    private fun decodeRecentFmTracks(raw: String?): List<RecentFmTrack> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<RecentFmTrack>>(raw) }
            .onFailure { AppLogger.w(TAG, "最近私人FM曲目反序列化失败", it) }
            .getOrDefault(emptyList())
    }

    val playbackState: Flow<PlaybackState> = dataStore.data.map { prefs ->
        val legacySongId = prefs[KEY_SONG_ID] ?: -1L
        val resolvedTrackId = prefs[KEY_TRACK_ID]?.let(TrackId::parseOrNull)
            ?: legacySongId.takeIf { it > 0L }?.let(TrackId::netease)
        PlaybackState(
            songId = legacySongId,
            title = prefs[KEY_TITLE] ?: "",
            artist = prefs[KEY_ARTIST] ?: "",
            coverUrl = prefs[KEY_COVER] ?: "",
            lastPositionMs = prefs[KEY_POSITION] ?: 0,
            durationMs = prefs[KEY_DURATION] ?: 0,
            trackId = resolvedTrackId
        )
    }

    val playMode: Flow<PlayMode> = dataStore.data.map { prefs ->
        val name = prefs[KEY_PLAY_MODE]
        if (name.isNullOrBlank()) {
            PlayMode.LIST_LOOP
        } else {
            runCatching { PlayMode.valueOf(name) }
                .onFailure { AppLogger.w(TAG, "播放模式反序列化失败 value=$name", it) }
                .getOrDefault(PlayMode.LIST_LOOP)
        }
    }.distinctUntilChanged()

    suspend fun savePlaybackState(state: PlaybackState) {
        val resolvedTrackId = state.effectiveTrackId
        val resolvedLegacySongId = if (state.songId > 0L) {
            state.songId
        } else {
            resolvedTrackId?.neteaseIdOrNull() ?: state.songId
        }
        dataStore.edit { prefs ->
            prefs[KEY_SONG_ID] = resolvedLegacySongId
            if (resolvedTrackId != null) {
                prefs[KEY_TRACK_ID] = resolvedTrackId.canonical
            } else {
                prefs.remove(KEY_TRACK_ID)
            }
            prefs[KEY_TITLE] = state.title
            prefs[KEY_ARTIST] = state.artist
            prefs[KEY_COVER] = state.coverUrl
            prefs[KEY_POSITION] = state.lastPositionMs
            prefs[KEY_DURATION] = state.durationMs
        }
    }

    suspend fun savePlayMode(mode: PlayMode) {
        dataStore.edit { prefs ->
            prefs[KEY_PLAY_MODE] = mode.name
        }
    }

    val queueState: Flow<QueueState> = dataStore.data.map { prefs ->
        val queueJson = prefs[KEY_QUEUE]
        val queue = if (queueJson.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<QueueItem>>(queueJson) }
            .onFailure { AppLogger.w(TAG, "播放队列反序列化失败", it) }
            .getOrDefault(emptyList())
        QueueState(
            queue = queue,
            currentIndex = prefs[KEY_QUEUE_INDEX] ?: -1,
            playContext = prefs[KEY_PLAY_CONTEXT],
            playSource = prefs[KEY_PLAY_SOURCE]?.let { raw ->
                runCatching { json.decodeFromString<PlaySource>(raw) }
                    .onFailure { AppLogger.w(TAG, "播放来源反序列化失败", it) }
                    .getOrNull()
            }
        )
    }

    suspend fun saveQueueState(queue: List<QueueItem>, currentIndex: Int, playContext: String?, playSource: PlaySource?) {
        dataStore.edit { prefs ->
            prefs[KEY_QUEUE] = json.encodeToString(queue)
            prefs[KEY_QUEUE_INDEX] = currentIndex
            if (playContext != null) prefs[KEY_PLAY_CONTEXT] = playContext
            else prefs.remove(KEY_PLAY_CONTEXT)
            if (playSource != null) prefs[KEY_PLAY_SOURCE] = json.encodeToString(playSource)
            else prefs.remove(KEY_PLAY_SOURCE)
        }
    }
}
