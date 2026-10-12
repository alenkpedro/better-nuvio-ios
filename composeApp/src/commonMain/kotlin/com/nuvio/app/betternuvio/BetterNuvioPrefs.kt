package com.nuvio.app.betternuvio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.ProfileScopedKey

/**
 * Better Nuvio settings, kept apart from the official settings storage so the fork
 * stays a small patch on top of upstream (merges touch few official files).
 * Values are per profile, like the official player settings.
 */
internal expect object BetterNuvioStorage {
    fun loadBoolean(key: String): Boolean?
    fun saveBoolean(key: String, value: Boolean)
    fun loadString(key: String): String?
    fun saveString(key: String, value: String?)
}

object BetterNuvioPrefs {
    private const val SUBTITLE_AUTOSYNC = "better_nuvio_subtitle_autosync"
    private const val STREAMS_BY_RESOLUTION = "better_nuvio_streams_by_resolution"

    private var byResolutionCache by mutableStateOf<Boolean?>(null)

    /** Source list grouped by resolution (4K / 1080p / 720p / SD), as on the TV. On by default. */
    var streamsByResolution: Boolean
        get() = byResolutionCache ?: (BetterNuvioStorage.loadBoolean(ProfileScopedKey.of(STREAMS_BY_RESOLUTION)) ?: true)
            .also { byResolutionCache = it }
        set(value) {
            byResolutionCache = value
            BetterNuvioStorage.saveBoolean(ProfileScopedKey.of(STREAMS_BY_RESOLUTION), value)
        }

    private var autoSyncCache by mutableStateOf<Boolean?>(null)
    private var autoSyncProfileKey: String? = null

    /** Subtitle AutoSync (MKV index reference). On by default. */
    var subtitleAutoSync: Boolean
        get() {
            val key = ProfileScopedKey.of(SUBTITLE_AUTOSYNC)
            if (autoSyncProfileKey != key) {
                autoSyncProfileKey = key
                autoSyncCache = BetterNuvioStorage.loadBoolean(key) ?: true
            }
            return autoSyncCache ?: true
        }
        set(value) {
            val key = ProfileScopedKey.of(SUBTITLE_AUTOSYNC)
            autoSyncProfileKey = key
            autoSyncCache = value
            BetterNuvioStorage.saveBoolean(key, value)
        }
}
