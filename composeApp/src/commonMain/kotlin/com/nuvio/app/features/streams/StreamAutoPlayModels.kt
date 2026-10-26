package com.nuvio.app.features.streams

enum class StreamAutoPlayMode {
    MANUAL,
    FIRST_STREAM,
    REGEX_MATCH,
    /** Better Nuvio: a regra da TV (streams.c, pontos): MP4 4K DV, 4K, MP4, Atmos, altura. */
    BEST_QUALITY,
}

enum class StreamAutoPlaySource {
    ALL_SOURCES,
    INSTALLED_ADDONS_ONLY,
    ENABLED_PLUGINS_ONLY,
}
