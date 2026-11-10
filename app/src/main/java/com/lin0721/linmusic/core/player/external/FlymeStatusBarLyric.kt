package com.lin0721.linmusic.core.player.external

import android.app.Notification
import android.os.Build
import androidx.annotation.DrawableRes

// 魅族状态栏歌词适配，见 https://open.flyme.cn/docs?id=239

// Flyme 私有的两个通知 flag
internal data class FlymeTickerFlags(
    val alwaysShowTicker: Int,
    val onlyUpdateTicker: Int
)

private const val FLYME_MANUFACTURER = "meizu"
private const val FLYME_TICKER_ICON_KEY = "ticker_icon"
private const val FLYME_TICKER_ICON_SWITCH_KEY = "ticker_icon_switch"

// AOSP 没有这两个常量；Android 9+ 隐藏 API 反射可能被拦，真魅族机上用此数值兜底
internal const val FLYME_FLAG_ALWAYS_SHOW_TICKER_FALLBACK = 0x01000000
internal const val FLYME_FLAG_ONLY_UPDATE_TICKER_FALLBACK = 0x02000000

internal fun isFlymeDevice(manufacturer: String?, brand: String?): Boolean =
    listOf(manufacturer, brand).any { it?.trim()?.equals(FLYME_MANUFACTURER, ignoreCase = true) == true }

// 反射失败可能只是被拦，不能据此否定支持，改按厂商名兜底
internal fun resolveFlymeTickerFlags(
    reflected: FlymeTickerFlags?,
    manufacturer: String?,
    brand: String?
): FlymeTickerFlags? {
    if (reflected != null) return reflected
    if (!isFlymeDevice(manufacturer, brand)) return null
    return FlymeTickerFlags(FLYME_FLAG_ALWAYS_SHOW_TICKER_FALLBACK, FLYME_FLAG_ONLY_UPDATE_TICKER_FALLBACK)
}

// 暂停时不写，否则最后一行会一直停在状态栏
internal fun resolveFlymeTickerText(
    supported: Boolean,
    enabled: Boolean,
    playing: Boolean,
    line: String?
): String? {
    if (!supported || !enabled || !playing) return null
    return line?.trim()?.takeIf { it.isNotEmpty() }
}

object FlymeStatusBarLyric {

    // 适配说明要求反射只做一次
    private val flags: FlymeTickerFlags? by lazy {
        resolveFlymeTickerFlags(readReflectedFlags(), Build.MANUFACTURER, Build.BRAND)
    }

    val isSupported: Boolean
        get() = flags != null

    // ticker 文本需在 build 前用 setTicker 写入
    fun applyTo(notification: Notification, @DrawableRes iconRes: Int) {
        val f = flags ?: return
        notification.flags = notification.flags or f.alwaysShowTicker or f.onlyUpdateTicker
        notification.extras.putInt(FLYME_TICKER_ICON_KEY, iconRes)
        notification.extras.putBoolean(FLYME_TICKER_ICON_SWITCH_KEY, false)
    }

    private fun readReflectedFlags(): FlymeTickerFlags? = runCatching {
        val cls = Notification::class.java
        FlymeTickerFlags(
            alwaysShowTicker = cls.getField("FLAG_ALWAYS_SHOW_TICKER").getInt(null),
            onlyUpdateTicker = cls.getField("FLAG_ONLY_UPDATE_TICKER").getInt(null)
        )
    }.getOrNull()
}
