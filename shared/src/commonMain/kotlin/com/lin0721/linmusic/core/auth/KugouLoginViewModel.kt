package com.lin0721.linmusic.core.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.lin0721.linmusic.core.kugou.KugouRiskException
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "KugouLoginViewModel"
private const val QR_SIZE_PX = 480
private const val MAX_CONSECUTIVE_POLL_FAILURES = 5
private const val CAPTCHA_COUNTDOWN_SECONDS = 60

// 酷狗二维码登录状态机
sealed interface KugouQrState {
    data object Idle : KugouQrState
    data object Loading : KugouQrState
    data class WaitingScan(val qrMatrix: BitMatrix) : KugouQrState
    data class Confirming(
        val qrMatrix: BitMatrix,
        val nickname: String? = null,
        val avatarUrl: String? = null
    ) : KugouQrState
    data object Expired : KugouQrState
    data class Success(val account: StoredAccount) : KugouQrState
    data class Error(val message: String, val suggestQrFallback: Boolean = false) : KugouQrState
}

// 酷狗手机号验证码登录状态机
sealed interface KugouPhoneLoginState {
    data object Idle : KugouPhoneLoginState
    data object SendingCaptcha : KugouPhoneLoginState
    data object CaptchaSent : KugouPhoneLoginState
    data object Submitting : KugouPhoneLoginState
    data class Success(val account: StoredAccount) : KugouPhoneLoginState
    data class Error(val message: String, val suggestQrFallback: Boolean = false) : KugouPhoneLoginState
}

