package com.lin0721.linmusic.desktop.platform.windows

import com.lin0721.linmusic.desktop.platform.AutoStartService
import com.lin0721.linmusic.desktop.platform.BrowserLocator
import com.lin0721.linmusic.desktop.platform.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.MediaSessionService
import com.lin0721.linmusic.desktop.platform.TaskbarThumbnailService
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.platform.windows.smtc.SmtcSession
import com.lin0721.linmusic.desktop.platform.windows.taskbar.WindowsTaskbarThumbnail
import org.koin.dsl.module
import java.io.File

val windowsPlatformModule = module {
    single<MediaSessionService> { SmtcSession() }
    single<GlobalHotkeyService> { Win32GlobalHotkeys() }
    single<AutoStartService> { WindowsAutoStart() }
    single { WindowsWindowEffects() }
    single<WindowEffects> { get<WindowsWindowEffects>() }
    single<TaskbarThumbnailService> { WindowsTaskbarThumbnail(get()) }
    single<BrowserLocator> { BrowserLocator(::findEdge) }
}

// 系统自带的 Edge，按机器级、用户级安装位置依次查找
private fun findEdge(): File? {
    val relative = "Microsoft\\Edge\\Application\\msedge.exe"
    val candidates = listOf(
        File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", relative),
        File(System.getenv("ProgramFiles") ?: "C:\\Program Files", relative),
        System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { File(it, relative) }
    )
    return candidates.firstOrNull { it != null && it.isFile && it.canExecute() }
}
