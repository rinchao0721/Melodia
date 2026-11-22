package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.kugou.KugouRiskException
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KugouLoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createEnv(provider: FakeKugouAuthProvider): Triple<UserPreferences, AccountSwitcher, KugouLoginViewModel> {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val repo = FakeAuthRepository(prefs)
        val registry = PlatformSessionRegistry(listOf(provider))
        val switcher = AccountSwitcher(
            userPreferences = prefs,
            authRepository = repo,
            platformSessionRegistry = registry
        )
        val vm = KugouLoginViewModel(
            authProvider = provider,
            userPreferences = prefs,
            accountSwitcher = switcher,
            pollIntervalMs = 1_000L,
            qrDispatcher = testDispatcher
        )
        return Triple(prefs, switcher, vm)
    }

    @Test
    fun qrLoginTransitionsThroughWaitingConfirmingSuccessAndEmitsAddedEvent() = runTest(testDispatcher) {
        val kugouAccount = StoredAccount(
            uid = 10086L,
            nickname = "酷狗测试用户",
            avatarUrl = "https://example.com/kg.png",
            platform = MusicPlatform.KUGOU,
            platformUid = "10086",
            credentials = mapOf("token" to "tok_abc", "userid" to "10086"),
            vipLabel = "酷狗 VIP"
        )
        val provider = FakeKugouAuthProvider().apply {
            pollResults = ArrayDeque(
                listOf(
                    Result.success(PlatformQrPollResult.Waiting),
                    Result.success(PlatformQrPollResult.Confirming(nickname = "酷狗测试用户")),
                    Result.success(
                        PlatformQrPollResult.Authorized(
                            PlatformAuthResult(
                                platform = MusicPlatform.KUGOU,
                                accountId = AccountId.kugou("10086"),
                                nickname = "酷狗测试用户",
                                avatarUrl = "https://example.com/kg.png",
                                credentials = mapOf("token" to "tok_abc", "userid" to "10086"),
                                vipLabel = "酷狗 VIP",
                                account = kugouAccount
                            )
                        )
                    )
                )
            )
        }
        val (prefs, _, vm) = createEnv(provider)
        prefs.upsertPlatformAccount(kugouAccount, activate = true)

        val events = mutableListOf<PlatformAccountChangeEvent>()
        val eventJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AccountEvents.platformSwitched.take(1).toList(events)
        }

        var callbackAccount: StoredAccount? = null
        vm.startQrLogin { callbackAccount = it }
        runCurrent()
        assertTrue(vm.qrState.value is KugouQrState.WaitingScan)

        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(vm.qrState.value is KugouQrState.WaitingScan)

        advanceTimeBy(1_000L)
        runCurrent()
        val confirming = vm.qrState.value
        assertTrue(confirming is KugouQrState.Confirming)
        assertEquals("酷狗测试用户", (confirming as KugouQrState.Confirming).nickname)

        advanceTimeBy(1_000L)
        runCurrent()
        val success = vm.qrState.value
        assertTrue(success is KugouQrState.Success)
        assertEquals(AccountId.kugou("10086"), (success as KugouQrState.Success).account.accountId)
        assertEquals(AccountId.kugou("10086"), callbackAccount?.accountId)

        eventJob.join()
        assertEquals(MusicPlatform.KUGOU, events.single().platform)
        assertEquals(AccountSwitchReason.ADDED, events.single().reason)
        vm.reset()
    }

    @Test
    fun qrExpiredStopsPollingAndCanRetry() = runTest(testDispatcher) {
        val provider = FakeKugouAuthProvider().apply {
            pollResults = ArrayDeque(listOf(Result.success(PlatformQrPollResult.Expired)))
        }
        val (_, _, vm) = createEnv(provider)

        vm.startQrLogin()
        runCurrent()
        assertTrue(vm.qrState.value is KugouQrState.WaitingScan)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(KugouQrState.Expired, vm.qrState.value)
        assertEquals(1, provider.pollCalls)

        // 过期后不再继续轮询
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1, provider.pollCalls)

        // 重试重新生成二维码并进入 WaitingScan
        provider.pollResults = ArrayDeque(listOf(Result.success(PlatformQrPollResult.Waiting)))
        vm.startQrLogin()
        runCurrent()
        assertTrue(vm.qrState.value is KugouQrState.WaitingScan)
        vm.reset()
    }

    @Test
    fun phoneCaptchaCountdownAndLoginSuccess() = runTest(testDispatcher) {
        val account = StoredAccount(
            uid = 20001L,
            nickname = "手机用户",
            platform = MusicPlatform.KUGOU,
            platformUid = "20001",
            credentials = mapOf("token" to "tok_phone", "userid" to "20001")
        )
        val provider = FakeKugouAuthProvider().apply {
            phoneLoginResult = Result.success(
                PlatformAuthResult(
                    platform = MusicPlatform.KUGOU,
                    accountId = account.accountId,
                    nickname = account.nickname,
                    credentials = account.credentials,
                    account = account
                )
            )
        }
        val (_, _, vm) = createEnv(provider)

        vm.sendPhoneCaptcha("13800138000")
        runCurrent()
        assertEquals(KugouPhoneLoginState.CaptchaSent, vm.phoneState.value)
        assertEquals(60, vm.captchaCountdownSec.value)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(57, vm.captchaCountdownSec.value)

        // 倒计时期间再次点击发送被忽略
        vm.sendPhoneCaptcha("13800138000")
        runCurrent()
        assertEquals(1, provider.sendCaptchaCalls)

        var successAccount: StoredAccount? = null
        vm.loginByPhone("13800138000", "123456") { successAccount = it }
        runCurrent()
        assertTrue(vm.phoneState.value is KugouPhoneLoginState.Success)
        assertEquals(0, vm.captchaCountdownSec.value)
        assertEquals(account.accountId, successAccount?.accountId)
        vm.reset()
    }

    @Test
    fun riskErrorSuggestsQrFallbackInPhoneLogin() = runTest(testDispatcher) {
        val provider = FakeKugouAuthProvider().apply {
            sendCaptchaResult = Result.failure(
                KugouRiskException(
                    PlatformRiskChallenge(
                        platform = MusicPlatform.KUGOU,
                        ssaCode = "verify",
                        message = "触发风控"
                    )
                )
            )
            phoneLoginResult = Result.failure(AppError.RiskControl)
        }
        val (_, _, vm) = createEnv(provider)

        vm.sendPhoneCaptcha("13800138000")
        runCurrent()
        val captchaErr = vm.phoneState.value as KugouPhoneLoginState.Error
        assertTrue(captchaErr.suggestQrFallback)

        vm.loginByPhone("13800138000", "654321")
        runCurrent()
        val loginErr = vm.phoneState.value as KugouPhoneLoginState.Error
        assertTrue(loginErr.suggestQrFallback)

        vm.reset()
        assertEquals(KugouQrState.Idle, vm.qrState.value)
        assertEquals(KugouPhoneLoginState.Idle, vm.phoneState.value)
        assertFalse(vm.captchaCountdownSec.value > 0)
    }

    @Test
    fun resetCancelsQrPollingAndCountdownJobs() = runTest(testDispatcher) {
        val provider = FakeKugouAuthProvider().apply {
            pollResults = ArrayDeque(listOf(Result.success(PlatformQrPollResult.Waiting)))
        }
        val (_, _, vm) = createEnv(provider)

        vm.startQrLogin()
        vm.sendPhoneCaptcha("13800138000")
        runCurrent()
        assertTrue(vm.qrState.value is KugouQrState.WaitingScan)
        assertEquals(60, vm.captchaCountdownSec.value)

        vm.reset()
        assertEquals(KugouQrState.Idle, vm.qrState.value)
        assertEquals(KugouPhoneLoginState.Idle, vm.phoneState.value)
        assertEquals(0, vm.captchaCountdownSec.value)

        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(0, provider.pollCalls)
        assertEquals(0, vm.captchaCountdownSec.value)
    }
}

