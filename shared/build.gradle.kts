plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.lin0721.linmusic.shared"
        compileSdk = 36
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    // 与 desktopApp 一致；未指定时会跟随编译用 JDK，产出安装包内置运行时无法加载的高版本字节码
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.okhttp)
            api(libs.okhttp.logging)
            api(libs.retrofit)
            api(libs.retrofit.kotlinx.serialization)
            api(libs.kotlinx.serialization.json)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            api(libs.androidx.datastore.preferences.core)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.zxing.core)
            implementation(libs.bouncycastle)
            implementation(libs.commons.compress)
            implementation(libs.xz)
            // Android 自带 org.json，只在编译期引用
            compileOnly(libs.org.json)
            // 分享卡片排版两端共用
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.org.json)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okhttp.mockwebserver)
        }
    }
}
