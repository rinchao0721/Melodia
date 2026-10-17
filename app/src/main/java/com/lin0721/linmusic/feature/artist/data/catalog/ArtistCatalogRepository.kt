package com.lin0721.linmusic.feature.artist.data.catalog

import android.content.Context
import com.lin0721.linmusic.BuildConfig
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogArtist
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogCredit
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogPreference
import com.lin0721.linmusic.feature.artist.domain.catalog.ArtistCatalogReleaseGroup
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ArtistCatalogException(
    message: String,
    val retryAtMs: Long? = null,
    val httpStatus: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause)

/** Optional metadata source. No NetEase cookie, account identifier or write API is used. */
class ArtistCatalogRepository internal constructor(
    private val cacheDirectory: File,
    private val preferences: ArtistCatalogPreferenceStorage,
    private val transport: ArtistCatalogTransport,
    private val gate: ArtistCatalogRequestGate = SharedMusicBrainzGate,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val baseUrl: HttpUrl = "https://musicbrainz.org/ws/2/".toHttpUrl()
) {
    constructor(context: Context) : this(
        File(context.filesDir, "artist-album-catalog"),
        AndroidArtistCatalogPreferences(context.applicationContext),
        OkHttpArtistCatalogTransport()
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheMutexes = ConcurrentHashMap<String, Mutex>()

    fun selection(artistId: Long): Flow<ArtistCatalogPreference> = preferences.observe(validArtistId(artistId))

    val defaultEnabled: Flow<Boolean> get() = preferences.defaultEnabled

    suspend fun setDefaultEnabled(enabled: Boolean) = preferences.setDefaultEnabled(enabled)

    suspend fun clearEnabledOverride(artistId: Long) = preferences.clearEnabledOverride(validArtistId(artistId))

    suspend fun setEnabled(artistId: Long, enabled: Boolean) = preferences.setEnabled(validArtistId(artistId), enabled)

    suspend fun bindArtist(artistId: Long, mbid: String, name: String? = null) =
        preferences.bind(validArtistId(artistId), validMbid(mbid), name?.trim()?.takeIf { it.isNotBlank() }?.take(500))

    suspend fun clearBinding(artistId: Long) = preferences.clearBinding(validArtistId(artistId))

    suspend fun findArtists(name: String): Result<List<ArtistCatalogArtist>> {
        val queryName = Normalizer.normalize(name, Normalizer.Form.NFKC).trim().replace(Regex("\\s+"), " ")
        if (queryName.isBlank() || queryName.length > 200) return Result.failure(ArtistCatalogException("请输入有效歌手名称（最多 200 字）"))
        val key = "search-${sha256(queryName.lowercase(Locale.ROOT))}"
        return withContext(ioDispatcher) { cacheMutexes.getOrPut(key) { Mutex() }.withLock {
            val previous = readCache(key, SEARCH_KIND)
            previous?.takeIf(::fresh)?.artists?.let { return@withLock Result.success(it) }
            cooldown(previous)?.let { return@withLock Result.failure(it) }
            try {
                val phrase = escapeLucene(queryName)
                val artists = fetchAllPages(
                    requestUrl = { offset -> baseUrl.newBuilder().addPathSegment("artist")
                        .addQueryParameter("query", "artist:\"$phrase\" OR alias:\"$phrase\"")
                        .addQueryParameter("limit", PAGE_SIZE.toString()).addQueryParameter("offset", offset.toString())
                        .addQueryParameter("fmt", "json").build() },
                    parse = ArtistCatalogApiCodec::artistPage,
                    id = ArtistCatalogArtist::id
                )
                writeCache(key, ArtistCatalogCacheRecord(kind = SEARCH_KIND, fetchedAtMs = nowMs(), artists = artists))
                Result.success(artists)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { recordFailure(key, SEARCH_KIND, previous, error) }
        } }
    }

    /** Returns only a complete successful browse. A failed refresh leaves its previous cache intact. */
    suspend fun albums(mbid: String, force: Boolean = false): Result<List<ArtistCatalogReleaseGroup>> {
        val artistMbid = try { validMbid(mbid) } catch (_: IllegalArgumentException) {
            return Result.failure(ArtistCatalogException("资料库歌手标识无效，请重新选择歌手"))
        }
        val key = "groups-$artistMbid"
        return withContext(ioDispatcher) { cacheMutexes.getOrPut(key) { Mutex() }.withLock {
            val previous = readCache(key, GROUP_KIND)
            previous?.takeIf { !force && fresh(it) }?.releaseGroups?.let { return@withLock Result.success(it) }
            cooldown(previous)?.let { return@withLock Result.failure(it) }
            try {
                // Browse every type so singles, EPs and live collections can be shown
                // as explicit exclusions instead of being mistaken for missing data.
                val albums = fetchAllPages(
                    requestUrl = { offset -> baseUrl.newBuilder().addPathSegment("release-group")
                        .addQueryParameter("artist", artistMbid)
                        .addQueryParameter("release-group-status", "website-default")
                        .addQueryParameter("inc", "artist-credits")
                        .addQueryParameter("limit", PAGE_SIZE.toString()).addQueryParameter("offset", offset.toString())
                        .addQueryParameter("fmt", "json").build() },
                    parse = ArtistCatalogApiCodec::releaseGroupPage,
                    id = ArtistCatalogReleaseGroup::id
                )
                writeCache(key, ArtistCatalogCacheRecord(kind = GROUP_KIND, fetchedAtMs = nowMs(), releaseGroups = albums))
                Result.success(albums)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { recordFailure(key, GROUP_KIND, previous, error) }
        } }
    }

    /** Null means never successfully cached; [] means a successfully fetched empty catalog. */
    suspend fun cachedAlbums(mbid: String): List<ArtistCatalogReleaseGroup>? = withContext(ioDispatcher) {
        val artistMbid = try { validMbid(mbid) } catch (_: IllegalArgumentException) { return@withContext null }
        val key = "groups-$artistMbid"
        cacheMutexes.getOrPut(key) { Mutex() }.withLock { readCache(key, GROUP_KIND)?.releaseGroups }
    }

    private suspend fun <T> fetchAllPages(
        requestUrl: (Int) -> HttpUrl,
        parse: (String) -> ArtistCatalogPage<T>,
        id: (T) -> String
    ): List<T> {
        val result = LinkedHashMap<String, T>()
        var offset = 0
        var total: Int? = null
        repeat(MAX_PAGES) {
            val response = gate.request { transport.get(requestUrl(offset)) }
            if (response.status !in 200..299) throw httpError(response)
            val page = parse(response.body)
            if (page.offset != offset) throw ArtistCatalogException("资料库分页位置异常，未把部分数据当作完整目录")
            if (page.total > MAX_RECORDS) throw ArtistCatalogException("资料库记录超过 $MAX_RECORDS 条，未截断为完整目录，请直接查看 MusicBrainz")
            if (total != null && page.total != total) throw ArtistCatalogException("资料库目录在读取期间发生变化，请重新加载")
            total = page.total
            if (page.rows.isEmpty() && offset < page.total) throw ArtistCatalogException("资料库返回了不完整分页，请稍后重新加载")
            page.rows.forEach { result[id(it)] = it }
            offset += page.rows.size
            if (offset >= page.total) {
                if (offset != page.total || result.size != page.total) throw ArtistCatalogException("资料库分页包含重复或不完整记录，请重新加载")
                return result.values.toList()
            }
        }
        throw ArtistCatalogException("资料库分页过多，未把部分数据当作完整目录，请直接查看 MusicBrainz")
    }

    private fun fresh(record: ArtistCatalogCacheRecord): Boolean = record.fetchedAtMs > 0L &&
        nowMs() >= record.fetchedAtMs && nowMs() - record.fetchedAtMs < SUCCESS_TTL_MS

    private fun cooldown(record: ArtistCatalogCacheRecord?): ArtistCatalogException? = record?.takeIf {
        it.retryAtMs > nowMs() && it.lastError != null
    }?.let { ArtistCatalogException(it.lastError!!, it.retryAtMs, it.lastStatus) }

    private fun <T> recordFailure(key: String, kind: String, previous: ArtistCatalogCacheRecord?, error: Exception): Result<T> {
        val reported = if (error is ArtistCatalogException) error else ArtistCatalogException(
            when (error) {
                is IOException -> "无法连接 MusicBrainz，已保留缓存；请检查网络后重试"
                else -> "资料库响应或缓存处理失败，已保留原有数据；请稍后重试"
            }, cause = error
        )
        val retryAt = reported.retryAtMs ?: nowMs() + FAILURE_COOLDOWN_MS
        val failed = (previous ?: ArtistCatalogCacheRecord(kind = kind)).copy(
            retryAtMs = retryAt, lastError = reported.message, lastStatus = reported.httpStatus)
        runCatching { writeCache(key, failed) }
        return Result.failure(ArtistCatalogException(reported.message.orEmpty(), retryAt, reported.httpStatus, reported))
    }

    private fun httpError(response: ArtistCatalogHttpResponse): ArtistCatalogException {
        val reason = when (response.status) {
            503 -> "MusicBrainz 暂时繁忙（503），请稍后重试"
            429 -> "MusicBrainz 请求受到限制（429），请稍后重试"
            400 -> "资料库不接受此查询（400），请重新选择歌手"
            404 -> "资料库未找到这位歌手（404），请重新选择歌手"
            401, 403 -> "资料库暂时拒绝访问（${response.status}），请稍后重试"
            else -> "资料库请求失败（${response.status}），请稍后重试"
        }
        val waitMs = ((response.retryAfterSeconds ?: 0L).coerceIn(0L, 300L) * 1_000L).coerceAtLeast(FAILURE_COOLDOWN_MS)
        return ArtistCatalogException(reason, nowMs() + waitMs, response.status)
    }

    private fun readCache(key: String, kind: String): ArtistCatalogCacheRecord? = runCatching {
        val file = File(cacheDirectory, "$key.json")
        if (!file.isFile || file.length() > MAX_CACHE_BYTES) return@runCatching null
        json.decodeFromString<ArtistCatalogCacheRecord>(file.readText(Charsets.UTF_8))
            .takeIf { it.schemaVersion == 1 && it.kind == kind }
    }.getOrNull()

    private fun writeCache(key: String, record: ArtistCatalogCacheRecord) = try {
        check(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) { "无法创建资料库缓存目录" }
        val bytes = json.encodeToString(record).toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_CACHE_BYTES) { "资料库缓存过大，未截断保存" }
        val temporary = File.createTempFile("$key-", ".tmp", cacheDirectory)
        try {
            temporary.outputStream().use { it.write(bytes) }
            val destination = File(cacheDirectory, "$key.json").toPath()
            try { Files.move(temporary.toPath(), destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), destination, StandardCopyOption.REPLACE_EXISTING) }
        } finally { temporary.delete() }
    } catch (error: Exception) {
        throw ArtistCatalogException("资料库缓存未能保存，原有缓存保留；请检查存储空间后重试", cause = error)
    }

    private fun validArtistId(id: Long): Long = id.also { require(it >= 0L) { "网易云歌手标识无效" } }
    private fun validMbid(id: String): String = id.trim().lowercase(Locale.ROOT).also {
        require(MBID_PATTERN.matches(it)) { "MusicBrainz 歌手标识无效" }
    }

    private fun escapeLucene(value: String): String = value.flatMap { character ->
        if (character in "+-&|!(){}[]^\"~*?:\\/") listOf('\\', character) else listOf(character)
    }.joinToString("")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        // Keep the metadata client identity current without coupling it to fork build options.
        val USER_AGENT = "Melodia/${BuildConfig.VERSION_NAME} (+https://github.com/rinchao0721/Melodia)"
        internal const val SUCCESS_TTL_MS = 7L * 24 * 60 * 60 * 1_000
        internal const val FAILURE_COOLDOWN_MS = 30_000L
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 100
        private const val MAX_RECORDS = 10_000
        private const val MAX_CACHE_BYTES = 16 * 1_024 * 1_024
        private const val SEARCH_KIND = "artist-search"
        private const val GROUP_KIND = "release-groups"
        private val MBID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

@Serializable
private data class ArtistCatalogCacheRecord(
    val schemaVersion: Int = 1,
    val kind: String,
    val fetchedAtMs: Long = 0L,
    val artists: List<ArtistCatalogArtist>? = null,
    val releaseGroups: List<ArtistCatalogReleaseGroup>? = null,
    val retryAtMs: Long = 0L,
    val lastError: String? = null,
    val lastStatus: Int? = null
)

internal data class ArtistCatalogHttpResponse(val status: Int, val body: String, val retryAfterSeconds: Long? = null)
internal fun interface ArtistCatalogTransport { suspend fun get(url: HttpUrl): ArtistCatalogHttpResponse }
internal interface ArtistCatalogRequestGate { suspend fun <T> request(action: suspend () -> T): T }

/** Shared across repository instances and request kinds; failed requests also consume their slot. */
internal class IntervalArtistCatalogGate(
    private val nowElapsedMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val intervalMs: Long = 1_100L
) : ArtistCatalogRequestGate {
    private val mutex = Mutex()
    private var nextStart: Long? = null
    override suspend fun <T> request(action: suspend () -> T): T = mutex.withLock {
        nextStart?.let { next -> (next - nowElapsedMs()).takeIf { it > 0L }?.let { wait(it) } }
        nextStart = nowElapsedMs() + intervalMs
        action()
    }
}

private object SharedMusicBrainzGate : ArtistCatalogRequestGate by IntervalArtistCatalogGate()

internal class OkHttpArtistCatalogTransport(
    private val client: OkHttpClient = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
) : ArtistCatalogTransport {
    override suspend fun get(url: HttpUrl): ArtistCatalogHttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", ArtistCatalogRepository.USER_AGENT)
            .header("Accept", "application/json").get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val stream = it.body?.byteStream() ?: throw IOException("资料库响应没有正文")
                        val bytes = stream.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8_192)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (output.size() + read > 8 * 1_024 * 1_024) throw IOException("资料库响应过大")
                                output.write(buffer, 0, read)
                            }
                            output.toByteArray()
                        }
                        val result = ArtistCatalogHttpResponse(it.code, bytes.toString(Charsets.UTF_8), it.header("Retry-After")?.toLongOrNull())
                        if (continuation.isActive) continuation.resume(result)
                    }
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
}

