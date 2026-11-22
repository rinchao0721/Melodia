package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.PlatformAuthProvider
import com.lin0721.linmusic.core.auth.PlatformAuthResult
import com.lin0721.linmusic.core.auth.PlatformQrKey
import com.lin0721.linmusic.core.auth.PlatformQrPollResult
import com.lin0721.linmusic.core.auth.PlatformUserDetail
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.source.MusicPlatform

// 酷狗音乐平台鉴权提供方：登录与续期成功后统一落盘到 UserPreferences(KUGOU)
class KugouAuthProvider(
    private val authService: KugouAuthService,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : PlatformAuthProvider {

    override val platform: MusicPlatform = MusicPlatform.KUGOU

    override suspend fun createQrLogin(): Result<PlatformQrKey> =
        authService.createQrLogin().map { qr ->
            PlatformQrKey(
                platform = MusicPlatform.KUGOU,
                key = qr.key,
                qrUrl = qr.qrUrl,
                qrImageBase64 = qr.qrImageBase64
            )
        }

    override suspend fun pollQrLogin(key: String): Result<PlatformQrPollResult> =
        authService.pollQrLogin(key).map { poll ->
            when (poll.state) {
                KugouQrPollResult.State.EXPIRED -> PlatformQrPollResult.Expired
                KugouQrPollResult.State.WAITING -> PlatformQrPollResult.Waiting
                KugouQrPollResult.State.CONFIRMING -> PlatformQrPollResult.Confirming(
                    nickname = poll.nickname.ifBlank { null },
                    avatarUrl = poll.avatarUrl.ifBlank { null }
                )
                KugouQrPollResult.State.FAILED -> PlatformQrPollResult.Failed(
                    message = poll.message.ifBlank { "酷狗二维码登录失败" },
                    errorCode = poll.rawStatus
                )
                KugouQrPollResult.State.AUTHORIZED -> {
                    val credentials = mapOf(
                        "token" to poll.token,
                        "userid" to poll.userid
                    )
                    val authResult = persistKugouAccount(
                        userid = poll.userid,
                        nickname = poll.nickname,
                        avatarUrl = poll.avatarUrl,
                        credentials = credentials
                    )
                    PlatformQrPollResult.Authorized(authResult)
                }
            }
        }

    override suspend fun sendPhoneCaptcha(mobile: String): Result<Unit> =
        authService.sendPhoneCaptcha(mobile)

    override suspend fun loginByPhone(
        mobile: String,
        code: String,
        userid: String?
    ): Result<PlatformAuthResult> =
        authService.loginByPhone(mobile = mobile, code = code, userid = userid).map { cred ->
            val vipLabel = if (cred.vipType > 0) "酷狗 VIP" else ""
            persistKugouAccount(
                userid = cred.userid,
                nickname = cred.nickname,
                avatarUrl = cred.avatarUrl,
                credentials = cred.toCredentialMap(),
                vipLabel = vipLabel
            )
        }

    override suspend fun refreshSession(account: StoredAccount): Result<PlatformAuthResult> =
        authService.refreshToken(
            token = account.credentials["token"],
            userid = account.effectivePlatformUid,
            t1Cookie = account.credentials["t1"]
        ).map { cred ->
            val updatedMap = cred.toCredentialMap()
            val refreshedAt = nowMs()
            val targetId = cred.userid.takeIf { it.isNotBlank() && it != "0" }
                ?: account.effectivePlatformUid
            val accountId = AccountId.kugou(targetId)
            userPreferences.updatePlatformCredentials(
                accountId = accountId,
                credentials = updatedMap,
                lastRefreshAt = refreshedAt
            )
            val updatedAccount = account.copy(
                uid = targetId.toLongOrNull() ?: account.uid,
                platformUid = targetId,
                nickname = cred.nickname.ifBlank { account.nickname },
                avatarUrl = cred.avatarUrl.ifBlank { account.avatarUrl },
                credentials = account.credentials + updatedMap,
                lastLoginRefreshAt = refreshedAt
            )
            PlatformAuthResult(
                platform = MusicPlatform.KUGOU,
                accountId = accountId,
                nickname = updatedAccount.nickname,
                avatarUrl = updatedAccount.avatarUrl,
                credentials = updatedAccount.credentials,
                vipLabel = updatedAccount.vipLabel,
                account = updatedAccount
            )
        }

    override suspend fun fetchUserDetail(account: StoredAccount): Result<PlatformUserDetail> =
        authService.fetchUserDetail(
            token = account.credentials["token"],
            userid = account.effectivePlatformUid
        ).map { detail ->
            val isVip = detail.isVip > 0 || detail.vipType > 0
            PlatformUserDetail(
                accountId = AccountId.kugou(detail.userid.takeIf { it > 0L }?.toString() ?: account.effectivePlatformUid),
                nickname = detail.nickname,
                avatarUrl = detail.pic,
                vipLabel = if (isVip) "酷狗 VIP" else "",
                isVip = isVip,
                extra = mapOf(
                    "vip_type" to detail.vipType.toString(),
                    "signature" to detail.signature
                )
            )
        }

    private suspend fun persistKugouAccount(
        userid: String,
        nickname: String,
        avatarUrl: String,
        credentials: Map<String, String>,
        vipLabel: String = ""
    ): PlatformAuthResult {
        val now = nowMs()
        val uidLong = userid.toLongOrNull() ?: 0L
        val accountId = AccountId.kugou(userid)
        val account = StoredAccount(
            uid = uidLong,
            nickname = nickname,
            avatarUrl = avatarUrl,
            cookies = "",
            lastLoginRefreshAt = now,
            lastUsedAt = now,
            platform = MusicPlatform.KUGOU,
            platformUid = userid,
            credentials = credentials,
            vipLabel = vipLabel
        )
        userPreferences.upsertPlatformAccount(account, activate = true)
        return PlatformAuthResult(
            platform = MusicPlatform.KUGOU,
            accountId = accountId,
            nickname = nickname,
            avatarUrl = avatarUrl,
            credentials = credentials,
            vipLabel = vipLabel,
            account = account
        )
    }
}
