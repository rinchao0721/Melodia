package com.lin0721.linmusic.core.network

import android.content.Context
import androidx.annotation.StringRes

// 供无法直接持有 Context 的 ViewModel 解析字符串资源；只持有 applicationContext，不存在内存泄漏风险
open class ResourceProvider(private val context: Context? = null) {
    open fun getString(@StringRes resId: Int): String = context?.getString(resId).orEmpty()
}
