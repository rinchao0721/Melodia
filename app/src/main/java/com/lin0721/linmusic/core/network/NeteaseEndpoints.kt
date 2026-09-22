package com.lin0721.linmusic.core.network

// 网易云的域名与主机常量。此前散落在 NetworkModule、HeaderInterceptor 与
// WebViewLoginScreen 三处共 6 个字面量，改域名或加白名单时容易漏改。
//
// 注意：这里不使用 buildConfigField —— 网易云只有单一线上环境，没有
// staging/sandbox 可切换，加构建变体只会得到一个永远单值的间接层。
object NeteaseEndpoints {

    // 根域名，weapi 路由与网页登录均走此域
    const val WEB_HOST = "music.163.com"

    const val WEB_BASE_URL = "https://$WEB_HOST"

    // eapi 路由改写到的移动端主机，用于绕开 PC 端风控
    const val EAPI_HOST = "interface.music.163.com"

    // xeapi 路由改写到的第三个独立主机（发表/回复/删除评论等写操作专用）
    const val XEAPI_HOST = "interface3.music.163.com"

    // 判定是否为网易域名，决定是否注入 X-Real-IP 等地域伪装头
    const val DOMAIN_SUFFIX = "163.com"

    // 网页登录入口
    const val LOGIN_URL = "$WEB_BASE_URL/m/login"
}
