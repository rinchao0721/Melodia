package com.lin0721.linmusic.feature.cloud.upload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

private const val LBS_TIMEOUT_SECONDS = 10L
private const val UPLOAD_TIMEOUT_SECONDS = 300L
private const val UPLOAD_CHUNK_SIZE = 8192L

@Serializable
private data class LbsResponse(val upload: List<String> = emptyList())

// 云盘上传的跨域裸传输客户端：wanproxy.127.net 查地址 + 动态分配的 NOS 存储服务器传字节，
// 两个都是网易之外的第三方主机，独立 OkHttpClient 不挂 CryptoInterceptor/HeaderInterceptor 任何一个，
// 避免登录 Cookie 随全局拦截器泄露给第三方域名
class NosUploadClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(LBS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // Stage 3a：查上传服务器地址列表
    suspend fun fetchUploadHosts(bucket: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://wanproxy.127.net/lbs?version=1.0&bucketname=$bucket"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful || text.isBlank()) return@use emptyList()
            runCatching { json.decodeFromString<LbsResponse>(text).upload }.getOrDefault(emptyList())
        }
    }

    // Stage 3b：真正的文件字节上传。objectKey 本身含斜杠，需转义成 %2F 保持为单一路径段
    suspend fun uploadBytes(
        host: String,
        bucket: String,
        objectKey: String,
        token: String,
        // 传 null 则不发 Content-MD5，上传图片只带 x-nos-token 与 Content-Type，
        // 且该头按标准要求的是 Base64 摘要而非十六进制串，图片场景带上会被服务端拒掉
        md5: String?,
        mimeType: String,
        contentLength: Long,
        openStream: () -> InputStream,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val encodedObjectKey = objectKey.replace("/", "%2F")
        val url = "$host/$bucket/$encodedObjectKey?offset=0&complete=true&version=1.0"

        val body = object : RequestBody() {
            override fun contentType() = mimeType.toMediaType()
            override fun contentLength() = contentLength
            override fun writeTo(sink: BufferedSink) {
                openStream().use { input ->
                    val source = input.source()
                    val buffer = Buffer()
                    var totalWritten = 0L
                    while (true) {
                        val read = source.read(buffer, UPLOAD_CHUNK_SIZE)
                        if (read == -1L) break
                        sink.write(buffer, read)
                        totalWritten += read
                        onProgress((totalWritten.toFloat() / contentLength).coerceIn(0f, 1f))
                    }
                }
            }
        }

        // Content-Type/Content-Length 由 RequestBody 自身提供，不再手动设置对应 header
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("x-nos-token", token)
            .apply { if (!md5.isNullOrBlank()) header("Content-MD5", md5) }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("NOS 上传失败: HTTP ${response.code}")
            }
        }
    }
}
