package com.nuvio.app.features.autosync

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

/** AutoSync preferences in NSUserDefaults, per profile, Android's keys with an "autosync_" prefix
 *  (NSUserDefaults is one flat namespace). Aggressive mode is Android-only and not stored here. */
internal object AutoSyncPreferencesIos {
    private const val preferredSubtitleAutoSyncOnStartKey = "autosync_preferred_subtitle_auto_sync_on_start"
    private const val syncToleranceMsKey = "autosync_auto_sync_tolerance_ms"
    private var installed = false

    fun initialize() {
        if (installed) return
        installed = true
        val defaults = NSUserDefaults.standardUserDefaults
        fun has(key: String) = defaults.objectForKey(key) != null
        AutoSyncPreferencesRepository.installPersistence(
            load = {
                val key = ProfileScopedKey.of(preferredSubtitleAutoSyncOnStartKey)
                if (has(key)) defaults.boolForKey(key) else null
            },
            save = { enabled -> defaults.setBool(enabled, ProfileScopedKey.of(preferredSubtitleAutoSyncOnStartKey)) },
            loadSyncToleranceMs = {
                val key = ProfileScopedKey.of(syncToleranceMsKey)
                if (has(key)) defaults.integerForKey(key).toInt() else null
            },
            saveSyncToleranceMs = { ms -> defaults.setInteger(ms.toLong(), ProfileScopedKey.of(syncToleranceMsKey)) },
        )
    }
}
