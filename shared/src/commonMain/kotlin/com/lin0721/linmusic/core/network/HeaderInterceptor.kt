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

    companion object {
        // 内部控制头：标记请求不读取也不附带本地已保存的账户 Cookie（供二维码扫码等无状态登录接口使用）
        const val HEADER_NO_SESSION_COOKIE = "X-No-Session-Cookie"
        // 内部控制头：临时覆盖本次请求使用的账户 Cookie（供添加/验证新账户时避免污染当前活跃会话）
        const val HEADER_OVERRIDE_SESSION_COOKIE = "X-Override-Session-Cookie"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url
        val urlString = url.toString()
        val noSessionCookie = originalRequest.header(HEADER_NO_SESSION_COOKIE)
            ?.equals("true", ignoreCase = true) == true
        val overrideCookie = originalRequest.header(HEADER_OVERRIDE_SESSION_COOKIE)
            ?.takeIf { it.isNotBlank() }

        // 先剥离内部控制头，避免泄露给远端或加密层
        val newRequestBuilder = originalRequest.newBuilder()
            .removeHeader(HEADER_NO_SESSION_COOKIE)
            .removeHeader(HEADER_OVERRIDE_SESSION_COOKIE)

        // DataStore 读取异常时降级为安全默认值，避免单次读取失败拖垮所有网络请求
        val sessionCookies = when {
            noSessionCookie -> null
            overrideCookie != null -> overrideCookie
            else -> try {
                runBlocking { userPreferences.cookies.first() }
            } catch (e: Exception) {
                AppLogger.e(TAG, "读取存储的 Cookie 失败，本次请求按未登录处理", e)
                null
            }
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

        // 域名白名单控制；二维码或无会话登录接口保持纯净直连，不注入伪装 IP 避免鉴权风控
        val isCleanLoginRequest = noSessionCookie || urlString.contains("/login/qrcode/")
        val ipAddress = realIpProvider.resolveIp(useRealIp, realIpValue)
        if (!isCleanLoginRequest && ipAddress != null && url.host.contains(NeteaseEndpoints.DOMAIN_SUFFIX)) {
            newRequestBuilder.header("X-Real-IP", ipAddress)
            newRequestBuilder.header("X-Forwarded-For", ipAddress)
        }

        if (urlString.contains("/eapi/")) {
            val newUrl = url.newBuilder()
                .host(NeteaseEndpoints.EAPI_HOST)
                .build()
            newRequestBuilder.url(newUrl)

            val androidUA = "NeteaseMusic/9.0.90 (Linux; U; Android ${DeviceInfo.osRelease}; zh_CN; ${DeviceInfo.model})"
            newRequestBuilder.header("User-Agent", androidUA)
            newRequestBuilder.removeHeader("Referer")

            val requestCookies = originalRequest.headers("Cookie").toMutableList()
            if (sessionCookies != null) requestCookies.add(sessionCookies)

            val cookiesStr = requestCookies.joinToString("; ")
            val filteredCookies = cookiesStr.split("; ").filterNot { it.trim().startsWith("os=") }.joinToString("; ")
            // 打卡上报（weblog）专用伪装：参考 scrobble.js 强制 os=osx，否则最近播放/听歌排行聚合层疑似只认桌面端来源
            val osCookie = if (urlString.contains("/eapi/feedback/weblog")) {
                "os=osx"
            } else {
                "os=android; appver=9.0.90; osver=${DeviceInfo.osRelease}"
            }
            newRequestBuilder.header("Cookie", if (filteredCookies.isEmpty()) osCookie else "$filteredCookies; $osCookie")
        } else if (urlString.contains("/xeapi/")) {
            val newUrl = url.newBuilder()
                .host(NeteaseEndpoints.XEAPI_HOST)
                .build()
            newRequestBuilder.url(newUrl)

            val deviceId = com.lin0721.linmusic.core.network.NeteaseDeviceId.current()
            val osVer = DeviceInfo.osRelease
            val appVer = "9.5.61"
            val buildVer = System.currentTimeMillis().toString().substring(0, 10)

            newRequestBuilder.header(
                "User-Agent",
                "NeteaseMusic/9.5.61.260802021928(9005061);Dalvik/2.1.0 (Linux; U; Android $osVer; ${DeviceInfo.model})"
            )
            newRequestBuilder.header("X-Client-Enc-State", "ENCRYPTED")
            newRequestBuilder.header("x-aeapi", "true")
            newRequestBuilder.header("content-type", "application/x-www-form-urlencoded;charset=utf-8")
            newRequestBuilder.header("x-deviceid", deviceId)
            newRequestBuilder.header("x-os", "android")
            newRequestBuilder.header("x-osver", osVer)
            newRequestBuilder.header("x-appver", appVer)
            newRequestBuilder.header("x-sdeviceid", deviceId)
            newRequestBuilder.header("x-buildver", buildVer)
            newRequestBuilder.removeHeader("Referer")

            val musicU = sessionCookies?.let { Regex("MUSIC_U=([^;]+)").find(it)?.groupValues?.get(1) }
            if (!musicU.isNullOrEmpty()) {
                newRequestBuilder.header("x-music-u", musicU)
            }

            val cookieParts = mutableListOf(
                "os=android",
                "osver=$osVer",
                "appver=$appVer",
                "deviceId=$deviceId",
                "sDeviceId=$deviceId",
                "buildver=$buildVer"
            )
            if (sessionCookies != null) cookieParts.add(sessionCookies)
            newRequestBuilder.header("Cookie", cookieParts.joinToString("; "))
        } else {
            newRequestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            newRequestBuilder.header("Referer", NeteaseEndpoints.WEB_BASE_URL)

            val requestCookies = originalRequest.headers("Cookie").toMutableList()
            // 二维码与无会话登录接口隔离历史 Cookie，避免失效或旧账户凭据污染新会话
            if (!isCleanLoginRequest && sessionCookies != null) requestCookies.add(sessionCookies)

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
