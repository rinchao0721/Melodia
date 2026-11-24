package com.lin0721.linmusic.core.auth

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

// 多平台并行账号体系、老数据无损迁移与 MetadataCache 作用域隔离单元测试
class UserPreferencesMultiPlatformTest {

    private var clockMs = 10_000L

    private fun newPrefs(store: InMemoryPreferencesStore = InMemoryPreferencesStore()) =
        UserPreferences(store, nowMs = { clockMs })

    @Test
    fun `旧版网易多账号 JSON 无损迁移到多平台并行存储`() = runTest {
        val store = InMemoryPreferencesStore()
        // 模拟老版本仅存在 accounts_json 与 active_uid（不含 active_accounts_by_platform）
        val legacyJson = """
            [
              {"uid":1001,"nickname":"网易老用户1","avatarUrl":"https://n/1.png","cookies":"MUSIC_U=n1","lastLoginRefreshAt":100,"lastUsedAt":200},
              {"uid":1002,"nickname":"网易老用户2","avatarUrl":"https://n/2.png","cookies":"MUSIC_U=n2","lastLoginRefreshAt":300,"lastUsedAt":400}
            ]
        """.trimIndent()
        store.edit { raw ->
            raw[stringPreferencesKey("accounts_json")] = legacyJson
            raw[longPreferencesKey("active_uid")] = 1002L
        }

        val prefs = newPrefs(store)
        assertEquals(1002L, prefs.activeUid.first())
        assertEquals("MUSIC_U=n2", prefs.cookies.first())
        assertEquals(MusicPlatform.NETEASE, prefs.currentPlatform.first())

        val activeByPlatform = prefs.activeAccountsByPlatform.first()
        assertEquals(1, activeByPlatform.size)
        assertEquals(AccountId.netease(1002L), activeByPlatform[MusicPlatform.NETEASE]?.accountId)
        assertEquals(2, prefs.accountsOf(MusicPlatform.NETEASE).first().size)
        assertTrue(prefs.accountsOf(MusicPlatform.KUGOU).first().isEmpty())
    }

    @Test
    fun `网易云与酷狗双平台并行在线 独立切换 独立登出与各平台五账号上限隔离`() = runTest {
        val prefs = newPrefs()

        // 登录 5 个网易云账号 + 2 个酷狗账号，互不占用上限且各自保持活跃
        for (i in 1L..5L) {
            clockMs += 100L
            val res = prefs.upsertPlatformAccount(
                StoredAccount(
                    uid = i,
                    nickname = "Netease-$i",
                    cookies = "MUSIC_U=n$i",
                    platform = MusicPlatform.NETEASE
                )
            )
            assertTrue(res is UpsertAccountResult.Added)
        }
        assertEquals(
            UpsertAccountResult.LimitReached,
            prefs.upsertPlatformAccount(StoredAccount(uid = 6L, platform = MusicPlatform.NETEASE))
        )

        // 酷狗平台仍可独立添加账号（不受网易云满 5 个影响）
        clockMs += 100L
        assertTrue(
            prefs.upsertPlatformAccount(
                StoredAccount(
                    uid = 8001L,
                    nickname = "Kugou-1",
                    platform = MusicPlatform.KUGOU,
                    platformUid = "8001",
                    credentials = mapOf("token" to "kg_tok_1", "userid" to "8001"),
                    vipLabel = "酷狗 VIP"
                )
            ) is UpsertAccountResult.Added
        )
        clockMs += 100L
        assertTrue(
            prefs.upsertPlatformAccount(
                StoredAccount(
                    uid = 8002L,
                    nickname = "Kugou-2",
                    platform = MusicPlatform.KUGOU,
                    platformUid = "8002",
                    credentials = mapOf("token" to "kg_tok_2", "userid" to "8002")
                )
            ) is UpsertAccountResult.Added
        )

        val activeMap = prefs.activeAccountsByPlatform.first()
        assertEquals(5L, activeMap[MusicPlatform.NETEASE]?.uid)
        assertEquals("8002", activeMap[MusicPlatform.KUGOU]?.effectivePlatformUid)
        assertEquals(5L, prefs.activeUid.first())

        // 切换酷狗账号不影响网易云当前活跃账号
        clockMs += 100L
        assertTrue(prefs.switchPlatformAccount(AccountId.kugou("8001")))
        assertEquals("8001", prefs.activeAccountOf(MusicPlatform.KUGOU).first()?.effectivePlatformUid)
        assertEquals(5L, prefs.activeUid.first())

        // 增量更新酷狗凭据不覆盖已有键
        prefs.updatePlatformCredentials(
            accountId = AccountId.kugou("8001"),
            credentials = mapOf("auth" to "kg_auth_xyz"),
            lastRefreshAt = 99_999L
        )
        val updatedKg = prefs.activeAccountOf(MusicPlatform.KUGOU).first()!!
        assertEquals("kg_tok_1", updatedKg.credentials["token"])
        assertEquals("kg_auth_xyz", updatedKg.credentials["auth"])
        assertEquals(99_999L, updatedKg.lastLoginRefreshAt)

        // 浏览平台切换
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)
        assertEquals(MusicPlatform.KUGOU, prefs.currentPlatform.first())

