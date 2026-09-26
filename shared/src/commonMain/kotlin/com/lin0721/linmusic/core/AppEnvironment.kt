package com.lin0721.linmusic.core

// 平台启动时写入的运行环境信息，共享层据此区分调试/发布行为
object AppEnvironment {
    @Volatile
    var isDebug: Boolean = false
}
