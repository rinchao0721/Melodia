package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.localmusic.domain.BatchLocalMatchStatus
import com.lin0721.linmusic.feature.localmusic.domain.ManualLocalAudioAssociations

/** Small metadata index: only related report rows need fresh file checks after a manual choice. */
internal data class ManualReportRowReference(
    val uri: String,
    val title: String,
    val localSongId: Long?,
    val matchedSongId: Long?
)

internal fun affectedManualReportUris(
    rows: List<ManualReportRowReference>,
    selectedUri: String,
    selectedTitle: String,
    relatedSongIds: Set<Long>
): Set<String> {
    val titleKey = ManualLocalAudioAssociations.titleKey(selectedTitle)
    return rows.filter { row ->
        row.uri == selectedUri || row.localSongId?.let { it in relatedSongIds } == true ||
            row.matchedSongId?.let { it in relatedSongIds } == true ||
            (titleKey.isNotEmpty() && ManualLocalAudioAssociations.titleKey(row.title) == titleKey)
    }.mapTo(LinkedHashSet()) { it.uri }
}

internal data class ManualReportRowUpdate(
    val status: BatchLocalMatchStatus,
    val track: Track?,
    val reason: String,
    val usesExistingSource: Boolean = false
)

/** The latest stored reference, rather than the tapped or an in-flight search candidate, is authoritative. */
internal fun reconcileManualReportRow(
    previousStatus: BatchLocalMatchStatus,
    previousSongId: Long?,
    linked: Track?,
    existingEquivalent: Track?,
    selected: Boolean
): ManualReportRowUpdate? = when {
    linked != null -> ManualReportRowUpdate(
        if (!selected && previousStatus == BatchLocalMatchStatus.MATCHED && previousSongId == linked.id)
            BatchLocalMatchStatus.MATCHED else BatchLocalMatchStatus.ALREADY_LINKED,
        linked, if (selected) "已手动确认关联" else "已有关联，已保留当前确认的歌曲 ID ${linked.id}"
    )
    existingEquivalent != null -> ManualReportRowUpdate(BatchLocalMatchStatus.ALREADY_LINKED,
        existingEquivalent, "同一歌曲已匹配，沿用已有优先音源", usesExistingSource = true)
    previousStatus == BatchLocalMatchStatus.MATCHED || previousStatus == BatchLocalMatchStatus.ALREADY_LINKED ->
        ManualReportRowUpdate(BatchLocalMatchStatus.NEEDS_CONFIRMATION, null,
            "原关联已更换或不再可用，请手动核对")
    else -> null
}
