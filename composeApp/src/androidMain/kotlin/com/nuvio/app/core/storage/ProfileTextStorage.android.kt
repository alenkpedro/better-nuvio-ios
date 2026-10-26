package com.nuvio.app.core.storage

import android.content.Context
import android.content.SharedPreferences

internal actual object ProfileTextStorage {
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences("bn_profile_text", Context.MODE_PRIVATE)
    }

    actual fun load(name: String, profileId: Int): String? =
        preferences?.getString(ProfileScopedKey.of("bn_$name", profileId), null)

    actual fun save(name: String, profileId: Int, payload: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of("bn_$name", profileId), payload)?.apply()
    }
}