internal data class ArtistCatalogPage<T>(val total: Int, val offset: Int, val rows: List<T>)

/** Missing secondary-types remains null so it can never silently mean an ordinary studio album. */
internal object ArtistCatalogApiCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun artistPage(body: String): ArtistCatalogPage<ArtistCatalogArtist> {
        val root = objectOf(json.parseToJsonElement(body))
        return ArtistCatalogPage(integer(root, "count"), integer(root, "offset"), array(root, "artists").map { element ->
            val artist = objectOf(element)
            ArtistCatalogArtist(mbid(artist, "id"), required(artist, "name"), text(artist, "sort-name").orEmpty(),
                text(artist, "disambiguation").orEmpty(), aliases(artist), text(artist, "country"), text(artist, "type"),
                text(artist, "score")?.toIntOrNull())
        })
    }

    fun releaseGroupPage(body: String): ArtistCatalogPage<ArtistCatalogReleaseGroup> {
        val root = objectOf(json.parseToJsonElement(body))
        return ArtistCatalogPage(integer(root, "release-group-count"), integer(root, "release-group-offset"),
            array(root, "release-groups").map { element ->
                val group = objectOf(element)
                val secondary = when (val types = group["secondary-types"]) {
                    null, JsonNull -> null
                    is JsonArray -> types.map { primitive(it) }
                    else -> throw ArtistCatalogException("资料库次级分类字段无效，请稍后重试")
                }
                val credits = (group["artist-credit"] as? JsonArray).orEmpty().map { element ->
                    val credit = objectOf(element)
                    val artist = objectOf(credit["artist"] ?: throw ArtistCatalogException("资料库歌手署名字段不完整"))
                    ArtistCatalogCredit(mbid(artist, "id"), text(credit, "name") ?: required(artist, "name"), text(credit, "joinphrase").orEmpty())
                }
                ArtistCatalogReleaseGroup(mbid(group, "id"), required(group, "title"), text(group, "first-release-date"),
                    text(group, "primary-type"), secondary, aliases(group), credits)
            })
    }

    private fun aliases(root: JsonObject): List<String> = when (val aliases = root["aliases"]) {
        null, JsonNull -> emptyList()
        is JsonArray -> aliases.map { required(objectOf(it), "name") }.distinct()
        else -> throw ArtistCatalogException("资料库别名字段无效")
    }
    private fun objectOf(element: JsonElement): JsonObject = element as? JsonObject
        ?: throw ArtistCatalogException("资料库响应格式无效，请稍后重试")
    private fun array(root: JsonObject, key: String): JsonArray = root[key] as? JsonArray
        ?: throw ArtistCatalogException("资料库响应缺少 $key，未把错误当作空目录")
    private fun primitive(element: JsonElement): String = (element as? JsonPrimitive)?.contentOrNull
        ?: throw ArtistCatalogException("资料库字段格式无效")
    private fun text(root: JsonObject, key: String): String? = when (val value = root[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> value.contentOrNull
        else -> throw ArtistCatalogException("资料库 $key 字段格式无效")
    }
    private fun required(root: JsonObject, key: String): String = text(root, key)?.takeIf(String::isNotBlank)
        ?: throw ArtistCatalogException("资料库响应缺少 $key")
    private fun mbid(root: JsonObject, key: String): String = required(root, key).lowercase(Locale.ROOT).also {
        if (!it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) {
            throw ArtistCatalogException("资料库标识字段无效，请稍后重试")
        }
    }
    private fun integer(root: JsonObject, key: String): Int = text(root, key)?.toIntOrNull()?.takeIf { it >= 0 }
        ?: throw ArtistCatalogException("资料库分页字段 $key 无效")
}
