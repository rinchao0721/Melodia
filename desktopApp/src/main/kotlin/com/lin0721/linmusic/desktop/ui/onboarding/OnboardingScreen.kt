package com.lin0721.linmusic.desktop.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.desktop.loadAppIcon
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.shell.PlatformBrandDot
import com.lin0721.linmusic.desktop.ui.shell.WindowControlButtons
import com.lin0721.linmusic.desktop.ui.shell.WindowDragArea
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens

private val ButtonWidth = 280.dp
private val ButtonHeight = 44.dp
private val TileGap = 6.dp
private val TileTarget = 132.dp
private val OutlineColor = Color(0xFF727272)

// 首次启动落地页：倾斜封面墙渐隐到纯黑，下方居中登录入口；保留窗口控制按钮与拖动区
@Composable
fun WindowScope.OnboardingScreen(
    coverUrls: List<String>,
    isMaximized: Boolean,
    onLoginNetease: () -> Unit,
    onLoginKugou: () -> Unit,
    onBrowseAsGuest: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        CoverCollage(coverUrls, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.15f),
                    0.45f to Color.Black.copy(alpha = 0.75f),
                    0.7f to Color.Black
                )
            )
        )
        Row(
            Modifier.fillMaxWidth().height(DesktopDimens.TitleBarHeight),
            verticalAlignment = Alignment.Top
        ) {
            WindowDragArea(Modifier.weight(1f).height(DesktopDimens.TitleBarHeight))
            Row(Modifier.height(DesktopDimens.TitleBarHeight)) {
                WindowControlButtons(isMaximized, onMinimize, onToggleMaximize, onClose)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val logo = remember { loadAppIcon() }
            Image(
                painter = logo,
                contentDescription = "Melodia",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape)
            )
            Text(
                "欢迎来到\nMelodia 的世界",
                color = Color.White,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            OnboardingPill("登录网易云音乐", MusicPlatform.NETEASE, solid = true, onClick = onLoginNetease)
            OnboardingPill("登录酷狗音乐", MusicPlatform.KUGOU, solid = false, onClick = onLoginKugou)
            GuestButton(onBrowseAsGuest)
        }
    }
}

@Composable
private fun OnboardingPill(text: String, platform: MusicPlatform, solid: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(50)
    val background = when {
        solid && hovered -> Color(0xFFE6E6E6)
        solid -> Color.White
        hovered -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Box(
        Modifier.width(ButtonWidth).height(ButtonHeight)
            .clip(shape)
            .background(background)
            .then(if (solid) Modifier else Modifier.border(1.dp, if (hovered) Color.White else OutlineColor, shape))
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PlatformBrandDot(platform, 16.dp, Modifier.align(Alignment.CenterStart).padding(start = 18.dp))
        Text(
            text,
            color = if (solid) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GuestButton(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Text(
        "先逛逛",
        color = if (hovered) Color.White else Color.White.copy(alpha = 0.85f),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
            .clip(RoundedCornerShape(50))
            .hoverable(source)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

// 倾斜放大的封面网格；无封面时退回渐变色块
@Composable
private fun CoverCollage(coverUrls: List<String>, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val columns = collageColumns(maxWidth, TileTarget)
        val tile = (maxWidth - TileGap * (columns + 1)) / columns
        val rows = collageRows(maxHeight, tile + TileGap)
        val tiles = remember(coverUrls, columns, rows) { collageTiles(coverUrls, columns * rows) }
        Column(
            Modifier.requiredSize(maxWidth, maxHeight)
                .graphicsLayer {
                    rotationZ = -8f
                    scaleX = 1.35f
                    scaleY = 1.35f
                    alpha = 0.55f
                }
                .padding(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap)
        ) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    for (col in 0 until columns) {
                        val index = row * columns + col
                        CollageTile(tiles[index], index, tile)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageTile(url: String?, index: Int, size: Dp) {
    if (url != null) {
        Cover(url, size, shape = RoundedCornerShape(6.dp), requestSize = 75.dp)
    } else {
        val (start, end) = FallbackGradients[index % FallbackGradients.size]
        Spacer(
            Modifier.size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(listOf(start, end)))
        )
    }
}

private val FallbackGradients = listOf(
    Color(0xFFE60026) to Color(0xFF5C0010),
    Color(0xFF2979FF) to Color(0xFF0D2A66),
    Color(0xFF7C4DFF) to Color(0xFF2A1466),
    Color(0xFF00BFA5) to Color(0xFF00463C),
    Color(0xFFFF9100) to Color(0xFF663A00),
    Color(0xFFEC407A) to Color(0xFF5E1A31)
)

internal fun collageColumns(width: Dp, target: Dp): Int =
    if (target.value <= 0f) 1 else (width.value / target.value).toInt().coerceIn(4, 16)

internal fun collageRows(height: Dp, step: Dp): Int =
    if (step.value <= 0f) 1 else (height.value / step.value).toInt().plus(1).coerceIn(1, 24)

// 封面不足时循环复用，无封面时全部为 null 交给渐变色块
internal fun collageTiles(urls: List<String>, count: Int): List<String?> {
    if (count <= 0) return emptyList()
    if (urls.isEmpty()) return List(count) { null }
    // 步长与封面数互质时相邻格不易重复
    val step = if (urls.size % 5 == 0) 1 else 5
    return List(count) { urls[(it * step) % urls.size] }
}

// 偏好未读出前不渲染主界面，避免首次启动先闪一下主界面
enum class OnboardingScreenState { LOADING, SHOWN, HIDDEN }

fun resolveOnboardingScreen(completed: Boolean?): OnboardingScreenState = when (completed) {
    null -> OnboardingScreenState.LOADING
    false -> OnboardingScreenState.SHOWN
    true -> OnboardingScreenState.HIDDEN
}
