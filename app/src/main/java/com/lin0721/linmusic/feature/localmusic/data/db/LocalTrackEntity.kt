package com.lin0721.linmusic.feature.localmusic.data.db

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lin0721.linmusic.feature.localmusic.data.scan.fallbackLocalQualityLevel
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource

// MediaStore 条目与 SAF 导入条目共用一张表，以 uri 为主键
@Entity(
    tableName = "local_track",
    indices = [Index("artist"), Index("album")]
)
data class LocalTrackEntity(
    @PrimaryKey val uri: String,
    val mediaStoreId: Long?,
    val songId: Long?,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val path: String?,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    val source: String,
    val albumArtist: String? = null,
    val year: Int? = null,
    // MediaStore 编码：碟号 * 1000 + 音轨号
    val trackNumber: Int? = null,
    // 由文件音频属性或扩展名推断的音质等级（standard/higher/exhigh/lossless/hires）
    val qualityLevel: String? = null
)

// 导入条目没有 MediaStore id，用 uri 哈希生成稳定的非零占位 id，播放队列靠它的相反数区分本地曲目
private fun LocalTrackEntity.stableId(): Long =
    mediaStoreId ?: uri.hashCode().toLong().let { if (it == 0L) 1L else it }

fun LocalTrackEntity.toDomain(): LocalTrack = LocalTrack(
    mediaStoreId = stableId(),
    songId = songId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    uri = Uri.parse(uri),
    path = path,
    dateAddedMs = dateAddedMs,
    source = LocalTrackSource.entries.firstOrNull { it.name == source } ?: LocalTrackSource.EXTERNAL,
    albumArtist = albumArtist,
    year = year,
    trackNumber = trackNumber,
    qualityLevel = qualityLevel?.takeIf { it.isNotBlank() }
        ?: fallbackLocalQualityLevel(path, uri, sizeBytes, durationMs)
)
