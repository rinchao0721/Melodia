package com.lin0721.linmusic.feature.localmusic.data.transfer

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

@Serializable
data class AssociationBackup(
    val format: String = "melodia-local-audio-associations",
    val schemaVersion: Int = 1,
    val exportedAtMs: Long = 0,
    val records: List<AssociationBackupRecord>
)

@Serializable
data class AssociationBackupRecord(val online: AssociationOnlineSong, val local: AssociationLocalFile)

@Serializable
data class AssociationOnlineSong(
    val songId: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null
)

@Serializable
data class AssociationLocalFile(
    val uri: String,
    val fileName: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null,
    val readableAtExport: Boolean = true,
    val exportIssue: String? = null
)

/** A strict, bounded document format. URIs are evidence hints, never portable file IDs. */
object AssociationBackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_RECORDS = 10_000
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    fun encode(backup: AssociationBackup): String {
        validate(backup)
        return json.encodeToString(backup).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "关联备份超过 20 MB" }
        }
    }

    fun decode(value: String): AssociationBackup {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "关联备份超过 20 MB" }
        return json.decodeFromString<AssociationBackup>(value).also(::validate)
    }

    fun validate(backup: AssociationBackup) {
        require(backup.format == "melodia-local-audio-associations" && backup.schemaVersion == 1) { "不支持此关联备份格式或版本" }
        require(backup.exportedAtMs >= 0L && backup.records.size <= MAX_RECORDS) { "关联备份记录数量或时间无效" }
        require(backup.records.map { it.online.songId }.distinct().size == backup.records.size) { "关联备份包含重复网易云歌曲 ID" }
        backup.records.forEach { record ->
            require(record.online.songId > 0L) { "网易云歌曲 ID 无效" }
            require(record.local.uri.startsWith("content://") && record.local.uri.length <= 16_384) { "本地文件引用无效" }
            val strings = listOf(record.online.title, record.online.artist, record.online.album,
                record.local.fileName, record.local.title, record.local.artist, record.local.album, record.local.exportIssue)
            require(strings.all { it == null || it.length <= 4_096 }) { "关联备份文字字段过长" }
            require(listOf(record.online.durationMs, record.local.durationMs, record.local.sizeBytes).all { it == null || it >= 0L }) {
                "关联备份时长或文件大小无效"
            }
        }
    }
}

data class AssociationLocalCandidate(
    val uri: String,
    val identity: String,
    val fileName: String?,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long
) {
    fun toBackupFile(): AssociationLocalFile = AssociationLocalFile(uri, fileName, title, artist, album, durationMs, sizeBytes)
}

enum class AssociationImportStatus { NEW, SAME, CONFLICT, UNRESOLVED }

data class AssociationImportRow(
    val record: AssociationBackupRecord,
    val status: AssociationImportStatus,
    val resolvedUri: String? = null,
    val resolvedIdentity: String? = null,
    val reason: String = ""
)

data class AssociationImportPreview(val rows: List<AssociationImportRow>, val existingLinks: Map<Long, String> = emptyMap()) {
    val newCount: Int get() = rows.count { it.status == AssociationImportStatus.NEW }
    val sameCount: Int get() = rows.count { it.status == AssociationImportStatus.SAME }
    val conflictCount: Int get() = rows.count { it.status == AssociationImportStatus.CONFLICT }
    val unresolvedCount: Int get() = rows.count { it.status == AssociationImportStatus.UNRESOLVED }
}

data class AssociationTransferIssue(val songId: Long, val title: String?, val message: String)
data class AssociationExportResult(val recordCount: Int, val issues: List<AssociationTransferIssue>)
data class AssociationImportResult(val importedCount: Int, val sameCount: Int, val skippedCount: Int, val issues: List<AssociationTransferIssue>)

/** Restoring a confirmed file is intentionally stricter than choosing an equivalent playback encoding. */
object AssociationImportPlanner {
    private val whitespace = Regex("\\s+")
    private fun normalize(value: String?): String = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")
    private data class EvidenceKey(val fileName: String, val title: String, val artist: String, val album: String, val size: Long)
    private fun key(local: AssociationLocalFile) = EvidenceKey(normalize(local.fileName), normalize(local.title),
        normalize(local.artist), normalize(local.album), local.sizeBytes ?: 0L)
    private fun key(candidate: AssociationLocalCandidate) = EvidenceKey(normalize(candidate.fileName), normalize(candidate.title),
        normalize(candidate.artist), normalize(candidate.album), candidate.sizeBytes)

    fun hasPortableEvidence(local: AssociationLocalFile): Boolean =
        !local.fileName.isNullOrBlank() && !local.title.isNullOrBlank() && !local.artist.isNullOrBlank() &&
            (local.durationMs ?: 0L) > 0L && (local.sizeBytes ?: 0L) > 0L

