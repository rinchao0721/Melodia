package com.lin0721.linmusic.core.auth

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "UserPreferences"

// 损坏时回退空数据（相当于自动登出）而非崩溃；用户可重新登录
private val Context.userDataStore by preferencesDataStore(
    name = "user_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        AppLogger.e(TAG, "用户偏好数据损坏，已重置为默认值", ex)
        emptyPreferences()
    }
)

// 用户基本信息模型
@Serializable
data class UserProfile(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String
)

// 用户信息持久化管理 (DataStore + JSON)
class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_USER_PROFILE = stringPreferencesKey("user_profile_json")
        private val KEY_COOKIES = stringPreferencesKey("user_cookies")
        private val KEY_BLOCKED_ARTIST_IDS = stringSetPreferencesKey("blocked_artist_ids")
    }

    private val json = Json { ignoreUnknownKeys = true }

    // 读取用户信息
    val userProfile: Flow<UserProfile?> = context.userDataStore.data.map { prefs ->
        prefs[KEY_USER_PROFILE]?.let { jsonStr ->
            runCatching { json.decodeFromString<UserProfile>(jsonStr) }
                .onFailure { AppLogger.w(TAG, "用户信息反序列化失败", it) }
                .getOrNull()
        }
    }

    // 读取 Cookie
    val cookies: Flow<String?> = context.userDataStore.data.map { prefs ->
        prefs[KEY_COOKIES]
    }

    // 读取屏蔽艺人 ID 列表
    val blockedArtistIds: Flow<Set<Long>> = context.userDataStore.data.map { prefs ->
        prefs[KEY_BLOCKED_ARTIST_IDS]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    // 屏蔽/取消屏蔽艺人
    suspend fun toggleBlockArtist(artistId: Long) {
        context.userDataStore.edit { prefs ->
            val current = prefs[KEY_BLOCKED_ARTIST_IDS] ?: emptySet()
            val newSet = if (current.contains(artistId.toString())) {
                current - artistId.toString()
            } else {
                current + artistId.toString()
            }
            prefs[KEY_BLOCKED_ARTIST_IDS] = newSet
        }
    }

    // 保存用户信息
    suspend fun saveUserProfile(profile: UserProfile) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_USER_PROFILE] = json.encodeToString(profile)
        }
    }

    // 保存 Cookie
    suspend fun saveCookies(cookies: String) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_COOKIES] = cookies
        }
    }

    // 清除用户信息与 Cookie（退出登录）
    suspend fun clearUserProfile() {
        context.userDataStore.edit { prefs ->
            prefs.remove(KEY_USER_PROFILE)
            prefs.remove(KEY_COOKIES)
        }
    }
}
