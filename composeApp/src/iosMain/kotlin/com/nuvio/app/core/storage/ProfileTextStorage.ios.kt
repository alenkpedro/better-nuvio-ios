package com.nuvio.app.core.storage

import platform.Foundation.NSUserDefaults

internal actual object ProfileTextStorage {
    actual fun load(name: String, profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of("bn_$name", profileId))

    actual fun save(name: String, profileId: Int, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of("bn_$name", profileId))
    }
}
