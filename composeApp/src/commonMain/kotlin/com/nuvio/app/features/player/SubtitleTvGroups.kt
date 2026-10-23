package com.nuvio.app.features.player

/**
 * Better Nuvio: os grupos da coluna Legendas, como na TV (faixas.c, montarGrupos):
 * Desligada, depois "Embutidas" com todas as faixas do arquivo, e então um grupo por
 * idioma das legendas de addon. Os idiomas preferidos vêm primeiro (o principal e o
 * parecido com ele, depois o segundo), os demais em ordem alfabética pelo nome, e o
 * idioma desconhecido fica no fim (prioridadeGrupo).
 */
internal const val SubtitleEmbeddedGroupKey = "__embedded__"

internal fun buildTvSubtitleGroups(
    subtitleTracks: List<SubtitleTrack>,
    addonSubtitles: List<AddonSubtitle>,
    preferredLanguage: String,
    secondaryPreferredLanguage: String?,
    showOnlyPreferredLanguages: Boolean,
    selectedLanguageKey: String,
    languageLabel: (String) -> String,
): List<SubtitleLanguageItem> {
    val primary = preferredLanguage.toTvPreferredKey()
    val secondary = secondaryPreferredLanguage.toTvPreferredKey()
    val counts = linkedMapOf<String, Int>()
    val seenAddonIds = mutableSetOf<String>()
    addonSubtitles.forEach { subtitle ->
        if (!seenAddonIds.add(SubtitleSelectionOption.Addon(subtitle).id)) return@forEach
        val key = subtitleLanguageKey(subtitle.language)
        counts[key] = (counts[key] ?: 0) + 1
    }
    // O filtro de favoritos só limita as legendas de addon; o idioma em uso continua.
    val visible = counts.entries.filter { (key, _) ->
        !showOnlyPreferredLanguages || key == selectedLanguageKey ||
            languageMatches(key, primary) || languageMatches(key, secondary)
    }
    val sorted = visible.sortedWith(
        compareBy<Map.Entry<String, Int>>(
            { tvGroupPriority(it.key, primary, secondary) },
            { languageLabel(it.key).lowercase() },
        ),
    )
    return buildList {
        add(SubtitleLanguageItem(SubtitleOffLanguageKey, 0))
        if (subtitleTracks.isNotEmpty()) add(SubtitleLanguageItem(SubtitleEmbeddedGroupKey, subtitleTracks.size))
        sorted.forEach { add(SubtitleLanguageItem(it.key, it.value)) }
    }
}

internal fun buildTvSubtitleOptions(
    groupKey: String,
    subtitleTracks: List<SubtitleTrack>,
    addonSubtitles: List<AddonSubtitle>,
): List<SubtitleSelectionOption> = when (groupKey) {
    SubtitleOffLanguageKey -> emptyList()
    SubtitleEmbeddedGroupKey -> subtitleTracks.map { SubtitleSelectionOption.BuiltIn(it) }
    else -> {
        val seen = mutableSetOf<String>()
        addonSubtitles
            .filter { subtitleLanguageKey(it.language) == groupKey }
            .map(SubtitleSelectionOption::Addon)
            .filter { seen.add(it.id) }
    }
}

/** O grupo da legenda tocando: a de addon pelo idioma dela, a do arquivo em Embutidas. */
internal fun tvSelectedSubtitleGroupKey(
    subtitleTracks: List<SubtitleTrack>,
    selectedSubtitleIndex: Int,
    selectedAddonSubtitle: AddonSubtitle?,
): String {
    selectedAddonSubtitle?.let { return subtitleLanguageKey(it.language) }
    val builtInSelected = subtitleTracks.any { it.index == selectedSubtitleIndex } ||
        (selectedSubtitleIndex < 0 && subtitleTracks.any { it.isSelected })
    return if (builtInSelected) SubtitleEmbeddedGroupKey else SubtitleOffLanguageKey
}

private fun String?.toTvPreferredKey(): String? {
    val normalized = normalizeLanguageCode(this) ?: return null
    if (normalized == SubtitleLanguageOption.NONE || normalized == SubtitleLanguageOption.FORCED) return null
    return subtitleLanguageKey(normalized).takeUnless { it == SubtitleUnknownLanguageKey }
}

private fun baseLanguage(key: String): String = key.substringBefore('-')

private fun languageMatches(key: String, preferred: String?): Boolean =
    preferred != null && key != SubtitleUnknownLanguageKey && baseLanguage(key) == baseLanguage(preferred)

private fun tvGroupPriority(key: String, primary: String?, secondary: String?): Int = when {
    primary != null && key == primary -> 0
    languageMatches(key, primary) -> 1
    secondary != null && key == secondary -> 2
    languageMatches(key, secondary) -> 3
    key == SubtitleUnknownLanguageKey -> 5
    else -> 4
}
