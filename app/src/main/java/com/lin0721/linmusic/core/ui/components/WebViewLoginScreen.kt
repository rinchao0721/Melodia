package com.lin0721.linmusic.core.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lin0721.linmusic.core.ui.theme.BackgroundBlack
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.WebLoginBackground
import com.lin0721.linmusic.core.network.NeteaseEndpoints
import com.lin0721.linmusic.core.network.RealIpProvider
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

/**
 * 网页授权登录界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewLoginScreen(
    onClose: () -> Unit,
    onLoginSuccess: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }

    // 净化 UA（剔除 wv/WebView 关键字）
    val baseUA = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

    val loginUrl = NeteaseEndpoints.LOGIN_URL

    // IP 伪装头受设置里的"使用真实IP"开关控制，与其余接口共用同一个 RealIpProvider 保持会话内一致
    val settingsPreferences: SettingsPreferences = koinInject()
    val realIpProvider: RealIpProvider = koinInject()
    var extraHeaders by remember { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(Unit) {
        val useRealIp = settingsPreferences.useRealIp.first()
        val realIpValue = settingsPreferences.realIpValue.first()
        val ip = realIpProvider.resolveIp(useRealIp, realIpValue)
        extraHeaders = if (ip != null) {
            mapOf("X-Real-IP" to ip, "X-Forwarded-For" to ip)
        } else {
            emptyMap()
        }
    }
    val resolvedHeaders = extraHeaders

    // WebView 原生视图创建较重，先等外层滑入动画播完再创建，避免和动画抢主线程帧时间造成卡顿
    var slideInFinished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ScreenSlideDurationMs.toLong())
        slideInFinished = true
    }
    val readyToLoadWebView = resolvedHeaders != null && slideInFinished

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "安全授权登录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
					)
				},
				navigationIcon = {
					MelodiaIconButton(onClick = onClose) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = "关闭",
							tint = Color.White
						)
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = BackgroundBlack
				)
			)
		},
        containerColor = WebLoginBackground // 容器底色改为网页同款浅灰，彻底避免键盘弹出或加载时闪黑屏/白屏
	) { paddingValues ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
                .background(WebLoginBackground) // 外层容器底色一致
		) {
			if (readyToLoadWebView) {
			AndroidView(
				factory = { context ->
					WebView(context).apply {
						// 完全隔离容器：初始化前清空旧状态
						CookieManager.getInstance().let { manager ->
							manager.setAcceptCookie(true)
							manager.setAcceptThirdPartyCookies(this, true)
							manager.removeAllCookies(null)
							manager.flush()
						}

                        // 将 WebView 底色置为浅灰，防止 resize 布局重绘时闪烁
                        setBackgroundColor(android.graphics.Color.parseColor("#F5F5F7"))

                        webChromeClient = WebChromeClient()
						
						settings.apply {
							javaScriptEnabled = true
							domStorageEnabled = true
							loadWithOverviewMode = true
							useWideViewPort = true
							mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
							// 净化 UA
							userAgentString = baseUA
						}

						webViewClient = object : WebViewClient() {
							override fun onPageFinished(view: WebView?, url: String?) {
								// 实时提取 Token (MUSIC_U)
								checkCookies(onLoginSuccess)
                                isLoading = false // 页面完全加载渲染完成后，关闭加载指示器
							}

							override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
								checkCookies(onLoginSuccess)
							}

							override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
								return false
							}
						}
						
						loadUrl(loginUrl, resolvedHeaders)
					}
				},
				modifier = Modifier.fillMaxSize()
			)
			}

			// 加载过渡
			AnimatedVisibility(
				visible = isLoading,
				enter = fadeIn(),
				exit = fadeOut(),
				modifier = Modifier.fillMaxSize()
			) {
                // 加载页背景也改成网页同款浅灰，保持视觉过渡一致
                Box(modifier = Modifier.fillMaxSize().background(WebLoginBackground), contentAlignment = Alignment.Center) {
					CircularProgressIndicator(color = NeteaseRed)
				}
			}
		}
	}
}

private fun checkCookies(onLoginSuccess: (String) -> Unit) {
    val cookieManager = CookieManager.getInstance()
    val cookies = cookieManager.getCookie(NeteaseEndpoints.WEB_BASE_URL)
    
    // 状态同步：一旦检测到 MUSIC_U= 则视为成功
    if (cookies != null && cookies.contains("MUSIC_U=")) {
        onLoginSuccess(cookies)
    }
}
