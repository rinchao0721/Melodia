package com.lin0721.linmusic.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.CancellationException
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

// 本地存储的完整账户快照（含多平台标识、凭据与近期使用时间）
@Serializable
data class StoredAccount(
    val uid: Long,
    val nickname: String = "",
    val avatarUrl: String = "",
    val cookies: String = "",
    val lastLoginRefreshAt: Long = 0L,
    val lastUsedAt: Long = 0L,
    val platform: MusicPlatform = MusicPlatform.NETEASE,
    val platformUid: String = "",
    val credentials: Map<String, String> = emptyMap(),
    val vipLabel: String = ""
) {
    val effectivePlatformUid: String
        get() = platformUid.trim().ifEmpty { uid.toString() }

    val accountId: AccountId
        get() = AccountId.of(platform, effectivePlatformUid)

    fun toUserProfile(): UserProfile = UserProfile(uid = uid, nickname = nickname, avatarUrl = avatarUrl)
}

// 写入账户列表的结果
sealed interface UpsertAccountResult {
    data class Added(val account: StoredAccount) : UpsertAccountResult
    data class Updated(val account: StoredAccount) : UpsertAccountResult
    data object LimitReached : UpsertAccountResult
}

// 用户信息持久化管理 (DataStore + JSON)，支持多平台并行在线、同平台多账户及按账户隔离的屏蔽艺人
class UserPreferences(
    private val dataStore: DataStore<Preferences>,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    // 本存储为空时用于判断是否老用户（如其他偏好文件已有数据）
    private val legacyUsageProbe: suspend () -> Boolean = { false }
) {
    // 供同仓库共享用户偏好存储文件（如酷狗设备指纹）使用，避免新建额外 DataStore 文件
    internal val preferencesDataStore: DataStore<Preferences>
        get() = dataStore

    companion object {
        const val MAX_ACCOUNTS = 5

        private val KEY_ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        private val KEY_ACTIVE_UID = longPreferencesKey("active_uid")
        // 按平台记录活跃账号规范 ID（platform.key -> AccountId.canonical）
        private val KEY_ACTIVE_ACCOUNTS_BY_PLATFORM = stringPreferencesKey("active_accounts_by_platform")
        // 当前平台（默认 netease），沿用旧浏览平台存储键
        private val KEY_CURRENT_BROWSE_PLATFORM = stringPreferencesKey("current_browse_platform")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
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

    // 若存在旧版单账户键或待同步的多平台活跃映射，在单个 DataStore 事务内一次性无损迁移
    suspend fun ensureMigrated() {
        val current = dataStore.data.first()
        if (!hasLegacyMigrationPending(current) && current[KEY_ONBOARDING_COMPLETED] != null) return
        migrationMutex.withLock {
            val latest = dataStore.data.first()
            val onboardingUndecided = latest[KEY_ONBOARDING_COMPLETED] == null
            if (!hasLegacyMigrationPending(latest) && !onboardingUndecided) return@withLock
            val usedElsewhere = onboardingUndecided && latest.asMap().isEmpty() && probeLegacyUsage()
            dataStore.edit { prefs ->
                migrateLegacyInTransaction(prefs)
                if (prefs[KEY_ONBOARDING_COMPLETED] == null) {
                    migrateOnboardingInTransaction(prefs, usedElsewhere)
                }
            }
        }
    }

    private suspend fun probeLegacyUsage(): Boolean = try {
        legacyUsageProbe()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG, "探测旧版使用痕迹失败，按全新安装处理", e)
        false
    }

    // 老用户直接视为已完成首次启动，并把当前平台校正到有账号的平台；全新安装写 false
    private fun migrateOnboardingInTransaction(prefs: MutablePreferences, usedElsewhere: Boolean) {
        val existingUser = usedElsewhere || prefs.asMap().keys.any { it != KEY_ONBOARDING_COMPLETED }
        prefs[KEY_ONBOARDING_COMPLETED] = existingUser
        if (!existingUser) return
        val accounts = decodeAccounts(prefs[KEY_ACCOUNTS_JSON])
        val stored = prefs[KEY_CURRENT_BROWSE_PLATFORM]?.let(MusicPlatform::fromKey) ?: MusicPlatform.NETEASE
        val active = resolveActiveAccountsByPlatform(prefs, accounts)
        val resolved = when {
            stored == MusicPlatform.LOCAL -> stored
            stored in active -> stored
            accounts.any { it.platform == stored } -> stored
            else -> accounts.maxByOrNull { it.lastUsedAt }?.platform ?: MusicPlatform.NETEASE
        }
        prefs[KEY_CURRENT_BROWSE_PLATFORM] = resolved.key
    }

    private fun migratedData(): Flow<Preferences> = flow {
        ensureMigrated()
        emitAll(dataStore.data)
    }

    // ─── 多平台并行账号新 API ───

    // 各平台当前活跃账号映射（仅包含实际已登录且有效命中的平台）
    val activeAccountsByPlatform: Flow<Map<MusicPlatform, StoredAccount>> = migratedData()
        .map { prefs -> resolveActiveAccountsByPlatform(prefs) }
        .distinctUntilChanged()

    // 当前平台（由账号切换决定），默认网易云音乐
    val currentPlatform: Flow<MusicPlatform> = migratedData()
        .map { prefs -> readCurrentPlatform(prefs) }
        .distinctUntilChanged()

    // 跨平台全部账号，按 lastUsedAt 倒序
    val allAccounts: Flow<List<StoredAccount>> = migratedData()
        .map { prefs -> decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).sortedByDescending { it.lastUsedAt } }
        .distinctUntilChanged()

    // 当前平台的活跃账号 ID；为 null 表示网易游客
    val currentAccountId: Flow<AccountId?> = migratedData()
        .map { prefs -> resolveActiveAccountsByPlatform(prefs)[readCurrentPlatform(prefs)]?.accountId }
        .distinctUntilChanged()

    // 首次启动流程是否已走完
    val onboardingCompleted: Flow<Boolean> = migratedData()
        .map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }
        .distinctUntilChanged()

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    private fun readCurrentPlatform(prefs: Preferences): MusicPlatform =
        prefs[KEY_CURRENT_BROWSE_PLATFORM]?.let(MusicPlatform::fromKey) ?: MusicPlatform.NETEASE

    // 指定平台下已登录的全部账号列表
    fun accountsOf(platform: MusicPlatform): Flow<List<StoredAccount>> = migratedData()
        .map { prefs -> decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).filter { it.platform == platform } }
        .distinctUntilChanged()

    // 指定平台当前活跃账号
    fun activeAccountOf(platform: MusicPlatform): Flow<StoredAccount?> = activeAccountsByPlatform
        .map { it[platform] }
        .distinctUntilChanged()

    // 新增或更新指定平台账号；单平台最多保存 MAX_ACCOUNTS 个，已存在账号更新不受上限限制
    suspend fun upsertPlatformAccount(account: StoredAccount, activate: Boolean = true): UpsertAccountResult {
        var result: UpsertAccountResult = UpsertAccountResult.LimitReached
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            result = upsertPlatformAccountInTransaction(prefs, account, activate)
        }
        return result
    }

    // 切换到指定平台账号并刷新其 lastUsedAt；目标不在列表中返回 false
    suspend fun switchPlatformAccount(accountId: AccountId): Boolean {
        var switched = false
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.accountId == accountId }
            if (idx < 0) return@edit
            val updated = list[idx].copy(lastUsedAt = nowMs())
            list[idx] = updated
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)

            val activeMap = decodeActiveMap(prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM]).toMutableMap()
            activeMap[accountId.platform.key] = accountId.canonical
            prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM] = json.encodeToString(activeMap)

            if (accountId.platform == MusicPlatform.NETEASE) {
                prefs[KEY_ACTIVE_UID] = updated.uid
                prefs.remove(KEY_PENDING_COOKIES)
                migrateBlockedArtistsToUid(prefs, updated.uid)
            }
            switched = true
        }
        return switched
    }

    // 移除指定平台账号；若移除的是该平台当前活跃账号，回退到该平台 lastUsedAt 最近的剩余账号
    suspend fun removePlatformAccount(accountId: AccountId): AccountId? {
        var nextAccountId: AccountId? = null
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            nextAccountId = removePlatformAccountInTransaction(prefs, accountId)
        }
        return nextAccountId
    }

    // 增量更新指定平台账号的凭据字典与可选刷新时间戳
    suspend fun updatePlatformCredentials(
        accountId: AccountId,
        credentials: Map<String, String>,
        lastRefreshAt: Long? = null
    ) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.accountId == accountId }
            if (idx < 0) return@edit
            val existing = list[idx]
            list[idx] = existing.copy(
                credentials = existing.credentials + credentials,
                lastLoginRefreshAt = lastRefreshAt ?: existing.lastLoginRefreshAt
            )
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
        }
    }

    // 仅供 AccountSwitcher 写入当前平台
    internal suspend fun setCurrentPlatform(platform: MusicPlatform) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            prefs[KEY_CURRENT_BROWSE_PLATFORM] = platform.key
        }
    }

    // 置空指定平台活跃账号但保留已存账号（网易游客）
    internal suspend fun deactivatePlatform(platform: MusicPlatform) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val activeMap = decodeActiveMap(prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM]).toMutableMap()
            activeMap.remove(platform.key)
            prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM] = json.encodeToString(activeMap)
            if (platform == MusicPlatform.NETEASE) {
                prefs.remove(KEY_ACTIVE_UID)
                prefs.remove(KEY_PENDING_COOKIES)
            }
        }
    }

    // ─── 网易云向后兼容公开 API（聚焦 NETEASE 平台，保持零回归） ───

    // 当前已登录网易账户列表（不含凭据的公开资料）
    val accounts: Flow<List<UserProfile>> = accountsOf(MusicPlatform.NETEASE)
        .map { list -> list.map { it.toUserProfile() } }
        .distinctUntilChanged()

    // 完整存储网易账户列表（供需要 Cookie/时间信息时读取）
    val storedAccounts: Flow<List<StoredAccount>> = accountsOf(MusicPlatform.NETEASE)

    // 当前活跃网易账户 UID；未登录为 null
    val activeUid: Flow<Long?> = activeAccountOf(MusicPlatform.NETEASE)
        .map { it?.uid }
        .distinctUntilChanged()

    // 读取当前活跃网易账户用户信息
    val userProfile: Flow<UserProfile?> = activeAccountOf(MusicPlatform.NETEASE)
        .map { it?.toUserProfile() }
        .distinctUntilChanged()

    // 读取当前活跃网易账户 Cookie
    val cookies: Flow<String?> = migratedData()
        .map { prefs ->
            resolveActiveNeteaseAccount(prefs)?.cookies?.takeIf { it.isNotEmpty() } ?: prefs[KEY_PENDING_COOKIES]
        }
        .distinctUntilChanged()

    // 当前活跃网易账户上次成功刷新登录态的时间戳（毫秒），从未刷新过为 0
    val lastLoginRefreshAt: Flow<Long> = activeAccountOf(MusicPlatform.NETEASE)
        .map { it?.lastLoginRefreshAt ?: 0L }
        .distinctUntilChanged()

    suspend fun saveLastLoginRefreshAt(timeMs: Long) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
            val activeNetease = resolveActiveNeteaseAccount(prefs, list) ?: return@edit
            val idx = list.indexOfFirst { it.accountId == activeNetease.accountId }
            if (idx >= 0) {
                list[idx] = list[idx].copy(lastLoginRefreshAt = timeMs)
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
            }
        }
    }

    // 按当前活跃网易账户 UID 隔离的屏蔽艺人 ID 列表；未登录使用独立匿名键
    @OptIn(ExperimentalCoroutinesApi::class)
    val blockedArtistIds: Flow<Set<Long>> = activeUid
        .flatMapLatest { uid ->
            val key = blockedArtistKey(uid)
            dataStore.data.map { prefs ->
                prefs[key]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
            }
        }
        .distinctUntilChanged()

    // 屏蔽/取消屏蔽艺人（作用于当前活跃网易账户）
    suspend fun toggleBlockArtist(artistId: Long) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val uid = resolveActiveNeteaseAccount(prefs)?.uid
            val key = blockedArtistKey(uid)
            val current = prefs[key] ?: emptySet()
            val idStr = artistId.toString()
            prefs[key] = if (current.contains(idStr)) current - idStr else current + idStr
        }
    }

    // 保存或更新当前网易账户基本信息（保持旧版公开 API 语义）
    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
            val stagedCookies = prefs[KEY_PENDING_COOKIES]
            val now = nowMs()
            val idx = list.indexOfFirst { it.platform == MusicPlatform.NETEASE && it.uid == profile.uid }
            if (idx >= 0) {
                val existing = list[idx]
                val updated = existing.copy(
                    nickname = profile.nickname,
                    avatarUrl = profile.avatarUrl,
                    cookies = stagedCookies ?: existing.cookies,
                    lastUsedAt = maxOf(existing.lastUsedAt, now)
                )
                list[idx] = updated
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
                syncActivePlatformInTransaction(prefs, updated.accountId, updated.uid)
                prefs.remove(KEY_PENDING_COOKIES)
            } else if (list.count { it.platform == MusicPlatform.NETEASE } < MAX_ACCOUNTS) {
                val created = StoredAccount(
                    uid = profile.uid,
                    nickname = profile.nickname,
                    avatarUrl = profile.avatarUrl,
                    cookies = stagedCookies.orEmpty(),
                    lastLoginRefreshAt = 0L,
                    lastUsedAt = now,
                    platform = MusicPlatform.NETEASE,
                    platformUid = profile.uid.toString()
                )
                list.add(created)
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
                syncActivePlatformInTransaction(prefs, created.accountId, created.uid)
                prefs.remove(KEY_PENDING_COOKIES)
                migrateBlockedArtistsToUid(prefs, profile.uid)
            } else {
                AppLogger.w(TAG, "账户数量已达上限 ($MAX_ACCOUNTS)，忽略新增 uid=${profile.uid}")
            }
        }
    }

    // 保存网易 Cookie（有活跃网易账户时更新当前账户，无活跃账户时暂存以兼容先存 Cookie 后存资料的调用序）
    suspend fun saveCookies(cookies: String) {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
            val activeNetease = resolveActiveNeteaseAccount(prefs, list)
            val idx = if (activeNetease != null) list.indexOfFirst { it.accountId == activeNetease.accountId } else -1
            if (idx >= 0) {
                list[idx] = list[idx].copy(cookies = cookies)
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
                prefs.remove(KEY_PENDING_COOKIES)
            } else {
                prefs[KEY_PENDING_COOKIES] = cookies
            }
        }
    }

    // 清除当前活跃网易账户（保留其余已登录账户并自动回落到最近使用的网易账户）
    suspend fun clearUserProfile() {
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs)
            prefs.remove(KEY_PENDING_COOKIES)
            val activeNetease = resolveActiveNeteaseAccount(prefs) ?: return@edit
            removePlatformAccountInTransaction(prefs, activeNetease.accountId)
        }
    }

    // 新增或更新指定账户（默认网易云；若传入其他平台则同构委派给 upsertPlatformAccount）
    suspend fun upsertAccount(account: StoredAccount, activate: Boolean = true): UpsertAccountResult =
        upsertPlatformAccount(account, activate)

    // 切换到指定网易账户并刷新其 lastUsedAt；目标不在列表中返回 false
    suspend fun switchTo(uid: Long): Boolean =
        switchPlatformAccount(AccountId.netease(uid))

    // 移除指定网易账户；若移除的是当前活跃账户，切到 lastUsedAt 最近的剩余网易账户，返回切换后的 activeUid（无则返回 null）
    suspend fun removeAccount(uid: Long): Long? {
        val nextId = removePlatformAccount(AccountId.netease(uid))
        return nextId?.neteaseIdOrNull()
    }

    // ─── 事务内部实现 ───

    private fun upsertPlatformAccountInTransaction(
        prefs: MutablePreferences,
        rawAccount: StoredAccount,
        activate: Boolean
    ): UpsertAccountResult {
        val account = if (rawAccount.platformUid.isBlank()) {
            rawAccount.copy(platformUid = rawAccount.uid.toString())
        } else {
            rawAccount
        }
        val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
        val activeMap = decodeActiveMap(prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM]).toMutableMap()
        val now = nowMs()
        val targetLastUsed = if (account.lastUsedAt > 0L) account.lastUsedAt else now
        val targetAccountId = account.accountId

        val idx = list.indexOfFirst { it.accountId == targetAccountId }
        if (idx >= 0) {
            val existing = list[idx]
            val mergedCredentials = if (account.credentials.isNotEmpty()) {
                existing.credentials + account.credentials
            } else {
                existing.credentials
            }
            val updated = existing.copy(
                uid = if (account.uid != 0L) account.uid else existing.uid,
                nickname = account.nickname,
                avatarUrl = account.avatarUrl,
                cookies = account.cookies,
                lastLoginRefreshAt = if (account.lastLoginRefreshAt > 0L) {
                    account.lastLoginRefreshAt
                } else {
                    existing.lastLoginRefreshAt
                },
                lastUsedAt = if (activate) maxOf(existing.lastUsedAt, targetLastUsed) else existing.lastUsedAt,
                platform = account.platform,
                platformUid = account.effectivePlatformUid,
                credentials = mergedCredentials,
                vipLabel = account.vipLabel.ifEmpty { existing.vipLabel }
            )
            list[idx] = updated
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)

            val shouldActivate = activate || activeMap[account.platform.key].isNullOrBlank()
            if (shouldActivate) {
                syncActivePlatformInTransaction(prefs, updated.accountId, updated.uid, activeMap)
            } else {
                prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM] = json.encodeToString(activeMap)
            }
            if (account.platform == MusicPlatform.NETEASE) {
                prefs.remove(KEY_PENDING_COOKIES)
                migrateBlockedArtistsToUid(prefs, updated.uid)
            }
            return UpsertAccountResult.Updated(updated)
        }

        if (list.count { it.platform == account.platform } >= MAX_ACCOUNTS) {
            return UpsertAccountResult.LimitReached
        }

        val added = account.copy(lastUsedAt = targetLastUsed)
        list.add(added)
        prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)

        val shouldActivate = activate || activeMap[account.platform.key].isNullOrBlank()
        if (shouldActivate) {
            syncActivePlatformInTransaction(prefs, added.accountId, added.uid, activeMap)
        } else {
            prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM] = json.encodeToString(activeMap)
        }
        if (account.platform == MusicPlatform.NETEASE) {
            prefs.remove(KEY_PENDING_COOKIES)
            migrateBlockedArtistsToUid(prefs, added.uid)
        }
        return UpsertAccountResult.Added(added)
    }

    private fun removePlatformAccountInTransaction(prefs: MutablePreferences, accountId: AccountId): AccountId? {
        val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON]).toMutableList()
        val removed = list.removeAll { it.accountId == accountId }
        if (removed) {
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
        }

        val activeMap = decodeActiveMap(prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM]).toMutableMap()
        val currentCanonical = activeMap[accountId.platform.key]
        val platformRemaining = list.filter { it.platform == accountId.platform }
        val currentStillPresent = currentCanonical != null &&
            currentCanonical != accountId.canonical &&
            platformRemaining.any { it.accountId.canonical == currentCanonical }

        // 该平台原本无活跃账号（游客）时不自动激活剩余账号
        val resolvedAccount: StoredAccount? = when {
            currentStillPresent -> platformRemaining.firstOrNull { it.accountId.canonical == currentCanonical }
            currentCanonical == null -> null
            else -> platformRemaining.maxByOrNull { it.lastUsedAt }
        }

        if (resolvedAccount != null) {
            activeMap[accountId.platform.key] = resolvedAccount.accountId.canonical
        } else {
            activeMap.remove(accountId.platform.key)
        }
        prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM] = json.encodeToString(activeMap)

        if (accountId.platform == MusicPlatform.NETEASE) {
            if (resolvedAccount != null) {
                prefs[KEY_ACTIVE_UID] = resolvedAccount.uid
            } else {
                prefs.remove(KEY_ACTIVE_UID)
            }
        }
        return resolvedAccount?.accountId
    }

    private fun syncActivePlatformInTransaction(
        prefs: MutablePreferences,
        accountId: AccountId,
        uidForNetease: Long,
        existingMap: MutableMap<String, String>? = null
    ) {
        val activeMap = existingMap ?: decodeActiveMap(prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM]).toMutableMap()
        activeMap[accountId.platform.key] = accountId.canonical
        prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM] = json.encodeToString(activeMap)
        if (accountId.platform == MusicPlatform.NETEASE) {
            prefs[KEY_ACTIVE_UID] = uidForNetease
        }
    }

    private fun hasLegacyMigrationPending(prefs: Preferences): Boolean {
        val hasLegacyAuth = prefs[KEY_ACCOUNTS_JSON].isNullOrBlank() &&
            (prefs[KEY_USER_PROFILE] != null || prefs[KEY_COOKIES] != null || prefs[KEY_LAST_LOGIN_REFRESH] != null)
        val hasLegacyBlocked = prefs[KEY_ACTIVE_UID] != null && prefs[KEY_BLOCKED_ARTIST_IDS] != null
        val hasPlatformMapPending = prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM] == null &&
            (!prefs[KEY_ACCOUNTS_JSON].isNullOrBlank() || prefs[KEY_ACTIVE_UID] != null)
        return hasLegacyAuth || hasLegacyBlocked || hasPlatformMapPending
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
                    lastUsedAt = nowMs(),
                    platform = MusicPlatform.NETEASE,
                    platformUid = legacyProfile.uid.toString()
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
        if (prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM] == null &&
            (!prefs[KEY_ACCOUNTS_JSON].isNullOrBlank() || activeUid != null)
        ) {
            val list = decodeAccounts(prefs[KEY_ACCOUNTS_JSON])
            val activeMap = mutableMapOf<String, String>()
            val neteaseMatch = if (activeUid != null) {
                list.firstOrNull { it.platform == MusicPlatform.NETEASE && it.uid == activeUid }
            } else {
                null
            }
            if (neteaseMatch != null) {
                activeMap[MusicPlatform.NETEASE.key] = neteaseMatch.accountId.canonical
            }
            prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM] = json.encodeToString(activeMap)
        }
    }

    private fun migrateBlockedArtistsToUid(prefs: MutablePreferences, uid: Long) {
        val legacyBlocked = prefs[KEY_BLOCKED_ARTIST_IDS] ?: return
        val targetKey = blockedArtistKey(uid)
        val merged = (prefs[targetKey] ?: emptySet()) + legacyBlocked
        prefs[targetKey] = merged
        prefs.remove(KEY_BLOCKED_ARTIST_IDS)
    }

    private fun resolveActiveAccountsByPlatform(
        prefs: Preferences,
        accounts: List<StoredAccount> = decodeAccounts(prefs[KEY_ACCOUNTS_JSON])
    ): Map<MusicPlatform, StoredAccount> {
        val rawMap = decodeActiveMap(prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM])
        val result = LinkedHashMap<MusicPlatform, StoredAccount>()
        for ((platformKey, canonical) in rawMap) {
            val platform = MusicPlatform.fromKey(platformKey) ?: continue
            val matched = accounts.firstOrNull { it.platform == platform && it.accountId.canonical == canonical }
            if (matched != null) {
                result[platform] = matched
            }
        }
        // 若尚未写入多平台映射（或测试直接写 KEY_ACTIVE_UID），从 KEY_ACTIVE_UID 兜底解析网易云活跃账号
        if (MusicPlatform.NETEASE !in result && prefs[KEY_ACTIVE_ACCOUNTS_BY_PLATFORM] == null) {
            val activeUid = prefs[KEY_ACTIVE_UID]
            if (activeUid != null) {
                val matched = accounts.firstOrNull { it.platform == MusicPlatform.NETEASE && it.uid == activeUid }
                if (matched != null) {
                    result[MusicPlatform.NETEASE] = matched
                }
            }
        }
        return result
    }

    private fun resolveActiveNeteaseAccount(
        prefs: Preferences,
        accounts: List<StoredAccount> = decodeAccounts(prefs[KEY_ACCOUNTS_JSON])
    ): StoredAccount? = resolveActiveAccountsByPlatform(prefs, accounts)[MusicPlatform.NETEASE]

    private fun decodeAccounts(raw: String?): List<StoredAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<StoredAccount>>(raw) }
            .onFailure { AppLogger.w(TAG, "账户列表反序列化失败", it) }
            .getOrDefault(emptyList())
    }

    private fun decodeActiveMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }
            .onFailure { AppLogger.w(TAG, "活跃平台账号映射反序列化失败", it) }
            .getOrDefault(emptyMap())
    }
}
