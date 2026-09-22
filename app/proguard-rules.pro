# Melodia R8 混淆规则
#
# 本项目对混淆敏感的只有两处：Retrofit 接口（动态代理在运行时读注解）与
# kotlinx.serialization 的 DTO（依赖编译期生成的 serializer）。二者失效的表现
# 都是接口静默解析失败而非崩溃，因此规则写显式声明，不单纯依赖各库自带的
# consumer rules。

# ===== 崩溃栈可读性 =====
# 项目内置 CrashHandler 收集崩溃，保留行号否则上报的堆栈无法定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== 泛型签名与运行时注解 =====
# Retrofit 解析 suspend 方法的返回类型、kotlinx.serialization 读取 @SerialName
# 均依赖这些属性，剥掉会导致运行时解析失败
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ===== Retrofit 接口 =====
# 接口本身由 Proxy 实现，方法签名与 @POST/@Body/@Path 注解必须完整保留
-keep,allowobfuscation interface com.lin0721.linmusic.core.api.NeteaseApiService { *; }
-keep,allowobfuscation interface com.lin0721.linmusic.**.data.*Api { *; }
-keep,allowobfuscation interface com.lin0721.linmusic.core.songlike.SongLikeApi { *; }
-keep,allowobfuscation interface com.lin0721.linmusic.core.userplaylist.UserPlaylistApi { *; }
-keep,allowobfuscation interface com.lin0721.linmusic.core.userartist.UserArtistApi { *; }

# suspend 方法的 Continuation 参数不能被裁剪
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ===== kotlinx.serialization =====
# 编译期生成的 serializer 与 Companion 是反序列化入口，被裁剪后 DTO 无法构造
-keepclassmembers class com.lin0721.linmusic.** {
    *** Companion;
}
-keepclasseswithmembers class com.lin0721.linmusic.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.lin0721.linmusic.**
-keepclassmembers class com.lin0721.linmusic.<1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class com.lin0721.linmusic.**
-keep class com.lin0721.linmusic.<1>$$serializer { *; }

# ===== 网易云加密 =====
# NeteaseCrypto 走标准 javax.crypto，无反射，无需 keep；此处仅声明以备查

# XeapiCrypto 用 BouncyCastleProvider 做 X25519 密钥协商，BC 内部通过字符串反射
# 注册 SPI 实现类，不 keep 会被 R8 当死代码裁掉，导致 release 包
# getInstance("X25519", bcProvider) 抛 NoSuchAlgorithmException（评论等 xeapi
# 接口必崩，仅正式混淆包复现，debug 包无法复现）
-keep class org.bouncycastle.** { *; }
-keepclassmembers class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ===== Media3 / Coil / Koin =====
# 三者均随包提供 consumer rules：Media3 保留 Player 相关回调，Coil 保留解码器，
# Koin 的构造函数 DSL（::X 函数引用）为编译期解析，均无需额外声明

# ===== Room & WorkManager =====
# WorkManager 内部使用 Room 保存作业状态，Room 依赖反射调用数据库实现类的无参构造函数。
# 在开启 R8 代码优化 (proguard-android-optimize.txt) 后，未显式 keep 的构造函数会被内联或裁减，
# 导致 WorkManager.initialize 时抛出 NoSuchMethodException: WorkDatabase_Impl.<init> [] 崩溃
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.impl.**

# Worker 构造函数保护
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ===== Android Parcelable & AIDL =====
# 跨进程传输时依赖 CREATOR 反射实例化
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
-keep class com.hchen.superlyricapi.** { *; }
