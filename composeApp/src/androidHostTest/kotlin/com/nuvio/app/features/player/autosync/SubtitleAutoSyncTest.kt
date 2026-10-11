package com.nuvio.app.features.player.autosync

import com.nuvio.app.features.player.PlayerSubtitleCueParser
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixtures made like the TV app's tests/legsync.sh: a 600 s black video with ref.srt
 * (105 lines) muxed by mkvmerge (two tracks, Cues with CueDuration), by ffmpeg (Cues
 * without durations), by ffmpeg -live 1 (no Cues) and the mkvmerge file with the Cues
 * entry erased from the SeekHead (Cues at the end, no pointer).
 */
class SubtitleAutoSyncTest {
    private val dir = listOf("src/androidHostTest/resources/autosync", "composeApp/src/androidHostTest/resources/autosync")
        .map(::File).first { it.isDirectory }

    private fun index(name: String): MkvIndexResult {
        val bytes = File(dir, name).readBytes()
        return runBlocking {
            MkvSubtitleIndex.read(MkvSubtitleIndex.RangeSource { start, end ->
                if (start >= bytes.size) null
                else bytes.copyOfRange(start.toInt(), minOf(bytes.size.toLong(), end + 1).toInt())
            })
        }
    }

    private val refStarts: DoubleArray by lazy {
        PlayerSubtitleCueParser.parse(File(dir, "ref.srt").readText(), "ref.srt")
            .map { it.startTimeMs / 1000.0 }.toDoubleArray()
    }

    @Test
    fun mkvmergeIndexHasEveryLineOfBothTracks() {
        val r = index("mkvmerge.mkv")
        assertEquals(MkvIndexReason.OK, r.reason)
        assertEquals(2, r.tracks.size)
        r.tracks.forEach { t ->
            assertEquals(refStarts.size, t.count)
            assertTrue(t.durations.all { it > 0 }, "mkvmerge writes CueDuration")
            assertTrue(abs(t.starts.first() - refStarts.first()) < 0.01)
        }
        assertTrue(r.tracks[0].track.language in setOf("por", "pt"), r.tracks[0].track.language)
        assertEquals("S_TEXT/UTF8", r.tracks[0].track.codec)
    }

    @Test
    fun ffmpegIndexWithoutDurations() {
        val r = index("ffmpeg.mkv")
        assertEquals(MkvIndexReason.OK, r.reason)
        assertEquals(refStarts.size, r.tracks.single().count)
    }

    @Test
    fun liveMuxHasNoCues() {
        val r = index("live.mkv")
        assertTrue(r.tracks.all { it.count == 0 })
        assertTrue(r.reason == MkvIndexReason.NO_CUES || r.reason == MkvIndexReason.CUES_WITHOUT_SUBTITLES)
    }

    @Test
    fun cuesWithoutPointerAreFoundInTheTail() {
        val r = index("semseek-fim.mkv")
        assertEquals(MkvIndexReason.OK, r.reason)
        assertEquals(refStarts.size, r.tracks.first().count)
    }

    @Test
    fun alignsAShiftedSubtitleAgainstTheIndex() {
        val ref = index("mkvmerge.mkv").tracks.first().starts
        val random = Random(3)
        // Addon 2.5 s early, a few lines missing, 60 ms jitter.
        val addon = refStarts.filter { random.nextDouble() > 0.1 }
            .map { it - 2.5 + (random.nextDouble() - 0.5) * 0.12 }.toDoubleArray()
        val a = SubtitleAligner.align(addon, ref)
        assertTrue(a.reliable, "fraction ${a.fraction} chance ${a.chance}")
        assertEquals(1.0, a.scale)
        assertTrue(abs(a.offsetSeconds - 2.5) < 0.1, "offset ${a.offsetSeconds}")
    }

    @Test
    fun detectsFrameRateDrift() {
        val addon = refStarts.map { it * 23.976 / 25.0 + 1.0 }.toDoubleArray()
        val a = SubtitleAligner.align(addon, refStarts)
        assertTrue(a.reliable)
        assertTrue(abs(a.scale - 25.0 / 23.976) < 1e-9, "scale ${a.scale}")
    }

    @Test
    fun rejectsAnUnrelatedSubtitle() {
        val random = Random(11)
        var t = 3.0
        val unrelated = DoubleArray(refStarts.size) { t += 1.5 + random.nextDouble() * 6; t }
        assertFalse(SubtitleAligner.align(unrelated, refStarts).reliable)
    }
}
