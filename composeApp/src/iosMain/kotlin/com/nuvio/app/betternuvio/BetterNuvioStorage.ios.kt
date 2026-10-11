package com.nuvio.app.betternuvio

import platform.Foundation.NSUserDefaults

internal actual object BetterNuvioStorage {
    actual fun loadBoolean(key: String): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else null
    }

    actual fun saveBoolean(key: String, value: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(value, forKey = key)
    }
}
