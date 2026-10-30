package com.lin0721.linmusic.core.plugin

import kotlinx.serialization.json.JsonElement

// 脚本发往宿主的消息；沙盒内通过全局 MelodiaHostBridge.post / invokeSync 调用，回调线程不固定
interface ScriptHost {
    fun onMessage(method: String, payload: String)

    // 同步调用只用于纯计算（如 md5），不能做 IO
    fun onSyncCall(method: String, payload: String): String = ""
}

// 单个隔离的 JS 沙盒：只负责执行脚本与双向消息，协议由上层注入的垫片脚本定义
interface ScriptSandbox {

    // 在全局作用域执行脚本，脚本抛错时返回失败
    suspend fun evaluate(script: String): Result<Unit>

    // 调用全局函数，参数按 JSON 字面量传入；沙盒已关闭时忽略
    suspend fun call(function: String, vararg args: JsonElement)

    fun close()
}

// Android 用 WebView 实现，桌面端用 GraalJS 实现
interface ScriptSandboxFactory {

    // prelude 在沙盒创建后、任何脚本之前执行；沙盒内另有 console 与 setTimeout
    suspend fun create(name: String, host: ScriptHost, prelude: String): ScriptSandbox
}
