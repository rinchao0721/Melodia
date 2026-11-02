# 只做裁剪，不混淆不优化，降低反射类代码被误伤的风险
-dontobfuscate
-dontoptimize
-dontnote **
-dontwarn **

# Retrofit：接口方法与参数注解在运行时反射读取
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleParameterAnnotations, KotlinMetadata
-keep,allowshrinking interface * { @retrofit2.http.* <methods>; }
-keep class retrofit2.** { *; }
-keep class kotlin.coroutines.Continuation

# kotlinx.serialization：生成的 $serializer 与 Companion.serializer() 由反射查找
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    *** Companion;
    static **$* *;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class **$$serializer { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }

# JNA：Library 接口方法按名称绑定 C 函数，Structure 字段按声明顺序映射内存
-keep class com.sun.jna.** { *; }
-keep interface * extends com.sun.jna.Library { *; }
-keep class * extends com.sun.jna.Structure { *; }

# 协程主线程调度器通过 ServiceLoader 加载
-keep class kotlinx.coroutines.swing.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keep class * implements kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class * implements kotlinx.coroutines.CoroutineExceptionHandler { *; }

# Coil 网络拉取组件通过 ServiceLoader 注册
-keep class coil3.network.okhttp.** { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }

# OkHttp / BouncyCastle 的平台探测与算法提供者
-keep class okhttp3.internal.platform.** { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }

# DataStore 偏好序列化依赖 protobuf 反射
-keep class androidx.datastore.preferences.protobuf.** { *; }
-keep class androidx.datastore.preferences.PreferencesProto** { *; }

# 应用自身的数据模型较多且体积小，整体保留，避免 JSON 字段被裁
-keep class com.lin0721.linmusic.** { *; }

# GraalJS / Truffle：语言与引擎实现通过 ServiceLoader 和反射加载，不能裁剪
-keep class org.graalvm.** { *; }
-keep class com.oracle.truffle.** { *; }
-keep class com.oracle.js.** { *; }
-keep class com.ibm.icu.** { *; }

# 暴露给 LX 插件沙盒脚本的宿主接口，成员由 Export 注解反射访问
-keep class com.lin0721.linmusic.desktop.plugin.LxHostBridge { *; }

# 压缩包解析（zip/7z）：编解码器与压缩方法按名称查找，不能裁剪
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }

# 音频标签读写：枚举 values() 与帧类型按反射访问，裁剪后静态初始化会失败
-keep class org.jaudiotagger.** { *; }
