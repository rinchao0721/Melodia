package com.lin0721.linmusic.core.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

// 仅在线链接走播放缓存，本地/已下载文件直读，避免被重复拷进缓存
@UnstableApi
class NetworkCacheRoutingDataSource(
    private val cached: DataSource,
    private val direct: DataSource
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        direct.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "数据源已打开" }
        val scheme = dataSpec.uri.scheme?.lowercase()
        val target = if (scheme == "http" || scheme == "https") cached else direct
        // 先记录再 open，open 抛异常时 close() 仍能关到它
        active = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val source = active ?: throw IllegalStateException("数据源未打开")
        return source.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = active ?: return
        active = null
        source.close()
    }
}
