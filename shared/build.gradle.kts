plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
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
    jvm("desktop")

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
            implementation(libs.bouncycastle)
            // Android 自带 org.json，只在编译期引用
            compileOnly(libs.org.json)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.org.json)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
