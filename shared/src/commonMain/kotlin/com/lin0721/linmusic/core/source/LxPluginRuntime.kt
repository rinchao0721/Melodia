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

    // 删除插件时清掉沙盒留在本地的数据
    suspend fun clearData(pluginId: String) {}

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
        quality: String
    ): String?

    // 销毁全部沙盒
    fun reset()
}
