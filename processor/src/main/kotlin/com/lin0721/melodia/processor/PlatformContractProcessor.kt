package com.lin0721.melodia.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

// 平台契约处理器：编译期校验 + 自动生成 Koin 注册模块。
//
// 目标：新增平台能力接口时，只需
//   1. 新建接口文件并标 @RequireAllPlatforms
//   2. 各平台实现类标 @PlatformImpl / @PlatformStub
// 其余（校验齐全性、生成单例注册）全部自动完成，无需再改任何手写代码。
//
// 校验两件事：
//   1. 平台覆盖度：标了 @RequireAllPlatforms 的接口，每个 DesktopPlatform 枚举值都要有实现
//   2. 生成物契约：实现类必须有无参构造（平台工厂靠反射实例化）
class PlatformContractProcessor(
    private val env: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private var done = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (done) return emptyList()
        done = true

        val contractInterfaces = resolver.getSymbolsWithAnnotation(REQUIRE_ALL_PLATFORMS)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == com.google.devtools.ksp.symbol.ClassKind.INTERFACE }
            .toList()

        // 其他模块没有该注解，不检查也不生成
        if (contractInterfaces.isEmpty()) return emptyList()

        val implSymbols = resolver.getSymbolsWithAnnotation(PLATFORM_IMPL).filterIsInstance<KSClassDeclaration>().toList()
        val stubSymbols = resolver.getSymbolsWithAnnotation(PLATFORM_STUB).filterIsInstance<KSClassDeclaration>().toList()

        val contractFqns = contractInterfaces.mapNotNull { it.qualifiedName?.asString() }.toSet()
        // 接口 FQN -> 平台 -> 实现信息
        val implByInterface = mutableMapOf<String, MutableMap<String, PlatformModuleGenerator.ImplInfo>>()
        val stubNotes = mutableListOf<String>()

        fun record(symbol: KSClassDeclaration, platform: String, isStub: Boolean, reason: String?) {
            val implFqn = symbol.qualifiedName?.asString() ?: return
            // 契约：实现类必须有无参构造，否则反射实例化会失败
            if (symbol.primaryConstructor?.parameters?.isNotEmpty() == true) {
                env.logger.error(
                    "${symbol.simpleName.asString()} 必须提供无参构造，平台工厂需要它来实例化",
                    symbol,
                )
            }
            for (superType in symbol.superTypes) {
                val ifaceFqn = superType.resolve().declaration.qualifiedName?.asString() ?: continue
                if (ifaceFqn !in contractFqns) continue
                implByInterface.getOrPut(ifaceFqn) { mutableMapOf() }[platform] =
                    PlatformModuleGenerator.ImplInfo(implFqn, isStub)
                if (isStub) {
                    stubNotes += "  ${symbol.simpleName.asString()} · $platform" +
                        (reason?.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "")
                }
            }
        }

        implSymbols.forEach { symbol ->
            val platform = platformOf(symbol, "PlatformImpl")
            if (platform == null) env.logger.error("${symbol.simpleName.asString()} 的 @PlatformImpl 缺少 platform 参数", symbol)
            else record(symbol, platform, isStub = false, reason = null)
        }
        stubSymbols.forEach { symbol ->
            val platform = platformOf(symbol, "PlatformStub")
            if (platform == null) env.logger.error("${symbol.simpleName.asString()} 的 @PlatformStub 缺少 platform 参数", symbol)
            else record(symbol, platform, isStub = true, reason = stringArg(symbol, "PlatformStub", "reason"))
        }

        // 1. 平台覆盖度
        for (iface in contractInterfaces) {
            val covered = implByInterface[iface.qualifiedName?.asString()].orEmpty().keys
            val missing = ALL_PLATFORMS.filter { it !in covered }
            if (missing.isNotEmpty()) {
                env.logger.error(
                    "接口 ${iface.simpleName.asString()} 缺少以下平台的实现：${missing.joinToString()}。" +
                        "请新增实现类并标注 @PlatformImpl(DesktopPlatform.<平台>) 或 @PlatformStub(DesktopPlatform.<平台>, \"原因\")",
                    iface,
                )
            }
        }

        // 生成 Koin 模块（覆盖度不全时也会生成，但编译已因上面的 error 失败）
        PlatformModuleGenerator(env.codeGenerator, env.logger).generate(contractInterfaces, implByInterface)

        if (stubNotes.isNotEmpty()) {
            env.logger.warn(
                "平台契约：${stubNotes.size} 项仍为占位实现（功能待补齐）\n" + stubNotes.joinToString("\n"),
            )
        }

        return emptyList()
    }

    private fun platformOf(symbol: KSClassDeclaration, annotationSimpleName: String): String? {
        val arg = symbol.annotations
            .firstOrNull { it.shortName.asString() == annotationSimpleName }
            ?.arguments?.firstOrNull()?.value
        return when (arg) {
            is KSClassDeclaration -> arg.simpleName.asString()
            is KSType -> arg.declaration.simpleName.asString()
            else -> null
        }
    }

    private fun stringArg(symbol: KSClassDeclaration, annotationSimpleName: String, key: String): String? =
        symbol.annotations.firstOrNull { it.shortName.asString() == annotationSimpleName }
            ?.arguments?.firstOrNull { it.name?.asString() == key }?.value as? String

    companion object {
        private const val PKG = "com.lin0721.linmusic.desktop.platform.native"
        const val REQUIRE_ALL_PLATFORMS = "$PKG.RequireAllPlatforms"
        const val PLATFORM_IMPL = "$PKG.PlatformImpl"
        const val PLATFORM_STUB = "$PKG.PlatformStub"
        // 与 DesktopPlatform 枚举保持一致（暂不含 macOS）
        val ALL_PLATFORMS = listOf("WINDOWS", "LINUX")
    }
}

class PlatformContractProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        PlatformContractProcessor(environment)
}
