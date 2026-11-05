package com.lin0721.linmusic.feature.source.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.plugin.ScriptHost
import com.lin0721.linmusic.core.plugin.ScriptSandbox
import com.lin0721.linmusic.core.plugin.ScriptSandboxFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import kotlin.coroutines.resume

private const val TAG = "WebViewScriptSandbox"
private const val PAGE_READY_TIMEOUT_MS = 4_000L
private const val EVALUATE_TIMEOUT_MS = 10_000L

// 脚本只能经宿主桥访问外部；CSP 补上 blockNetworkLoads 管不到的 WebSocket
private const val CONTENT_SECURITY_POLICY =
    "default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval'; base-uri 'none'; form-action 'none'"

// WebRTC 不受 CSP 限制
private const val DISABLE_WEBRTC =
    "delete window.RTCPeerConnection; delete window.webkitRTCPeerConnection; delete window.RTCDataChannel;"

// 每个沙盒一个顶级站点，localStorage、IndexedDB 与 Cookie 互不相通；页面来自本地数据，不解析这个域名
private fun sandboxOrigin(name: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(name.toByteArray())
    return "https://${digest.joinToString("") { "%02x".format(it) }}.melodia-sandbox"
}

// Android 脚本沙盒工厂：每个沙盒一个无头 WebView
class WebViewScriptSandboxFactory(private val context: Context) : ScriptSandboxFactory {

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun create(name: String, host: ScriptHost, prelude: String): ScriptSandbox = withContext(Dispatchers.Main) {
        val ready = CompletableDeferred<Unit>()
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.blockNetworkLoads = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    ready.complete(Unit)
                }

                // 不允许脚本跳转到别的页面
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        AppLogger.d(TAG, "[$name] JS Console: ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                    }
                    return true
                }
            }
            addJavascriptInterface(WebViewHostBridge(name, host), "MelodiaHostBridge")
        }
        val html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"$CONTENT_SECURITY_POLICY\">" +
            "</head><body><script>$DISABLE_WEBRTC</script><script>\n$prelude\n</script></body></html>"
        webView.loadDataWithBaseURL(sandboxOrigin(name), html, "text/html", "UTF-8", null)
        withTimeoutOrNull(PAGE_READY_TIMEOUT_MS) { ready.await() }
        WebViewScriptSandbox(name, webView)
    }

    override suspend fun clearData(name: String) = withContext(Dispatchers.Main) {
        WebStorage.getInstance().deleteOrigin(sandboxOrigin(name))
    }
}

class WebViewScriptSandbox internal constructor(
    private val name: String,
    private val webView: WebView
) : ScriptSandbox {

    @Volatile
    private var closed = false

    // 间接 eval 在全局作用域执行，同时能捕获同步抛出的错误并经回调带回
    override suspend fun evaluate(script: String): Result<Unit> {
        if (closed) return Result.failure(IllegalStateException("沙盒已关闭"))
        val wrapped = "(function(){try{(0,eval)(${JsonPrimitive(script)});return null;}" +
            "catch(e){return String((e&&e.stack)||e);}})()"
        val raw = withTimeoutOrNull(EVALUATE_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<String?> { continuation ->
                    webView.evaluateJavascript(wrapped) { value -> if (continuation.isActive) continuation.resume(value) }
                }
            }
        } ?: return Result.failure(Exception("脚本执行超时"))
        val error = try {
            (Json.parseToJsonElement(raw) as? JsonPrimitive)?.takeIf { it.isString }?.content
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        return if (error == null) Result.success(Unit) else Result.failure(Exception(error))
    }

    override suspend fun call(function: String, vararg args: JsonElement) {
        if (closed) return
        val functionName = JsonPrimitive(function).toString()
        val js = "(function(f){if(typeof f==='function')f(${args.joinToString(",")});})(window[$functionName])"
        withContext(Dispatchers.Main) {
            if (!closed) webView.evaluateJavascript(js, null)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        val destroy = {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (e: Exception) {
                AppLogger.w(TAG, "[$name] 销毁沙盒异常", e)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) destroy() else Handler(Looper.getMainLooper()).post(destroy)
    }
}

// 暴露给沙盒脚本的宿主接口，在 WebView 的 JavaBridge 线程上回调
private class WebViewHostBridge(
    private val name: String,
    private val host: ScriptHost
) {
    @JavascriptInterface
    fun post(method: String, payload: String) {
        try {
            host.onMessage(method, payload)
        } catch (e: Exception) {
            AppLogger.w(TAG, "[$name] 处理脚本消息 $method 异常", e)
        }
    }

    @JavascriptInterface
    fun invokeSync(method: String, payload: String): String = try {
        host.onSyncCall(method, payload)
    } catch (e: Exception) {
        AppLogger.w(TAG, "[$name] 同步调用 $method 异常", e)
        ""
    }

    @JavascriptInterface
    fun log(level: String, message: String) {
        if (level == "w") AppLogger.w(TAG, "[$name] $message") else AppLogger.d(TAG, "[$name] $message")
    }
}
