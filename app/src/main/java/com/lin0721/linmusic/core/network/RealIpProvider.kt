package com.lin0721.linmusic.core.network

// 国内 IP 伪装选取逻辑：HeaderInterceptor（所有接口）与 WebViewLoginScreen（网页登录）共用同一实例，
// 保证未配置自定义 IP 时，同一次登录会话中登录请求与后续鉴权请求伪装的是同一个随机 IP
class RealIpProvider {

    private val ipv4Pattern = Regex(
        "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    )

    // 单次进程生命周期内锁定的随机国内 IP
    val sessionIpAddress: String by lazy {
        val ipPrefixes = listOf(
            "116.25", "218.17", "113.88", "121.14", "119.137",
            "58.60", "124.127", "223.73", "116.228", "180.168"
        )
        val prefix = ipPrefixes.random()
        "$prefix.${(1..254).random()}.${(1..254).random()}"
    }

    private fun isValidIpv4(ip: String): Boolean = ipv4Pattern.matches(ip.trim())

    // 未开启伪装返回 null；开启时优先用用户自定义 IP，否则用本次会话随机 IP
    fun resolveIp(useRealIp: Boolean, customIp: String): String? {
        if (!useRealIp) return null
        return if (customIp.isNotBlank() && isValidIpv4(customIp)) customIp.trim() else sessionIpAddress
    }
}
