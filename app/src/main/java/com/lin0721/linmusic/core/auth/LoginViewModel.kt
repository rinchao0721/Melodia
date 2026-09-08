package com.lin0721.linmusic.core.auth

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LoginViewModel"
private const val QR_LOGIN_URL_PREFIX = "https://music.163.com/login?codekey="
private const val POLL_INTERVAL_MS = 2500L
private const val QR_SIZE_PX = 480
private const val MAX_CONSECUTIVE_POLL_FAILURES = 5

sealed interface QrLoginState {
    data object Idle : QrLoginState
    data object Loading : QrLoginState
    data class WaitingScan(val qrBitmap: Bitmap) : QrLoginState
    data class WaitingConfirm(val qrBitmap: Bitmap) : QrLoginState
    data object Expired : QrLoginState
    data class Error(val message: String) : QrLoginState
}

// 登录弹窗内的二维码轮询状态机与 Cookie 提交校验，供 LoginBottomSheet 消费
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _qrState = MutableStateFlow<QrLoginState>(QrLoginState.Idle)
    val qrState: StateFlow<QrLoginState> = _qrState.asStateFlow()

    private var pollJob: Job? = null

    // 开始二维码登录：取 key → 本地生成二维码 → 轮询扫码状态
    fun startQrLogin(onLoginSuccess: (String) -> Unit) {
        stopQrPolling()
        _qrState.value = QrLoginState.Loading
        viewModelScope.launch {
            val key = authRepository.getQrKey().first().getOrNull()?.unikey
            if (key.isNullOrEmpty()) {
                _qrState.value = QrLoginState.Error("二维码生成失败，请重试")
                return@launch
            }
            val bitmap = try {
                withContext(Dispatchers.Default) { generateQrBitmap(QR_LOGIN_URL_PREFIX + key) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "二维码图片生成失败", e)
                _qrState.value = QrLoginState.Error("二维码生成失败，请重试")
                return@launch
            }
            _qrState.value = QrLoginState.WaitingScan(bitmap)
            pollQrStatus(key, bitmap, onLoginSuccess)
        }
    }

    private fun pollQrStatus(key: String, bitmap: Bitmap, onLoginSuccess: (String) -> Unit) {
        pollJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (true) {
                delay(POLL_INTERVAL_MS)
                val response = authRepository.checkQrStatus(key).first().getOrNull()
                if (response == null) {
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                        _qrState.value = QrLoginState.Error("网络异常，请重试")
                        return@launch
                    }
                    continue
                }
                consecutiveFailures = 0
                when (response.code) {
                    800 -> {
                        _qrState.value = QrLoginState.Expired
                        return@launch
                    }
                    801 -> _qrState.value = QrLoginState.WaitingScan(bitmap)
                    802 -> _qrState.value = QrLoginState.WaitingConfirm(bitmap)
                    803 -> {
                        val cookies = response.cookies
                        if (cookies.isNullOrEmpty()) {
                            AppLogger.e(TAG, "二维码登录返回 803 但未解析到 Set-Cookie")
                            _qrState.value = QrLoginState.Error("登录成功但未获取到会话，请重试")
                        } else {
                            onLoginSuccess(cookies)
                        }
                        return@launch
                    }
                }
            }
        }
    }

    fun stopQrPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // 弹窗关闭/切换登录方式时重置，避免下次打开残留上一次的二维码画面
    fun resetQrState() {
        stopQrPolling()
        _qrState.value = QrLoginState.Idle
    }

    // Cookie 登录：支持粘贴完整 Cookie 字符串，也支持只粘贴裸 MUSIC_U 值——
    // 浏览器里 MUSIC_U 是 HttpOnly，Cookie 编辑类工具通常只能单独复制到它的 Value，不含字段名和分号分隔的其他字段
    fun submitCookieLogin(rawCookie: String, onLoginSuccess: (String) -> Unit): Boolean {
        val trimmed = rawCookie.trim()
        val cookieString = when {
            trimmed.isEmpty() -> return false
            trimmed.contains("MUSIC_U=") -> trimmed
            !trimmed.contains("=") && !trimmed.contains(";") && !trimmed.contains(" ") -> "MUSIC_U=$trimmed"
            else -> return false
        }
        onLoginSuccess(cookieString)
        return true
    }

    // 一次性 setPixels 批量写入
    private fun generateQrBitmap(content: String): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(
            content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX
        )
        val pixels = IntArray(QR_SIZE_PX * QR_SIZE_PX)
        for (y in 0 until QR_SIZE_PX) {
            val rowOffset = y * QR_SIZE_PX
            for (x in 0 until QR_SIZE_PX) {
                pixels[rowOffset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(QR_SIZE_PX, QR_SIZE_PX, Bitmap.Config.RGB_565)
        bitmap.setPixels(pixels, 0, QR_SIZE_PX, 0, 0, QR_SIZE_PX, QR_SIZE_PX)
        return bitmap
    }

    override fun onCleared() {
        stopQrPolling()
        super.onCleared()
    }
}
