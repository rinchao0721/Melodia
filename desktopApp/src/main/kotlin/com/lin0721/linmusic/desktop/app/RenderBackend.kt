package com.lin0721.linmusic.desktop.app

import com.lin0721.linmusic.core.log.AppLogger
import java.io.File

private const val TAG = "RenderBackend"
private const val RENDER_API_PROPERTY = "skiko.renderApi"
private const val RENDER_API_ENV = "SKIKO_RENDER_API"
private const val SESSION_MARKER = "render_session.marker"
private const val MARKER_SEPARATOR = "|"

// 渲染后端；AUTO 交给 Skiko 按平台选择，其余取值即 skiko.renderApi
enum class RenderBackend(val skikoApi: String?) {
    AUTO(null),
    DIRECT3D("DIRECT3D"),
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
// 日志都来不及落盘。崩过一次就再进不去设置页，所以用上次会话遗留的标记判断是否异常退出，
// 自动把 Direct3D 降到 OpenGL。正常退出会删除标记；已选 OpenGL/软件渲染时不动用户的设置。
object RenderSessionGuard {
    private val marker: File get() = File(DesktopPaths.dataDir, SESSION_MARKER)

    fun begin(requested: RenderBackend, persist: (RenderBackend) -> Unit): RenderBackend {
        val previous = runCatching {
            marker.takeIf { it.isFile }?.readText()?.substringBefore(MARKER_SEPARATOR)
        }.getOrNull()?.takeIf { it.isNotBlank() }
        marker.parentFile?.mkdirs()
        // 先写标记再返回：若本次在首帧就崩掉，标记会留到下次启动
        runCatching { marker.writeText("${requested.name}$MARKER_SEPARATOR${System.currentTimeMillis()}") }
            .onFailure { AppLogger.w(TAG, "写入渲染会话标记失败，异常退出将无法自动降级", it) }
        if (previous == null) return requested

        AppLogger.w(TAG, "上次启动（渲染方式=$previous）未正常退出")
        if (requested != RenderBackend.AUTO && requested != RenderBackend.DIRECT3D) return requested

        AppLogger.w(TAG, "Direct3D 崩溃后改用 OpenGL 渲染，可在设置 › 界面 › 渲染方式里重新选择")
        runCatching { persist(RenderBackend.OPENGL) }
            .onFailure { AppLogger.w(TAG, "保存降级后的渲染方式失败", it) }
        return RenderBackend.OPENGL
    }

    fun end() {
        runCatching { marker.delete() }
    }
}
