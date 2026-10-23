package com.nuvio.app.core.tracking

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import com.nuvio.app.features.anilist.AniListTracker
import com.nuvio.app.features.simkl.SimklAuthRepository
import com.nuvio.app.features.simkl.SimklMutationRepository
import com.nuvio.app.features.simkl.SimklLibraryRepository
import com.nuvio.app.features.simkl.SimklProgressRepository
import com.nuvio.app.features.simkl.SimklTrackingLibraryProvider
import com.nuvio.app.features.simkl.SimklTrackingProgressProvider
import com.nuvio.app.features.simkl.SimklWatchedSyncAdapter
import com.nuvio.app.features.simkl.SimklSyncRepository
import com.nuvio.app.features.simkl.SimklRatingsProvider
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.mdblist.MdbListTracker
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktRatingsProvider
import com.nuvio.app.features.trakt.TraktScrobbleRepository
import com.nuvio.app.features.trakt.TraktTrackingLibraryProvider
import com.nuvio.app.features.trakt.TraktTrackingProgressProvider
import com.nuvio.app.features.wetrakr.WeTrakrTracker
import com.nuvio.app.features.watching.sync.TraktWatchedSyncAdapter

private val bootstrapLog = Logger.withTag("TrackingBootstrap")
private val failedOptionalProvidersLock = SynchronizedObject()
private val failedOptionalProviders = mutableSetOf<String>()

// MDBList, AniList and WeTrakr are optional providers. If one of them fails to initialize, skip it
// instead of crashing callers such as profile selection; the failure is logged once per provider.
private inline fun registerOptionalProvider(name: String, register: () -> Unit) {
    try {
        register()
    } catch (error: Throwable) {
        if (synchronized(failedOptionalProvidersLock) { failedOptionalProviders.add(name) }) {
            bootstrapLog.e(error) { "Skipping $name tracking provider: initialization failed" }
        }
    }
}

fun ensureTrackingProvidersRegistered() {
    registerOptionalProvider("MDBList") { MdbListTracker.register() }
    registerOptionalProvider("AniList") { AniListTracker.register() }
    registerOptionalProvider("WeTrakr") { WeTrakrTracker.register() }
    TraktAuthRepository.descriptor
    TraktScrobbleRepository.ensureRegistered()
    SimklAuthRepository.descriptor
    SimklSyncRepository.state
    SimklLibraryRepository.uiState
    SimklProgressRepository.uiState
    SimklMutationRepository.ensureRegistered()
    TrackingProviderRegistry.registerLibraryProvider(TraktTrackingLibraryProvider)
    TrackingProviderRegistry.registerLibraryProvider(SimklTrackingLibraryProvider)
    TrackingProviderRegistry.registerWatchedProvider(TraktWatchedSyncAdapter)
    TrackingProviderRegistry.registerWatchedProvider(SimklWatchedSyncAdapter)
    TrackingProviderRegistry.registerProgressProvider(TraktTrackingProgressProvider)
    TrackingProviderRegistry.registerProgressProvider(SimklTrackingProgressProvider)
    TrackingProviderRegistry.registerRatingProvider(TraktRatingsProvider)
    TrackingProviderRegistry.registerRatingProvider(SimklRatingsProvider)
}
