package com.lin0721.linmusic.core.deeplink

import android.content.Context

// 记住「已经问过的剪贴板内容」：包括自己复制出去的分享链接，
// 这样下次打开应用不会再对着自己刚复制的链接问一遍
object ClipboardLinkPrefs {

    private const val PREFS = "song_link"
    private const val KEY = "last_clipboard_link"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun lastHandled(): String? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, null)

    fun markHandled(text: String) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY, text)
            ?.apply()
    }
}
