package com.lin0721.linmusic.core.platform

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.download.recognition.parseSongIdTag
import com.lin0721.linmusic.core.download.recognition.parseTrackIdTag
import com.lin0721.linmusic.core.player.LoudnessEntry
import com.lin0721.linmusic.core.player.LoudnessSource
import com.lin0721.linmusic.core.player.LoudnessStore
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.songquality.SongQualityCache
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LegacyUpgradeSnapshotTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacyUserAndPlaybackAndCachesUpgradeSeamlesslyWithKugouCoexistence() = runTest {
        // 1. UserPreferences 老版本仅含网易 accounts_json + active_uid 升级
        val userStore = InMemoryPreferencesStore()
        userStore.edit { prefs ->
            prefs[stringPreferencesKey("accounts_json")] = """[{"uid":90001,"nickname":"老用户","avatarUrl":"https://example.com/a.jpg","cookies":"MUSIC_U=legacy"}]"""
            prefs[longPreferencesKey("active_uid")] = 90001L
        }
        val userPrefs = UserPreferences(userStore)
        assertEquals(90001L, userPrefs.activeUid.first())
        val kugouAccountId = AccountId.kugou("kg_10086")
        userPrefs.upsertPlatformAccount(
            StoredAccount(
                uid = 0L,
                nickname = "酷狗用户",
                cookies = "token=kg",
                platform = MusicPlatform.KUGOU,
                platformUid = "kg_10086"
            )
        )
        assertEquals(90001L, userPrefs.activeAccountOf(MusicPlatform.NETEASE).first()?.uid)
        assertEquals(kugouAccountId, userPrefs.activeAccountOf(MusicPlatform.KUGOU).first()?.accountId)

        // 2. PlaybackPreferences 老版本 last_song_id 升级
        val playbackStore = InMemoryPreferencesStore()
        playbackStore.edit { prefs ->
            prefs[longPreferencesKey("last_song_id")] = 556677L
        }
        val playbackPrefs = PlaybackPreferences(playbackStore)
        assertEquals(TrackId.netease(556677L), playbackPrefs.playbackState.first().effectiveTrackId)

        val tempDir = Files.createTempDirectory("legacy-upgrade-test").toFile()
        try {
            // 3. LoudnessStore 老版本 Long id 映射
            val loudnessFile = File(tempDir, "loudness.json")
            val loudnessStore = LoudnessStore(loudnessFile)
            loudnessStore.put(12345L, LoudnessEntry(lufs = -14.2, peak = 0.95, source = LoudnessSource.TAG))
            assertEquals(-14.2, loudnessStore.get(TrackId.netease(12345L))!!.lufs, 0.001)

            // 4. LyricsCache 老版本 lyrics/amll/$songId.ttml
            val legacyAmllDir = File(tempDir, "lyrics/amll").apply { mkdirs() }
            File(legacyAmllDir, "8899.ttml").writeText("<tt>legacy</tt>")
            val lyricsCache = LyricsCache(tempDir)
            assertEquals("<tt>legacy</tt>", lyricsCache.getRaw(TrackId.netease(8899L), LyricsCache.Namespace.AMLL))

            // 5. SongQualityCache 老版本 q_$songId 键
            val now = 1_700_000_000_000L
            val qualityStore = InMemoryPreferencesStore()
            qualityStore.edit { prefs ->
                prefs[stringPreferencesKey("q_8899")] = "lossless|$now"
            }
            val qualityCache = SongQualityCache(
                enabledFlow = MutableStateFlow(true),
                fetcher = { emptyMap() },
                scope = backgroundScope,
                dataStore = qualityStore,
                nowMs = { now }
            )
            runCurrent()
            assertEquals("lossless", qualityCache.get(TrackId.netease(8899L)))

            // 6. PlaylistExternalTrackStore 老版本 Long 歌单外链
            val extStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
            extStore.add(
                playlistId = 1001L,
                tracks = listOf(ExternalTrack(platform = MusicPlatform.KUGOU, id = "kg_hash", name = "T", artists = "A"))
            )
            assertTrue(extStore.contains(PlaylistId.netease(1001L), TrackId.kugou("kg_hash")))
            val snap = extStore.snapshot()
            assertEquals(1, snap.entries[1001L]?.size)

            // 7. DownloadRecord 老版本无 trackId JSON 与 parseSongIdTag / parseTrackIdTag
            val legacyDownloadJson = """{"songId":7788,"mediaStoreUri":"/tmp/a.mp3","quality":"lossless","downloadedAt":1000,"fileSize":1024}"""
            val record = json.decodeFromString<DownloadRecord>(legacyDownloadJson)
            assertNull(record.trackId)
            assertEquals(TrackId.netease(7788L), record.effectiveTrackId)
            assertEquals(7788L, parseSongIdTag("7788"))
            assertNull(parseSongIdTag("kugou:abcdef"))
            assertNotNull(parseTrackIdTag("kugou:abcdef"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
