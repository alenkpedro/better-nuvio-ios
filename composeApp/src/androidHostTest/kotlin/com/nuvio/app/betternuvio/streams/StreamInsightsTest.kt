package com.nuvio.app.betternuvio.streams

import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamInsightsTest {
    private fun stream(name: String, description: String, filename: String? = null, size: Long? = null, url: String? = null) =
        StreamItem(
            name = name, description = description, url = url, addonName = "Addon", addonId = "addon",
            behaviorHints = StreamBehaviorHints(filename = filename, videoSize = size),
        )

    @Test
    fun torrentioStyle4kDolbyVisionAtmos() {
        val s = stream(
            "Torrentio\n4k DV | HDR",
            "Movie.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-GRP\n👤 12 💾 18.4 GB ⚙️ TPB\n🇬🇧 / 🇧🇷",
        )
        val i = StreamInsightsParser.parse(s)
        assertEquals(2160, i.height)
        assertTrue(i.dolbyVision)
        assertTrue(i.atmos)
        assertTrue("r-4k" in i.badges)
        assertTrue("q-webdl" in i.badges)
        assertTrue("a-atmos-dv" in i.badges)
        assertTrue("co-x265" in i.badges)
        assertEquals(listOf("PT-BR", "EN"), i.audioLanguages)
        assertTrue((i.sizeBytes ?: 0) > 18L * 1024 * 1024 * 1024)
    }

    @Test
    fun subtitleLanguagesAreNotAudio() {
        // AIOStreams: flag = audio, "≡" = subtitles.
        val s = stream("AIOStreams 1080p", "⚑ PT  ≡ PT-BR · ES\nMovie.1080p.BluRay.x264")
        assertEquals(listOf("PT"), StreamInsightsParser.parse(s).audioLanguages)
        val f = stream("Addon", "Show.S01E01.1080p.WEB.h264 sub ita eng")
        assertFalse("EN" in StreamInsightsParser.parse(f).audioLanguages)
    }

    @Test
    fun dualAudioAndUncached() {
        val s = stream("[TB download] 720p", "Serie.S01E02.720p.DUAL-GRUPO ⏳")
        val i = StreamInsightsParser.parse(s)
        assertTrue(i.uncached)
        assertTrue("DUAL" in i.audioLanguages)
        assertEquals(720, i.height)
    }

    @Test
    fun dvdIsNotDolbyVision() {
        val i = StreamInsightsParser.parse(stream("Addon", "Old.Movie.1999.DVDRip.XviD"))
        assertFalse(i.dolbyVision)
        assertFalse("v-dv" in i.badges)
    }

    @Test
    fun containerAndExactSize() {
        val i = StreamInsightsParser.parse(stream("Direct", "Movie", url = "https://x/y/movie.mp4", size = 2_147_483_648))
        assertEquals("MP4", i.container)
        assertEquals(2_147_483_648, i.sizeBytes)
    }
}
