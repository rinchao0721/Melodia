package com.lin0721.linmusic.core.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.common.BitMatrix
import com.lin0721.linmusic.core.auth.KugouLoginViewModel
import com.lin0721.linmusic.core.auth.KugouPhoneLoginState
import com.lin0721.linmusic.core.auth.KugouQrState
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Phone
import com.lin0721.linmusic.core.ui.icons.QrCode
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import org.koin.androidx.compose.koinViewModel

private enum class KugouLoginTab(val label: String) {
    QR("扫码登录"),
    PHONE("手机验证码")
}

// 酷狗音乐登录底部弹窗：支持二维码扫码与手机短信验证码登录，风控时引导一键切至扫码登录
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KugouLoginBottomSheet(
    onDismiss: () -> Unit,
    onLoginSuccess: (StoredAccount) -> Unit,
    addAccount: Boolean = false
) {
    val viewModel: KugouLoginViewModel = koinViewModel()
    var activeTab by remember { mutableStateOf(KugouLoginTab.QR) }
    val currentOnLoginSuccess by rememberUpdatedState(onLoginSuccess)
    val stableOnLoginSuccess = remember {
        { account: StoredAccount -> currentOnLoginSuccess(account) }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.reset() }
    }

    LaunchedEffect(Unit) {
        viewModel.reset()
        activeTab = KugouLoginTab.QR
    }

    LaunchedEffect(activeTab) {
        if (activeTab == KugouLoginTab.QR) {
            val cur = viewModel.qrState.value
            if (cur is KugouQrState.Idle || cur is KugouQrState.Expired) {
                viewModel.startQrLogin(stableOnLoginSuccess)
            }
        } else {
            viewModel.stopQrPolling()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MelodiaColors.Surface,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (addAccount) "添加酷狗账号" else "登录酷狗音乐",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MelodiaColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "酷狗账号会话独立维护，不会影响当前网易云登录与播放",
                fontSize = 12.sp,
                color = MelodiaColors.HintText
            )

            Spacer(modifier = Modifier.height(MelodiaSpacing.md))

            // 方式切换胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MelodiaColors.SurfaceLight.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KugouLoginTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MelodiaColors.Surface else Color.Transparent)
                            .clickable { activeTab = tab }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (tab == KugouLoginTab.QR) MelodiaIcons.QrCode else MelodiaIcons.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selected) MelodiaColors.AccentText else MelodiaColors.HintText
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MelodiaColors.TextPrimary else MelodiaColors.HintText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(MelodiaSpacing.lg))

            AnimatedContent(
                targetState = activeTab,
                label = "KugouLoginTabContent"
            ) { tab ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (tab) {
                        KugouLoginTab.QR -> KugouQrLoginContent(
                            viewModel = viewModel,
                            onLoginSuccess = stableOnLoginSuccess
                        )
                        KugouLoginTab.PHONE -> KugouPhoneLoginContent(
                            viewModel = viewModel,
                            onLoginSuccess = stableOnLoginSuccess,
                            onSwitchToQr = {
                                activeTab = KugouLoginTab.QR
                                viewModel.startQrLogin(stableOnLoginSuccess)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KugouQrLoginContent(
    viewModel: KugouLoginViewModel,
    onLoginSuccess: (StoredAccount) -> Unit
) {
    val qrState by viewModel.qrState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        when (val s = qrState) {
            is KugouQrState.WaitingScan -> {
                val bitmap = remember(s.qrMatrix) { s.qrMatrix.toImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = "酷狗登录二维码",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }
            is KugouQrState.Confirming -> {
                val bitmap = remember(s.qrMatrix) { s.qrMatrix.toImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = "酷狗登录二维码",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .alpha(0.3f)
                )
                CircularProgressIndicator(color = MelodiaColors.Accent)
            }
            is KugouQrState.Expired, is KugouQrState.Error -> {
                MelodiaTextButton(onClick = { viewModel.startQrLogin(onLoginSuccess) }) {
                    Text("点击刷新二维码", color = MelodiaColors.AccentText, fontWeight = FontWeight.Bold)
                }
            }
            else -> CircularProgressIndicator(color = MelodiaColors.Accent)
        }
    }

    Spacer(modifier = Modifier.height(MelodiaSpacing.md))

    val statusText = when (val s = qrState) {
        KugouQrState.Idle, KugouQrState.Loading -> "正在生成酷狗二维码..."
        is KugouQrState.WaitingScan -> "请使用酷狗音乐 App 扫码登录"
        is KugouQrState.Confirming -> {
            val name = s.nickname?.takeIf { it.isNotBlank() }
            if (name != null) "已扫描（$name），请在手机上确认登录" else "扫描成功，请在手机上确认登录"
        }
        KugouQrState.Expired -> "二维码已过期，请刷新重试"
        is KugouQrState.Success -> "登录成功：${s.account.nickname.ifBlank { s.account.effectivePlatformUid }}"
        is KugouQrState.Error -> s.message
    }

    Text(
        text = statusText,
        fontSize = 14.sp,
        color = if (qrState is KugouQrState.Error) MelodiaColors.Error else MelodiaColors.PillContent
    )

    if (qrState is KugouQrState.Expired || qrState is KugouQrState.Error) {
        Spacer(modifier = Modifier.height(MelodiaSpacing.sm))
        MelodiaTextButton(onClick = { viewModel.startQrLogin(onLoginSuccess) }) {
            Text("重新获取二维码", color = MelodiaColors.AccentText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun KugouPhoneLoginContent(
    viewModel: KugouLoginViewModel,
    onLoginSuccess: (StoredAccount) -> Unit,
    onSwitchToQr: () -> Unit
) {
    val phoneState by viewModel.phoneState.collectAsStateWithLifecycle()
    val countdownSec by viewModel.captchaCountdownSec.collectAsStateWithLifecycle()

    var mobile by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val isBusy = phoneState is KugouPhoneLoginState.SendingCaptcha || phoneState is KugouPhoneLoginState.Submitting

    PlaceholderTextField(
        value = mobile,
        onValueChange = {
            mobile = it.filter(Char::isDigit).take(15)
            if (phoneState is KugouPhoneLoginState.Error) viewModel.resetPhoneState()
        },
        placeholder = "请输入酷狗绑定手机号",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
    )

    Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaceholderTextField(
            value = code,
            onValueChange = {
                code = it.filter(Char::isDigit).take(8)
                if (phoneState is KugouPhoneLoginState.Error) viewModel.resetPhoneState()
            },
            placeholder = "短信验证码",
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        MelodiaOutlinedButton(
            onClick = { viewModel.sendPhoneCaptcha(mobile) },
            enabled = !isBusy && countdownSec == 0 && mobile.length >= 6,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MelodiaColors.SurfaceLight),
            modifier = Modifier.height(44.dp)
        ) {
            val btnText = when {
                phoneState is KugouPhoneLoginState.SendingCaptcha -> "发送中…"
                countdownSec > 0 -> "${countdownSec}s"
                else -> "获取验证码"
            }
            Text(text = btnText, fontSize = 13.sp, color = MelodiaColors.TextPrimary)
        }
    }

    Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

    when (val s = phoneState) {
        KugouPhoneLoginState.CaptchaSent -> {
            Text(
                text = "验证码已发送，请留意短信",
                fontSize = 12.sp,
                color = MelodiaColors.HintText,
                modifier = Modifier.fillMaxWidth()
            )
        }
        is KugouPhoneLoginState.Error -> {
            Text(
                text = s.message,
                fontSize = 12.sp,
                color = MelodiaColors.Error,
                modifier = Modifier.fillMaxWidth()
            )
            if (s.suggestQrFallback) {
                Spacer(modifier = Modifier.height(6.dp))
                MelodiaOutlinedButton(
                    onClick = onSwitchToQr,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MelodiaColors.Accent.copy(alpha = 0.6f))
                ) {
                    Text("改用扫码登录", color = MelodiaColors.AccentText, fontWeight = FontWeight.Bold)
                }
            }
        }
        else -> Unit
    }

    Spacer(modifier = Modifier.height(MelodiaSpacing.lg))

    MelodiaButton(
        onClick = { viewModel.loginByPhone(mobile, code, onLoginSuccess) },
        enabled = !isBusy && mobile.length >= 6 && code.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MelodiaColors.Accent)
    ) {
        if (phoneState is KugouPhoneLoginState.Submitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MelodiaColors.OnAccent,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "登录酷狗",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MelodiaColors.OnAccent
            )
        }
    }
}

private fun BitMatrix.toImageBitmap(): ImageBitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val rowOffset = y * width
        for (x in 0 until width) {
            pixels[rowOffset + x] = if (get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}
