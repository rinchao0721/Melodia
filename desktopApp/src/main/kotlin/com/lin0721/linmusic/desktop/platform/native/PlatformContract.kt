package com.lin0721.linmusic.desktop.platform.native

// 平台能力的编译期契约，由 KSP 处理器（processor 模块）校验并生成注册代码。
//
// 处理器做两件事：
//   1. 校验平台覆盖度：@RequireAllPlatforms 标注的接口，DesktopPlatform 每个枚举值都必须有实现
//   2. 校验实现契约：实现类必须有无参构造（平台工厂靠反射实例化）
//
// 违反任一条都会在编译期直接报错，避免"新增平台忘写实现"拖到运行期才暴露。
// 校验通过后，处理器会把泛型正确的 Koin 注册代码生成到 di/generated/GeneratedPlatformModule.kt。

// 标注在平台能力接口上：声明该接口必须被所有 DesktopPlatform 覆盖，并自动纳入生成的 Koin 模块
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RequireAllPlatforms

// 标注真实实现：声明该实现服务于哪个平台
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PlatformImpl(val platform: DesktopPlatform)

// 标注占位实现：功能尚未落地（如 Linux 的 MPRIS），处理器放行但会在编译日志中汇总提示。
// 与 @PlatformImpl 互斥，用于区分"真的做完了"和"先占个坑"。
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PlatformStub(val platform: DesktopPlatform, val reason: String)

