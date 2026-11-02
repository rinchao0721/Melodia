package com.lin0721.linmusic.desktop.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.common.BitMatrix
import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.auth.QrLoginState
import com.lin0721.linmusic.desktop.platform.BrowserLocator
import com.lin0721.linmusic.desktop.platform.DesktopWebViewLoginManager
import com.lin0721.linmusic.desktop.ui.artist.TabBar
import com.lin0721.linmusic.desktop.ui.playlist.dialogFieldColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import java.awt.image.BufferedImage

private enum class LoginTab(val label: String) {
    SCAN("扫码"),
    WEB("网页"),
    COOKIE("Cookie")
}

@Composable
fun LoginDialog(
    viewModel: LoginViewModel,
    browserLocator: BrowserLocator,
    onLoginSuccess: (cookies: String) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(LoginTab.SCAN) }

    // 只有停在扫码标签时才轮询二维码，切走即停止
    LaunchedEffect(tab) {
        if (tab == LoginTab.SCAN) viewModel.startQrLogin(onLoginSuccess) else viewModel.resetQrState()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetQrState()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DesktopColors.PopupSurface
        ) {
            Column(
                Modifier.width(380.dp).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("登录", color = DesktopColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TabBar(
                    tabs = LoginTab.entries,
                    selected = tab,
                    label = { it.label },
                    onSelect = { tab = it },
                    modifier = Modifier.padding(top = 14.dp, bottom = 18.dp),
                    small = true
                )
                when (tab) {
                    LoginTab.WEB -> WebLogin(browserLocator, onLoginSuccess)
                    LoginTab.SCAN -> ScanLogin(viewModel, onLoginSuccess)
                    LoginTab.COOKIE -> CookieLogin(viewModel, onLoginSuccess)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("取消", color = DesktopColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun WebLogin(browserLocator: BrowserLocator, onLoginSuccess: (String) -> Unit) {
    var loginError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableStateOf(0) }

    DisposableEffect(retryTrigger) {
        val manager = DesktopWebViewLoginManager(
            browserLocator = browserLocator,
            onLoginSuccess = onLoginSuccess,
            onError = { loginError = it }
        )
        manager.start()
        onDispose {
            manager.cancel()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().height(268.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loginError != null) {
            Text(
                loginError ?: "登录窗口启动失败",
                color = DesktopColors.AccentText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    loginError = null
                    retryTrigger++
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.TextPrimary)
            ) {
                Text("重试")
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = DesktopColors.AccentText,
                strokeWidth = 3.dp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "已调起网易云官方网页登录窗口",
                color = DesktopColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "请在弹出的官方页面中完成登录，完成后将自动同步并关闭窗口",
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = {
                    loginError = null
                    retryTrigger++
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.TextPrimary)
            ) {
                Text("重新打开登录窗口", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ScanLogin(viewModel: LoginViewModel, onLoginSuccess: (String) -> Unit) {
    val state by viewModel.qrState.collectAsState()
    Text(
        "使用网易云音乐 App 扫描二维码",
        color = DesktopColors.TextGray,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        QrContent(state, onRetry = { viewModel.startQrLogin(onLoginSuccess) })
    }
    Spacer(Modifier.height(16.dp))
    Text(statusText(state), color = DesktopColors.TextGray, fontSize = 13.sp)
}

@Composable
private fun CookieLogin(viewModel: LoginViewModel, onLoginSuccess: (String) -> Unit) {
    var cookie by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(
        value = cookie,
        onValueChange = {
            cookie = it
            error = null
        },
        placeholder = { Text("在浏览器登录 music.163.com 后，复制 Cookie 中 MUSIC_U 的值粘贴到这里", fontSize = 13.sp) },
        minLines = 3,
        maxLines = 5,
        colors = dialogFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        error ?: "仅保存在本机，用于调用网易云接口",
        color = if (error != null) DesktopColors.AccentText else DesktopColors.TextGray,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().height(28.dp).padding(top = 8.dp)
    )
    LoginButton(loading = false) {
        if (!viewModel.submitCookieLogin(cookie, onLoginSuccess)) error = "Cookie 格式不正确"
    }
}

@Composable
private fun LoginButton(loading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !loading,
        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = DesktopColors.TextPrimary, strokeWidth = 2.dp)
        } else {
            Text("登录", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QrContent(state: QrLoginState, onRetry: () -> Unit) {
    when (state) {
        QrLoginState.Idle, QrLoginState.Loading -> CircularProgressIndicator(color = DesktopColors.Accent)
        is QrLoginState.WaitingScan -> QrImage(state.qrMatrix, dimmed = false)
        is QrLoginState.WaitingConfirm -> Box(contentAlignment = Alignment.Center) {
            QrImage(state.qrMatrix, dimmed = true)
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
        QrLoginState.Expired, is QrLoginState.Error -> TextButton(onClick = onRetry) {
            Text("刷新二维码", color = DesktopColors.AccentText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QrImage(matrix: BitMatrix, dimmed: Boolean) {
    val image = remember(matrix) { matrix.toImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = "登录二维码",
        modifier = Modifier.fillMaxSize().padding(12.dp).alpha(if (dimmed) 0.3f else 1f)
    )
}

private fun statusText(state: QrLoginState): String = when (state) {
    QrLoginState.Idle, QrLoginState.Loading -> "正在生成二维码"
    is QrLoginState.WaitingScan -> "等待扫码"
    is QrLoginState.WaitingConfirm -> "已扫码，请在手机上确认登录"
    QrLoginState.Expired -> "二维码已过期"
    is QrLoginState.Error -> state.message
}

private fun BitMatrix.toImageBitmap(): ImageBitmap {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            image.setRGB(x, y, if (get(x, y)) 0x000000 else 0xFFFFFF)
        }
    }
    return image.toComposeImageBitmap()
}