        // 退出酷狗活跃账号 8001 -> 自动回退到 8002，同时网易云 5L 保持不变
        val nextKg = prefs.removePlatformAccount(AccountId.kugou("8001"))
        assertEquals(AccountId.kugou("8002"), nextKg)
        assertEquals("8002", prefs.activeAccountOf(MusicPlatform.KUGOU).first()?.effectivePlatformUid)
        assertEquals(5L, prefs.activeUid.first())

        // 退出所有酷狗账号后，仅酷狗未登录，网易云仍在线
        assertNull(prefs.removePlatformAccount(AccountId.kugou("8002")))
        assertNull(prefs.activeAccountOf(MusicPlatform.KUGOU).first())
        assertEquals(5L, prefs.activeUid.first())
    }

    @Test
    fun `PlatformSessionRegistry 分发注册平台与未注册平台行为正确`() {
        val neteaseProvider = object : PlatformAuthProvider {
            override val platform: MusicPlatform = MusicPlatform.NETEASE
            override suspend fun createQrLogin() = Result.success(PlatformQrKey(MusicPlatform.NETEASE, "ne"))
            override suspend fun pollQrLogin(key: String) = Result.success(PlatformQrPollResult.Waiting)
            override suspend fun refreshSession(account: StoredAccount) = Result.failure<PlatformAuthResult>(IllegalStateException())
            override suspend fun fetchUserDetail(account: StoredAccount) = Result.failure<PlatformUserDetail>(IllegalStateException())
        }
        val kugouProvider = object : PlatformAuthProvider {
            override val platform: MusicPlatform = MusicPlatform.KUGOU
            override suspend fun createQrLogin() = Result.success(PlatformQrKey(MusicPlatform.KUGOU, "kg"))
            override suspend fun pollQrLogin(key: String) = Result.success(PlatformQrPollResult.Waiting)
            override suspend fun refreshSession(account: StoredAccount) = Result.failure<PlatformAuthResult>(IllegalStateException())
            override suspend fun fetchUserDetail(account: StoredAccount) = Result.failure<PlatformUserDetail>(IllegalStateException())
        }

        val registry = PlatformSessionRegistry(listOf(neteaseProvider, kugouProvider))
        assertEquals(setOf(MusicPlatform.NETEASE, MusicPlatform.KUGOU), registry.supportedPlatforms())
        assertEquals(neteaseProvider, registry.requireProvider(MusicPlatform.NETEASE))
        assertEquals(kugouProvider, registry.providerOf(MusicPlatform.KUGOU))
        assertNull(registry.providerOf(MusicPlatform.QQ))
    }

    @Serializable
    private data class CacheDummy(val title: String)

    @Test
    fun `MetadataCache 按平台账号作用域隔离并支持单独清理指定作用域`() = runTest {
        val rootDir = Files.createTempDirectory("melodia-meta-scope").toFile()
        try {
            var activeScope = "netease_12345"
            val cache = MetadataCache.scoped(rootDir) { activeScope }

            cache.write("home_feed", CacheDummy.serializer(), CacheDummy("网易云推荐"))
            assertEquals("网易云推荐", cache.read("home_feed", CacheDummy.serializer())?.title)

            activeScope = "kugou_67890"
            assertNull(cache.read("home_feed", CacheDummy.serializer()))
            cache.write("home_feed", CacheDummy.serializer(), CacheDummy("酷狗推荐"))
            assertEquals("酷狗推荐", cache.read("home_feed", CacheDummy.serializer())?.title)

            // 切回网易作用域仍保留原数据
            activeScope = "netease_12345"
            assertEquals("网易云推荐", cache.read("home_feed", CacheDummy.serializer())?.title)

            // 仅清空酷狗作用域，网易云数据不受影响
            cache.clearScope("kugou_67890")
            assertEquals("网易云推荐", cache.read("home_feed", CacheDummy.serializer())?.title)
            activeScope = "kugou_67890"
            assertNull(cache.read("home_feed", CacheDummy.serializer()))
        } finally {
            rootDir.deleteRecursively()
        }
    }
}
