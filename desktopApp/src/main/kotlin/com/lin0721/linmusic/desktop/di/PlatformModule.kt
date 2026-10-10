package com.lin0721.linmusic.desktop.di

import com.lin0721.linmusic.desktop.di.generated.generatedPlatformModule
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import org.koin.dsl.module

// 平台能力注册：全部由 KSP 生成的 generatedPlatformModule 提供。
//
// 新增平台能力接口时只需：新建接口文件标 @RequireAllPlatforms + 各平台实现标 @PlatformImpl/@PlatformStub。
// 本文件无需改动 —— 注册项由处理器自动生成，平台覆盖度也在编译期强制校验。
val platformModule = module {
    includes(generatedPlatformModule)
}

// 供生成代码引用的当前平台。判定逻辑统一收敛在 DesktopPlatform.current。
val currentPlatform: DesktopPlatform get() = DesktopPlatform.current
