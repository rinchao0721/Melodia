package com.lin0721.linmusic.desktop.app

import com.lin0721.linmusic.core.log.AppLogger
import com.sun.management.HotSpotDiagnosticMXBean
import java.lang.management.ManagementFactory

private const val TAG = "HeapTrim"
private const val MIN_FREE = "MinHeapFreeRatio"
private const val MAX_FREE = "MaxHeapFreeRatio"

// 后台时调低堆空闲比例，回收后把空闲堆还给系统；前台恢复原值，避免频繁扩缩堆影响流畅度
object HeapTrim {
    private const val BACKGROUND_MIN_FREE = "10"
    private const val BACKGROUND_MAX_FREE = "30"

    private val bean: HotSpotDiagnosticMXBean? by lazy {
        try {
            ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean::class.java)
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "运行时缺少 jdk.management，后台缩堆不可用", e)
            null
        }
    }
    private val defaults: Pair<String, String>? by lazy {
        bean?.let { runCatching { it.getVMOption(MIN_FREE).value to it.getVMOption(MAX_FREE).value }.getOrNull() }
    }

    @Volatile
    private var shrunk = false

    fun shrink() {
        if (apply(BACKGROUND_MIN_FREE, BACKGROUND_MAX_FREE)) shrunk = true
    }

    // 未缩过就不碰，免得启动时无谓加载管理接口
    fun restore() {
        if (!shrunk) return
        defaults?.let { (min, max) -> if (apply(min, max)) shrunk = false }
    }

    // 两个比例要求 min <= max，按先放宽后收紧的顺序设置，任一时刻都不越界
    private fun apply(min: String, max: String): Boolean {
        val target = bean ?: return false
        if (defaults == null) return false
        return try {
            val currentMax = target.getVMOption(MAX_FREE).value.toInt()
            if (max.toInt() >= currentMax) {
                target.setVMOption(MAX_FREE, max)
                target.setVMOption(MIN_FREE, min)
            } else {
                target.setVMOption(MIN_FREE, min)
                target.setVMOption(MAX_FREE, max)
            }
            true
        } catch (e: RuntimeException) {
            AppLogger.w(TAG, "调整堆空闲比例失败", e)
            false
        }
    }
}
