package com.lin0721.linmusic.feature.recognition.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.ui.components.AdaptiveContentWidth
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.ContentSwitchDurationMs
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.TextGray
import org.koin.androidx.compose.koinViewModel

private const val TAG = "RecognitionScreen"

private val MiniPlayerMaxWidth = 680.dp

// 听歌识曲全屏覆盖层：出现即开始聆听，离开时释放 WebView 并按需恢复播放
@Composable
fun RecognitionScreen(
    onClose: () -> Unit,
    // 由宿主传入迷你播放条，识别模块不直接依赖播放器界面
    miniPlayer: @Composable (Modifier) -> Unit,
    // 全屏播放页盖在识别页之上时，返回键交给外层先收起播放页
    backHandlerEnabled: Boolean = true,
    viewModel: RecognitionViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    // 只把 State 往下传，进度每次刷新只重组结果卡片
    val positionState = viewModel.playbackPosition.collectAsStateWithLifecycle()
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.start()
        } else {
            // 拒绝后仍不需要展示理由，说明用户勾了"不再询问"，只能去系统设置开启
            val activity = context as? Activity
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            viewModel.onPermissionDenied(permanentlyDenied)
        }
    }

    fun startWithPermission() {
        if (hasMicPermission()) viewModel.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) {
        viewModel.onOpened()
        startWithPermission()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onClosed() }
    }

    // 从系统设置授权回来后自动开始；普通授权弹窗关闭也会触发 ON_RESUME，那条路径已由权限回调负责，不能重复启动
    val currentState by rememberUpdatedState(uiState)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val state = currentState
            if (event == Lifecycle.Event.ON_RESUME &&
                state is RecognitionUiState.PermissionRequired &&
                state.permanentlyDenied &&
                hasMicPermission()
            ) {
                viewModel.start()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = backHandlerEnabled) {
        if (showHistory) showHistory = false else onClose()
    }

    // 聆听中播放已暂停，且迷你条会挡住取消按钮
    val showMiniPlayer = nowPlaying != null && uiState !is RecognitionUiState.Listening

    // Surface 吃掉触摸，避免点击穿透到下层页面
    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundDark) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 底部导航栏留白由最外层统一处理，内部（含历史页 Scaffold）不再重复计算
            Box(
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    RecognitionTopBar(
                        title = if (uiState is RecognitionUiState.Found) "识别结果" else "听歌识曲",
                        onClose = onClose,
                        onOpenHistory = { showHistory = true }
                    )
                    AdaptiveContentWidth(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = uiState,
                            contentKey = { it::class },
                            transitionSpec = {
                                fadeIn(tween(ContentSwitchDurationMs)) togetherWith fadeOut(tween(ContentSwitchDurationMs))
                            },
                            label = "recognition_state"
                        ) { state ->
                            when (state) {
                                RecognitionUiState.Idle -> Box(Modifier.fillMaxSize())
                                is RecognitionUiState.PermissionRequired -> RecognitionPermissionContent(
                                    permanentlyDenied = state.permanentlyDenied,
                                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                    onOpenSettings = {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { context.startActivity(intent) }
                                            .onFailure { AppLogger.e(TAG, "打开应用设置失败", it) }
                                    }
                                )
                                is RecognitionUiState.Listening -> RecognitionListeningContent(
                                    progress = state.progress,
                                    onCancel = onClose
                                )
                                is RecognitionUiState.Found -> RecognitionFoundContent(
                                    state = state,
                                    nowPlaying = nowPlaying,
                                    positionProvider = { positionState.value },
                                    onOpen = viewModel::openCandidate,
                                    onRetry = { startWithPermission() }
                                )
                                is RecognitionUiState.Failed -> RecognitionFailedContent(
                                    reason = state.reason,
                                    progress = state.progress,
                                    onRetry = { startWithPermission() },
                                    onOpenHistory = { showHistory = true }
                                )
                            }
                        }
                    }
                }

                // 外层 Column 的作用域会劫持重载解析，这里显式调用非作用域版本
                androidx.compose.animation.AnimatedVisibility(
                    visible = showHistory,
                    enter = slideInHorizontally(tween(ContentSwitchDurationMs)) { it } + fadeIn(tween(ContentSwitchDurationMs)),
                    exit = slideOutHorizontally(tween(ContentSwitchDurationMs)) { it } + fadeOut(tween(ContentSwitchDurationMs))
                ) {
                    RecognitionHistoryPage(
                        history = history,
                        nowPlaying = nowPlaying,
                        onBack = { showHistory = false },
                        onPlay = viewModel::openHistory,
                        onRemove = viewModel::removeHistory,
                        onClear = viewModel::clearHistory
                    )
                }
            }

            AnimatedVisibility(
                visible = showMiniPlayer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                // 平板上与内容列同宽居中，手机上 680dp 上限不生效
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    miniPlayer(
                        Modifier
                            .widthIn(max = MiniPlayerMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = MelodiaSpacing.sm, vertical = MelodiaSpacing.sm)
                    )
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun RecognitionTopBar(
    title: String,
    onClose: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp)
    ) {
        MelodiaIconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Text(
            text = title,
            color = TextGray,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )
        MelodiaIconButton(onClick = onOpenHistory, modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)) {
            Icon(Icons.Rounded.History, contentDescription = "识别历史", tint = Color.White)
        }
    }
}
