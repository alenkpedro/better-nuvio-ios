package com.nuvio.app.features.tracking

import com.nuvio.app.features.library.LibrarySourceMode
import kotlinx.serialization.Serializable

@Serializable
enum class WatchProgressSource {
    // Better Nuvio: "Ambas" from the TV (cwFonteLocal 0). Nuvio account progress
    // first, completed by Trakt and Simkl when linked; the newest row wins.
    // Enum names are what is stored, so new values are only ever added.
    BOTH,
    TRAKT,
    SIMKL,
    NUVIO_SYNC,
    MDBLIST,
    ANILIST,
    WETRAKR;

    val providerId: TrackingProviderId?
        get() = when (this) {
            TRAKT -> TrackingProviderId.TRAKT
            SIMKL -> TrackingProviderId.SIMKL
            MDBLIST -> TrackingProviderId.MDBLIST
            ANILIST -> TrackingProviderId.ANILIST
            WETRAKR -> TrackingProviderId.WETRAKR
            NUVIO_SYNC -> null
            BOTH -> null
        }

    /** Providers whose progress is merged on top of the Nuvio account rows. */
    val mergedProviderIds: List<TrackingProviderId>
        get() = if (this == BOTH) BOTH_MERGED_PROVIDER_IDS else emptyList()

    /** True when the Nuvio account is the base of this source (pull, deletes). */
    val usesNuvioSync: Boolean
        get() = providerId == null

    companion object {
        fun fromStorage(value: String?): WatchProgressSource =
            entries.firstOrNull { it.name == value } ?: DEFAULT_WATCH_PROGRESS_SOURCE
    }
}

// Same order of preference as the TV merge: account, then Trakt, then Simkl.
val BOTH_MERGED_PROVIDER_IDS: List<TrackingProviderId> =
    listOf(TrackingProviderId.TRAKT, TrackingProviderId.SIMKL)

// Better Nuvio: same default as the TV ("Ambas").
val DEFAULT_WATCH_PROGRESS_SOURCE: WatchProgressSource = WatchProgressSource.BOTH
val DEFAULT_LIBRARY_SOURCE_MODE: LibrarySourceMode = LibrarySourceMode.TRAKT

fun librarySourceModeFromStorage(value: String?): LibrarySourceMode =
    LibrarySourceMode.entries.firstOrNull { it.name == value } ?: DEFAULT_LIBRARY_SOURCE_MODE

val LibrarySourceMode.providerId: TrackingProviderId?
    get() = when (this) {
        LibrarySourceMode.LOCAL -> null
        LibrarySourceMode.TRAKT -> TrackingProviderId.TRAKT
        LibrarySourceMode.SIMKL -> TrackingProviderId.SIMKL
        LibrarySourceMode.MDBLIST -> TrackingProviderId.MDBLIST
        LibrarySourceMode.ANILIST -> TrackingProviderId.ANILIST
        LibrarySourceMode.WETRAKR -> TrackingProviderId.WETRAKR
    }

fun effectiveWatchProgressSource(
    requestedSource: WatchProgressSource,
    isProviderAuthenticated: (TrackingProviderId) -> Boolean,
): WatchProgressSource {
    // "Ambas" never falls back: with nothing linked it is just the account, as on the TV.
    if (requestedSource == WatchProgressSource.BOTH) return WatchProgressSource.BOTH
    val providerId = requestedSource.providerId ?: return WatchProgressSource.NUVIO_SYNC
    return requestedSource.takeIf { isProviderAuthenticated(providerId) }
        ?: WatchProgressSource.NUVIO_SYNC
}

fun effectiveLibrarySourceMode(
    requestedSource: LibrarySourceMode,
    isProviderAuthenticated: (TrackingProviderId) -> Boolean,
): LibrarySourceMode {
    val providerId = requestedSource.providerId ?: return LibrarySourceMode.LOCAL
    return requestedSource.takeIf { isProviderAuthenticated(providerId) }
        ?: LibrarySourceMode.LOCAL
}
