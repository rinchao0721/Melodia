package com.lin0721.linmusic.core.network

import android.content.Context
import com.lin0721.linmusic.R

// 文案统一收在 strings.xml；只持有 applicationContext，不存在内存泄漏风险
class AndroidResourceProvider(private val context: Context) : ResourceProvider() {
    override fun getString(key: AppString): String = context.getString(
        when (key) {
            AppString.ErrorNetwork -> R.string.app_error_network
            AppString.ErrorRiskControl -> R.string.app_error_risk_control
            AppString.ErrorUnauthorized -> R.string.app_error_unauthorized
            AppString.ErrorParse -> R.string.app_error_parse
            AppString.ErrorBizDefault -> R.string.app_error_biz_default
        }
    )
}
