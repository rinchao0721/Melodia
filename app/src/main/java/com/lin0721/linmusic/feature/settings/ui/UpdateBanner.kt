package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.MiniStatusBanner
import com.lin0721.linmusic.core.ui.theme.uiEnter
import com.lin0721.linmusic.core.ui.theme.uiExit
import com.lin0721.linmusic.core.update.UpdateChecker
import com.lin0721.linmusic.core.update.domain.UpdateInfo
import org.koin.compose.koinInject

// 更新横幅，点击展开 UpdateDialog
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val updateChecker: UpdateChecker = koinInject()
    val info by updateChecker.availableUpdate.collectAsStateWithLifecycle()
    val isBannerVisible by updateChecker.isBannerVisible.collectAsStateWithLifecycle()
    // 保留最后一个版本信息供退出动画使用
    var lastInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    info?.let { lastInfo = it }

    AnimatedVisibility(
        visible = isBannerVisible && info != null,
        enter = uiEnter(expandVertically() + fadeIn()),
        exit = uiExit(shrinkVertically() + fadeOut()),
        modifier = modifier
    ) {
        lastInfo?.let { shown ->
            MiniStatusBanner(
                icon = Icons.Rounded.SystemUpdate,
                title = "发现新版本 ${shown.versionName}${if (shown.isPrerelease) "（测试版）" else ""}",
                subtitle = "点击查看更新内容",
                onClick = { updateChecker.showDialog() },
                onClose = { updateChecker.dismiss() }
            )
        }
    }
}
