package com.lin0721.linmusic.core.network

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

private const val TAG = "HeaderInterceptor"

// 网易云反风控拦截器。UA、Referer、海外 IP 伪装及用户 Cookie。
class HeaderInterceptor(
    private val userPreferences: UserPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val realIpProvider: RealIpProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url
        val urlString = url.toString()
        val newRequestBuilder = originalRequest.newBuilder()

        // DataStore 读取异常时降级为安全默认值，避免单次读取失败拖垮所有网络请求
        val storedCookies = try {
            runBlocking { userPreferences.cookies.first() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取存储的 Cookie 失败，本次请求按未登录处理", e)
            null
        }
        val useRealIp = try {
            runBlocking { settingsPreferences.useRealIp.first() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取真实IP开关失败，本次请求跳过IP伪装", e)
            false
        }
        val realIpValue = try {
            runBlocking { settingsPreferences.realIpValue.first() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取真实IP值失败", e)
            ""
        }

        // 域名白名单控制；二维码接口保持纯净直连，不注入伪装 IP 避免鉴权风控
        val isQrLogin = urlString.contains("/login/qrcode/")
        val ipAddress = realIpProvider.resolveIp(useRealIp, realIpValue)
        if (!isQrLogin && ipAddress != null && url.host.contains(NeteaseEndpoints.DOMAIN_SUFFIX)) {
            newRequestBuilder.header("X-Real-IP", ipAddress)
            newRequestBuilder.header("X-Forwarded-For", ipAddress)
        }

        if (urlString.contains("/eapi/")) {
            val newUrl = url.newBuilder()
                .host(NeteaseEndpoints.EAPI_HOST)
                .build()
            newRequestBuilder.url(newUrl)

            val androidUA = "NeteaseMusic/9.0.90 (Linux; U; Android ${android.os.Build.VERSION.RELEASE}; zh_CN; ${android.os.Build.MODEL})"
            newRequestBuilder.header("User-Agent", androidUA)
            newRequestBuilder.removeHeader("Referer")
            
            val requestCookies = originalRequest.headers("Cookie").toMutableList()
            if (storedCookies != null) requestCookies.add(storedCookies)
            
            val cookiesStr = requestCookies.joinToString("; ")
            val filteredCookies = cookiesStr.split("; ").filterNot { it.trim().startsWith("os=") }.joinToString("; ")
            // 打卡上报（weblog）专用伪装：参考 scrobble.js 强制 os=osx，否则最近播放/听歌排行聚合层疑似只认桌面端来源
            val osCookie = if (urlString.contains("/eapi/feedback/weblog")) {
                "os=osx"
            } else {
                "os=android; appver=9.0.90; osver=${android.os.Build.VERSION.RELEASE}"
            }
            newRequestBuilder.header("Cookie", if (filteredCookies.isEmpty()) osCookie else "$filteredCookies; $osCookie")
        } else {
            newRequestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            newRequestBuilder.header("Referer", NeteaseEndpoints.WEB_BASE_URL)
            
            val requestCookies = originalRequest.headers("Cookie").toMutableList()
            // 二维码接口隔离历史 Cookie，避免失效凭据污染新会话
            if (!isQrLogin && storedCookies != null) requestCookies.add(storedCookies)
            
            val cookiesStr = requestCookies.joinToString("; ")
            if (!cookiesStr.contains("os=")) {
                val osCookie = "os=pc; osver=Microsoft-Windows-10-Professional-build-10512-64bit; appver=3.0.1.201552"
                newRequestBuilder.header("Cookie", if (cookiesStr.isEmpty()) osCookie else "$cookiesStr; $osCookie")
            } else {
                newRequestBuilder.header("Cookie", cookiesStr)
            }
        }
        
        return chain.proceed(newRequestBuilder.build())
    }
}
