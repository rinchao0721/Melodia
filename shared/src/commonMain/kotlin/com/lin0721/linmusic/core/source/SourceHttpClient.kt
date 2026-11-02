package com.lin0721.linmusic.core.source

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// 第三方音源 Provider 共用的轻量 HTTP 客户端，不挂载网易云加密拦截器
object SourceHttpClient {

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // UNM 服务需要上游实时解析，耗时明显长于普通接口，超时不能比官方 API（30s）更短
    val unmClient: OkHttpClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val noRedirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .build()

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        httpClient: OkHttpClient = client,
        maxBodyBytes: Long? = null
    ): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).get()
            headers.forEach { (k, v) -> builder.addHeader(k, v) }
            httpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SourceException("HTTP ${response.code}: $url")
                }
                readBody(response, url, maxBodyBytes)
            }
        }

    // 限制了 maxBodyBytes 时只缓冲 maxBodyBytes+1 字节，超限直接报错，避免大文件撑爆堆
    private fun readBody(response: Response, url: String, maxBodyBytes: Long?): String {
        if (maxBodyBytes == null) {
            return response.body?.string() ?: throw SourceException("空响应体: $url")
        }
        val declared = response.body?.contentLength() ?: -1L
        if (declared > maxBodyBytes) {
            throw SourceException("响应体过大(${declared}B): $url")
        }
        val peeked = response.peekBody(maxBodyBytes + 1)
        if (peeked.contentLength() > maxBodyBytes) {
            throw SourceException("响应体超过 ${maxBodyBytes}B: $url")
        }
        return peeked.string()
    }

    suspend fun getOrNull(url: String, headers: Map<String, String> = emptyMap()): String? =
        try {
            get(url, headers)
        } catch (e: Exception) {
            null
        }

    // 获取 URL 响应，若遇到 30x 重定向则直接返回 Location 目标直链，若 200 则返回 body 字符串
    suspend fun fetchUrlOrRedirect(url: String, headers: Map<String, String> = emptyMap()): String? =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).get()
            headers.forEach { (k, v) -> builder.addHeader(k, v) }
            try {
                noRedirectClient.newCall(builder.build()).execute().use { response ->
                    if (response.code in 300..399) {
                        val location = response.header("Location")?.trim()
                        if (!location.isNullOrBlank()) {
                            return@withContext location
                        }
                    }
                    if (response.isSuccessful) {
                        return@withContext response.body?.string()?.trim()
                    }
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        maxBodyBytes: Long? = null
    ): String =
        withContext(Dispatchers.IO) {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = body.toRequestBody(mediaType)
            val builder = Request.Builder().url(url).post(requestBody)
            headers.forEach { (k, v) -> builder.addHeader(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SourceException("HTTP ${response.code}: $url")
                }
                readBody(response, url, maxBodyBytes)
            }
        }
}

class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)
