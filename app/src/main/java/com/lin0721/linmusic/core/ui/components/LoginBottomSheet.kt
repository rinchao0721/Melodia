package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.auth.QrLoginState
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.SurfaceDark
import com.lin0721.linmusic.core.ui.theme.SurfaceLight
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import org.koin.androidx.compose.koinViewModel

private enum class LoginMode { CHOICE, QR, COOKIE }

/**
 * 登录方式选择底部弹窗
 * 网页登录跳全屏 WebView，二维码/Cookie 登录直接在弹窗内内联完成
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginBottomSheet(
    onDismiss: () -> Unit,
    onWebLogin: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    val viewModel: LoginViewModel = koinViewModel()
    var mode by remember { mutableStateOf(LoginMode.CHOICE) }

    // 弹窗整体离开组合时兜底停止轮询，覆盖点击遮罩/手势关闭等未经返回按钮的路径
    DisposableEffect(Unit) {
        onDispose { viewModel.resetQrState() }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.resetQrState()
            onDismiss()
        },
        containerColor = SurfaceDark,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (mode != LoginMode.CHOICE) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MelodiaIconButton(onClick = {
                        viewModel.resetQrState()
                        mode = LoginMode.CHOICE
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                }
            }

            when (mode) {
                LoginMode.CHOICE -> LoginChoiceContent(
                    onWebLogin = onWebLogin,
                    onQrLogin = {
                        mode = LoginMode.QR
                        viewModel.startQrLogin(onLoginSuccess)
                    },
                    onCookieLogin = { mode = LoginMode.COOKIE }
                )
                LoginMode.QR -> QrLoginContent(viewModel = viewModel, onLoginSuccess = onLoginSuccess)
                LoginMode.COOKIE -> CookieLoginContent(
                    onSubmit = { raw -> viewModel.submitCookieLogin(raw, onLoginSuccess) }
                )
            }
        }
    }
}

/**
 * 方式选择态：网页登录 / 二维码登录 / Cookie 登录
 */
@Composable
private fun LoginChoiceContent(
    onWebLogin: () -> Unit,
    onQrLogin: () -> Unit,
    onCookieLogin: () -> Unit
) {
    // 大标题
    Text(
        text = "登录",
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
    )
    Text(
        text = "登录后享受完整体验",
        fontSize = 13.sp,
        color = Color.Gray,
        modifier = Modifier.padding(bottom = MelodiaSpacing.xl)
    )

    LoginOptionButton(text = "网页登录", icon = Icons.Default.Language, isPrimary = true, onClick = onWebLogin)
    Spacer(modifier = Modifier.height(MelodiaSpacing.sm))
    LoginOptionButton(text = "二维码登录", icon = Icons.Default.QrCode, isPrimary = false, onClick = onQrLogin)
    Spacer(modifier = Modifier.height(MelodiaSpacing.sm))
    LoginOptionButton(text = "Cookie 登录", icon = Icons.Default.ContentPaste, isPrimary = false, onClick = onCookieLogin)
}

/**
 * 二维码登录内联态：展示二维码图片 + 轮询状态文案
 */
@Composable
private fun QrLoginContent(
    viewModel: LoginViewModel,
    onLoginSuccess: (String) -> Unit
) {
    val state by viewModel.qrState.collectAsStateWithLifecycle()

    Text(
        text = "二维码登录",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(bottom = MelodiaSpacing.lg)
    )

    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            is QrLoginState.WaitingScan -> {
                Image(
                    bitmap = s.qrBitmap.asImageBitmap(),
                    contentDescription = "登录二维码",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }
            is QrLoginState.WaitingConfirm -> {
                Image(
                    bitmap = s.qrBitmap.asImageBitmap(),
                    contentDescription = "登录二维码",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .alpha(0.3f)
                )
                CircularProgressIndicator(color = NeteaseRed)
            }
            else -> CircularProgressIndicator(color = NeteaseRed)
        }
    }

    Spacer(modifier = Modifier.height(MelodiaSpacing.lg))

    val statusText = when (val s = state) {
        QrLoginState.Idle, QrLoginState.Loading -> "正在生成二维码..."
        is QrLoginState.WaitingScan -> "请使用网易云音乐App扫码登录"
        is QrLoginState.WaitingConfirm -> "扫描成功，请在手机上确认登录"
        QrLoginState.Expired -> "二维码已过期"
        is QrLoginState.Error -> s.message
    }
    Text(text = statusText, fontSize = 14.sp, color = Color.LightGray)

    if (state is QrLoginState.Expired || state is QrLoginState.Error) {
        Spacer(modifier = Modifier.height(MelodiaSpacing.md))
        MelodiaTextButton(onClick = { viewModel.startQrLogin(onLoginSuccess) }) {
            Text("点击刷新", color = NeteaseRed, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Cookie 登录内联态
 */
@Composable
private fun CookieLoginContent(onSubmit: (String) -> Boolean) {
    var cookieInput by remember { mutableStateOf("") }
    // 校验失败提示放在弹窗内联展示：ModalBottomSheet 是独立 Window，全局 ToastManager 的提示会被它挡住看不见
    var errorText by remember { mutableStateOf<String?>(null) }

    Text(
        text = "Cookie 登录",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
    )
    Text(
        text = "粘贴Cookie 字符串",
        fontSize = 13.sp,
        color = Color.Gray,
        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
    )
    PlaceholderTextField(
        value = cookieInput,
        onValueChange = {
            cookieInput = it
            errorText = null
        },
        placeholder = "MUSIC_U=xxxxxx",
        singleLine = false,
        minLines = 3
    )
    if (errorText != null) {
        Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
        Text(
            text = errorText.orEmpty(),
            fontSize = 12.sp,
            color = NeteaseRed,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(modifier = Modifier.height(MelodiaSpacing.lg))
    MelodiaButton(
        onClick = {
            if (!onSubmit(cookieInput)) {
                errorText = "格式不正确，请粘贴完整 Cookie 字符串或 MUSIC_U 的值"
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NeteaseRed),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp)
    ) {
        Text("确认登录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/**
 * 登录选项按钮
 */
@Composable
private fun LoginOptionButton(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    if (isPrimary) {
        MelodiaButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeteaseRed
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 2.dp
            )
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    } else {
        MelodiaOutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(SurfaceLight)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            )
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color.LightGray
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
