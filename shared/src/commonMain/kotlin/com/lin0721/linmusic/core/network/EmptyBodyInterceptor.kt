package com.lin0721.linmusic.core.network

import com.lin0721.linmusic.core.log.AppLogger
import okhttp3.Interceptor
import okhttp3.Response

private const val TAG = "EmptyBodyInterceptor"

// 拦截器：检测空响应体。避免 kotlinx.serialization 抛出 EOF 异常。
class EmptyBodyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val bodyString = response.peekBody(Long.MAX_VALUE).string()
        if (bodyString.isBlank()) {
            AppLogger.w(TAG, "空响应体，疑似风控/未登录: ${request.url} code=${response.code}")
            throw ApiException("API body is empty, possibly auth failed")
        }
        
        return response
    }
}
