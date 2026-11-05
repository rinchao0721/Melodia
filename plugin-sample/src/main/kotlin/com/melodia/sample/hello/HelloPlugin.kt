package com.melodia.sample.hello

import com.lin0721.linmusic.plugin.api.MelodiaPlugin
import com.lin0721.linmusic.plugin.api.NowPlayingSong
import com.lin0721.linmusic.plugin.api.PlaybackListener
import com.lin0721.linmusic.plugin.api.PluginContext
import kotlinx.coroutines.launch

class HelloPlugin : MelodiaPlugin {

    override fun onLoad(context: PluginContext) {
        context.extensions.command("hello") {
            val count = (context.storage.get(KEY_COUNT)?.toIntOrNull() ?: 0) + 1
            context.storage.set(KEY_COUNT, count.toString())
            val song = context.player.current()?.title ?: "没有在播放"
            context.ui.toast("第 $count 次问好，当前：$song")
        }
        context.extensions.playbackListener(object : PlaybackListener {
            override fun onSongChanged(song: NowPlayingSong?) {
                context.scope.launch { context.logger.info("切歌：${song?.title}") }
            }
        })
        context.extensions.backdropSource("aurora", AuroraBackdrop())
        context.extensions.composePage("counter") { CounterPage() }
        context.extensions.composePage("spectrum") { SpectrumPage(context.spectrum) }
        context.scope.launch {
            val starts = (context.storage.get(KEY_STARTS)?.toIntOrNull() ?: 0) + 1
            context.storage.set(KEY_STARTS, starts.toString())
            context.logger.info("示例插件第 $starts 次启动")
        }
    }

    private companion object {
        const val KEY_COUNT = "count"
        const val KEY_STARTS = "starts"
    }
}
