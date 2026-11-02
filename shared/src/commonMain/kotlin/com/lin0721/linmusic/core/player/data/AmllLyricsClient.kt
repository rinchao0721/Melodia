package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.player.domain.TtmlLyricParser
import com.lin0721.linmusic.core.log.AppLogger
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owns a fresh client: never inherit authenticated NetEase interceptors or cookies. */
class AmllLyricsClient(private val cache: LyricsCache? = null) {
    internal data class FetchResult(val xml: String?, val allNotFound: Boolean)
    private data class DownloadResult(val body: String?, val notFound: Boolean)

    internal val client = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .callTimeout(6, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(songId: Long): String? {
        if (songId <= 0) return null
        return fetchFor(songId, listOf(
            "https://amlldb.bikonoo.com/ncm-lyrics/$songId.ttml",
            "https://amll-ttml-db.stevexmh.net/ncm/$songId",
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/ncm-lyrics/$songId.ttml"
        ))
    }

    // 缓存命中同样有磁盘读取、XML 校验；不可继承界面调用方的主线程。
    internal suspend fun fetchFor(songId: Long, urls: List<String>): String? = withContext(Dispatchers.IO) {
        cached(songId)?.let { return@withContext it }
        if (cache?.isNegative(songId) == true) return@withContext null
        val result = fetchFromDetailed(urls)
        AppLogger.d("AmllLyricsClient", "查询完成 songId=$songId found=${result.xml != null} allNotFound=${result.allNotFound}")
        result.xml?.let { cache?.putRaw(songId, it) }
        if (result.allNotFound) cache?.putNegative(songId)
        result.xml
    }

    internal fun cached(songId: Long): String? {
        cache?.getRaw(songId)?.let { value ->
            if (TtmlLyricParser.parse(value).isNotEmpty()) return value
            cache.removeRaw(songId)
        }
        return null
    }

    internal suspend fun fetchFrom(urls: List<String>): String? = fetchFromDetailed(urls).xml

    internal suspend fun fetchFromDetailed(urls: List<String>): FetchResult = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope FetchResult(null, false)
        val results = Channel<DownloadResult>(urls.size)
        val jobs = urls.mapIndexed { index, url ->
            launch {
                // 镜像交错并发，单个慢握手不会阻塞其他镜像。
                delay(index * 250L)
                val outcome = try {
                    downloadResult(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) { DownloadResult(null, false) }
                val valid = outcome.body?.takeIf { TtmlLyricParser.parse(it).isNotEmpty() }
                results.send(outcome.copy(body = valid))
            }
        }
        try {
            var missing = 0
            repeat(urls.size) {
                val result = results.receive()
                if (result.notFound) missing++
                result.body?.let { return@coroutineScope FetchResult(it, false) }
            }
            FetchResult(null, missing == urls.size)
        } finally {
            jobs.forEach { it.cancel() }
            results.cancel()
        }
    }

    internal suspend fun download(url: String): String? = downloadResult(url).body

    private suspend fun downloadResult(url: String): DownloadResult = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        val started = System.nanoTime()
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                AppLogger.d("AmllLyricsClient", "镜像失败 host=${call.request().url.host} elapsedMs=${(System.nanoTime() - started) / 1_000_000} reason=${e.javaClass.simpleName}")
                continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val value = response.use {
                        AppLogger.d("AmllLyricsClient", "镜像响应 host=${it.request.url.host} status=${it.code} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
                        val body = it.body
                        if (!it.isSuccessful || body == null) DownloadResult(null, it.code == 404)
                        else {
                            val source = body.source()
                            val content = if (source.request(TtmlLyricParser.MAX_LENGTH.toLong() + 1)) null
                            else source.readUtf8().takeIf(String::isNotBlank)
                            DownloadResult(content, false)
                        }
                    }
                    continuation.resume(value)
                } catch (e: IOException) { continuation.resumeWithException(e) }
            }
        })
    }
}
