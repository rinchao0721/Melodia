package com.lin0721.linmusic.core.kugou

// 酷狗客户端模式（默认概念版 LITE，支持运行时切换与单接口覆盖）
enum class KugouClientMode(
    val appid: Int,
    val clientver: Int,
    val androidSignSalt: String,
    val signKeySalt: String,
    val cloudAppkey: String,
    val loginTokenKey: String,
    val loginTokenIv: String,
    val publicKeyPem: String
) {
    LITE(
        appid = 3116,
        clientver = 11440,
        androidSignSalt = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA",
        signKeySalt = "185672dd44712f60bb1736df5a377e82",
        cloudAppkey = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA",
        loginTokenKey = "c24f74ca2820225badc01946dba4fdf7",
        loginTokenIv = "adc01946dba4fdf7",
        publicKeyPem = KugouConfig.PUBLIC_LITE_RSA_KEY
    ),
    STANDARD(
        appid = 1005,
        clientver = 20489,
        androidSignSalt = "OIlwieks28dk2k092lksi2UIkp",
        signKeySalt = "57ae12eb6890223e355ccfcb74edf70d",
        cloudAppkey = "OIlwieks28dk2k092lksi2UIkp",
        loginTokenKey = "90b8382a1bb4ccdcf063102053fd75b8",
        loginTokenIv = "f063102053fd75b8",
        publicKeyPem = KugouConfig.PUBLIC_STANDARD_RSA_KEY
    );

    val isLite: Boolean
        get() = this == LITE
}

// 酷狗独立协议配置（参数严格对照 KuGouMusicApi/util/config.json、crypto.js、helper.js、request.js）
class KugouConfig(
    initialMode: KugouClientMode = KugouClientMode.LITE,
    val gatewayBaseUrl: String = DEFAULT_GATEWAY_BASE_URL,
    // 仅供测试将多域名请求重定向到 MockWebServer，默认原样返回
    internal val baseUrlRewriter: (String) -> String = { it },
    // 仅供端到端加密单测注入可持私钥解密的 RSA 公钥，生产默认 null（使用 mode 内置公钥）
    internal val overridePublicKeyPem: String? = null
) {
    @Volatile
    var mode: KugouClientMode = initialMode

    fun effectiveAppId(overrideAppId: Int? = null): Int = overrideAppId ?: mode.appid

    fun effectiveClientVer(overrideClientVer: Int? = null): Int = overrideClientVer ?: mode.clientver

    fun effectivePublicKeyPem(): String = overridePublicKeyPem ?: mode.publicKeyPem

    fun resolveBaseUrl(rawBaseUrl: String?): String {
        val target = rawBaseUrl?.takeIf { it.isNotBlank() } ?: gatewayBaseUrl
        return baseUrlRewriter(target).trimEnd('/')
    }

    companion object {
        const val SRC_APPID = 2919
        const val APIVER = 20

        const val DEFAULT_GATEWAY_BASE_URL = "https://gateway.kugou.com"
        const val DEFAULT_USER_AGENT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"

        const val WEB_SIGN_SALT = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
        const val REGISTER_SIGN_SALT = "1014"
        const val SIGN_PARAMS_SALT = "R6snCXJgbCaj9WFRJKefTMIFp0ey6Gza"
        const val SIGN_CLOUD_KEY_SALT = "ebd1ac3134c880bda6a2194537843caa0162e2e7"

        const val LITE_T1_KEY = "5e4ef500e9597fe004bd09a46d8add98"
        const val LITE_T1_IV = "04bd09a46d8add98"
        const val LITE_T2_KEY = "fd14b35e3f81af3817a20ae7adae7020"
        const val LITE_T2_IV = "17a20ae7adae7020"

        const val PUBLIC_STANDARD_RSA_KEY =
            "-----BEGIN PUBLIC KEY-----\n" +
                "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/g\n" +
                "bjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+\n" +
                "UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE\n" +
                "5E221wf/4WLFxwAtRQIDAQAB\n" +
                "-----END PUBLIC KEY-----"

        const val PUBLIC_LITE_RSA_KEY =
            "-----BEGIN PUBLIC KEY-----\n" +
                "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO0\n" +
                "1rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXL\n" +
                "gFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08\n" +
                "/q6ifSioBszvb3QiwIDAQAB\n" +
                "-----END PUBLIC KEY-----"
    }
}
