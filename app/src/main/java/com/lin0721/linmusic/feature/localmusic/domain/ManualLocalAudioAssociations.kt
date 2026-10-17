package com.lin0721.linmusic.feature.localmusic.domain

import java.text.Normalizer
import java.util.Locale

/** A legacy file may have several online IDs; never choose one by map iteration order. */
internal object ManualLocalAudioAssociations {
    private val whitespace = Regex("\\s+")

    /** Same exact-title normalization as the playback projection, computed once per title. */
    fun titleKey(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .trim().lowercase(Locale.ROOT).replace(whitespace, " ")

    /** The chosen URI was already inspected. Other distinct references are opened once per save. */
    fun idsForReadableFile(
        links: Map<Long, String>,
        selectedUri: String,
        selectedIdentity: String,
        identityOf: (String) -> String?
    ): Set<Long> {
        val identities = HashMap<String, String?>()
        return links.filter { (_, uri) ->
            if (uri == selectedUri) true else {
                if (!identities.containsKey(uri)) identities[uri] = identityOf(uri)
                identities[uri] == selectedIdentity
            }
        }.keys
    }

    /** Reconcile a preflight identity plan with concurrent reference edits without restarting it. */
    fun completeReplacementIds(
        linksBefore: Map<Long, String>,
        plannedIds: Set<Long>,
        currentLinks: Map<Long, String>,
        selectedUri: String,
        selectedIdentity: String,
        identityOf: (String) -> String?
    ): Set<Long> {
        val unchanged = plannedIds.filterTo(LinkedHashSet()) { currentLinks.containsKey(it) && currentLinks[it] == linksBefore[it] }
        val changed = currentLinks.filter { (id, uri) -> linksBefore[id] != uri }
        return unchanged + idsForReadableFile(changed, selectedUri, selectedIdentity, identityOf)
    }

    /** Exact URI lookup needs no descriptor; physical aliases share one fresh read per URI. */
    fun confirmedIdsForFiles(
        links: Map<Long, String>,
        titles: Map<Long, String>,
        files: List<Pair<String, String>>,
        identityOf: (String) -> String?
    ): Map<String, Long?> {
        val byUri = links.entries.groupBy({ it.value }, { it.key })
        val byTitle = HashMap<String, MutableSet<Long>>()
        val bySearchTitle = HashMap<String, MutableSet<Long>>()
        val untitled = HashSet<Long>()
        links.keys.forEach { songId ->
            val title = titles[songId]
            if (title.isNullOrBlank()) untitled += songId else {
                titleKey(title).takeIf(String::isNotEmpty)?.let { byTitle.getOrPut(it, ::LinkedHashSet) += songId }
                titleKey(LocalMatchTitle.forSearch(title)).takeIf(String::isNotEmpty)?.let {
                    bySearchTitle.getOrPut(it, ::LinkedHashSet) += songId
                }
            }
        }
        val identities = HashMap<String, String?>()
        fun identity(uri: String): String? {
            if (!identities.containsKey(uri)) identities[uri] = identityOf(uri)
            return identities[uri]
        }
        return files.associate { (uri, title) ->
            val exact = byUri[uri].orEmpty()
            val songId = if (exact.isNotEmpty()) exact.singleOrNull() else {
                val possible = untitled + byTitle[titleKey(title)].orEmpty() +
                    bySearchTitle[titleKey(LocalMatchTitle.forSearch(title))].orEmpty()
                if (possible.isEmpty()) null else identity(uri)?.let { selected ->
                    possible.filter { identity(links.getValue(it)) == selected }.singleOrNull()
                }
            }
            uri to songId
        }
    }

    fun uniqueByIdentity(
        links: Map<Long, String>,
        identityOf: (String) -> String?
    ): Map<String, Long> = links.entries
        .filter { it.key > 0L }
        .mapNotNull { (songId, uri) -> identityOf(uri)?.let { it to songId } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (identity, ids) -> ids.distinct().singleOrNull()?.let { identity to it } }
        .toMap()

    fun idsForIdentity(
        links: Map<Long, String>,
        identity: String,
        identityOf: (String) -> String?
    ): Set<Long> = links.filterValues { identityOf(it) == identity }.keys
}
