package com.lin0721.linmusic.core.player

import androidx.media3.common.Player

// 保证同一监听在任一时刻只挂在一个播放器上，Media3 的监听列表不去重
internal class PlayerListenerBinding {

    var listener: Player.Listener? = null
        private set

    var target: Player? = null
        private set

    fun setListener(newListener: Player.Listener?) {
        if (listener === newListener) return
        unbind()
        listener = newListener
    }

    // 传 null 表示摘下；目标未变时不重复挂载
    fun bindTo(player: Player?) {
        val current = listener
        val desired = if (current != null) player else null
        if (target === desired) return
        unbind()
        if (desired != null && current != null) {
            desired.addListener(current)
            target = desired
        }
    }

    fun unbind() {
        val current = listener
        val bound = target
        if (bound != null && current != null) bound.removeListener(current)
        target = null
    }
}
