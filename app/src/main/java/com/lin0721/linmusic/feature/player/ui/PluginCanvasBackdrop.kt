package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.lin0721.linmusic.core.plugin.LayoutColor
import com.lin0721.linmusic.core.plugin.LayoutCoverColors
import com.lin0721.linmusic.core.plugin.resolve
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import androidx.compose.material3.MaterialTheme
import kotlinx.serialization.json.*

/** Generic drawing surface. No effect geometry, color recipe, network or native JS bridge. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PluginCanvasBackdrop(script: String, base: Color, playing: Boolean, modifier: Modifier = Modifier, colors: Map<String, LayoutColor> = emptyMap()) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val dark = MelodiaColors.IsDark
    val motion = LocalUiMotion.current.enabled
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val resolvedColors = colors.toSortedMap().mapValues { (_, value) ->
        val target = value.resolve(LayoutCoverColors(base, base))
        animateColorAsState(target, tween(800), label = "canvasColor").value
    }
    val state = buildJsonObject {
        put("version", 1)
        put("width", size.width / density)
        put("height", size.height / density)
        put("pixelRatio", density.coerceAtMost(1.5f))
        put("theme", if (dark) "dark" else "light")
        put("coverColor", "#%06X".format(base.toArgb() and 0xFFFFFF))
        put("colors", buildJsonObject {
            resolvedColors.forEach { (name, color) -> put(name, "#%06X".format(color.toArgb() and 0xFFFFFF)) }
        })
        put("playing", playing)
        put("motion", motion)
        put("visible", lifecycle.isAtLeast(Lifecycle.State.RESUMED))
    }.toString()
    Box(modifier.background(MaterialTheme.colorScheme.background).onSizeChanged { size = it }) {
        // Remove the entire document in background: plugin timers cannot keep executing.
        if (lifecycle.isAtLeast(Lifecycle.State.RESUMED)) key(script) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { BackgroundCanvasView(it, script) },
                update = { it.bind(state) },
                onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.removeAllViews(); it.destroy() }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class BackgroundCanvasView(context: Context, private val script: String) : WebView(context) {
    private var ready = false
    private var state = "{}"
    init {
        // Compose measures the view, but WebView also consults its layout params
        // for the CSS viewport. WRAP_CONTENT leaves percentage heights at zero.
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(AndroidColor.TRANSPARENT)
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.blockNetworkLoads = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                android.util.Log.d("PluginCanvas", message.message()); return true
            }
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean { result.cancel(); return true }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(byteArrayOf()))
            override fun onPageFinished(view: WebView, url: String) {
                evaluateJavascript("try { " + script + "\n; true } catch(e) { console.error('background entry failed: '+e); false }") { result ->
                    ready = result == "true"
                    if (ready) evaluateJavascript("window.__applyBackground($state)", null)
                }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                ready = false
                visibility = GONE
                return true
            }
        }
        loadDataWithBaseURL(null, DOCUMENT, "text/html", "UTF-8", null)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    fun bind(json: String) {
        if (state == json && ready) return
        state = json
        if (ready) evaluateJavascript("window.__applyBackground($json)", null)
    }
    companion object {
        private val DOCUMENT = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'none'">
            <style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent}canvas{width:100%;height:100%;display:block}</style>
            <canvas id="surface"></canvas><script>
            (()=>{
              const canvas=document.getElementById('surface'),ctx=canvas.getContext('2d');
              let state={},raf=0,last=0,time=0,failed=false;
              window.background={version:1,render:null,onState:null};
              function draw(now){
                raf=0;
                if(failed)return;
                const moving=state.playing&&state.motion&&state.visible;
                if(!last||now-last>=1000/30||!moving){
                  if(moving&&last)time+=Math.min(now-last,100);
                  last=now;
                  try{ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,canvas.width,canvas.height);
                    ctx.scale(state.pixelRatio,state.pixelRatio);
                    if(background.render)background.render(ctx,state,time);
                  }catch(e){console.error('background render failed: '+e);failed=true;ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,canvas.width,canvas.height);return;}
                }
                if(moving)raf=requestAnimationFrame(draw);
              }
              window.__applyBackground=s=>{
                state=s;
                const maxRatio=Math.sqrt(1000000/Math.max(1,s.width*s.height));
                state.pixelRatio=Math.max(0.25,Math.min(s.pixelRatio,maxRatio));
                const w=Math.max(1,Math.round(s.width*state.pixelRatio)),h=Math.max(1,Math.round(s.height*state.pixelRatio));
                if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
                if(raf)cancelAnimationFrame(raf);last=0;
                try{if(background.onState)background.onState(state);}catch(e){failed=true;}
                draw(performance.now());
              };
            })();
            </script>
        """.trimIndent()
    }
}
