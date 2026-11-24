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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.common.BitMatrix
import com.lin0721.linmusic.core.auth.KugouLoginViewModel
import com.lin0721.linmusic.core.auth.KugouPhoneLoginState
import com.lin0721.linmusic.core.auth.KugouQrState
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.ui.icons.CheckCircle
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Warning
import com.lin0721.linmusic.desktop.ui.artist.TabBar
import com.lin0721.linmusic.desktop.ui.playlist.dialogFieldColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import java.awt.image.BufferedImage

private enum class KugouLoginTab(val label: String) {
    SCAN("扫码"),
    PHONE("手机号")
}

// 桌面端酷狗登录弹窗：支持二维码扫码与手机验证码登录，关闭或切出扫码标签自动清理轮询协程
@Composable
fun KugouLoginDialog(
    viewModel: KugouLoginViewModel,
    onLoginSuccess: (StoredAccount) -> Unit,
    onDismiss: () -> Unit,
    addAccount: Boolean = false
) {
    var tab by remember { mutableStateOf(KugouLoginTab.SCAN) }

    // 停留在扫码标签时启动二维码轮询，切走立即停止，防止后台空转
    LaunchedEffect(tab) {
        if (tab == KugouLoginTab.SCAN) {
            viewModel.startQrLogin(onLoginSuccess)
        } else {
            viewModel.stopQrPolling()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.reset()
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
                Text(
                    if (addAccount) "添加酷狗帐号" else "酷狗音乐登录",
                    color = DesktopColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                TabBar(
                    tabs = KugouLoginTab.entries,
                    selected = tab,
                    label = { it.label },
                    onSelect = { tab = it },
                    modifier = Modifier.padding(top = 14.dp, bottom = 18.dp),
                    small = true
                )
                when (tab) {
                    KugouLoginTab.SCAN -> KugouScanLogin(viewModel, onLoginSuccess)
                    KugouLoginTab.PHONE -> KugouPhoneLogin(
                        viewModel = viewModel,
                        onLoginSuccess = onLoginSuccess,
                        onSwitchToQr = { tab = KugouLoginTab.SCAN }
                    )
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
private fun KugouScanLogin(
    viewModel: KugouLoginViewModel,
    onLoginSuccess: (StoredAccount) -> Unit
) {
    val state by viewModel.qrState.collectAsState()
    Text(
        "使用酷狗音乐 App 扫描二维码",
        color = DesktopColors.TextGray,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        KugouQrContent(state, onRetry = { viewModel.startQrLogin(onLoginSuccess) })
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = kugouQrStatusText(state),
        color = if (state is KugouQrState.Error) DesktopColors.AccentText else DesktopColors.TextGray,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun KugouPhoneLogin(
    viewModel: KugouLoginViewModel,
    onLoginSuccess: (StoredAccount) -> Unit,
    onSwitchToQr: () -> Unit
) {
    val phoneState by viewModel.phoneState.collectAsState()
    val countdownSec by viewModel.captchaCountdownSec.collectAsState()
    var mobile by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val isBusy = phoneState is KugouPhoneLoginState.SendingCaptcha || phoneState is KugouPhoneLoginState.Submitting

    OutlinedTextField(
        value = mobile,
        onValueChange = { input ->
            mobile = input.filter { it.isDigit() }
            if (phoneState is KugouPhoneLoginState.Error) viewModel.resetPhoneState()
        },
        placeholder = { Text("输入酷狗账号手机号", fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        colors = dialogFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { input ->
                code = input.filter { it.isDigit() }
                if (phoneState is KugouPhoneLoginState.Error) viewModel.resetPhoneState()
            },
            placeholder = { Text("短信验证码", fontSize = 13.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = dialogFieldColors(),
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = { viewModel.sendPhoneCaptcha(mobile) },
            enabled = !isBusy && countdownSec == 0 && mobile.isNotBlank(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.TextPrimary),
            modifier = Modifier.height(52.dp)
        ) {
            if (phoneState is KugouPhoneLoginState.SendingCaptcha) {
                CircularProgressIndicator(Modifier.size(16.dp), color = DesktopColors.AccentText, strokeWidth = 2.dp)
            } else {
                Text(
                    if (countdownSec > 0) "${countdownSec}s" else "发送验证码",
                    fontSize = 12.sp
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    val hintText = when (val s = phoneState) {
        KugouPhoneLoginState.Idle -> ""
        KugouPhoneLoginState.SendingCaptcha -> "正在发送短信验证码…"
        KugouPhoneLoginState.CaptchaSent -> "验证码已发送，请查收短信"
        KugouPhoneLoginState.Submitting -> "正在验证登录…"
        is KugouPhoneLoginState.Success -> "登录成功，正在同步资料…"
        is KugouPhoneLoginState.Error -> s.message
    }
    Text(
        text = hintText,
        color = if (phoneState is KugouPhoneLoginState.Error) DesktopColors.AccentText else DesktopColors.TextGray,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth()
    )

    val riskError = (phoneState as? KugouPhoneLoginState.Error)?.takeIf { it.suggestQrFallback }
    if (riskError != null) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSwitchToQr,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.AccentText),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("改用扫码登录", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }

    Spacer(Modifier.height(14.dp))
    Button(
        onClick = { viewModel.loginByPhone(mobile, code, onLoginSuccess) },
        enabled = !isBusy && mobile.isNotBlank() && code.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (phoneState is KugouPhoneLoginState.Submitting) {
            CircularProgressIndicator(Modifier.size(16.dp), color = DesktopColors.OnAccent, strokeWidth = 2.dp)
        } else {
            Text("登录", color = DesktopColors.OnAccent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun KugouQrContent(state: KugouQrState, onRetry: () -> Unit) {
    when (state) {
        KugouQrState.Idle, KugouQrState.Loading -> CircularProgressIndicator(color = DesktopColors.Accent)
        is KugouQrState.WaitingScan -> KugouQrImage(state.qrMatrix, dimmed = false)
        is KugouQrState.Confirming -> Box(contentAlignment = Alignment.Center) {
            KugouQrImage(state.qrMatrix, dimmed = true)
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
        KugouQrState.Expired -> TextButton(onClick = onRetry) {
            Text("刷新二维码", color = DesktopColors.AccentText, fontWeight = FontWeight.Bold)
        }
        is KugouQrState.Error -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                MelodiaIcons.Warning,
                contentDescription = null,
                tint = DesktopColors.AccentText,
                modifier = Modifier.size(32.dp)
            )
            TextButton(onClick = onRetry) {
                Text("重试", color = DesktopColors.AccentText, fontWeight = FontWeight.Bold)
            }
        }
        is KugouQrState.Success -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                MelodiaIcons.CheckCircle,
                contentDescription = null,
                tint = DesktopColors.Accent,
                modifier = Modifier.size(40.dp)
            )
            Text("登录成功", color = DesktopColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun KugouQrImage(matrix: BitMatrix, dimmed: Boolean) {
    val image = remember(matrix) { matrix.toComposeImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = "酷狗登录二维码",
        modifier = Modifier.fillMaxSize().padding(12.dp).alpha(if (dimmed) 0.3f else 1f)
    )
}

private fun kugouQrStatusText(state: KugouQrState): String = when (state) {
    KugouQrState.Idle, KugouQrState.Loading -> "正在生成二维码"
    is KugouQrState.WaitingScan -> "等待扫码"
    is KugouQrState.Confirming -> {
        val nick = state.nickname?.takeIf { it.isNotBlank() }
        if (nick != null) "已扫码（$nick），请在手机上确认登录" else "已扫码，请在手机上确认登录"
    }
    KugouQrState.Expired -> "二维码已过期，请点击刷新"
    is KugouQrState.Success -> "欢迎，${state.account.nickname.ifBlank { state.account.effectivePlatformUid }}"
    is KugouQrState.Error -> state.message
}

private fun BitMatrix.toComposeImageBitmap(): ImageBitmap {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            image.setRGB(x, y, if (get(x, y)) 0x000000 else 0xFFFFFF)
        }
    }
    return image.toComposeImageBitmap()
}
