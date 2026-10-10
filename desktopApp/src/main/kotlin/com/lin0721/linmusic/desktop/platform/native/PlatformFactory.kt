package com.lin0721.linmusic.desktop.platform.native

// 平台实现类的实例化入口，供 KSP 生成的 Koin 模块调用。
//
// 为什么需要反射：KSP 生成代码时只知道实现类的全限定名（字符串），无法在生成期直接 new。
// 这些类都约定为无参构造（处理器会校验），因此反射调用开销极小，且只在启动注册时发生一次。
object PlatformFactory {

    private val cache = HashMap<String, Any>()

    // 按全限定名创建实现实例；同一类只构造一次
    @Suppress("UNCHECKED_CAST")
    fun <T> create(qualifiedName: String): T = synchronized(cache) {
        cache.getOrPut(qualifiedName) {
            val clazz = Class.forName(qualifiedName, true, PlatformFactory::class.java.classLoader)
            clazz.getDeclaredConstructor().newInstance()
        } as T
    }
}
