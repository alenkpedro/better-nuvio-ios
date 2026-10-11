package com.nuvio.app.betternuvio

import android.content.Context
import android.content.SharedPreferences

internal actual object BetterNuvioStorage {
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences("better_nuvio", Context.MODE_PRIVATE)
    }

    actual fun loadBoolean(key: String): Boolean? =
        preferences?.takeIf { it.contains(key) }?.getBoolean(key, false)

    actual fun saveBoolean(key: String, value: Boolean) {
        preferences?.edit()?.putBoolean(key, value)?.apply()
    }
}
