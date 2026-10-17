package com.lin0721.linmusic.feature.localmusic.data.scan

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.kyant.taglib.TagLib
import com.lin0721.linmusic.feature.localmusic.domain.albumTitleOf

/** 仅核实 MediaStore 的同名文件夹占位，不读取封面、不写入音频。 */
internal class MediaStoreAlbumReader(private val context: Context) {
    fun readAlbum(uri: Uri): String? = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        // TagLib 需要随机访问；管道等不可 seek 的 provider 由调用方按原规则降级。
        Os.lseek(pfd.fileDescriptor, 0L, OsConstants.SEEK_CUR)
        // TagLib 接管并关闭传入的 fd，外层 pfd 由 use 关闭。
        val metadata = pfd.dup().use { duplicate ->
            val fd = duplicate.detachFd()
            try {
                TagLib.getMetadata(fd, readPictures = false)
            } catch (error: LinkageError) {
                // 加载 native 库或解析 JNI 方法失败时尚未接管 fd；仅此路径由调用方回收。
                runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
                throw error
            }
        }
        metadata?.propertyMap?.get("ALBUM")?.firstNotNullOfOrNull { albumTitleOf(it) }
    }
}
