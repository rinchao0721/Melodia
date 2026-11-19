package com.lin0721.linmusic.core.player

import androidx.media3.common.PlaybackException
import java.net.URI

private val NETEASE_HOST_SUFFIXES = listOf("126.net", "163.com", "netease.com")
private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]")

private val CONNECTION_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
)

// 插件/UNM 等第三方直链连不上时返回其域名，用于提示是音源服务器的问题；网易自家链接与本地代理返回 null
fun thirdPartySourceUnreachableHost(errorCode: Int, sourceUri: String?): String? {
    if (errorCode !in CONNECTION_ERROR_CODES) return null
    if (sourceUri.isNullOrBlank()) return null
    val uri = runCatching { URI(sourceUri) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    if (host in LOOPBACK_HOSTS) return null
    if (NETEASE_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) return null
    return host
}

fun formatSourceUnreachableMessage(host: String): String = "音源服务器无响应（$host）"
