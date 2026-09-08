package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.api.AccountInfoResponse
import com.lin0721.linmusic.core.api.NeteaseApiService
import com.lin0721.linmusic.core.api.QrCheckRequest
import com.lin0721.linmusic.core.api.QrCheckResponse
import com.lin0721.linmusic.core.api.QrKeyResponse
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.network.mapToAppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

private const val TAG = "AuthRepositoryImpl"

class AuthRepositoryImpl(
    private val apiService: NeteaseApiService
) : AuthRepository {

    override fun getAccountInfo(): Flow<Result<AccountInfoResponse>> = apiFlow(
        request = { apiService.getAccountInfo() },
        isSuccess = { it.code == 200 },
        code = { it.code },
        transform = { it }
    )

    override fun logout(): Flow<Result<Unit>> = apiFlow(
        request = { apiService.logoutApi() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { Unit }
    )

    override fun getQrKey(): Flow<Result<QrKeyResponse>> = apiFlow(
        request = { apiService.getQrKey() },
        isSuccess = { it.code == 200 },
        code = { it.code },
        transform = { it }
    )

    // 800/801/802/803 都是正常的轮询状态，不当业务错误处理；只有网络/解析异常才走 failure
    override fun checkQrStatus(key: String): Flow<Result<QrCheckResponse>> = flow {
        val response = apiService.checkQrStatus(QrCheckRequest(key = key))
        val body = response.body()
        if (body == null) {
            AppLogger.e(TAG, "二维码状态查询响应体为空，httpCode=${response.code()}")
            emit(Result.failure(AppError.NetworkError))
        } else {
            val cookies = response.headers().values("Set-Cookie")
                .map { it.substringBefore(";").trim() }
                .filter { it.contains("=") }
                .joinToString("; ")
                .takeIf { it.isNotEmpty() }
            emit(Result.success(body.copy(cookies = cookies)))
        }
    }.catch { e ->
        AppLogger.e(TAG, "二维码状态查询异常: ${e::class.simpleName}", e)
        emit(Result.failure(mapToAppError(e)))
    }
}
