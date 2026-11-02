package com.lin0721.linmusic.desktop.data.recognition

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import com.lin0721.linmusic.feature.recognition.engine.FingerprintGenerator
import com.lin0721.linmusic.feature.recognition.engine.PcmMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotAccess
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.io.IOAccess
import java.util.concurrent.Executors

private const val TAG = "GraalAfpFingerprintEngine"
private const val RESOURCE_DIR = "afp"
private const val INIT_TIMEOUT_MS = 20_000L
private const val IDLE_RELEASE_MS = 120_000L

// 补齐 afp.js 依赖的浏览器全局；定时器由宿主 afpTick 驱动
private val PRELUDE = """
    globalThis.window = globalThis;
    var __afpTimers = {};
    var __afpNextTimer = 1;
    globalThis.setInterval = function (fn) { var id = __afpNextTimer++; __afpTimers[id] = { fn: fn, repeat: true }; return id; };
    globalThis.setTimeout = function (fn) { var id = __afpNextTimer++; __afpTimers[id] = { fn: fn, repeat: false }; return id; };
    globalThis.clearInterval = function (id) { delete __afpTimers[id]; };
    globalThis.clearTimeout = globalThis.clearInterval;
    var __afpAlphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    var __afpLookup = {};
    for (var i = 0; i < __afpAlphabet.length; i++) __afpLookup[__afpAlphabet.charAt(i)] = i;
    globalThis.atob = function (s) {
        s = String(s).replace(/=+$/, '');
        var out = '', buffer = 0, bits = 0;
        for (var i = 0; i < s.length; i++) {
            buffer = (buffer << 6) | __afpLookup[s.charAt(i)];
            bits += 6;
            if (bits >= 8) { bits -= 8; out += String.fromCharCode((buffer >> bits) & 255); }
        }
        return out;
    };
    globalThis.btoa = function (s) {
        var out = '';
        for (var i = 0; i < s.length; i += 3) {
            var a = s.charCodeAt(i), b = s.charCodeAt(i + 1), c = s.charCodeAt(i + 2);
            var t = (a << 16) | ((b || 0) << 8) | (c || 0);
            out += __afpAlphabet.charAt((t >> 18) & 63) + __afpAlphabet.charAt((t >> 12) & 63)
                + (i + 1 < s.length ? __afpAlphabet.charAt((t >> 6) & 63) : '=')
                + (i + 2 < s.length ? __afpAlphabet.charAt(t & 63) : '=');
        }
        return out;
    };
""".trimIndent()

// 只实例化一次 WASM 并复用；PCM 以 base64 传入，直接解码进 Uint8Array 免去中间字符串
private val BRIDGE = """
    var __afpRuntime = null;
    var __afpError = null;
    function afpStart() {
        instantiateRuntime().then(
            function (runtime) { __afpRuntime = runtime; },
            function (e) { __afpError = String((e && e.message) || e); }
        );
    }
    function afpTick() {
        var ids = Object.keys(__afpTimers);
        for (var i = 0; i < ids.length; i++) {
            var timer = __afpTimers[ids[i]];
            if (!timer) continue;
            if (!timer.repeat) delete __afpTimers[ids[i]];
            timer.fn();
        }
        return __afpRuntime !== null || __afpError !== null;
    }
    function afpError() { return __afpError; }
    function afpDecode(s) {
        var len = s.length;
        while (len > 0 && s.charAt(len - 1) === '=') len--;
        var out = new Uint8Array(Math.floor(len * 3 / 4));
        var buffer = 0, bits = 0, p = 0;
        for (var i = 0; i < len; i++) {
            buffer = (buffer << 6) | __afpLookup[s.charAt(i)];
            bits += 6;
            if (bits >= 8) { bits -= 8; out[p++] = (buffer >> bits) & 255; }
        }
        return out;
    }
    function afpGenerate(pcmBase64) {
        var bytes = afpDecode(pcmBase64);
        var vector = __afpRuntime.ExtractQueryFP(bytes.buffer);
        try {
            var size = vector.size();
            var out = new Uint8Array(size);
            for (var i = 0; i < size; i++) out[i] = vector.get(i);
            return b64encode(out);
        } finally {
            vector.delete();
        }
    }
""".trimIndent()

