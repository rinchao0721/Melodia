package com.lin0721.linmusic.core.platform

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.download.recognition.LocalAudioEntry
import com.lin0721.linmusic.core.download.recognition.parseSongIdTag
import com.lin0721.linmusic.core.download.recognition.parseTrackIdTag
import com.lin0721.linmusic.core.player.LocalRecentPlaylist
import com.lin0721.linmusic.core.player.LoudnessEntry
import com.lin0721.linmusic.core.player.LoudnessSource
import com.lin0721.linmusic.core.player.LoudnessStore
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.PlaybackState
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.RecentFmTrack
import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.core.songquality.SongQualityCache
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Phase3PersistenceMigrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun queueItemDecodesLegacyJsonAndBuildsFromUnifiedTrack() {
        val legacyJson = """
            {"songId":123456,"title":"稻香","artist":"周杰伦","coverUrl":"https://example.com/a.jpg"}
        """.trimIndent()
        val legacy = json.decodeFromString<QueueItem>(legacyJson)
        assertNull(legacy.trackId)
        assertNull(legacy.kugouMeta)
        assertEquals(TrackId.netease(123456L), legacy.effectiveTrackId)
        assertEquals(MusicPlatform.NETEASE, legacy.effectivePlatform)

        val kugouUnified = UnifiedTrack(
            id = TrackId.kugou("B5E3CA48FC0D01F01DB0B6BBA570480F"),
            name = "以我",
            artists = listOf(UnifiedArtist(name = "张赫煊")),
            durationMs = 229_000L,
            coverUrl = "https://imge.kugou.com/stdmusic/400/test.jpg",
            kugouMeta = KugouTrackMeta(
                hash = "b5e3ca48fc0d01f01db0b6bba570480f",
                mixSongId = 1109118629L,
                albumAudioId = 988834222L,
                albumId = "215644565"
            )
        )
        val converted = QueueItem.fromUnified(kugouUnified)
        assertTrue(converted.songId < 0L)
        assertEquals(TrackId.kugou("b5e3ca48fc0d01f01db0b6bba570480f"), converted.effectiveTrackId)
        assertEquals(MusicPlatform.KUGOU, converted.effectivePlatform)
        assertEquals(1109118629L, converted.kugouMeta?.mixSongId)

        val encoded = json.encodeToString(QueueItem.serializer(), converted)
        val roundTrip = json.decodeFromString<QueueItem>(encoded)
        assertEquals(converted, roundTrip)
    }

    @Test
    fun playSourcePreservesLegacyJsonAndSupportsKugouContainers() {
        val legacyJson = """{"kind":"PLAYLIST","id":9988,"name":"老歌单","coverUrl":""}"""
        val legacy = json.decodeFromString<PlaySource>(legacyJson)
        assertEquals(MusicPlatform.NETEASE, legacy.platform)
        assertEquals("9988", legacy.effectiveRawId)
        assertEquals(PlaylistId.netease(9988L), legacy.playlistIdOrNull)
        assertNull(legacy.albumIdOrNull)

        val kugouAlbum = PlaySource(
            kind = PlaySource.Kind.ALBUM,
            id = -1L,
            name = "酷狗专辑",
            platform = MusicPlatform.KUGOU,
            rawId = "215644565"
        )
        assertEquals(AlbumId.kugou("215644565"), kugouAlbum.albumIdOrNull)
        assertNull(kugouAlbum.playlistIdOrNull)
    }

    @Test
    fun playbackPreferencesMigratesLegacyKeysAndPersistsMultiPlatformHistory() = runTest {
        val store = InMemoryPreferencesStore()
        // 写入老版本仅含 last_song_id / 老版 JSON 的 DataStore 数据
        store.edit { prefs ->
            prefs[longPreferencesKey("last_song_id")] = 556677L
            prefs[stringPreferencesKey("last_song_title")] = "老歌"
            prefs[stringPreferencesKey("last_song_artist")] = "老歌手"
            prefs[stringPreferencesKey("local_recent_playlists")] =
                """[{"id":1001,"name":"老歌单","coverUrl":"","playTime":100}]"""
            prefs[stringPreferencesKey("recent_fm_tracks")] =
                """[{"songId":2002,"coverUrl":"","title":"老FM"}]"""
        }

        val prefs = PlaybackPreferences(store)
        val restoredState = prefs.playbackState.first()
        assertEquals(556677L, restoredState.songId)
        assertEquals(TrackId.netease(556677L), restoredState.effectiveTrackId)

        val restoredPlaylists = prefs.recentPlaylists.first()
        assertEquals(1, restoredPlaylists.size)
        assertEquals(MusicPlatform.NETEASE, restoredPlaylists[0].platform)
        assertEquals("netease:1001", restoredPlaylists[0].dedupeKey)

        val restoredFm = prefs.recentFmTracks.first()
        assertEquals(1, restoredFm.size)
        assertTrue(restoredFm[0].isValid)
        assertEquals("netease:2002", restoredFm[0].dedupeKey)

        // 保存酷狗正在播放与最近歌单/FM
        val kugouTrackId = TrackId.kugou("abcdef0123456789")
        prefs.savePlaybackState(
            PlaybackState(
                songId = -1L,
                title = "以我",
                artist = "张赫煊",
                trackId = kugouTrackId
            )
        )
        val afterKugou = prefs.playbackState.first()
        assertEquals(kugouTrackId, afterKugou.trackId)
        assertEquals(kugouTrackId, afterKugou.effectiveTrackId)

        prefs.recordRecentPlaylist(
            LocalRecentPlaylist(
                id = 0L,
                name = "酷狗歌单",
                playTime = 200L,
                platform = MusicPlatform.KUGOU,
                rawId = "gcid_xyz"
            )
        )
        prefs.recordRecentPlaylist(
            LocalRecentPlaylist(
                id = 0L,
                name = "酷狗歌单(更新)",
                playTime = 300L,
                platform = MusicPlatform.KUGOU,
                rawId = "gcid_xyz"
            )
        )
        val mergedPlaylists = prefs.recentPlaylists.first()
        assertEquals(2, mergedPlaylists.size)
        assertEquals("酷狗歌单(更新)", mergedPlaylists.first().name)

        prefs.saveRecentFmTracks(
            listOf(
                RecentFmTrack(
                    songId = -1L,
                    title = "酷狗FM",
                    platform = MusicPlatform.KUGOU,
                    rawId = "kg_fm_hash_1"
                )
            )
        )
        val mergedFm = prefs.recentFmTracks.first()
        assertEquals(2, mergedFm.size)
        assertEquals("kugou:kg_fm_hash_1", mergedFm.first().dedupeKey)
    }

    @Test
    fun loudnessStoreReadsLegacyIdAndPersistsCrossPlatformTrackId() = runTest {
        val tempDir = Files.createTempDirectory("loudness-migration-test").toFile()
        try {
            val file = File(tempDir, "loudness.json")
            file.writeText(
                """{"entries":[{"id":42,"lufs":-14.5,"peak":0.92,"source":"NETEASE"}]}"""
            )

            val store = LoudnessStore(file)
            val legacyByLong = store.get(42L)
            assertNotNull(legacyByLong)
            assertEquals(-14.5, legacyByLong!!.lufs, 0.001)
            assertEquals(legacyByLong, store.get(TrackId.netease(42L)))

            val kgId = TrackId.kugou("B5E3CA48FC0D01F01DB0B6BBA570480F")
            store.put(kgId, LoudnessEntry(lufs = -11.2, peak = 0.98, source = LoudnessSource.TAG))

            // 重新实例加载，验证老网易 id 字段与新 trackId 字段共存
            val reloaded = LoudnessStore(file)
            assertEquals(-14.5, reloaded.get(42L)!!.lufs, 0.001)
            assertEquals(-11.2, reloaded.get(kgId)!!.lufs, 0.001)
            val rawText = file.readText()
            assertTrue(rawText.contains(""""id":42"""))
            assertTrue(rawText.contains(""""trackId":"kugou:b5e3ca48fc0d01f01db0b6bba570480f""""))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun lyricsCachePreservesLegacyAmllFilesAndIsolatesKrcNamespace() {
        val tempDir = Files.createTempDirectory("lyrics-cache-migration-test").toFile()
        try {
            val legacyFile = File(tempDir, "lyrics/amll/777.ttml")
            legacyFile.parentFile.mkdirs()
            legacyFile.writeText("<tt>legacy-amll</tt>")
            val legacyMiss = File(tempDir, "lyrics/amll/888.miss")
            legacyMiss.writeText("")

            val cache = LyricsCache(tempDir)
            assertEquals("<tt>legacy-amll</tt>", cache.getRaw(777L))
            assertEquals("<tt>legacy-amll</tt>", cache.getRaw(TrackId.netease(777L), LyricsCache.Namespace.AMLL))
            assertTrue(cache.isNegative(888L))
            assertTrue(cache.isNegative(TrackId.netease(888L), LyricsCache.Namespace.AMLL))

            val kgId = TrackId.kugou("ABC123DEF456")
            cache.putRaw(kgId, "[1000,500]<0,500,0>嗨", LyricsCache.Namespace.KRC)
            assertEquals("[1000,500]<0,500,0>嗨", cache.getRaw(kgId, LyricsCache.Namespace.KRC))
            assertNull(cache.getRaw(kgId, LyricsCache.Namespace.AMLL))
            assertTrue(File(tempDir, "lyrics/krc").listFiles()?.isNotEmpty() == true)
            assertTrue(cache.diskSizeBytes() > 0L)

            cache.clear()
            assertNull(cache.getRaw(777L))
            assertNull(cache.getRaw(kgId, LyricsCache.Namespace.KRC))
            assertEquals(0L, cache.diskSizeBytes())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun songQualityCacheReadsLegacyAndMultiPlatformPersistedKeys() = runTest {
        val store = InMemoryPreferencesStore()
        val now = 1_700_000_000_000L
        store.edit { prefs ->
            prefs[stringPreferencesKey("q_1001")] = "hires|$now"
            prefs[stringPreferencesKey("q_kugou:oldhash")] = "jymaster|$now"
        }

        val platformCalls = mutableListOf<Pair<MusicPlatform, List<TrackId>>>()
        val cache = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = { emptyMap() },
            scope = backgroundScope,
            dataStore = store,
            nowMs = { now },
            platformFetcher = { platform, ids ->
                platformCalls += platform to ids
                ids.associateWith { "sky" }
            }
        )
        runCurrent()

        assertEquals("hires", cache.levels.value[1001L])
        assertEquals("hires", cache.get(TrackId.netease(1001L)))
        assertEquals("jymaster", cache.get(TrackId.kugou("oldhash")))

        val newKgId = TrackId.kugou("newhash")
        cache.prefetchTrackIds(listOf(newKgId))
        assertEquals(listOf(MusicPlatform.KUGOU to listOf(newKgId)), platformCalls)
        assertEquals("sky", cache.get(newKgId))
        assertEquals("sky", cache.trackLevels.value[newKgId])

        // 验证持久化键格式保持 "q_kugou:newhash" 且老网易键不破坏
        val rawMap = store.data.first().asMap()
        assertEquals("sky|$now", rawMap[stringPreferencesKey("q_kugou:newhash")])
    }

    @Test
    fun downloadAndRecognitionModelsKeepBackwardCompatibilityAndParseTags() {
        val legacyDownloadJson = """
            {"songId":321,"mediaStoreUri":"content://x/1","quality":"lossless","downloadedAt":1000,"fileSize":999}
        """.trimIndent()
        val downloadRecord = json.decodeFromString<DownloadRecord>(legacyDownloadJson)
        assertNull(downloadRecord.trackId)
        assertEquals(TrackId.netease(321L), downloadRecord.effectiveTrackId)

        val legacyEntryJson = """
            {"uri":"/music/a.flac","title":"Song","artist":"Singer"}
        """.trimIndent()
        val entry = json.decodeFromString<LocalAudioEntry>(legacyEntryJson).copy(songIdTag = 654L)
        assertNull(entry.trackIdTag)
        assertEquals(TrackId.netease(654L), entry.effectiveTrackIdTag)

        // 标签解析兼容性测试
        assertEquals(TrackId.netease(123L), parseTrackIdTag("123"))
        assertEquals(TrackId.netease(123L), parseTrackIdTag("netease:123"))
        assertEquals(TrackId.kugou("abcdef"), parseTrackIdTag("kugou:ABCDEF"))
        assertNull(parseTrackIdTag("0"))
        assertNull(parseTrackIdTag("-5"))
        assertNull(parseTrackIdTag("netease:0"))
        assertNull(parseTrackIdTag("unknown:123"))

        assertEquals(123L, parseSongIdTag("123"))
        assertEquals(123L, parseSongIdTag("netease:123"))
        assertNull(parseSongIdTag("kugou:abcdef"))
        assertFalse(parseSongIdTag("0") != null)
    }
}
