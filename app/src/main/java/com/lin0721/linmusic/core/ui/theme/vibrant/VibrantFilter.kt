package com.lin0721.linmusic.core.ui.theme.vibrant

// node-vibrant 默认注册的 "default" filter（见 packages/node-vibrant/src/pipeline/index.ts）：
// 排除透明和纯白像素，不排除黑
internal fun defaultVibrantFilter(r: Int, g: Int, b: Int, a: Int): Boolean =
    a >= 125 && !(r > 250 && g > 250 && b > 250)