// 酷狗登录状态机（双端共用）：支持二维码扫码轮询与手机验证码登录，并在触发风控时引导切换扫码登录
class KugouLoginViewModel(
    private val authProvider: PlatformAuthProvider,
    private val userPreferences: UserPreferences,
    private val accountSwitcher: AccountSwitcher,
    private val pollIntervalMs: Long = 2500L,
    private val qrDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _qrState = MutableStateFlow<KugouQrState>(KugouQrState.Idle)
    val qrState: StateFlow<KugouQrState> = _qrState.asStateFlow()

    private val _phoneState = MutableStateFlow<KugouPhoneLoginState>(KugouPhoneLoginState.Idle)
    val phoneState: StateFlow<KugouPhoneLoginState> = _phoneState.asStateFlow()

    private val _captchaCountdownSec = MutableStateFlow(0)
    val captchaCountdownSec: StateFlow<Int> = _captchaCountdownSec.asStateFlow()

    private var qrStartJob: Job? = null
    private var pollJob: Job? = null
    private var phoneJob: Job? = null
    private var countdownJob: Job? = null

    // 启动/重试酷狗二维码登录：获取 key/url -> 生成点阵 -> 轮询扫码状态
    fun startQrLogin(onLoginSuccess: (StoredAccount) -> Unit = {}) {
        stopQrPolling()
        _qrState.value = KugouQrState.Loading
        qrStartJob = viewModelScope.launch {
            val qrResult = authProvider.createQrLogin()
            val qr = qrResult.getOrElse { err ->
                AppLogger.w(TAG, "获取酷狗登录二维码失败", err)
                _qrState.value = if (isRiskError(err)) {
                    KugouQrState.Error("酷狗触发安全验证，建议稍后重试扫码", suggestQrFallback = true)
                } else {
                    KugouQrState.Error(formatFriendlyError(err, "二维码生成失败，请重试"))
                }
                return@launch
            }

            val content = qr.qrUrl.ifBlank { qr.key }
            if (content.isBlank()) {
                _qrState.value = KugouQrState.Error("二维码内容为空，请重试")
                return@launch
            }

            val matrix = try {
                withContext(qrDispatcher) { generateQrMatrix(content) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "生成酷狗二维码点阵失败", e)
                _qrState.value = KugouQrState.Error("二维码生成失败，请重试")
                return@launch
            }

            _qrState.value = KugouQrState.WaitingScan(matrix)
            pollQrStatus(qr.key, matrix, onLoginSuccess)
        }
    }

    private fun pollQrStatus(
        key: String,
        matrix: BitMatrix,
        onLoginSuccess: (StoredAccount) -> Unit
    ) {
        pollJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (true) {
                delay(pollIntervalMs)
                val previousAccountId = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()?.accountId
                val pollResult = authProvider.pollQrLogin(key)
                val err = pollResult.exceptionOrNull()
                if (err != null) {
                    if (isRiskError(err)) {
                        AppLogger.w(TAG, "酷狗二维码轮询触发风控", err)
                        _qrState.value = KugouQrState.Error("酷狗触发安全验证，建议改用扫码登录", suggestQrFallback = true)
                        return@launch
                    }
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                        AppLogger.w(TAG, "酷狗二维码轮询连续失败次数超限", err)
                        _qrState.value = KugouQrState.Error("网络异常，请重试")
                        return@launch
                    }
                    continue
                }
                consecutiveFailures = 0
                when (val status = pollResult.getOrThrow()) {
                    PlatformQrPollResult.Waiting -> {
                        _qrState.value = KugouQrState.WaitingScan(matrix)
                    }
                    is PlatformQrPollResult.Confirming -> {
                        _qrState.value = KugouQrState.Confirming(
                            qrMatrix = matrix,
                            nickname = status.nickname,
                            avatarUrl = status.avatarUrl
                        )
                    }
                    PlatformQrPollResult.Expired -> {
                        _qrState.value = KugouQrState.Expired
                        return@launch
                    }
                    is PlatformQrPollResult.Failed -> {
                        _qrState.value = KugouQrState.Error(
                            status.message.ifBlank { "酷狗二维码登录失败，请重试" }
                        )
                        return@launch
                    }
                    is PlatformQrPollResult.Authorized -> {
                        val account = resolveSavedAccount(status.auth)
                        accountSwitcher.onPlatformAccountLoggedIn(account, previousAccountId)
                        _qrState.value = KugouQrState.Success(account)
                        onLoginSuccess(account)
                        return@launch
                    }
                }
            }
        }
    }

    // 发送酷狗短信验证码
    fun sendPhoneCaptcha(mobile: String) {
        val cleanMobile = mobile.trim()
        if (!cleanMobile.matches(Regex("^\\d{6,15}$"))) {
            _phoneState.value = KugouPhoneLoginState.Error("请输入正确的手机号")
            return
        }
        if (_captchaCountdownSec.value > 0) return

        phoneJob?.cancel()
        _phoneState.value = KugouPhoneLoginState.SendingCaptcha
        phoneJob = viewModelScope.launch {
            val result = authProvider.sendPhoneCaptcha(cleanMobile)
            val err = result.exceptionOrNull()
            if (err != null) {
                AppLogger.w(TAG, "发送酷狗短信验证码失败", err)
                _phoneState.value = if (isRiskError(err)) {
                    KugouPhoneLoginState.Error("酷狗触发安全验证，建议改用扫码登录", suggestQrFallback = true)
                } else {
                    KugouPhoneLoginState.Error(formatFriendlyError(err, "验证码发送失败，请重试"))
                }
                return@launch
            }
            _phoneState.value = KugouPhoneLoginState.CaptchaSent
            startCountdown()
        }
    }

    // 提交手机号和验证码登录酷狗
    fun loginByPhone(
        mobile: String,
        code: String,
        onLoginSuccess: (StoredAccount) -> Unit = {}
    ) {
        val cleanMobile = mobile.trim()
        val cleanCode = code.trim()
        if (!cleanMobile.matches(Regex("^\\d{6,15}$")) || cleanCode.isBlank()) {
            _phoneState.value = KugouPhoneLoginState.Error("请输入正确的手机号和验证码")
            return
        }

        phoneJob?.cancel()
        _phoneState.value = KugouPhoneLoginState.Submitting
        phoneJob = viewModelScope.launch {
            val previousAccountId = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()?.accountId
            val result = authProvider.loginByPhone(cleanMobile, cleanCode)
            val err = result.exceptionOrNull()
            if (err != null) {
                AppLogger.w(TAG, "酷狗手机验证码登录失败", err)
                _phoneState.value = if (isRiskError(err)) {
                    KugouPhoneLoginState.Error("酷狗触发安全验证，建议改用扫码登录", suggestQrFallback = true)
                } else {
                    KugouPhoneLoginState.Error(formatFriendlyError(err, "验证码登录失败，请重试"))
                }
                return@launch
            }
            val authResult = result.getOrThrow()
            val account = resolveSavedAccount(authResult)
            accountSwitcher.onPlatformAccountLoggedIn(account, previousAccountId)
            countdownJob?.cancel()
            countdownJob = null
            _captchaCountdownSec.value = 0
            _phoneState.value = KugouPhoneLoginState.Success(account)
            onLoginSuccess(account)
        }
    }

    fun stopQrPolling() {
        qrStartJob?.cancel()
        qrStartJob = null
        pollJob?.cancel()
        pollJob = null
    }

    // 取消全部协程并复位全部状态，供关闭弹窗/退出登录页调用
    fun reset() {
        stopQrPolling()
        phoneJob?.cancel()
        phoneJob = null
        countdownJob?.cancel()
        countdownJob = null
        _captchaCountdownSec.value = 0
        _qrState.value = KugouQrState.Idle
        _phoneState.value = KugouPhoneLoginState.Idle
    }

    fun resetPhoneState() {
        phoneJob?.cancel()
        phoneJob = null
        _phoneState.value = KugouPhoneLoginState.Idle
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        _captchaCountdownSec.value = CAPTCHA_COUNTDOWN_SECONDS
        countdownJob = viewModelScope.launch {
            for (sec in (CAPTCHA_COUNTDOWN_SECONDS - 1) downTo 0) {
                delay(1000L)
                _captchaCountdownSec.value = sec
            }
        }
    }

    private suspend fun resolveSavedAccount(authResult: PlatformAuthResult): StoredAccount {
        authResult.account?.let { return it }
        val active = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        if (active != null && active.accountId == authResult.accountId) return active
        val fallback = StoredAccount(
            uid = authResult.accountId.rawId.toLongOrNull() ?: 0L,
            nickname = authResult.nickname,
            avatarUrl = authResult.avatarUrl,
            cookies = authResult.cookies,
            platform = MusicPlatform.KUGOU,
            platformUid = authResult.accountId.rawId,
            credentials = authResult.credentials,
            vipLabel = authResult.vipLabel
        )
        userPreferences.upsertPlatformAccount(fallback, activate = true)
        return fallback
    }

    private fun isRiskError(error: Throwable): Boolean =
        error is KugouRiskException || error is AppError.RiskControl

    private fun formatFriendlyError(error: Throwable, fallback: String): String = when (error) {
        is AppError.NetworkError -> "网络连接异常，请检查网络后重试"
        is AppError.BizError -> error.rawMsg?.takeIf { it.isNotBlank() } ?: fallback
        else -> error.message?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun generateQrMatrix(content: String): BitMatrix =
        MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX)

    override fun onCleared() {
        reset()
        super.onCleared()
    }
}
