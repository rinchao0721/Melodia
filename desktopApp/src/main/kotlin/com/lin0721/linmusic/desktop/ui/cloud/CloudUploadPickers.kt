package com.lin0721.linmusic.desktop.ui.cloud

import com.lin0721.linmusic.desktop.ui.local.pickAudioFiles
import com.lin0721.linmusic.desktop.ui.local.pickMusicFolder
import com.lin0721.linmusic.desktop.ui.shell.DesktopNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

internal fun uploadEnqueuedMessage(requested: Int, enqueued: Int): String = when {
    requested == 0 -> "没有可上传的音频文件"
    enqueued == 0 -> "这些歌曲已在上传队列中"
    enqueued < requested -> "已加入上传队列 $enqueued 首，其余已在队列中"
    else -> "已加入上传队列 $enqueued 首"
}

// 未登录时引导登录，返回是否可以继续上传
private fun DesktopNavigator.requireLogin(): Boolean {
    if (isLoggedIn) return true
    showMessage("请先登录账号")
    openLogin()
    return false
}

internal fun uploadFiles(cloud: CloudMusicActions, navigator: DesktopNavigator, files: Collection<File>) {
    if (!navigator.requireLogin()) return
    navigator.showMessage(uploadEnqueuedMessage(files.size, cloud.uploadFiles(files)))
}

internal fun uploadFilesFromPicker(scope: CoroutineScope, cloud: CloudMusicActions, navigator: DesktopNavigator) {
    if (!navigator.requireLogin()) return
    scope.launch {
        val files = pickAudioFiles()
        if (files.isNotEmpty()) navigator.showMessage(uploadEnqueuedMessage(files.size, cloud.uploadFiles(files)))
    }
}

internal fun uploadFolderFromPicker(scope: CoroutineScope, cloud: CloudMusicActions, navigator: DesktopNavigator) {
    if (!navigator.requireLogin()) return
    scope.launch {
        val folder = pickMusicFolder() ?: return@launch
        val files = cloud.audioFilesIn(folder)
        navigator.showMessage(uploadEnqueuedMessage(files.size, cloud.uploadFiles(files)))
    }
}
