package com.lin0721.linmusic.core.auth

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.recognition.data.RecognitionHistoryPreferences
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountIsolationPreferencesTest {

    @Test
    fun personalFmModeIsolatesByUidAndMigratesLegacyToFirstLoggedInUid() = runTest {
        val userPrefs = UserPreferences(InMemoryPreferencesStore())
        val settingsStore = InMemoryPreferencesStore()
        settingsStore.edit { it[stringPreferencesKey("personal_fm_mode")] = PersonalFmMode.NIGHT_EMO.key }
        val settings = SettingsPreferences(settingsStore, userPreferences = userPrefs)

        // 未登录态使用独立匿名键，不消费旧全局键
        assertEquals(PersonalFmMode.DEFAULT, settings.personalFmMode.first())
        assertTrue(settingsStore.data.first().contains(stringPreferencesKey("personal_fm_mode")))
        settings.savePersonalFmMode(PersonalFmMode.FAMILIAR)
        assertEquals(PersonalFmMode.FAMILIAR, settings.personalFmMode.first())

        // 登录账户 1 后继承旧全局键并清理旧键
        userPrefs.upsertAccount(StoredAccount(uid = 1L, nickname = "A", cookies = "MUSIC_U=1"))
        assertEquals(PersonalFmMode.NIGHT_EMO, settings.personalFmMode.first())
        assertFalse(settingsStore.data.first().contains(stringPreferencesKey("personal_fm_mode")))

        // 登录账户 2 拥有独立默认值，互不干扰
        userPrefs.upsertAccount(StoredAccount(uid = 2L, nickname = "B", cookies = "MUSIC_U=2"))
        assertEquals(PersonalFmMode.DEFAULT, settings.personalFmMode.first())
        settings.savePersonalFmMode(PersonalFmMode.EXPLORE)
        assertEquals(PersonalFmMode.EXPLORE, settings.personalFmMode.first())

        // 切回账户 1 与匿名态均保留各自配置
        userPrefs.switchTo(1L)
        assertEquals(PersonalFmMode.NIGHT_EMO, settings.personalFmMode.first())
        userPrefs.removeAccount(1L)
        userPrefs.removeAccount(2L)
        assertEquals(PersonalFmMode.FAMILIAR, settings.personalFmMode.first())
    }

    @Test
    fun searchHistoryIsolatesByUidAndMigratesLegacyToFirstLoggedInUid() = runTest {
        val userPrefs = UserPreferences(InMemoryPreferencesStore())
        val searchStore = InMemoryPreferencesStore()
        searchStore.edit { it[stringPreferencesKey("search_history")] = Json.encodeToString(listOf("旧关键词")) }
        val searchHistory = SearchHistoryPreferences(searchStore, userPrefs)

        // 匿名态使用独立键，保留旧全局键给首个登录账户
        assertEquals(emptyList<String>(), searchHistory.history.first())
        searchHistory.addKeyword("匿名词")
        assertEquals(listOf("匿名词"), searchHistory.history.first())

        userPrefs.upsertAccount(StoredAccount(uid = 10L, nickname = "U1", cookies = "MUSIC_U=1"))
        assertEquals(listOf("旧关键词"), searchHistory.history.first())
        searchHistory.addKeyword("U1新词")
        assertEquals(listOf("U1新词", "旧关键词"), searchHistory.history.first())
        assertFalse(searchStore.data.first().contains(stringPreferencesKey("search_history")))

        userPrefs.upsertAccount(StoredAccount(uid = 20L, nickname = "U2", cookies = "MUSIC_U=2"))
        assertEquals(emptyList<String>(), searchHistory.history.first())
        searchHistory.addKeyword("U2词")
        assertEquals(listOf("U2词"), searchHistory.history.first())

        userPrefs.switchTo(10L)
        assertEquals(listOf("U1新词", "旧关键词"), searchHistory.history.first())
    }

    @Test
    fun recognitionHistoryIsolatesByUidAndMigratesLegacyToFirstLoggedInUid() = runTest {
        val userPrefs = UserPreferences(InMemoryPreferencesStore())
        val recStore = InMemoryPreferencesStore()
        val legacyJson = """[{"id":"legacy1","songId":7,"title":"老歌","artists":"歌手","coverUrl":"","startTimeMs":0,"recognizedAt":1}]"""
        recStore.edit { it[stringPreferencesKey("recognition_history")] = legacyJson }
        val recognitionHistory = RecognitionHistoryPreferences(recStore, userPrefs)

        assertTrue(recognitionHistory.history.first().isEmpty())
        recognitionHistory.record(
            RecognitionCandidate(
                songId = 100L,
                title = "匿名识曲",
                artists = "Anon",
                album = "",
                coverUrl = "",
                startTimeMs = 0L,
                durationMs = 0L
            )
        )
        assertEquals(listOf(100L), recognitionHistory.history.first().map { it.songId })

        userPrefs.upsertAccount(StoredAccount(uid = 1L, nickname = "A", cookies = "MUSIC_U=1"))
        assertEquals(listOf(7L), recognitionHistory.history.first().map { it.songId })
        assertFalse(recStore.data.first().contains(stringPreferencesKey("recognition_history")))

        userPrefs.upsertAccount(StoredAccount(uid = 2L, nickname = "B", cookies = "MUSIC_U=2"))
        assertTrue(recognitionHistory.history.first().isEmpty())
        recognitionHistory.record(
            RecognitionCandidate(
                songId = 200L,
                title = "B识曲",
                artists = "B",
                album = "",
                coverUrl = "",
                startTimeMs = 0L,
                durationMs = 0L
            )
        )
        assertEquals(listOf(200L), recognitionHistory.history.first().map { it.songId })

        userPrefs.switchTo(1L)
        assertEquals(listOf(7L), recognitionHistory.history.first().map { it.songId })
    }
}
