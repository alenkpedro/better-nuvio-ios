package com.nuvio.app.features.watchprogress

import com.nuvio.app.features.tracking.WatchProgressSource

internal fun projectWatchProgressSourceEntries(
    source: WatchProgressSource,
    nuvioEntries: Collection<WatchProgressEntry>,
    providerEntries: Collection<WatchProgressEntry>,
): List<WatchProgressEntry> = when {
    source == WatchProgressSource.BOTH -> mergeBothWatchProgressEntries(nuvioEntries, providerEntries)
    source.providerId == null -> nuvioEntries.toList()
    else -> providerEntries.toList()
}

/**
 * "Ambas", as the TV does it (watch_service.c + continuar_motor.c cw_selecionar):
 * account rows first, then Trakt, then Simkl ([providerEntries] in that order).
 * Per progress row the most recent update wins; on an exact tie the earlier
 * source wins, so the Nuvio account beats Trakt and Trakt beats Simkl. Picking
 * one row per series is left to continueWatchingEntries, like before.
 */
internal fun mergeBothWatchProgressEntries(
    nuvioEntries: Collection<WatchProgressEntry>,
    providerEntries: Collection<WatchProgressEntry>,
): List<WatchProgressEntry> {
    val merged = linkedMapOf<String, WatchProgressEntry>()
    (nuvioEntries.asSequence() + providerEntries.asSequence()).forEach { entry ->
        val key = entry.resolvedProgressKey()
        val existing = merged[key]
        if (existing == null || entry.lastUpdatedEpochMs > existing.lastUpdatedEpochMs) {
            merged[key] = entry
        }
    }
    return merged.values.toList()
}
