package com.lin0721.linmusic.core.plugin

import kotlinx.coroutines.flow.StateFlow

// 桌面端的原生插件层，Android 没有实现。只在启动时加载，停用与删除立即注销扩展，类要等重启才释放
interface NativePluginHost {
    val status: StateFlow<Map<String, PluginRuntimeStatus>>

    fun setFailureHandler(handler: suspend (pluginId: String, reason: String) -> Unit)

    fun isActive(pluginId: String): Boolean

    suspend fun sync(plugins: List<InstalledPlugin>, allowed: Boolean)

    suspend fun unload(pluginId: String)
}
