package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.AppEnvironment
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.Proxy
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "KugouGatewayClient"

enum class KugouEncryptType {
    ANDROID,
    WEB,
    REGISTER
}

data class KugouRequestOptions(
    val method: String = "GET",
    val url: String,
    val baseUrl: String? = null,
    val params: Map<String, Any?> = emptyMap(),
    val data: Any? = null,
    val headers: Map<String, String> = emptyMap(),
    val encryptType: KugouEncryptType = KugouEncryptType.ANDROID,
    val cookieOverrides: Map<String, String> = emptyMap(),
    val skipActiveAccountCookies: Boolean = false,
    val encryptKey: Boolean = false,
    val clearDefaultParams: Boolean = false,
    val clearDefaultHeaders: Boolean = false,
    val notSignature: Boolean = false,
    val binaryResponse: Boolean = false,
    val overrideAppId: Int? = null,
    val overrideClientVer: Int? = null
)

data class KugouRawResponse(
    val httpCode: Int,
    val bodyBytes: ByteArray,
    val bodyText: String,
    val bodyJson: JsonObject? = null,
    val cookies: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val ssaChallenge: KugouSsaChallenge? = null
) {
    val statusOrNull: Int?
        get() = bodyJson?.get("status")?.jsonPrimitive?.intOrNull

    val status: Int
        get() = statusOrNull ?: 0

    val errorCode: Int
        get() = bodyJson?.get("error_code")?.jsonPrimitive?.intOrNull ?: 0

    val errorMsg: String
        get() = bodyJson?.get("error_msg")?.jsonPrimitive?.content
            ?: bodyJson?.get("msg")?.jsonPrimitive?.content
            ?: ""

    // 对照 KuGouMusicApi/util/request.js：仅 status === 0 或 error_code != 0 视为业务失败
    val isBizSuccess: Boolean
        get() = httpCode in 200..299 && statusOrNull != 0 && errorCode == 0
}

