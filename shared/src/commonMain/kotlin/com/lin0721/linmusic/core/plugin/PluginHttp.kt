package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.source.SourceHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PluginHttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?
)

data class PluginHttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String
)

// 插件网络出口：不自动跟随重定向，由调用方逐跳核对域名白名单
fun interface PluginHttpClient {
    suspend fun execute(request: PluginHttpRequest): PluginHttpResponse
}

class OkHttpPluginHttpClient(
    private val client: OkHttpClient = SourceHttpClient.client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) : PluginHttpClient {

    override suspend fun execute(request: PluginHttpRequest): PluginHttpResponse = withContext(Dispatchers.IO) {
        val contentType = request.headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
        val body = request.body?.toRequestBody((contentType ?: "text/plain; charset=utf-8").toMediaTypeOrNull())
        val okRequest = Request.Builder()
            .url(request.url)
            .headers(request.headers.toHeaders())
            .method(request.method, body)
            .build()
        client.newCall(okRequest).execute().use { response ->
            val responseBody = response.body
            val declared = responseBody?.contentLength() ?: -1L
            if (declared > MAX_BODY_BYTES) throw IOException("响应超过 ${MAX_BODY_BYTES / 1024 / 1024}MB")
            val peeked = response.peekBody(MAX_BODY_BYTES + 1)
            if (peeked.contentLength() > MAX_BODY_BYTES) throw IOException("响应超过 ${MAX_BODY_BYTES / 1024 / 1024}MB")
            PluginHttpResponse(
                status = response.code,
                headers = response.headers.toMultimap().mapValues { (_, values) -> values.joinToString(", ") },
                body = peeked.string()
            )
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 4L * 1024 * 1024
    }
}
