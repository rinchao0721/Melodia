package com.lin0721.linmusic.feature.localmusic.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.SearchResultRowSkeleton
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons

private const val SKELETON_ROW_COUNT = 8

// 曲库就绪才渲染 content
@Composable
fun LocalLibraryStateGate(
    viewModel: LocalMusicViewModel,
    content: @Composable (LocalMusicUiState.Success) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.checkPermissionAndLoad() }

    LaunchedEffect(viewModel) {
        viewModel.ensureLoaded()
    }

    when (val state = uiState) {
        LocalMusicUiState.Loading -> {
            Column(modifier = Modifier.padding(top = MelodiaSpacing.sm)) {
                repeat(SKELETON_ROW_COUNT) { SearchResultRowSkeleton() }
            }
        }

        is LocalMusicUiState.NeedsPermission -> {
            val alreadyDenied = remember(state.permission) {
                ContextCompat.checkSelfPermission(context, state.permission) == PackageManager.PERMISSION_DENIED
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        icon = MelodiaIcons.Library,
                        title = "需要访问设备存储权限",
                        subtitle = if (alreadyDenied) {
                            "才能扫描并展示手机里的音频文件，请到系统设置里手动开启"
                        } else {
                            "用于扫描并展示手机里已有的音频文件"
                        }
                    )
                    MelodiaButton(onClick = { permissionLauncher.launch(state.permission) }) {
                        Text("授权", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        is LocalMusicUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(message = state.message, onRetry = { viewModel.load() })
            }
        }

        is LocalMusicUiState.Success -> content(state)
    }
}