private class FakeKugouAuthProvider : PlatformAuthProvider {
    override val platform: MusicPlatform = MusicPlatform.KUGOU

    var createQrResult: Result<PlatformQrKey> = Result.success(
        PlatformQrKey(platform = MusicPlatform.KUGOU, key = "k1", qrUrl = "https://kugou.test/qr?k=k1")
    )
    var pollResults: ArrayDeque<Result<PlatformQrPollResult>> = ArrayDeque()
    var sendCaptchaResult: Result<Unit> = Result.success(Unit)
    var phoneLoginResult: Result<PlatformAuthResult> = Result.failure(AppError.NetworkError)

    var pollCalls = 0
    var sendCaptchaCalls = 0

    override suspend fun createQrLogin(): Result<PlatformQrKey> = createQrResult

    override suspend fun pollQrLogin(key: String): Result<PlatformQrPollResult> {
        pollCalls++
        return if (pollResults.isEmpty()) {
            Result.success(PlatformQrPollResult.Waiting)
        } else {
            pollResults.removeFirst()
        }
    }

    override suspend fun sendPhoneCaptcha(mobile: String): Result<Unit> {
        sendCaptchaCalls++
        return sendCaptchaResult
    }

    override suspend fun loginByPhone(mobile: String, code: String, userid: String?): Result<PlatformAuthResult> =
        phoneLoginResult

    override suspend fun refreshSession(account: StoredAccount): Result<PlatformAuthResult> =
        Result.failure(UnsupportedOperationException())

    override suspend fun fetchUserDetail(account: StoredAccount): Result<PlatformUserDetail> =
        Result.failure(UnsupportedOperationException())
}
