package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamAutoPlaySelectorTest {


    @Test
    fun `bingeGroup-first selects matching stream before first stream mode`() {
        val first = stream(
            addonName = "AddonA",
            url = "https://example.com/first.m3u8",
            name = "1080p",
            bingeGroup = "other-group",
        )
        val preferred = stream(
            addonName = "AddonB",
            url = "https://example.com/preferred.m3u8",
            name = "720p",
            bingeGroup = "same-group",
        )

        val selected = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(first, preferred),
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("AddonA", "AddonB"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            preferredBingeGroup = "same-group",
            preferBingeGroupInSelection = true,
        )

        assertEquals(preferred, selected)
    }

    @Test
    fun `falls back to normal mode when no bingeGroup match exists`() {
        val first = stream(
            addonName = "AddonA",
            url = "https://example.com/first.m3u8",
            name = "First",
            bingeGroup = "group-a",
        )
        val second = stream(
            addonName = "AddonB",
            url = "https://example.com/second.m3u8",
            name = "Second",
            bingeGroup = "group-b",
        )

        val selected = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(first, second),
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("AddonA", "AddonB"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            preferredBingeGroup = "missing-group",
            preferBingeGroupInSelection = true,
        )

        assertEquals(first, selected)
    }

    @Test
    fun `bingeGroup-first respects source and addon plugin filters`() {
        val filteredOutAddonMatch = stream(
            addonName = "AddonFilteredOut",
            url = "https://example.com/addon-match.m3u8",
            bingeGroup = "same-group",
        )
        val allowedPluginMatch = stream(
            addonName = "PluginAllowed",
            url = "https://example.com/plugin-match.m3u8",
            bingeGroup = "same-group",
        )

        val selected = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(filteredOutAddonMatch, allowedPluginMatch),
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ENABLED_PLUGINS_ONLY,
            installedAddonNames = setOf("AddonFilteredOut"),
            selectedAddons = emptySet(),
            selectedPlugins = setOf("PluginAllowed"),
            preferredBingeGroup = "same-group",
            preferBingeGroupInSelection = true,
        )

        assertEquals(allowedPluginMatch, selected)
    }

    @Test
    fun `blank preferredBingeGroup behaves as disabled`() {
        val first = stream(
            addonName = "AddonA",
            url = "https://example.com/first.m3u8",
            bingeGroup = "group-a",
        )
        val second = stream(
            addonName = "AddonB",
            url = "https://example.com/second.m3u8",
            bingeGroup = "group-b",
        )

        val selected = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(first, second),
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("AddonA", "AddonB"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            preferredBingeGroup = "   ",
            preferBingeGroupInSelection = true,
        )

        assertEquals(first, selected)
    }

    @Test
    fun `manual mode remains manual even with matching bingeGroup`() {
        val matched = stream(
            addonName = "AddonA",
            url = "https://example.com/match.m3u8",
            bingeGroup = "same-group",
        )

        val selected = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(matched),
            mode = StreamAutoPlayMode.MANUAL,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("AddonA"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            preferredBingeGroup = "same-group",
            preferBingeGroupInSelection = true,
        )

        assertNull(selected)
    }

    @Test
    fun `first stream mode can select direct debrid candidate without resolved URL`() {
        val directDebrid = stream(
            addonName = "Torbox Instant",
            url = null,
            name = "TB Instant",
            directDebrid = true,
        )

        val selected = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(directDebrid),
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = emptySet(),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
        )

        assertEquals(directDebrid, selected)
    }

    @Test
    fun `first stream mode does not auto select external url browser link`() {
        val external = stream(
            addonName = "External Addon",
            externalUrl = "https://example.com/watch",
            name = "Watch on site",
        )

        val selected = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(external),
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("External Addon"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
        )

        assertNull(selected)
    }

    @Test
    fun `timeout evaluation keeps pending regex debrid candidate open`() {
        val pending = stream(
            addonName = "Torrentio",
            name = "The Show 1080p",
            infoHash = "hash-pending",
            cacheState = StreamDebridCacheState.CHECKING,
        )

        val evaluation = StreamAutoPlaySelector.evaluateAutoPlayStream(
            streams = listOf(pending),
            mode = StreamAutoPlayMode.REGEX_MATCH,
            regexPattern = "1080p",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Torrentio"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            debridEnabled = true,
            activeResolverProviderId = "premiumize",
        )

        assertNull(evaluation.stream)
        assertTrue(evaluation.hasPendingDebridCandidate)
    }

    @Test
    fun `timeout evaluation still selects direct link while debrid candidate is pending`() {
        val pending = stream(
            addonName = "Torrentio",
            name = "The Show 1080p",
            infoHash = "hash-pending",
            cacheState = StreamDebridCacheState.CHECKING,
        )
        val direct = stream(
            addonName = "Direct Addon",
            url = "https://example.com/video.mp4",
            name = "The Show 1080p",
        )

        val evaluation = StreamAutoPlaySelector.evaluateAutoPlayStream(
            streams = listOf(pending, direct),
            mode = StreamAutoPlayMode.REGEX_MATCH,
            regexPattern = "1080p",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Torrentio", "Direct Addon"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            debridEnabled = true,
            activeResolverProviderId = "premiumize",
        )

        assertEquals(direct, evaluation.stream)
        assertFalse(evaluation.hasPendingDebridCandidate)
    }

    @Test
    fun `direct debrid candidate must match active resolver`() {
        val torbox = stream(
            addonName = "Comet",
            name = "TB Instant",
            directDebrid = true,
            directDebridService = "torbox",
        )

        val evaluation = StreamAutoPlaySelector.evaluateAutoPlayStream(
            streams = listOf(torbox),
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Comet"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            debridEnabled = true,
            activeResolverProviderId = "premiumize",
        )

        assertNull(evaluation.stream)
        assertFalse(evaluation.hasPendingDebridCandidate)
    }

    @Test
    fun `nested exclusions do not crash regex selection`() {
        val stream = stream(
            addonName = "Direct Addon",
            url = "https://example.com/video.mp4",
            name = "Movie 1080p WEB",
        )

        val selected = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(stream),
            mode = StreamAutoPlayMode.REGEX_MATCH,
            regexPattern = "^(?!.*\\b(CAM(?!RIP)|TS)\\b).*1080p",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Direct Addon"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
        )

        assertEquals(stream, selected)
    }

    // --- Better Nuvio: melhor qualidade (pontos() da TV) ---------------------

    private fun bestQuality(
        streams: List<StreamItem>,
        maxQualityHeight: Int = 0,
        remembered: RememberedSource? = null,
    ): StreamAutoPlayEvaluation = StreamAutoPlaySelector.evaluateAutoPlayStream(
        streams = streams,
        mode = StreamAutoPlayMode.BEST_QUALITY,
        regexPattern = "",
        source = StreamAutoPlaySource.ALL_SOURCES,
        installedAddonNames = streams.mapTo(mutableSetOf()) { it.addonName },
        selectedAddons = emptySet(),
        selectedPlugins = emptySet(),
        rememberedSource = remembered,
        maxQualityHeight = maxQualityHeight,
    )

    @Test
    fun `best quality orders mp4 4k dv then 4k then mp4 then atmos then height`() {
        val hd = stream(addonName = "A", url = "https://example.com/a.mkv", name = "720p")
        val atmos = stream(addonName = "A", url = "https://example.com/b.mkv", name = "1080p Atmos")
        val mp4 = stream(addonName = "A", url = "https://example.com/c.mp4", name = "1080p")
        val uhd = stream(addonName = "A", url = "https://example.com/d.mkv", name = "2160p DV")
        val uhdMp4Dv = stream(addonName = "A", url = "https://example.com/e.mp4", name = "4K DV")
        val fullHd = stream(addonName = "A", url = "https://example.com/f.mkv", name = "1080p")

        val evaluation = bestQuality(listOf(hd, atmos, mp4, uhd, uhdMp4Dv, fullHd))

        assertEquals(listOf(uhdMp4Dv, uhd, mp4, atmos, fullHd, hd), evaluation.readyStreams)
    }

    @Test
    fun `best quality keeps addon order on ties`() {
        val first = stream(addonName = "A", url = "https://example.com/1.mkv", name = "1080p")
        val second = stream(addonName = "B", url = "https://example.com/2.mkv", name = "1080p")

        assertEquals(first, bestQuality(listOf(first, second)).stream)
        assertEquals(second, bestQuality(listOf(second, first)).stream)
    }

    @Test
    fun `max quality moves sources above it to the end without dropping them`() {
        val uhd = stream(addonName = "A", url = "https://example.com/uhd.mp4", name = "4K DV")
        val fullHd = stream(addonName = "A", url = "https://example.com/fhd.mkv", name = "1080p")

        assertEquals(listOf(fullHd, uhd), bestQuality(listOf(uhd, fullHd), maxQualityHeight = 1080).readyStreams)
        assertEquals(uhd, bestQuality(listOf(uhd), maxQualityHeight = 1080).stream)
    }

    @Test
    fun `uncached debrid sources rank below cached ones`() {
        val uncached = stream(addonName = "A", url = "https://example.com/u.mp4", name = "⏳ 4K DV")
        val cached = stream(addonName = "A", url = "https://example.com/c.mkv", name = "⚡ 720p")

        assertEquals(cached, bestQuality(listOf(uncached, cached)).stream)
    }

    @Test
    fun `warning urls are never auto played`() {
        val slate = stream(addonName = "A", url = "https://example.com/static/downloading.mp4", name = "4K")
        val real = stream(addonName = "A", url = "https://example.com/movie.mkv", name = "720p")

        assertEquals(real, bestQuality(listOf(slate, real)).stream)
    }

    @Test
    fun `remembered source wins by binge group then by provider and audio`() {
        val best = stream(addonName = "A", url = "https://example.com/best.mp4", name = "4K DV")
        val chosen = stream(addonName = "B", url = "https://example.com/dub.mkv", name = "720p Dublado", bingeGroup = "b-720")
        val remembered = RememberedSource(provider = "B", audio = "DUB", bingeGroup = "b-720", height = 720, label = "720p Dublado")

        assertEquals(chosen, bestQuality(listOf(best, chosen), remembered = remembered).stream)

        // O addon trocou o bingeGroup: casa por provedor + trilha de áudio.
        val renamed = chosen.copy(behaviorHints = StreamBehaviorHints(bingeGroup = "b-new"))
        val subbed = stream(addonName = "B", url = "https://example.com/leg.mkv", name = "1080p Legendado")
        assertEquals(renamed, bestQuality(listOf(best, subbed, renamed), remembered = remembered).stream)

        // Nada casou: a melhor qualidade assume.
        val other = RememberedSource(provider = "C", audio = "DUB")
        assertEquals(best, bestQuality(listOf(best, chosen), remembered = other).stream)
    }

    @Test
    fun `audio signature decodes flags and terms in a stable order`() {
        val a = stream(addonName = "A", name = "🇺🇸 / 🇧🇷 Dual Audio")
        val b = stream(addonName = "A", name = "Dual Áudio 🇧🇷 🇺🇸")
        assertEquals("BR+DUAL+US", RememberedSourceMatcher.audioSignature(a))
        assertEquals(RememberedSourceMatcher.audioSignature(a), RememberedSourceMatcher.audioSignature(b))
    }

    @Test
    fun `no subscription warnings are detected like on the TV`() {
        val torrentio = stream(
            addonName = "Torrentio",
            url = "https://torrentio.strem.fun/videos/failed_access_v3.mp4",
            name = "Torrentio\nRD error",
        )
        val comet = stream(addonName = "Comet", name = "[❌] realdebrid").copy(description = "realdebrid: No active subscription.")
        val movie = stream(addonName = "A", url = "https://example.com/m.mkv", name = "Expired subscription 1080p")

        assertTrue(StreamWarnings.isNoSubscriptionWarning(torrentio))
        assertTrue(StreamWarnings.isNoSubscriptionWarning(comet))
        assertTrue(StreamWarnings.isPureWarning(comet))
        assertFalse(StreamWarnings.isPureWarning(torrentio))
        assertFalse(StreamWarnings.isNoSubscriptionWarning(movie))
    }

    private fun stream(
        addonName: String,
        url: String? = null,
        externalUrl: String? = null,
        name: String? = null,
        bingeGroup: String? = null,
        directDebrid: Boolean = false,
        directDebridService: String = "torbox",
        infoHash: String? = null,
        cacheState: StreamDebridCacheState? = null,
    ): StreamItem = StreamItem(
        name = name,
        url = url,
        externalUrl = externalUrl,
        infoHash = infoHash,
        addonName = addonName,
        addonId = "addon:$addonName",
        clientResolve = if (directDebrid) {
            StreamClientResolve(
                type = "debrid",
                service = directDebridService,
                isCached = true,
                infoHash = "hash",
            )
        } else {
            null
        },
        debridCacheStatus = cacheState?.let { state ->
            StreamDebridCacheStatus(
                providerId = "premiumize",
                providerName = "Premiumize",
                state = state,
            )
        },
        behaviorHints = StreamBehaviorHints(
            bingeGroup = bingeGroup,
        ),
    )
}
