package com.lin0721.linmusic.core.network

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * 统一承载 Repository 层的 flow + catch + 业务码判定，替代重复的 flow{}.catch{} 模板。
 * 项目里各响应 DTO 没有共同的基类/接口，故用 lambda 取值而非泛型约束。
 */
inline fun <T, R> apiFlow(
    crossinline request: suspend () -> T,
    crossinline isSuccess: (T) -> Boolean,
    crossinline code: (T) -> Int,
    crossinline msg: (T) -> String? = { null },
    crossinline transform: suspend (T) -> R
): Flow<Result<R>> = flow {
    val response = request()
    if (isSuccess(response)) {
        emit(Result.success(transform(response)))
    } else if (code(response) == 301) {
        emit(Result.failure(AppError.Unauthorized))
    } else {
        AppLogger.e("ApiFlow", "业务错误码 code=${code(response)} msg=${msg(response)}")
        emit(Result.failure(AppError.BizError(code(response), msg(response))))
    }
}.catch { e ->
    AppLogger.e("ApiFlow", "请求异常: ${e::class.simpleName}", e)
    emit(Result.failure(mapToAppError(e)))
}

// 拦截器/序列化等非业务码异常统一映射为 AppError
fun mapToAppError(e: Throwable): Throwable = when (e) {
    is AppError -> e
    is ApiException -> AppError.RiskControl
    is SerializationException -> AppError.ParseError
    is IOException -> AppError.NetworkError
    else -> e
}