// 酷狗完全独立网关客户端：严禁复用网易云拦截器或 Netease OkHttpClient/Retrofit
class KugouGatewayClient(
    private val config: KugouConfig,
    private val deviceStore: KugouDeviceStore,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    okHttpClient: OkHttpClient? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val random = SecureRandom()

    private val client: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .addInterceptor(
            HttpLoggingInterceptor { message -> AppLogger.d("KugouHttp", message) }.apply {
                level = if (AppEnvironment.isDebug) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
                redactHeader("Cookie")
                redactHeader("Set-Cookie")
            }
        )
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 对照 KuGouMusicApi/util/request.js#createRequest
    suspend fun executeRequest(options: KugouRequestOptions): KugouRawResponse {
        config.ensureModeSynced()
        val mode = config.mode
        val effectiveAppId = options.overrideAppId ?: mode.appid
        val effectiveClientVer = options.overrideClientVer ?: mode.clientver
        val device = deviceStore.getOrCreateDeviceIdentity()
        val activeAccount = if (options.skipActiveAccountCookies) {
            null
        } else {
            userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        }

        val dfid = options.cookieOverrides["dfid"]?.takeIf { it.isNotBlank() }
            ?: device.effectiveDfid
        val mid = options.cookieOverrides["KUGOU_API_MID"]?.takeIf { it.isNotBlank() }
            ?: options.cookieOverrides["mid"]?.takeIf { it.isNotBlank() }
            ?: device.mid
        val uuid = "-"
        val token = options.cookieOverrides["token"]
            ?: activeAccount?.credentials?.get("token").orEmpty()
        val userid = options.cookieOverrides["userid"]
            ?: activeAccount?.credentials?.get("userid")
            ?: activeAccount?.effectivePlatformUid?.takeIf { it.isNotBlank() }
            ?: "0"
        val clienttime = nowMs() / 1000L

        val mergedParams = LinkedHashMap<String, Any?>()
        if (!options.clearDefaultParams) {
            mergedParams["dfid"] = dfid
            mergedParams["mid"] = mid
            mergedParams["uuid"] = uuid
            mergedParams["appid"] = effectiveAppId
            mergedParams["clientver"] = effectiveClientVer
            mergedParams["clienttime"] = clienttime
            if (token.isNotBlank()) {
                mergedParams["token"] = token
            }
            if (userid.isNotBlank() && userid != "0") {
                mergedParams["userid"] = userid
            }
        }
        options.params.forEach { (k, v) ->
            if (v != null) {
                mergedParams[k] = v
            }
        }

        if (options.encryptKey) {
            val hash = mergedParams["hash"]?.toString().orEmpty()
            val pMid = mergedParams["mid"]?.toString().orEmpty()
            val pUserId = mergedParams["userid"] ?: 0
            val pAppId = mergedParams["appid"]?.toString()?.toIntOrNull()
                ?: effectiveAppId
            mergedParams["key"] = KugouSigner.signKey(
                hash = hash,
                mid = pMid,
                userid = pUserId,
                appid = pAppId,
                mode = mode
            )
        }

        val serializedBody = serializeBody(options.data)
        val signParamsInput = mergedParams.mapValues { (_, v) -> formatParamValue(v) }
        if (!signParamsInput.containsKey("signature") && !options.notSignature) {
            val signature = when (options.encryptType) {
                KugouEncryptType.REGISTER -> KugouSigner.signatureRegisterParams(signParamsInput)
                KugouEncryptType.WEB -> KugouSigner.signatureWebParams(signParamsInput, serializedBody.asString)
                KugouEncryptType.ANDROID -> when {
                    serializedBody.rawBytes != null -> KugouSigner.signatureAndroidParams(
                        params = signParamsInput,
                        data = serializedBody.rawBytes,
                        mode = mode
                    )
                    else -> KugouSigner.signatureAndroidParams(
                        params = signParamsInput,
                        data = serializedBody.asString,
                        mode = mode
                    )
                }
            }
            mergedParams["signature"] = signature
        }

        val effectiveClientTime = mergedParams["clienttime"]?.toString() ?: clienttime.toString()
        val requestHeaders = LinkedHashMap<String, String>()
        if (!options.clearDefaultHeaders) {
            requestHeaders["User-Agent"] = KugouConfig.DEFAULT_USER_AGENT
            requestHeaders["dfid"] = dfid
            requestHeaders["clienttime"] = effectiveClientTime
            requestHeaders["mid"] = mid
            requestHeaders["kg-rc"] = "1"
            requestHeaders["kg-thash"] = "5d816a0"
            requestHeaders["kg-rec"] = "1"
            requestHeaders["kg-rf"] = "B9EDA08A64250DEFFBCADDEE00F8F25F"
        }
        options.headers.forEach { (k, v) -> requestHeaders[k] = v }

        val resolvedBase = config.resolveBaseUrl(options.baseUrl)
        val rawPath = if (options.url.startsWith("/")) options.url else "/${options.url}"
        val fullUrl = if ((options.baseUrl ?: "").contains("openapicdn")) {
            val rawQuery = mergedParams.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(formatParamValue(v), "UTF-8")}"
            }
            if (rawQuery.isEmpty()) "$resolvedBase$rawPath" else "$resolvedBase$rawPath?$rawQuery"
        } else {
            val urlBuilder = "$resolvedBase$rawPath".toHttpUrl().newBuilder()
            mergedParams.forEach { (k, v) ->
                urlBuilder.addQueryParameter(k, formatParamValue(v))
            }
            urlBuilder.build().toString()
        }

        val methodUpper = options.method.uppercase()
        val reqBody = when (methodUpper) {
            "POST", "PUT", "PATCH" -> {
                val bytes = serializedBody.rawBytes ?: serializedBody.asString.toByteArray(Charsets.UTF_8)
                val mediaType = if (serializedBody.rawBytes != null) {
                    "application/octet-stream".toMediaType()
                } else {
                    "application/json; charset=utf-8".toMediaType()
                }
                bytes.toRequestBody(mediaType)
            }
            else -> null
        }

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .method(methodUpper, reqBody)
        requestHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        val responseData = executeCall(requestBuilder.build())
        val responseHeaders = responseData.headers
        val ssaCode = responseHeaders.entries
            .firstOrNull { it.key.equals("ssa-code", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }

        val ssaChallenge = ssaCode?.let {
            KugouRiskSimulator.generateSimulate(
                mid = mid,
                userid = userid,
                dfid = dfid,
                webglHash = device.webglHash,
                ssaCode = it,
                timestampMs = nowMs()
            )
        }

        val bodyText = if (options.binaryResponse) "" else responseData.bodyBytes.toString(Charsets.UTF_8)
        val bodyJson = if (options.binaryResponse || bodyText.isBlank()) {
            null
        } else {
            runCatching { json.parseToJsonElement(bodyText).jsonObject }.getOrNull()
        }

        return KugouRawResponse(
            httpCode = responseData.httpCode,
            bodyBytes = responseData.bodyBytes,
            bodyText = bodyText,
            bodyJson = bodyJson,
            cookies = responseData.cookies,
            headers = responseHeaders,
            ssaChallenge = ssaChallenge
        )
    }

    // 对照 KuGouMusicApi/util/request.js#createCloudRequest（云歌单服务双向 AES-CBC + RSA 通道）
    suspend fun executeCloudRequest(
        url: String,
        bodyJson: String,
        cookieOverrides: Map<String, String> = emptyMap(),
        baseUrl: String? = null
    ): KugouRawResponse {
        config.ensureModeSynced()
        val mode = config.mode
        val effectivePublicKeyPem = config.overridePublicKeyPem ?: mode.publicKeyPem
        val device = deviceStore.getOrCreateDeviceIdentity()
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()

        val mid = cookieOverrides["KUGOU_API_MID"]?.takeIf { it.isNotBlank() }
            ?: cookieOverrides["mid"]?.takeIf { it.isNotBlank() }
            ?: device.mid
        val dfid = cookieOverrides["dfid"]?.takeIf { it.isNotBlank() } ?: device.effectiveDfid
        val token = cookieOverrides["token"]
            ?: activeAccount?.credentials?.get("token").orEmpty()
        val useridStr = cookieOverrides["userid"]
            ?: activeAccount?.credentials?.get("userid")
            ?: activeAccount?.effectivePlatformUid
            ?: "0"
        val uidNumeric = useridStr.toLongOrNull() ?: 0L

        val aesSecret = randomAlphaNum6()
        val aesMd5 = KugouCrypto.md5Hex(aesSecret)
        val aesKeyBytes = aesMd5.substring(0, 16).toByteArray(Charsets.UTF_8)
        val aesIvBytes = aesMd5.substring(16, 32).toByteArray(Charsets.UTF_8)

        val clienttime = nowMs() / 1000L
        val key = KugouCrypto.md5Hex("${mode.appid}${mode.cloudAppkey}${mode.clientver}${clienttime}").lowercase()
        val portraitPlain = buildJsonObject {
            put("aes", aesSecret)
            put("uid", uidNumeric)
            put("token", token)
        }.toString()
        val portrait = KugouCrypto.rsaEncrypt2(portraitPlain, effectivePublicKeyPem).uppercase()

        val resolvedBase = config.resolveBaseUrl(baseUrl)
        val rawPath = if (url.startsWith("/")) url else "/$url"
        val httpUrl = "$resolvedBase$rawPath".toHttpUrl().newBuilder()
            .addQueryParameter("appid", mode.appid.toString())
            .addQueryParameter("clientver", mode.clientver.toString())
            .addQueryParameter("mid", mid)
            .addQueryParameter("clienttime", clienttime.toString())
            .addQueryParameter("key", key)
            .addQueryParameter("dfid", dfid)
            .addQueryParameter("p", portrait)
            .build()

        val encryptedBody = KugouCrypto.aesCbcEncryptBytes(
            plainBytes = bodyJson.toByteArray(Charsets.UTF_8),
            keyBytes = aesKeyBytes,
            ivBytes = aesIvBytes
        )

        val request = Request.Builder()
            .url(httpUrl)
            .post(encryptedBody.toRequestBody("application/json;charset=utf-8".toMediaType()))
            .header("x-router", "cloudlist.service.kugou.com")
            .header("dfid", dfid)
            .header("clienttime", clienttime.toString())
            .header("mid", mid)
            .header("kg-rc", "1")
            .header("kg-thash", "5d816a0")
            .header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .build()

        val responseData = executeCall(request)
        val decryptedText = runCatching {
            KugouCrypto.aesCbcDecryptBytes(responseData.bodyBytes, aesKeyBytes, aesIvBytes)
                .toString(Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: responseData.bodyBytes.toString(Charsets.UTF_8)

        val parsedJson = runCatching { json.parseToJsonElement(decryptedText).jsonObject }.getOrNull()
        return KugouRawResponse(
            httpCode = responseData.httpCode,
            bodyBytes = responseData.bodyBytes,
            bodyText = decryptedText,
            bodyJson = parsedJson,
            cookies = responseData.cookies,
            headers = responseData.headers
        )
    }

    private suspend fun executeCall(request: Request): RawHttpResult =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(AppError.NetworkError(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val bytes = resp.body?.bytes() ?: ByteArray(0)
                        val cookies = resp.headers("Set-Cookie").map(::parseCookieString)
                        val headersMap = LinkedHashMap<String, String>()
                        for (i in 0 until resp.headers.size) {
                            headersMap[resp.headers.name(i)] = resp.headers.value(i)
                        }
                        if (continuation.isActive) {
                            continuation.resume(
                                RawHttpResult(
                                    httpCode = resp.code,
                                    bodyBytes = bytes,
                                    cookies = cookies,
                                    headers = headersMap
                                )
                            )
                        }
                    }
                }
            })
        }

    private fun randomAlphaNum6(): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return buildString(6) {
            repeat(6) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    private fun serializeBody(data: Any?): SerializedBody = when (data) {
        null -> SerializedBody(asString = "", rawBytes = null)
        is ByteArray -> SerializedBody(asString = "", rawBytes = data)
        is String -> SerializedBody(asString = data, rawBytes = null)
        is JsonElement -> SerializedBody(asString = data.toString(), rawBytes = null)
        is Map<*, *> -> SerializedBody(asString = anyToJsonElement(data).toString(), rawBytes = null)
        else -> SerializedBody(asString = data.toString(), rawBytes = null)
    }

    private fun formatParamValue(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is JsonElement -> value.toString()
        is Map<*, *> -> anyToJsonElement(value).toString()
        is Iterable<*> -> anyToJsonElement(value).toString()
        else -> value.toString()
    }

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) }
        )
        is Iterable<*> -> JsonArray(value.map(::anyToJsonElement))
        else -> JsonPrimitive(value.toString())
    }

    private data class SerializedBody(
        val asString: String,
        val rawBytes: ByteArray?
    )

    private data class RawHttpResult(
        val httpCode: Int,
        val bodyBytes: ByteArray,
        val cookies: List<String>,
        val headers: Map<String, String>
    )

    companion object {
        private val COOKIE_META_REGEX = Regex("\\s*(Domain|domain|path|expires)=[^(;|$)]+;*")

        fun parseCookieString(raw: String): String =
            raw.replace(COOKIE_META_REGEX, "").replace(";HttpOnly", "").trim()
    }
}
