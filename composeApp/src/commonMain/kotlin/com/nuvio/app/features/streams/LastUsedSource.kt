package com.nuvio.app.features.streams

import com.nuvio.app.features.watchprogress.WatchProgressEntry

/**
 * A fonte que a pessoa usou por último num título ("a de sempre"): o addon e a
 * resolução, tirados do progresso salvo pelo player (providerName e o nome da
 * fonte). Sem progresso local com fonte, não há o que mostrar.
 */
internal data class LastUsedSource(
    val addonName: String,
    val resolution: String?,
) {
    /** "BestCine · 1080p", a linha da fonte do destaque da Home. */
    val label: String
        get() = listOfNotNull(addonName, resolution).joinToString(" · ")
}

private const val LiveTvProviderName = "Live TV"

internal fun lastUsedSourceFor(
    parentMetaId: String?,
    entries: List<WatchProgressEntry>,
): LastUsedSource? {
    val id = parentMetaId?.takeIf { it.isNotBlank() } ?: return null
    val entry = entries
        .filter { entry ->
            entry.parentMetaId == id &&
                !entry.providerName.isNullOrBlank() &&
                entry.providerName.trim() != LiveTvProviderName
        }
        .maxByOrNull { it.lastUpdatedEpochMs }
        ?: return null
    return LastUsedSource(
        addonName = entry.providerName!!.trim(),
        resolution = bnResolutionInText(
            listOfNotNull(entry.lastStreamTitle, entry.lastStreamSubtitle).joinToString(" "),
        ),
    )
}

/** A mesma coisa a partir do link salvo para reuso (é o que o Play vai tocar). */
internal fun CachedStreamLink.toLastUsedSource(): LastUsedSource? {
    val addon = addonName.trim().takeIf { it.isNotEmpty() } ?: return null
    return LastUsedSource(
        addonName = addon,
        resolution = bnResolutionInText(listOfNotNull(streamName, filename).joinToString(" ")),
    )
}
