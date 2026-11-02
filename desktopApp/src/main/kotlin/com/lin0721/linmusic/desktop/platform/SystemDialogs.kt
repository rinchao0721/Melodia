package com.lin0721.linmusic.desktop.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.lang.reflect.InvocationTargetException
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

// 系统选择框会开启嵌套事件循环：在协程或点击回调里直接阻塞，会让正在执行的协程被重入而崩溃。
// 先挂起到 IO 线程，再交给界面线程弹出，弹窗期间协程保持挂起。
private suspend fun <T> onEventThread(block: () -> T): T = withContext(Dispatchers.IO) {
    var result: Any? = null
    try {
        SwingUtilities.invokeAndWait { result = block() }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }
    @Suppress("UNCHECKED_CAST")
    result as T
}

// 取消返回 null
suspend fun chooseDirectory(initial: File, title: String): File? = onEventThread {
    val chooser = JFileChooser(initial.takeIf { it.isDirectory }).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

// filter 形如 "*.js;*.zip"；取消返回 null
suspend fun chooseFile(title: String, filter: String): File? = onEventThread {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        // Windows 原生对话框把 file 当作过滤模式
        file = filter
        isVisible = true
    }
    dialog.file?.let { File(dialog.directory.orEmpty(), it) }
}

// 在系统文件管理器中定位文件；Windows 直接选中，其他平台打开所在文件夹
fun revealInFileManager(file: File): Boolean = try {
    when {
        !file.exists() -> false
        File.separatorChar == '\\' -> {
            ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
            true
        }
        Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR) -> {
            Desktop.getDesktop().browseFileDirectory(file)
            true
        }
        Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN) -> {
            Desktop.getDesktop().open(file.parentFile ?: file)
            true
        }
        else -> false
    }
} catch (_: Exception) {
    false
}

// 多选版本；取消返回空列表
suspend fun chooseFiles(title: String, filter: String): List<File> = onEventThread {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        isMultipleMode = true
        file = filter
        isVisible = true
    }
    dialog.files?.toList().orEmpty()
}

// 另存为；未带扩展名时补上 extension；取消返回 null
suspend fun chooseSaveFile(title: String, defaultName: String, extension: String): File? = onEventThread {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
        file = defaultName
        isVisible = true
    }
    dialog.file?.let { name ->
        val withExt = if (name.endsWith(".$extension", ignoreCase = true)) name else "$name.$extension"
        File(dialog.directory.orEmpty(), withExt)
    }
}
