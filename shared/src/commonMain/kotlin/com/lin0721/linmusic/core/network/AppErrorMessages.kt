package com.lin0721.linmusic.core.network

// 将 AppError 类型解析为面向用户的提示文案
fun AppError.toMessage(resourceProvider: ResourceProvider): String = when (this) {
    AppError.NetworkError -> resourceProvider.getString(AppString.ErrorNetwork)
    AppError.RiskControl -> resourceProvider.getString(AppString.ErrorRiskControl)
    AppError.Unauthorized -> resourceProvider.getString(AppString.ErrorUnauthorized)
    AppError.ParseError -> resourceProvider.getString(AppString.ErrorParse)
    is AppError.BizError -> resourceProvider.getString(AppString.ErrorBizDefault)
}

// 统一取错误提示：Repository 出口应始终是 AppError，非 AppError 兜底走原始 message
fun Throwable.toUserMessage(resourceProvider: ResourceProvider): String =
    (this as? AppError)?.toMessage(resourceProvider) ?: (message ?: resourceProvider.getString(AppString.ErrorBizDefault))
