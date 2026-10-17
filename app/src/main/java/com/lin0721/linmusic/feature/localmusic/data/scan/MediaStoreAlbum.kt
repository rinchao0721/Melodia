package com.lin0721.linmusic.feature.localmusic.data.scan

import com.lin0721.linmusic.feature.localmusic.domain.albumTitleOf

/**
 * MediaStore 可能用文件夹名代填缺失的 ALBUM，单凭二者同名无法判断是否有真实标签。
 * 只对这类歧义条目读取嵌入标签；普通条目沿用系统结果，不增加文件 I/O。
 */
internal fun resolveMediaStoreAlbum(
    systemAlbum: String?,
    folderPath: String?,
    readEmbeddedAlbum: () -> String?
): String? {
    val album = albumTitleOf(systemAlbum) ?: return null
    if (album != folderPath?.substringAfterLast('/')) return album

    return try {
        // 文件中有 ALBUM 才保留，真实标签也可能与尚未更新的系统结果不同。
        albumTitleOf(readEmbeddedAlbum())
    } catch (_: Exception) {
        // 无权限、文件损坏或不支持的 provider，按原有无专辑规则处理，不中止整库扫描。
        null
    } catch (_: LinkageError) {
        // native 库不可用时仍保留原有扫描能力。
        null
    }
}
