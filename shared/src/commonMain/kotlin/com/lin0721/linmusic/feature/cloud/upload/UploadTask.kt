package com.lin0721.linmusic.feature.cloud.upload

import java.util.UUID

enum class UploadStatus {
    QUEUED, CHECKING, ALLOCATING_TOKEN, UPLOADING, REGISTERING, PUBLISHING, SUCCESS, FAILED
}

// 断点续传的真实边界：只有阶段级跳过，没有字节级续传（NOS 上传接口本身是一次性整包提交，
// 不支持分片）。md5/fileSize/metadata/checkedSongId 跨重试缓存复用；token/objectKey 不缓存，
// 每次进入 UPLOADING 前都重新申请，避免使用可能已过期的旧凭证
data class UploadTask(
    val id: String = UUID.randomUUID().toString(),
    val source: UploadSource,
    val fileName: String,
    val progress: Float = 0f,
    val status: UploadStatus = UploadStatus.QUEUED,
    val errorMessage: String? = null,
    val md5: String? = null,
    val fileSize: Long? = null,
    val metadata: LocalAudioMetadata? = null,
    val checkedSongId: String? = null,
    val needUpload: Boolean? = null
) {
    val isActive: Boolean get() = status != UploadStatus.SUCCESS && status != UploadStatus.FAILED
}
