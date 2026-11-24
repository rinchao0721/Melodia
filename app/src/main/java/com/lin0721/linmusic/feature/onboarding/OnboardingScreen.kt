package com.lin0721.linmusic.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lin0721.linmusic.R
import com.lin0721.linmusic.core.ui.components.KugouLoginBottomSheet
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.PlatformBrand
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import org.koin.androidx.compose.koinViewModel

private const val GridColumns = 6
private val GridGap = 4.dp
private val OutlineGray = Color(0xFF727272)

// 无封面时的渐变色块
private val FallbackTiles = listOf(
    Color(0xFF3A1C71) to Color(0xFFD76D77),
    Color(0xFF0F2027) to Color(0xFF2C5364),
    Color(0xFF8E2DE2) to Color(0xFF4A00E0),
    Color(0xFFE60026) to Color(0xFF6A0010),
    Color(0xFF2979FF) to Color(0xFF0D2A66),
    Color(0xFF11998E) to Color(0xFF38EF7D),
    Color(0xFFF7971E) to Color(0xFFFFD200),
    Color(0xFF434343) to Color(0xFF1A1A1A)
)

// 首次启动页：登录网易云 / 登录酷狗 / 先逛逛
@Composable
fun OnboardingScreen() {
    val viewModel: OnboardingViewModel = koinViewModel()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var showNeteaseLogin by rememberSaveable { mutableStateOf(false) }
    var showNeteaseWebLogin by rememberSaveable { mutableStateOf(false) }
    var showKugouLogin by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CoverCollage(covers = covers, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.45f to Color.Black.copy(alpha = 0.75f),
                        0.7f to Color.Black
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "Melodia",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
            Text(
                text = "欢迎来到\nMelodia 的世界",
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            MelodiaButton(
                onClick = { showNeteaseLogin = true },
                enabled = !busy,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                PillLabel(text = "登录网易云音乐", dotColor = PlatformBrand.NeteaseRed)
            }
            MelodiaOutlinedButton(
                onClick = { showKugouLogin = true },
                enabled = !busy,
                shape = CircleShape,
                border = BorderStroke(1.dp, OutlineGray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                PillLabel(text = "登录酷狗音乐", dotColor = PlatformBrand.KugouBlue)
            }
            MelodiaTextButton(
                onClick = viewModel::browseAsGuest,
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) {
                Text(text = "先逛逛", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showNeteaseWebLogin) {
            WebViewLoginScreen(
                onClose = { showNeteaseWebLogin = false },
                onLoginSuccess = { cookies ->
                    showNeteaseWebLogin = false
                    viewModel.onNeteaseLoginCookies(cookies)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showNeteaseLogin) {
        LoginBottomSheet(
            onDismiss = { showNeteaseLogin = false },
            onWebLogin = {
                showNeteaseLogin = false
                showNeteaseWebLogin = true
            },
            onLoginSuccess = { cookies ->
                showNeteaseLogin = false
                viewModel.onNeteaseLoginCookies(cookies)
            }
        )
    }

    if (showKugouLogin) {
        KugouLoginBottomSheet(
            onDismiss = { showKugouLogin = false },
            onLoginSuccess = {
                showKugouLogin = false
                viewModel.onKugouLoggedIn()
            }
        )
    }
}

@Composable
private fun PillLabel(text: String, dotColor: Color) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(18.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// 倾斜的封面网格；封面不足时循环复用
@Composable
private fun CoverCollage(covers: List<String>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val cell: Dp = (maxWidth - GridGap * (GridColumns + 1)) / GridColumns
        // 旋转放大后四角仍需铺满，多铺几行
        val rows = remember(maxHeight, cell) { ((maxHeight / (cell + GridGap)).toInt() + 3).coerceAtLeast(1) }
        Column(
            modifier = Modifier
                .requiredSize(maxWidth, (cell + GridGap) * rows)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    rotationZ = -8f
                    scaleX = 1.35f
                    scaleY = 1.35f
                    alpha = 0.55f
                }
                .padding(GridGap),
            verticalArrangement = Arrangement.spacedBy(GridGap)
        ) {
            repeat(rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                    repeat(GridColumns) { col ->
                        val index = row * GridColumns + col
                        val tileModifier = Modifier
                            .size(cell)
                            .clip(RoundedCornerShape(6.dp))
                        if (covers.isEmpty()) {
                            val (from, to) = FallbackTiles[(index + row) % FallbackTiles.size]
                            Box(modifier = tileModifier.background(Brush.linearGradient(listOf(from, to))))
                        } else {
                            AsyncImage(
                                model = covers[index % covers.size],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = tileModifier.background(Color(0xFF1A1A1A))
                            )
                        }
                    }
                }
            }
        }
    }
}
