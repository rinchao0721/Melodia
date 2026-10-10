package com.lin0721.melodia.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

// 根据平台契约注解，生成 Koin 模块源码。
//
// 生成物形如：
//   internal val generatedPlatformModule = module {
//       single<SystemMediaSession> { PlatformFactory.create(DesktopPlatform.WINDOWS, SystemMediaSession::class) as SystemMediaSession }
//       ...
//   }
//
// 为什么生成泛型调用而不是运行时反射注册：
//   Koin 的 single<T> 依赖编译期类型参数，且 saveMapping/indexPrimaryType 等运行时注册入口是 internal。
//   因此唯一可行路径是把"泛型正确"的调用写进生成代码，反射只用于实例化实现类。
internal class PlatformModuleGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) {
    fun generate(
        contractInterfaces: List<KSClassDeclaration>,
        implByInterface: Map<String, Map<String, ImplInfo>>,
    ) {
        if (contractInterfaces.isEmpty()) return

        val pkg = "com.lin0721.linmusic.desktop.di.generated"
        val fileName = "GeneratedPlatformModule"

        val body = buildString {
            appendLine("// 由 KSP 自动生成，请勿手动修改。")
            appendLine("// 新增平台能力接口时无需改动本文件：只要接口标 @RequireAllPlatforms、各平台实现标 @PlatformImpl/@PlatformStub，")
            appendLine("// 处理器会在下次编译时重新生成本模块。")
            appendLine("package $pkg")
            appendLine()
            appendLine("import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform")
            appendLine("import com.lin0721.linmusic.desktop.platform.native.PlatformFactory")
            appendLine("import com.lin0721.linmusic.desktop.di.currentPlatform")
            appendLine("import org.koin.dsl.module")
            contractInterfaces
                .mapNotNull { it.qualifiedName?.asString() }
                .sorted()
                .forEach { appendLine("import $it") }
            appendLine()
            appendLine("internal val generatedPlatformModule = module {")
            contractInterfaces.forEach { iface ->
                val fqn = iface.qualifiedName?.asString() ?: return@forEach
                val simple = iface.simpleName.asString()
                val impls = implByInterface[fqn].orEmpty()
                    .filterKeys { it != "MACOS" }
                if (impls.isEmpty()) {
                    logger.warn("接口 $fqn 无任何平台实现，跳过生成", iface)
                    return@forEach
                }
                // 生成时用 when(platform) 逐个分支，缺某平台时由编译期校验提前报错
                appendLine("    single<$simple> {")
                appendLine("        when (currentPlatform) {")
                impls.entries.sortedBy { it.key }.forEach { (platform, info) ->
                    appendLine("            DesktopPlatform.$platform -> PlatformFactory.create(\"${info.fqn}\") as $simple")
                }
                appendLine("            else -> error(\"未支持平台: \" + currentPlatform)")
                appendLine("        }")
                appendLine("    }")
            }
            appendLine("}")
        }

        val deps = Dependencies(
            aggregating = true,
            *contractInterfaces.mapNotNull { it.containingFile }.toTypedArray(),
        )
        codeGenerator.createNewFile(deps, pkg, fileName, "kt").bufferedWriter().use { it.write(body) }
        logger.info("已生成 $pkg.$fileName（${contractInterfaces.size} 个平台接口）")
    }

    // 实现类信息：全限定名 + 是否占位
    data class ImplInfo(val fqn: String, val isStub: Boolean)

    companion object {
        fun isInterface(decl: KSClassDeclaration) = decl.classKind == ClassKind.INTERFACE

        fun platformOf(decl: KSClassDeclaration, annotationSimpleName: String): String? {
            val arg = decl.annotations
                .firstOrNull { it.shortName.asString() == annotationSimpleName }
                ?.arguments?.firstOrNull()?.value
            return when (arg) {
                is KSClassDeclaration -> arg.simpleName.asString()
                is KSType -> arg.declaration.simpleName.asString()
                else -> null
            }
        }
    }
}
