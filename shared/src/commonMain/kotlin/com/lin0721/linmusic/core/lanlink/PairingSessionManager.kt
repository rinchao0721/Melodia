package com.lin0721.linmusic.core.lanlink

import java.security.SecureRandom

data class PairingCodeState(
    val code: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val remainingAttempts: Int
)

sealed interface PairingVerifyOutcome {
    data class Verified(val code: String) : PairingVerifyOutcome
    data class InvalidCode(val remainingAttempts: Int) : PairingVerifyOutcome
    data object LockedOut : PairingVerifyOutcome
    data object UnavailableOrExpired : PairingVerifyOutcome
}

// 管理桌面端 6 位配对码生命周期：2 分钟有效期、最多 5 次错误尝试、验证成功后即作废
class PairingSessionManager(
    private val ttlMs: Long = LanLinkConstants.PAIRING_CODE_TTL_MS,
    private val maxAttempts: Int = LanLinkConstants.PAIRING_MAX_ATTEMPTS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val codeGenerator: () -> String = {
        val num = SecureRandom().nextInt(1_000_000)
        num.toString().padStart(6, '0')
    }
) {
    private val lock = Any()
    private var active: PairingCodeState? = null

    fun startNewCode(): PairingCodeState = synchronized(lock) {
        val now = nowMs()
        val created = PairingCodeState(
            code = codeGenerator(),
            createdAtMs = now,
            expiresAtMs = now + ttlMs,
            remainingAttempts = maxAttempts
        )
        active = created
        created
    }

    fun cancelCode() = synchronized(lock) {
        active = null
    }

    fun currentState(): PairingCodeState? = synchronized(lock) {
        val current = active ?: return@synchronized null
        if (nowMs() >= current.expiresAtMs || current.remainingAttempts <= 0) {
            active = null
            return@synchronized null
        }
        current
    }

    fun verifyAttempt(verifier: (code: String) -> Boolean): PairingVerifyOutcome = synchronized(lock) {
        val current = active ?: return@synchronized PairingVerifyOutcome.UnavailableOrExpired
        if (nowMs() >= current.expiresAtMs || current.remainingAttempts <= 0) {
            active = null
            return@synchronized PairingVerifyOutcome.UnavailableOrExpired
        }
        if (verifier(current.code)) {
            active = null
            return@synchronized PairingVerifyOutcome.Verified(current.code)
        }
        val left = current.remainingAttempts - 1
        if (left <= 0) {
            active = null
            PairingVerifyOutcome.LockedOut
        } else {
            active = current.copy(remainingAttempts = left)
            PairingVerifyOutcome.InvalidCode(left)
        }
    }
}