// GraalJS + GraalWasm 承载网易 afp WASM；普通 JDK 上 Truffle 只能解释执行，单次指纹约 2–3 秒
class GraalAfpFingerprintEngine : FingerprintGenerator {

    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "afp-engine").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 以下两个字段只在 dispatcher 线程读写
    private var context: Context? = null
    private var releaseJob: Job? = null

    // 打开识曲时调用，提前完成 WASM 加载（约 3 秒）
    suspend fun warmUp() {
        try {
            withContext(dispatcher) {
                ensureContext()
                scheduleRelease()
            }
        } catch (e: RecognitionException) {
            AppLogger.w(TAG, "指纹引擎预热失败，首次识别时重试", e)
        }
    }

    override suspend fun generate(pcm: FloatArray): String {
        if (pcm.isEmpty()) {
            throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "PCM 为空")
        }
        val encoded = withContext(Dispatchers.Default) { PcmMath.toLittleEndianBase64(pcm) }
        val fingerprint = withContext(dispatcher) {
            val ctx = ensureContext()
            scheduleRelease()
            try {
                ctx.getBindings("js").getMember("afpGenerate").execute(encoded).asString()
            } catch (e: PolyglotException) {
                AppLogger.e(TAG, "afp 指纹计算失败", e)
                throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "指纹计算失败", e)
            }
        }
        if (fingerprint.isNullOrEmpty()) {
            throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "指纹为空")
        }
        return fingerprint
    }

    private fun ensureContext(): Context {
        context?.let { return it }
        val started = System.nanoTime()
        // 发布版经 ProGuard 重打包后 Truffle 的 Multi-Release 清单丢失，需关掉这项自检
        System.setProperty("polyglotimpl.DisableMultiReleaseCheck", "true")
        val ctx = try {
            Context.newBuilder("js", "wasm")
                .allowExperimentalOptions(true)
                .option("js.webassembly", "true")
                .option("engine.WarnInterpreterOnly", "false")
                .allowPolyglotAccess(PolyglotAccess.ALL)
                .allowHostAccess(HostAccess.NONE)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .build()
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建 GraalJS 上下文失败", e)
            throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "指纹引擎不可用", e)
        }
        try {
            ctx.eval("js", PRELUDE)
            ctx.eval(loadSource("afp.wasm.js"))
            ctx.eval(loadSource("afp.js"))
            ctx.eval("js", BRIDGE)
            ctx.eval("js", "afpStart()")
            val tick = ctx.getBindings("js").getMember("afpTick")
            val deadline = started + INIT_TIMEOUT_MS * 1_000_000
            while (!tick.execute().asBoolean()) {
                if (System.nanoTime() > deadline) {
                    throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "指纹引擎加载超时")
                }
                Thread.sleep(5)
            }
            val error = ctx.eval("js", "afpError()")
            if (!error.isNull) {
                throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "指纹引擎加载失败: ${error.asString()}")
            }
        } catch (e: Exception) {
            ctx.close()
            if (e is RecognitionException) throw e
            AppLogger.e(TAG, "加载 afp 脚本失败", e)
            throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "指纹引擎加载失败", e)
        }
        AppLogger.d(TAG, "afp WASM 已就绪，耗时 ${(System.nanoTime() - started) / 1_000_000} ms")
        context = ctx
        return ctx
    }

    private fun loadSource(name: String): Source {
        val text = javaClass.classLoader?.getResourceAsStream("$RESOURCE_DIR/$name")?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: throw RecognitionException(RecognitionFailure.ENGINE_UNAVAILABLE, "缺少指纹库资源 $name")
        return Source.newBuilder("js", text, name).build()
    }

    // 空闲一段时间后释放上下文，WASM 实例常驻约占数十 MB
    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            withContext(dispatcher) {
                context?.close()
                context = null
                AppLogger.d(TAG, "afp 引擎空闲已释放")
            }
        }
    }
}
