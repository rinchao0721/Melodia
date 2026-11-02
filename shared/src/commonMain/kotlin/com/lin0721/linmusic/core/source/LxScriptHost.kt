package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.plugin.ScriptHost
import com.lin0721.linmusic.core.plugin.ScriptSandbox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

private const val TAG = "LxScriptHost"

// 沙盒内 lx.request 的网络出口
typealias LxHttpFetcher = suspend (url: String, method: String, headers: Map<String, String>, body: String) -> String

fun sourceHttpFetcher(maxBodyBytes: Long? = null): LxHttpFetcher = { url, method, headers, body ->
    if (method.equals("POST", ignoreCase = true)) {
        SourceHttpClient.post(url, body, headers, maxBodyBytes = maxBodyBytes)
    } else {
        SourceHttpClient.get(url, headers, maxBodyBytes = maxBodyBytes)
    }
}

// LX 沙盒的宿主消息处理，两端共用：网络代理、inited 事件、直链回传与 md5
class LxScriptHost(
    private val pluginId: String,
    private val ioScope: CoroutineScope,
    private val fetcher: LxHttpFetcher,
    private val onInited: (List<String>) -> Unit,
    private val onResolved: (callId: Int, url: String?) -> Unit
) : ScriptHost {

    // 沙盒创建后由运行时回填；回填前不会收到网络请求
    @Volatile
    var sandbox: ScriptSandbox? = null

    override fun onMessage(method: String, payload: String) {
        val data = try {
            Json.parseToJsonElement(payload) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return
        when (method) {
            "http" -> handleHttp(data)
            "event" -> handleEvent(data)
            "resolved" -> handleResolved(data)
        }
    }

    override fun onSyncCall(method: String, payload: String): String = if (method == "md5") md5(payload) else ""

    private fun handleHttp(data: JsonObject) {
        val id = (data["id"] as? JsonPrimitive)?.intOrNull ?: return
        val url = data.string("url") ?: return
        val method = data.string("method") ?: "GET"
        val body = data.string("body").orEmpty()
        val headers = runCatching {
            data["headers"]?.jsonObject?.mapValues { (_, value) -> value.jsonPrimitive.content }
        }.getOrNull().orEmpty()
        ioScope.launch {
            val reply = try {
                val responseBody = fetcher(url, method, headers, body)
                arrayOf(JsonPrimitive(id), JsonNull, JsonPrimitive(OK_RESPONSE), JsonPrimitive(responseBody))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                arrayOf(JsonPrimitive(id), JsonPrimitive(e.message ?: "Network error"), JsonNull, JsonNull)
            }
            try {
                sandbox?.call("__melodia_handle_http_response", *reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "[$pluginId] 回传网络响应失败: ${e.message}")
            }
        }
    }

    private fun handleEvent(data: JsonObject) {
        if (data.string("event") != "inited") return
        val sources = runCatching { (data["data"] as? JsonObject)?.get("sources")?.jsonObject?.keys?.toList() }
            .getOrNull().orEmpty()
        onInited(sources)
    }

    private fun handleResolved(data: JsonObject) {
        val callId = (data["callId"] as? JsonPrimitive)?.intOrNull ?: return
        val error = data.string("error")
        if (!error.isNullOrBlank()) AppLogger.w(TAG, "插件沙盒[$pluginId] 解析直链失败: $error")
        onResolved(callId, data.string("url")?.takeIf { it.isNotBlank() })
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val OK_RESPONSE = "{\"statusCode\":200}"

        fun md5(input: String): String = try {
            MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}

// 写入脚本元信息，供插件读取 lx.currentScriptInfo
fun lxScriptInfoJs(name: String, description: String, version: String, author: String): String =
    "window.lx.currentScriptInfo = { name: ${JsonPrimitive(name)}, description: ${JsonPrimitive(description)}, " +
        "version: ${JsonPrimitive(version)}, author: ${JsonPrimitive(author)} };"
