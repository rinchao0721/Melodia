package com.lin0721.linmusic.feature.cloud.upload

import java.io.InputStream
import java.security.MessageDigest

private const val MD5_BUFFER_SIZE = 8192

data class LocalAudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?
)

// 平台无关的待上传文件
interface UploadSource {
    // 去重键，同一文件多次入队时应一致
    val key: String
    val fileName: String
    val mimeType: String

    fun size(): Long

    fun openStream(): InputStream

    // 读不到的字段返回 null，兜底文案由上传流水线决定
    fun readMetadata(): LocalAudioMetadata
}

// 流式计算 MD5，避免大文件一次性读入内存
fun streamMd5(input: InputStream): String {
    val digest = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(MD5_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

// 参考实现里找不到扩展名时兜底 mp3
fun fileExtension(fileName: String): String {
    val dotIndex = fileName.lastIndexOf('.')
    if (dotIndex < 0 || dotIndex == fileName.lastIndex) return "mp3"
    return fileName.substring(dotIndex + 1).lowercase()
}