    fun sameFileEvidence(local: AssociationLocalFile, candidate: AssociationLocalCandidate): Boolean =
        hasPortableEvidence(local) && normalize(local.fileName) == normalize(candidate.fileName) &&
            normalize(local.title) == normalize(candidate.title) && normalize(local.artist) == normalize(candidate.artist) &&
            normalize(local.album) == normalize(candidate.album) && local.sizeBytes == candidate.sizeBytes &&
            candidate.durationMs > 0L && abs(local.durationMs!! - candidate.durationMs) <= 1_000L

    // Limits expensive file inspection to plausible currently authorized library entries.
    fun mayMatch(local: AssociationLocalFile, uri: String, title: String, fileName: String?): Boolean =
        local.uri == uri || (!local.title.isNullOrBlank() && normalize(local.title) == normalize(title)) ||
            (!local.fileName.isNullOrBlank() && normalize(local.fileName) == normalize(fileName))

    fun candidateFilter(records: List<AssociationBackupRecord>): (String, String, String?) -> Boolean {
        val locals = records.map { it.local }.filter { it.readableAtExport && hasPortableEvidence(it) }
        val uris = locals.map { it.uri }.toSet()
        val titles = locals.map { normalize(it.title) }.toSet()
        val names = locals.map { normalize(it.fileName) }.toSet()
        return { uri, title, fileName -> uri in uris || normalize(title) in titles || normalize(fileName) in names }
    }

    fun preview(
        records: List<AssociationBackupRecord>,
        candidates: List<AssociationLocalCandidate>,
        existing: Map<Long, String>,
        identityOfUri: (String) -> String?
    ): AssociationImportPreview {
        val existingIdentities = existing.mapValues { (_, uri) -> identityOfUri(uri) }
        val existingByIdentity = existingIdentities.entries.filter { it.value != null }.groupBy({ it.value }, { it.key })
        val candidatesByEvidence = candidates.groupBy(::key)
        val resolved = records.map { record ->
            val local = record.local
            if (!local.readableAtExport) return@map AssociationImportRow(record, AssociationImportStatus.UNRESOLVED,
                reason = local.exportIssue ?: "导出时文件不可读取，需重新选择本地音频")
            if (!hasPortableEvidence(local)) return@map AssociationImportRow(record, AssociationImportStatus.UNRESOLVED,
                reason = "备份缺少完整文件名、标签、时长或大小，需重新选择")
            val matches = candidatesByEvidence[key(local)].orEmpty().filter {
                it.durationMs > 0L && abs(local.durationMs!! - it.durationMs) <= 1_000L
            }
            // A MediaStore URI can be reused on another phone. Its apparent equality
            // never disambiguates two different files, even when their metadata agrees.
            val match = matches.distinctBy { it.identity }.singleOrNull()
                ?: return@map AssociationImportRow(record, AssociationImportStatus.UNRESOLVED,
                    reason = if (matches.isEmpty()) "未找到已授权且信息一致的本地音频，请扫描或导入后重试" else "找到多份不同文件，需手动重新选择")
            val songId = record.online.songId
            val oldUri = existing[songId]
            val same = oldUri == match.uri || existingIdentities[songId] == match.identity
            val anotherSong = existingByIdentity[match.identity].orEmpty().any { it != songId }
            when {
                oldUri != null && !same -> AssociationImportRow(record, AssociationImportStatus.CONFLICT, match.uri, match.identity, "这首网易云歌曲已有不同本地关联，保留现有关联")
                anotherSong -> AssociationImportRow(record, AssociationImportStatus.CONFLICT, match.uri, match.identity, "此本地文件已关联其他网易云歌曲，保留现有关联")
                same -> AssociationImportRow(record, AssociationImportStatus.SAME, match.uri, match.identity, "与现有关联一致")
                else -> AssociationImportRow(record, AssociationImportStatus.NEW, match.uri, match.identity, "已找到唯一且信息一致的本地文件")
            }
        }
        val duplicatedTargets = resolved.filter { it.status == AssociationImportStatus.NEW || it.status == AssociationImportStatus.SAME }
            .groupBy { it.resolvedIdentity }.filterValues { rows -> rows.map { it.record.online.songId }.distinct().size > 1 }.keys
        return AssociationImportPreview(resolved.map { row ->
            if (row.status == AssociationImportStatus.NEW && row.resolvedIdentity in duplicatedTargets) row.copy(
                status = AssociationImportStatus.CONFLICT, reason = "本次备份有多个网易云 ID 指向同一本地文件，需重新选择") else row
        }, existing.toMap())
    }
}
