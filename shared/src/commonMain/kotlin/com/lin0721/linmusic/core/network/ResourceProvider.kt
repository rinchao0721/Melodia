package com.lin0721.linmusic.core.network

// 共享层用到的文案键，具体文本由平台提供
enum class AppString {
    ErrorNetwork,
    ErrorRiskControl,
    ErrorUnauthorized,
    ErrorParse,
    ErrorBizDefault,
}

// 供无法直接持有平台上下文的 ViewModel 解析文案
open class ResourceProvider {
    open fun getString(key: AppString): String = ""
}
