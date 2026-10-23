package com.nuvio.app.features.watchprogress

import com.nuvio.app.features.tracking.WatchProgressSource
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchProgressSourceProjectionTest {
    @Test
    fun `remote source excludes every Nuvio progress entry`() {
        val nuvioEntries = listOf(
            entry(parentMetaId = "shared", updatedAt = 200L),
            entry(parentMetaId = "nuvio-only", updatedAt = 300L),
        )
        val providerEntries = listOf(
            entry(parentMetaId = "shared", updatedAt = 100L),
        )

        listOf(WatchProgressSource.TRAKT, WatchProgressSource.SIMKL).forEach { source ->
            val projected = projectWatchProgressSourceEntries(
                source = source,
                nuvioEntries = nuvioEntries,
                providerEntries = providerEntries,
            )

            assertEquals(providerEntries, projected)
        }
    }

    @Test
    fun `Nuvio source excludes every provider progress entry`() {
        val nuvioEntries = listOf(entry(parentMetaId = "nuvio"))
        val providerEntries = listOf(entry(parentMetaId = "provider"))

        val projected = projectWatchProgressSourceEntries(
            source = WatchProgressSource.NUVIO_SYNC,
            nuvioEntries = nuvioEntries,
            providerEntries = providerEntries,
        )

        assertEquals(nuvioEntries, projected)
    }

    @Test
    fun `Ambas keeps account rows and adds provider rows with the newest row winning`() {
        val nuvioEntries = listOf(
            entry(parentMetaId = "newer-on-account", updatedAt = 300L),
            entry(parentMetaId = "newer-on-trakt", updatedAt = 100L),
            entry(parentMetaId = "tie", updatedAt = 200L),
            entry(parentMetaId = "account-only", updatedAt = 50L),
        )
        val traktNewer = entry(parentMetaId = "newer-on-trakt", updatedAt = 400L)
        val providerEntries = listOf(
            entry(parentMetaId = "newer-on-account", updatedAt = 250L),
            traktNewer,
            entry(parentMetaId = "tie", updatedAt = 200L).copy(title = "from provider"),
            entry(parentMetaId = "trakt-only", updatedAt = 10L),
        )

        val projected = projectWatchProgressSourceEntries(
            source = WatchProgressSource.BOTH,
            nuvioEntries = nuvioEntries,
            providerEntries = providerEntries,
        ).associateBy { it.parentMetaId }

        assertEquals(
            setOf("newer-on-account", "newer-on-trakt", "tie", "account-only", "trakt-only"),
            projected.keys,
        )
        assertEquals(300L, projected.getValue("newer-on-account").lastUpdatedEpochMs)
        assertEquals(traktNewer, projected.getValue("newer-on-trakt"))
        assertEquals("tie", projected.getValue("tie").title)
    }

    private fun entry(
        parentMetaId: String,
        updatedAt: Long = 1L,
    ): WatchProgressEntry = WatchProgressEntry(
        contentType = "series",
        parentMetaId = parentMetaId,
        parentMetaType = "series",
        videoId = "$parentMetaId:1:1",
        title = parentMetaId,
        seasonNumber = 1,
        episodeNumber = 1,
        lastPositionMs = 10L,
        durationMs = 100L,
        lastUpdatedEpochMs = updatedAt,
    )
}
