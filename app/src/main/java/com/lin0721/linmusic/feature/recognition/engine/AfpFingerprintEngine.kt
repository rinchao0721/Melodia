package com.lin0721.linmusic.feature.recognition.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "AfpFingerprintEngine"
private const val HOST_URL = "file:///android_asset/afp/host.html"
private const val BRIDGE_NAME = "AfpBridge"

// 首次调用含 WASM 编译，低端机实测可达数秒
private const val PAGE_LOAD_TIMEOUT_MS = 10_000L
private const val FINGERPRINT_TIMEOUT_MS = 10_000L

fun interface FingerprintGenerator {
    // 输入 8 kHz 单声道 PCM，返回网易 afp 指纹的 base64
    suspend fun generate(pcm: FloatArray): String
}

// 隐藏 WebView 承载网易云 afp WASM；JS 跑在 WebView 渲染线程，不占主线程，无需 Web Worker
class AfpFingerprintEngine(private val context: Context) : FingerprintGenerator {

    private var webView: WebView? = null
    private var pageReady = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val nextRequestId = AtomicInteger(0)

    // 进入识别界面时调用，提前完成 WebView 创建与 WASM 编译
    suspend fun warmUp() {
        withContext(Dispatchers.Main) {
            ensureWebView()?.evaluateJavascript("afpWarmUp()", null)
        }
    }

    override suspend fun generate(pcm: FloatArray): String {
        if (pcm.isEmpty()) {
            throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "PCM 为空")
        }
        val ready = withContext(Dispatchers.Main) {
            ensureWebView()
            pageReady
        }
        withTimeoutOrNull(PAGE_LOAD_TIMEOUT_MS) { ready.await() }
            ?: throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "指纹页面加载超时")

        val encoded = withContext(Dispatchers.Default) { PcmMath.toLittleEndianBase64(pcm) }
        val requestId = nextRequestId.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        pending[requestId] = deferred
        try {
            withContext(Dispatchers.Main) {
                val view = webView
                    ?: throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "指纹引擎已释放")
                // base64 字符集不含引号与反斜杠，可直接拼进 JS 字符串字面量
                view.evaluateJavascript("afpGenerate($requestId,'$encoded')", null)
            }
            return withTimeoutOrNull(FINGERPRINT_TIMEOUT_MS) { deferred.await() }
                ?: throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "指纹计算超时")
        } finally {
            pending.remove(requestId)
        }
    }

    // 离开识别界面或 ViewModel 销毁时调用；之后再次 generate 会重建 WebView
    fun release() {
        val view = webView ?: return
        webView = null
        pending.values.forEach { it.cancel(CancellationException("指纹引擎已释放")) }
        pending.clear()
        if (!pageReady.isCompleted) pageReady.cancel()
        pageReady = CompletableDeferred()
        view.stopLoading()
        view.removeJavascriptInterface(BRIDGE_NAME)
        view.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun ensureWebView(): WebView? {
        webView?.let { return it }
        val view = try {
            WebView(context.applicationContext)
        } catch (e: Exception) {
            // 系统 WebView 缺失或正在更新时构造会直接抛异常
            AppLogger.e(TAG, "WebView 创建失败", e)
            pageReady.completeExceptionally(
                RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "系统 WebView 不可用", e)
            )
            return null
        }
        val ready = CompletableDeferred<Unit>()
        pageReady = ready
        view.settings.apply {
            javaScriptEnabled = true
            // 只跑本地资源，禁掉一切网络与外部文件访问
            blockNetworkLoads = true
            allowFileAccess = false
            allowContentAccess = false
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                ready.complete(Unit)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
        }
        view.addJavascriptInterface(Bridge(), BRIDGE_NAME)
        view.loadUrl(HOST_URL)
        webView = view
        return view
    }

    // 回调运行在 WebView 的 JavaBridge 线程
    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            AppLogger.d(TAG, "afp WASM 已就绪")
        }

        @JavascriptInterface
        fun onFingerprint(requestId: Int, fingerprint: String?) {
            val deferred = pending[requestId] ?: return
            if (fingerprint.isNullOrEmpty()) {
                deferred.completeExceptionally(
                    RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "指纹为空")
                )
            } else {
                deferred.complete(fingerprint)
            }
        }

        @JavascriptInterface
        fun onError(requestId: Int, message: String?) {
            AppLogger.e(TAG, "afp 指纹计算失败 id=$requestId: $message")
            pending[requestId]?.completeExceptionally(
                RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, message ?: "指纹计算失败")
            )
        }
    }
}
