package com.lin0721.linmusic.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "UserPreferences"

// 用户基本信息模型
@Serializable
data class UserProfile(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String
)

// 本地存储的完整账户快照（含凭据与近期使用时间）
@Serializable
data class StoredAccount(
    val uid: Long,
    val nickname: String = "",
    val avatarUrl: String = "",
    val cookies: String = "",
    val lastLoginRefreshAt: Long = 0L,
    val lastUsedAt: Long = 0L
) {
    fun toUserProfile(): UserProfile = UserProfile(uid = uid, nickname = nickname, avatarUrl = avatarUrl)
}

// 写入账户列表的结果
sealed interface UpsertAccountResult {
    data class Added(val account: StoredAccount) : UpsertAccountResult
    data class Updated(val account: StoredAccount) : UpsertAccountResult
    data object LimitReached : UpsertAccountResult
}

// 用户信息持久化管理 (DataStore + JSON)，支持多账户列表及按账户隔离的屏蔽艺人
class UserPreferences(
    private val dataStore: DataStore<Preferences>,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        const val MAX_ACCOUNTS = 5

        private val KEY_ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        private val KEY_ACTIVE_UID = longPreferencesKey("active_uid")
        // 兼容旧单账户直接调用 saveCookies -> saveUserProfile 时暂存未关联 uid 的 Cookie
        private val KEY_PENDING_COOKIES = stringPreferencesKey("pending_cookies")

        // 旧版单账户键（迁移后删除）
        private val KEY_USER_PROFILE = stringPreferencesKey("user_profile_json")
        private val KEY_COOKIES = stringPreferencesKey("user_cookies")
        private val KEY_LAST_LOGIN_REFRESH = longPreferencesKey("last_login_refresh_at")
        private val KEY_BLOCKED_ARTIST_IDS = stringSetPreferencesKey("blocked_artist_ids")

        private fun blockedArtistKey(uid: Long?) =
            stringSetPreferencesKey(if (uid != null) "blocked_artist_ids_$uid" else "blocked_artist_ids_anon")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val migrationMutex = Mutex()

    // 若存在旧版单账户键且新账户列表为空，在单个 DataStore 事务内一次性完成迁移并删旧键
    suspend fun ensureMigrated() {
        val current = dataStore.data.first()
        if (!hasLegacyMigrationPending(current)) return
        migrationMutex.withLock {
            val latest = dataStore.data.first()
            if (!hasLegacyMigrationPending(latest)) return@withLock
            dataStore.edit { prefs ->
                migrateLegacyInTransaction(prefs)
            }
        }
    }

    private fun migratedData(): Flow<Preferences> = flow {
        ensureMigrated()
        emitAll(dataStore.data)
    }

    // 当前已登录账户列表（不含凭据的公开资料）
    val accounts: Flow<List<UserProfile>> = migratedData()
        .map { prefs -> decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).map { it.toUserProfile() } }
        .distinctUntilChanged()

    // 完整存储账户列表（供需要 Cookie/时间信息时读取）
    val storedAccounts: Flow<List<StoredAccount>> = migratedData()
        .map { prefs -> decodeAccounts(prefs[KEY_ACCOUNTS_JSON]) }
        .distinctUntilChanged()

    // 当前活跃账户 UID；未登录为 null
    val activeUid: Flow<Long?> = migratedData()
        .map { prefs -> resolveActiveAccount(prefs)?.uid }
        .distinctUntilChanged()

    // 读取当前活跃账户用户信息
    val userProfile: Flow<UserProfile?> = migratedData()
        .map { prefs -> resolveActiveAccount(prefs)?.toUserProfile() }
        .distinctUntilChanged()

    // 读取当前活跃账户 Cookie
    val cookies: Flow<String?> = migratedData()
        .map { prefs ->
            resolveActiveAccount(prefs)?.cookies?.takeIf { it.isNotEmpty() } ?: prefs[KEY_PENDING_COOKIES]
        }
        .distinctUntilChanged()

    // 当前活跃账户上次成功刷新登录态的时间戳（毫秒），从未刷新过为 0
    val lastLoginRefreshAt: Flow<Long> = migratedData()
        .map { prefs -> resolveActiveAccount(prefs)?.lastLoginRefreshAt ?: 0L }
        .distinctUntilChanged()

    suspend fun saveLastLoginRefreshAt(timeMs: Long) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
            val activeUid = prefs[KEY_ACTIVE_UID] ?: return@edit
            val idx = list.indexOfFirst { it.uid == activeUid }
            if (idx >= 0) {
                list[idx] = list[idx].copy(lastLoginRefreshAt = timeMs)
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
            }
        }
    }

    // 按当前活跃账户 UID 隔离的屏蔽艺人 ID 列表；未登录使用独立匿名键
    @OptIn(ExperimentalCoroutinesApi::class)
    val blockedArtistIds: Flow<Set<Long>> = activeUid
        .flatMapLatest { uid ->
            val key = blockedArtistKey(uid)
            dataStore.data.map { prefs ->
                prefs[key]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
            }
        }
        .distinctUntilChanged()

    // 屏蔽/取消屏蔽艺人（作用于当前活跃账户）
    suspend fun toggleBlockArtist(artistId: Long) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val uid = resolveActiveAccount(prefs)?.uid
            val key = blockedArtistKey(uid)
            val current = prefs[key] ?: emptySet()
            val idStr = artistId.toString()
            prefs[key] = if (current.contains(idStr)) current - idStr else current + idStr
        }
    }

    // 保存或更新当前账户基本信息（保持旧版公开 API 语义）
    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
            val stagedCookies = prefs[KEY_PENDING_COOKIES]
            val now = nowMs()
            val idx = list.indexOfFirst { it.uid == profile.uid }
            if (idx >= 0) {
                val existing = list[idx]
                list[idx] = existing.copy(
                    nickname = profile.nickname,
                    avatarUrl = profile.avatarUrl,
                    cookies = stagedCookies ?: existing.cookies,
                    lastUsedAt = maxOf(existing.lastUsedAt, now)
                )
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
                prefs[KEY_ACTIVE_UID] = profile.uid
                prefs.remove(KEY_PENDING_COOKIES)
            } else if (list.size < MAX_ACCOUNTS) {
                val created = StoredAccount(
                    uid = profile.uid,
                    nickname = profile.nickname,
                    avatarUrl = profile.avatarUrl,
                    cookies = stagedCookies.orEmpty(),
                    lastLoginRefreshAt = 0L,
                    lastUsedAt = now
                )
                list.add(created)
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
                prefs[KEY_ACTIVE_UID] = profile.uid
                prefs.remove(KEY_PENDING_COOKIES)
                migrateBlockedArtistsToUid(prefs, profile.uid)
            } else {
                AppLogger.w(TAG, "账户数量已达上限 ($MAX_ACCOUNTS)，忽略新增 uid=${profile.uid}")
            }
        }
    }

    // 保存 Cookie（有活跃账户时更新当前账户，无活跃账户时暂存以兼容先存 Cookie 后存资料的调用序）
    suspend fun saveCookies(cookies: String) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
            val activeUid = prefs[KEY_ACTIVE_UID]
            val idx = if (activeUid != null) list.indexOfFirst { it.uid == activeUid } else -1
            if (idx >= 0) {
                list[idx] = list[idx].copy(cookies = cookies)
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
                prefs.remove(KEY_PENDING_COOKIES)
            } else {
                prefs[KEY_PENDING_COOKIES] = cookies
            }
        }
    }

    // 清除当前活跃账户（保留其余已登录账户并自动回落到最近使用的账户）
    suspend fun clearUserProfile() {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            prefs.remove(KEY_PENDING_COOKIES)
            val activeUid = prefs[KEY_ACTIVE_UID] ?: return@edit
            removeAccountInTransaction(prefs, activeUid)
        }
    }

    // 新增或更新指定账户；uid 已存在时更新资料与 Cookie 而不受 5 个上限限制
    suspend fun upsertAccount(account: StoredAccount, activate: Boolean = true): UpsertAccountResult {
        var result: UpsertAccountResult = UpsertAccountResult.LimitReached
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
            val now = nowMs()
            val targetLastUsed = if (account.lastUsedAt > 0L) account.lastUsedAt else now
            val idx = list.indexOfFirst { it.uid == account.uid }
            if (idx >= 0) {
                val existing = list[idx]
                val updated = existing.copy(
                    nickname = account.nickname,
                    avatarUrl = account.avatarUrl,
                    cookies = account.cookies,
                    lastLoginRefreshAt = if (account.lastLoginRefreshAt > 0L) {
                        account.lastLoginRefreshAt
                    } else {
                        existing.lastLoginRefreshAt
                    },
                    lastUsedAt = if (activate) maxOf(existing.lastUsedAt, targetLastUsed) else existing.lastUsedAt
                )
                list[idx] = updated
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
                if (activate || prefs[KEY_ACTIVE_UID] == null) {
                    prefs[KEY_ACTIVE_UID] = updated.uid
                }
                prefs.remove(KEY_PENDING_COOKIES)
                migrateBlockedArtistsToUid(prefs, updated.uid)
                result = UpsertAccountResult.Updated(updated)
            } else if (list.size >= MAX_ACCOUNTS) {
                result = UpsertAccountResult.LimitReached
            } else {
                val added = account.copy(lastUsedAt = targetLastUsed)
                list.add(added)
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
                if (activate || prefs[KEY_ACTIVE_UID] == null) {
                    prefs[KEY_ACTIVE_UID] = added.uid
                }
                prefs.remove(KEY_PENDING_COOKIES)
                migrateBlockedArtistsToUid(prefs, added.uid)
                result = UpsertAccountResult.Added(added)
            }
        }
        return result
    }

    // 切换到指定账户并刷新其 lastUsedAt；目标不在列表中返回 false
    suspend fun switchTo(uid: Long): Boolean {
        var switched = false
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.uid == uid }
            if (idx < 0) return@edit
            list[idx] = list[idx].copy(lastUsedAt = nowMs())
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
            prefs[KEY_ACTIVE_UID] = uid
            prefs.remove(KEY_PENDING_COOKIES)
            migrateBlockedArtistsToUid(prefs, uid)
            switched = true
        }
        return switched
    }

    // 移除指定账户；若移除的是当前活跃账户，切到 lastUsedAt 最近的剩余账户，返回切换后的 activeUid（无则返回 null）
    suspend fun removeAccount(uid: Long): Long? {
        var nextActiveUid: Long? = null
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            nextActiveUid = removeAccountInTransaction(prefs, uid)
        }
        return nextActiveUid
    }

    private fun removeAccountInTransaction(prefs: MutablePreferences, uid: Long): Long? {
        val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
        val removed = list.removeAll { it.uid == uid }
        if (removed) {
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
        }
        val currentActive = prefs[KEY_ACTIVE_UID]
        return if (currentActive == uid || list.none { it.uid == currentActive }) {
            val fallback = list.maxByOrNull { it.lastUsedAt }?.uid
            if (fallback != null) {
                prefs[KEY_ACTIVE_UID] = fallback
            } else {
                prefs.remove(KEY_ACTIVE_UID)
            }
            fallback
        } else {
            currentActive
        }
    }

    private fun hasLegacyMigrationPending(prefs: Preferences): Boolean {
        val hasLegacyAuth = prefs[KEY_ACCOUNTS_JSON].isNullOrBlank() &&
            (prefs[KEY_USER_PROFILE] != null || prefs[KEY_COOKIES] != null || prefs[KEY_LAST_LOGIN_REFRESH] != null)
        val hasLegacyBlocked = prefs[KEY_ACTIVE_UID] != null && prefs[KEY_BLOCKED_ARTIST_IDS] != null
        return hasLegacyAuth || hasLegacyBlocked
    }

    private fun migrateLegacyInTransaction(prefs: MutablePreferences) {
        if (prefs[KEY_ACCOUNTS_JSON].isNullOrBlank() &&
            (prefs[KEY_USER_PROFILE] != null || prefs[KEY_COOKIES] != null || prefs[KEY_LAST_LOGIN_REFRESH] != null)
        ) {
            val legacyProfile = prefs[KEY_USER_PROFILE]?.let { raw ->
                runCatching { json.decodeFromString<UserProfile>(raw) }
                    .onFailure { AppLogger.w(TAG, "旧版用户信息反序列化失败", it) }
                    .getOrNull()
            }
            if (legacyProfile != null && legacyProfile.uid > 0L) {
                val migrated = StoredAccount(
                    uid = legacyProfile.uid,
                    nickname = legacyProfile.nickname,
                    avatarUrl = legacyProfile.avatarUrl,
                    cookies = prefs[KEY_COOKIES].orEmpty(),
                    lastLoginRefreshAt = prefs[KEY_LAST_LOGIN_REFRESH] ?: 0L,
                    lastUsedAt = nowMs()
                )
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(listOf(migrated))
                prefs[KEY_ACTIVE_UID] = migrated.uid
                migrateBlockedArtistsToUid(prefs, migrated.uid)
            }
            prefs.remove(KEY_USER_PROFILE)
            prefs.remove(KEY_COOKIES)
            prefs.remove(KEY_LAST_LOGIN_REFRESH)
        }
        val activeUid = prefs[KEY_ACTIVE_UID]
        if (activeUid != null) {
            migrateBlockedArtistsToUid(prefs, activeUid)
        }
    }

    private fun migrateBlockedArtistsToUid(prefs: MutablePreferences, uid: Long) {
        val legacyBlocked = prefs[KEY_BLOCKED_ARTIST_IDS] ?: return
        val targetKey = blockedArtistKey(uid)
        val merged = (prefs[targetKey] ?: emptySet()) + legacyBlocked
        prefs[targetKey] = merged
        prefs.remove(KEY_BLOCKED_ARTIST_IDS)
    }

    private fun resolveActiveAccount(prefs: Preferences): StoredAccount? {
        val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON])
        val activeUid = prefs[KEY_ACTIVE_UID] ?: return null
        return list.firstOrNull { it.uid == activeUid }
    }

    private fun decodeAccounts(raw: String?): List<StoredAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<StoredAccount>>(raw) }
            .onFailure { AppLogger.w(TAG, "账户列表反序列化失败", it) }
            .getOrDefault(emptyList())
    }
}
