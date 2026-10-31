package com.lin0721.linmusic.core.source

// LX 插件脚本运行时，Android 用 WebView 实现，桌面端用 GraalJS 实现
interface LxPluginRuntime {

    // 从远程 URL 下载脚本内容
    suspend fun downloadScript(url: String): Result<String>

    // 在临时沙盒中运行脚本并提取插件信息
    suspend fun loadAndVerifyScript(scriptText: String): Result<LxPluginInfo>

    // 激活并注册插件沙盒
    suspend fun activatePlugin(item: LxPluginItem): Boolean

    fun deactivatePlugin(pluginId: String)

    // 让活跃沙盒与已启用的插件列表保持一致
    suspend fun syncActivePlugins(items: List<LxPluginItem>)

    fun getSupportedSources(pluginId: String): List<String>

    suspend fun resolveMusicUrl(
        pluginId: String,
        source: String,
        songId: String,
        songName: String,
        singer: String,
        albumName: String,
        durationMs: Long,
        quality: String,
        // 单次解析上限，由调用方按用户设置传入（默认 12s，宽松模式 30s）
        timeoutMs: Long = LxScriptSupport.RESOLVE_TIMEOUT_MS
    ): String?

    // 销毁全部沙盒
    fun reset()
}
