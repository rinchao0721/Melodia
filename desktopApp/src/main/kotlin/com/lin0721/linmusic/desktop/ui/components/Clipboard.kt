package com.lin0721.linmusic.desktop.ui.components

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private var clipboardCopyObserver: ((String) -> Unit)? = null

fun registerClipboardCopyObserver(observer: ((String) -> Unit)?) {
    clipboardCopyObserver = observer
}

fun copyTextToClipboard(text: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}.onSuccess {
    clipboardCopyObserver?.invoke(text)
}.isSuccess

// 复制歌曲信息的统一格式：歌名 - 歌手
fun songInfoText(name: String, artists: String): String =
    if (artists.isBlank()) name else "$name - $artists"

// 网易云单曲页链接
fun songLink(songId: Long): String = "https://music.163.com/song?id=$songId"

// 读取系统剪贴板文本，无文本或读取失败返回空串
fun readTextFromClipboard(): String = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
}.getOrNull().orEmpty()
