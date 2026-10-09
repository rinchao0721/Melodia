import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.jna)
    implementation(libs.jaudiotagger)
    testImplementation(libs.junit)
}

// 与 Android 端共用发版参数，保证两端版本号一致
val desktopVersion = (findProperty("releaseVersionName") as String?) ?: "1.0.0"

// 调用本机 cmake 编译原生桥接 DLL；缺少 cmake 或 MSVC 时只告警跳过，运行时由代码回退
abstract class CmakeDllTask @Inject constructor(private val execOps: ExecOperations) : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:Internal
    abstract val cmakeBuildDir: DirectoryProperty

    @get:Input
    abstract val dllName: Property<String>

    @get:OutputFile
    abstract val outputDll: RegularFileProperty

    @TaskAction
    fun build() {
        val cmake = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, "cmake.exe") }
            .firstOrNull { it.isFile }
        if (cmake == null) {
            logger.warn("未找到 cmake，跳过 ${dllName.get()} 编译")
            return
        }
        val buildDir = cmakeBuildDir.get().asFile
        val steps = listOf(
            listOf(cmake.absolutePath, "-S", sourceDir.get().asFile.absolutePath, "-B", buildDir.absolutePath),
            listOf(cmake.absolutePath, "--build", buildDir.absolutePath, "--config", "Release")
        )
        for (command in steps) {
            val log = ByteArrayOutputStream()
            val result = execOps.exec {
                commandLine(command)
                standardOutput = log
                errorOutput = log
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                logger.warn("${dllName.get()} 编译失败，已跳过：\n$log")
                return
            }
        }
        File(buildDir, "Release/${dllName.get()}").copyTo(outputDll.get().asFile, overwrite = true)
    }
}

val buildSmtc by tasks.registering(CmakeDllTask::class) {
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
    sourceDir.set(layout.projectDirectory.dir("native-src/smtc"))
    cmakeBuildDir.set(layout.buildDirectory.dir("smtc"))
    dllName.set("melodia_smtc.dll")
    outputDll.set(layout.projectDirectory.file("native/melodia_smtc.dll"))
}

// libmpv 由开发者放在仓库外的 native 目录，打包与运行前同步进 Compose 约定的平台资源目录
val prepareNativeResources by tasks.registering(Sync::class) {
    dependsOn(buildSmtc)
    from(layout.projectDirectory.dir("native")) {
        include("*.dll")
    }
    into(layout.buildDirectory.dir("appResources/windows"))
}

val generateAppInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/appinfo")
    val version = desktopVersion
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        dir.resolve("app-info.properties").writeText("version=$version" + System.lineSeparator())
    }
}

sourceSets.main {
    resources.srcDir(generateAppInfo)
}

// jpackage 需要 JDK 17+ 完整版，由工具链自动下载，不影响日常编译所用 JDK
val packagingJavaHome = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    vendor.set(JvmVendorSpec.ADOPTIUM)
}.map { it.metadata.installationPath.asFile.absolutePath }

compose.desktop {
    application {
        mainClass = "com.lin0721.linmusic.desktop.MainKt"
        javaHome = packagingJavaHome.get()
        // 中文系统默认 GBK，统一日志输出编码
        jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
        // 发布版打包时用 ProGuard 裁掉未使用的类（图标库等体积大头）
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
            obfuscate.set(false)
            optimize.set(false)
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Melodia"
            // MSI 只接受 MAJOR.MINOR.BUILD，beta/rc 的 -beta.1 后缀要去掉，否则配置阶段就会报错
            packageVersion = desktopVersion.substringBefore('-')
            vendor = "Melodia"
            description = "Melodia 音乐播放器"
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
            // jdeps 建议列表 + TLS 椭圆曲线套件所需的 jdk.crypto.ec（jdeps 无法静态分析出）
            modules("java.instrument", "java.naming", "java.prefs", "java.sql", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                iconFile.set(layout.projectDirectory.file("icons/melodia.ico"))
                // 固定值，覆盖安装时据此识别为同一应用并升级
                upgradeUuid = "6f0b8a52-3c1e-4d7a-9b25-8e4c2f1d7a93"
                perUserInstall = true
                menu = true
                menuGroup = "Melodia"
                shortcut = true
                dirChooser = false
            }
        }
    }
}

// 运行与所有打包任务都先同步 DLL，避免产物沿用旧版本
afterEvaluate {
    tasks.matching { task ->
        task.name == "run" || task.name == "prepareAppResources" ||
            task.name.startsWith("createDistributable") || task.name.startsWith("createReleaseDistributable") ||
            (task.name.startsWith("package") && task.name != "packageReleaseZip")
    }.configureEach { dependsOn(prepareNativeResources) }
    // gradle run 视为调试环境
    tasks.named<JavaExec>("run") { jvmArgs("-Dmelodia.debug=true") }
}

// 免安装版：把发布版程序目录打成 zip
val packageReleaseZip by tasks.registering(Zip::class) {
    dependsOn("createReleaseDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main-release/app"))
    archiveFileName.set("Melodia-$desktopVersion-windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main-release/zip"))
}
