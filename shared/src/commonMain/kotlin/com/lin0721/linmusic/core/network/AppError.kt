package com.lin0721.linmusic.core.network

// Repository 出口统一的错误语义类型，UI 层按类型分流渲染，不携带面向用户的文案
sealed class AppError : Exception() {
    // 连接层失败（IOException 等），非 ApiException
    data object NetworkError : AppError()

    // 映射自 ApiException（0 字节响应，风控/鉴权失败）
    data object RiskControl : AppError()

    // code == 301，唯一有代码依据的未登录标识
    data object Unauthorized : AppError()

    // 序列化/反序列化失败
    data object ParseError : AppError()

    // 其余未细分的业务失败兜底
    data class BizError(val code: Int, val rawMsg: String?) : AppError()
}
