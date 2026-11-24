package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.api.Account
import com.lin0721.linmusic.core.api.AccountInfoResponse
import com.lin0721.linmusic.core.api.QrCheckResponse
import com.lin0721.linmusic.core.api.QrKeyResponse
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

internal fun loggedInAccount(
    uid: Long = 1L,
    nickname: String = "测试用户",
    avatarUrl: String = ""
) = AccountInfoResponse(
    code = 200,
    account = Account(id = uid),
    profile = com.lin0721.linmusic.core.api.UserProfile(
        userId = uid,
        nickname = nickname,
        avatarUrl = avatarUrl
    )
)

internal fun anonymousAccount() = AccountInfoResponse(code = 200, account = null, profile = null)

internal fun tempUserPreferences(nowMs: () -> Long = { System.currentTimeMillis() }): Pair<UserPreferences, File> {
    val dir = Files.createTempDirectory("melodia-auth-test").toFile()
    return UserPreferences(PreferencesStores.get(File(dir, "user.preferences_pb")), nowMs = nowMs) to dir
}

internal class FakeAuthRepository(
    private val userPreferences: UserPreferences? = null
) : AuthRepository {

    // 每次 getAccountInfo 依次取一个结果，取完后一直返回最后一个
    var accountResults: List<Result<AccountInfoResponse>> = listOf(Result.failure(AppError.NetworkError))
    var accountResultByCookie: Map<String, Result<AccountInfoResponse>> = emptyMap()
    var refreshResult: Result<List<String>> = Result.success(emptyList())

    var accountCalls = 0
    var overrideAccountCalls = 0
    var lastOverrideCookies: String? = null
    var logoutCalls = 0
    var logoutActiveUids = mutableListOf<Long?>()
    var refreshCalls = 0

    override fun getAccountInfo(): Flow<Result<AccountInfoResponse>> = flow {
        val result = accountResults[minOf(accountCalls, accountResults.lastIndex)]
        accountCalls++
        emit(result)
    }

    override fun getAccountInfo(overrideCookies: String): Flow<Result<AccountInfoResponse>> = flow {
        lastOverrideCookies = overrideCookies
        overrideAccountCalls++
        val mapped = accountResultByCookie[overrideCookies]
        val result = if (mapped != null) {
            mapped
        } else {
            val r = accountResults[minOf(accountCalls, accountResults.lastIndex)]
            accountCalls++
            r
        }
        emit(result)
    }

    override fun logout(): Flow<Result<Unit>> = flow {
        logoutCalls++
        // 记录触发 logout 时活跃账户仍在列表内
        if (userPreferences != null) {
            logoutActiveUids.add(userPreferences.activeUid.firstOrNull())
        }
        emit(Result.success(Unit))
    }

    override fun getQrKey(): Flow<Result<QrKeyResponse>> = flow { emit(Result.failure(AppError.NetworkError)) }

    override fun checkQrStatus(key: String): Flow<Result<QrCheckResponse>> = flow { emit(Result.failure(AppError.NetworkError)) }

    override fun refreshLogin(): Flow<Result<List<String>>> = flow {
        refreshCalls++
        emit(refreshResult)
    }
}

internal class RecordingPlaybackController(
    private val delegate: FakePlaybackController = FakePlaybackController()
) : PlaybackController by delegate {
    var stopAndClearCalls = 0

    override fun stopAndClearQueue() {
        stopAndClearCalls++
        delegate.stopAndClearQueue()
    }

    override fun stopAndClearForAccountChange() = stopAndClearQueue()
}
