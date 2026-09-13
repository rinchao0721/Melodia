package com.lin0721.linmusic.core.network

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.crypto.NeteaseCrypto
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer

private const val TAG = "CryptoInterceptor"

// OkHttp 拦截器 —— 自动识别网易云 API 类型并加密请求体 (WeApi/EApi/LinuxApi)
class CryptoInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        // 仅拦截带有请求体的请求
        val originalBody = originalRequest.body ?: return chain.proceed(originalRequest)

        val cryptoType = resolveCryptoType(url) ?: return chain.proceed(originalRequest)
        val rawJson = originalBody.readString()
        val cookies = originalRequest.header("Cookie") ?: ""

        val encryptedForm = when (cryptoType) {
            CryptoType.WEAPI -> buildWeApiForm(rawJson, cookies)
            CryptoType.EAPI -> buildEApiForm(url, rawJson)
            CryptoType.LINUXAPI -> buildLinuxApiForm(rawJson)
        }

        val newRequest = originalRequest.newBuilder()
            .post(encryptedForm)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        return chain.proceed(newRequest)
    }

    // ---------- 路由判定 ----------

    internal enum class CryptoType { WEAPI, EAPI, LINUXAPI }

    // 根据 URL 路径判断加密类型（internal 以便单测直接调用）
    internal fun resolveCryptoType(url: String): CryptoType? = when {
        url.contains("/eapi/") -> CryptoType.EAPI
        url.contains("/linux/api/") -> CryptoType.LINUXAPI
        url.contains("/weapi/") || url.contains("/api/") -> CryptoType.WEAPI
        else -> null
    }

    // ---------- 各模式加密并构造 FormBody ----------

    // WeApi：加密后包含 params + encSecKey
    private fun buildWeApiForm(rawJson: String, cookies: String): FormBody {
        val csrfToken = Regex("__csrf=([^;]+)").find(cookies)?.groupValues?.get(1) ?: ""
        
        val jsonPayload = try {
            val orgJson = org.json.JSONObject(rawJson)
            orgJson.put("csrf_token", csrfToken)
            // 告知服务器返回明文 JSON (e_r=false)，避免响应被加密为 binary
            orgJson.put("e_r", false)
            orgJson.toString()
        } catch (e: Exception) {
            AppLogger.w(TAG, "WeApi csrf_token 注入失败，回退使用原始请求体", e)
            rawJson
        }

        val encrypted = NeteaseCrypto.weapi(jsonPayload)
        return FormBody.Builder()
            .add("params", encrypted.getValue("params"))
            .add("encSecKey", encrypted.getValue("encSecKey"))
            .build()
    }

    // EApi：加密后仅包含 params
    private fun buildEApiForm(url: String, rawJson: String): FormBody {
        // 从完整 URL 中提取 /eapi/... 路径部分作为 EApi 所需的 url 参数
        val eapiPath = extractEApiPath(url)
        val apiPath = eapiPath.replace("/eapi/", "/api/")
        
        // 给原生 JSON payload 加入风控 Header 上下文
        val rootObj = try {
            org.json.JSONObject(rawJson)
        } catch (e: Exception) {
            AppLogger.e(TAG, "EApi 请求体解析失败，payload 已清空: $url", e)
            org.json.JSONObject()
        }
        
        val model = android.os.Build.MODEL
        val deviceId = NeteaseDeviceId.current()

        val headerObj = org.json.JSONObject().apply {
            put("osver", android.os.Build.VERSION.RELEASE)
            put("deviceId", deviceId)
            put("os", "android")
            put("appver", "9.0.90")
            put("versioncode", "140")
            put("mobilename", model)
            put("buildver", System.currentTimeMillis().toString().substring(0, 10))
            put("resolution", "1920x1080")
            put("requestId", "${System.currentTimeMillis()}_${(1000..9999).random()}")
        }
        rootObj.put("header", headerObj)
        rootObj.put("e_r", false)

        val encrypted = NeteaseCrypto.eapi(apiPath, rootObj.toString())
        return FormBody.Builder()
            .add("params", encrypted.getValue("params"))
            .build()
    }

    // LinuxApi：加密后仅包含 eparams
    private fun buildLinuxApiForm(rawJson: String): FormBody {
        val encrypted = NeteaseCrypto.linuxapi(rawJson)
        return FormBody.Builder()
            .add("eparams", encrypted.getValue("eparams"))
            .build()
    }

    // ---------- 工具方法 ----------

    // 将 [RequestBody] 读取为字符串
    private fun RequestBody.readString(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }
    // 从完整 URL 中提取 /eapi/ 及之后的路径
    private fun extractEApiPath(url: String): String {
        val idx = url.indexOf("/eapi/")
        return if (idx != -1) url.substring(idx) else url
    }
}
