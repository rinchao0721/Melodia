package com.lin0721.linmusic.desktop.app

import com.lin0721.linmusic.core.AppEnvironment
import com.lin0721.linmusic.core.log.AppLogger
import java.awt.GraphicsEnvironment
import java.io.File
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager

private const val TAG = "RenderBackend"
private const val RENDER_API_PROPERTY = "skiko.renderApi"
private const val RENDER_API_ENV = "SKIKO_RENDER_API"
private const val SESSION_MARKER = "render_session.marker"
// gradle run 被 IDE 直接结束时不会走退出流程，与安装版分开记录
private const val DEBUG_SESSION_MARKER = "render_session_debug.marker"
private const val MARKER_SEPARATOR = "|"
private const val DIRECT3D_API = "DIRECT3D"
// 旧版标记记录的是设置值，AUTO 在 Windows 上即 Direct3D
private const val AUTO_API = "AUTO"

// 渲染后端；AUTO 交给 Skiko 按平台选择，其余取值即 skiko.renderApi
enum class RenderBackend(val skikoApi: String?) {
    AUTO(null),
    DIRECT3D(DIRECT3D_API),
    OPENGL("OPENGL"),
    SOFTWARE("SOFTWARE");

    companion object {
        fun fromName(name: String?): RenderBackend = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

// 须在创建任何窗口前调用；环境变量优先级高于系统属性，留作显卡异常打不开设置页时的兜底
fun applyRenderBackend(backend: RenderBackend) {
    val override = System.getenv(RENDER_API_ENV)
    if (!override.isNullOrBlank()) {
        AppLogger.i(TAG, "环境变量 $RENDER_API_ENV=$override 生效，忽略设置项 ${backend.name}")
        return
    }
    backend.skikoApi?.let { System.setProperty(RENDER_API_PROPERTY, it) }
    AppLogger.i(TAG, "渲染方式设置=${backend.name}")
}

// Direct3D 绘制路径重建交换链时不检查 D3D12 的创建结果（skiko makeDirectXSurface 直接解引用
// 失败后仍为空的 COM 接口），窗口缩放或显示器变化时可能整进程崩在 native 代码里，JVM 捕获不到，
// 日志都来不及落盘。崩过一次就再进不去设置页，所以用上次会话遗留的标记判断是否异常退出。
// 标记记录实际生效的后端，只有上次确实跑在 Direct3D 上才询问是否改用 OpenGL。
// 标记由关闭钩子删除：native 崩溃不会执行钩子，其余有序退出都会清掉标记。
internal class RenderSession(
    private val marker: File,
    private val envOverride: String?,
    private val isWindows: Boolean
) {

    fun begin(
        requested: RenderBackend,
        confirmFallback: () -> Boolean,
        persist: (RenderBackend) -> Unit
    ): RenderBackend {
        val previous = readPrevious()
        var chosen = requested
        if (previous != null) {
            AppLogger.w(TAG, "上次启动（渲染方式=$previous）未正常退出")
            // 环境变量强制指定时改设置也无效
            val canFallback = envOverride.isNullOrBlank() && effectiveApi(requested) == DIRECT3D_API
            if (previous == DIRECT3D_API && canFallback) {
                val accepted = runCatching(confirmFallback)
                    .onFailure { AppLogger.w(TAG, "询问是否改用 OpenGL 失败", it) }
                    .getOrDefault(false)
                if (accepted) {
                    AppLogger.w(TAG, "Direct3D 异常退出后用户选择改用 OpenGL 渲染")
                    runCatching { persist(RenderBackend.OPENGL) }
                        .onFailure { AppLogger.w(TAG, "保存降级后的渲染方式失败", it) }
                    chosen = RenderBackend.OPENGL
                } else {
                    AppLogger.i(TAG, "用户选择继续使用 ${requested.name}")
                }
            }
        }
        marker.parentFile?.mkdirs()
        // 先写标记再创建窗口：若本次在首帧就崩掉，标记会留到下次启动
        runCatching { marker.writeText("${effectiveApi(chosen)}$MARKER_SEPARATOR${System.currentTimeMillis()}") }
            .onFailure { AppLogger.w(TAG, "写入渲染会话标记失败，异常退出将无法检测", it) }
        return chosen
    }

    fun end() {
        runCatching { marker.delete() }
    }

    internal fun effectiveApi(backend: RenderBackend): String =
        envOverride?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
            ?: backend.skikoApi
            ?: if (isWindows) DIRECT3D_API else AUTO_API

    private fun readPrevious(): String? {
        val raw = runCatching {
            marker.takeIf { it.isFile }?.readText()?.substringBefore(MARKER_SEPARATOR)?.trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return if (raw == AUTO_API && isWindows) DIRECT3D_API else raw
    }
}

object RenderSessionGuard {
    private val session by lazy {
        val name = if (AppEnvironment.isDebug) DEBUG_SESSION_MARKER else SESSION_MARKER
        RenderSession(
            marker = File(DesktopPaths.dataDir, name),
            envOverride = System.getenv(RENDER_API_ENV),
            isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        )
    }

    fun begin(
        requested: RenderBackend,
        confirmFallback: () -> Boolean,
        persist: (RenderBackend) -> Unit
    ): RenderBackend {
        val chosen = session.begin(requested, confirmFallback, persist)
        runCatching { Runtime.getRuntime().addShutdownHook(Thread(::end, "render-session-end")) }
            .onFailure { AppLogger.w(TAG, "注册渲染会话关闭钩子失败", it) }
        return chosen
    }

    fun end() {
        session.end()
    }
}

// 首帧前就可能崩，不能等 Compose 窗口，用 Swing 对话框询问
fun confirmOpenGlFallback(): Boolean {
    if (GraphicsEnvironment.isHeadless()) return false
    var accepted = false
    SwingUtilities.invokeAndWait {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val options = arrayOf("改用 OpenGL", "继续使用当前方式")
        val choice = JOptionPane.showOptionDialog(
            null,
            "Melodia 上次运行时意外退出，可能是 DirectX 渲染出现问题。\n" +
                "是否改用 OpenGL 渲染？之后可在 设置 › 界面与歌词 › 渲染方式 中修改。",
            "Melodia",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0]
        )
        accepted = choice == 0
    }
    return accepted
}
