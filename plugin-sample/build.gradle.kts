plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
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
    // 运行时由宿主提供，不打进插件
    compileOnly(project(":plugin-api"))
    compileOnly(compose.material3)
}

tasks.jar {
    archiveFileName.set("plugin.jar")
}

// 产物在 build/plugin，可直接在设置里导入
val packagePlugin by tasks.registering(Zip::class) {
    from(tasks.jar)
    from(layout.projectDirectory.file("plugin.json"))
    archiveFileName.set("sample-native.melodiaplugin")
    destinationDirectory.set(layout.buildDirectory.dir("plugin"))
}
