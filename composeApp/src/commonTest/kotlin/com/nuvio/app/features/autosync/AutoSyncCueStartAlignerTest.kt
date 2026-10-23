package com.nuvio.app.features.autosync

import com.nuvio.app.features.player.SubtitleSyncCue
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoSyncCueStartAlignerTest {
    /** A film's worth of cue starts: irregular gaps of 1.5 to 8 s over about two hours. */
    private fun reference(seed: Int, count: Int = 900): DoubleArray {
        val random = Random(seed)
        var t = 30.0
        return DoubleArray(count) { t += 1.5 + random.nextDouble() * 6.5; t }
    }

    /** The same dialogue as seen by an add-on file: some cues missing, small jitter. */
    private fun addonFrom(ref: DoubleArray, scale: Double, offset: Double, seed: Int): DoubleArray {
        val random = Random(seed)
        return ref.filter { random.nextDouble() > 0.15 }
            .map { (it - offset) / scale + (random.nextDouble() - 0.5) * 0.1 }
            .toDoubleArray()
    }

    @Test
    fun findsConstantOffset() {
        val ref = reference(1)
        val result = AutoSyncCueStartAligner.align(addonFrom(ref, 1.0, 2.35, 2), ref)
        assertTrue(result.confident, result.describe())
        assertEquals(1.0, result.scale)
        assertTrue(abs(result.offsetSeconds - 2.35) < 0.08, result.describe())
    }

    @Test
    fun findsNegativeOffset() {
        val ref = reference(3)
        val result = AutoSyncCueStartAligner.align(addonFrom(ref, 1.0, -41.7, 4), ref)
        assertTrue(result.confident, result.describe())
        assertTrue(abs(result.offsetSeconds + 41.7) < 0.08, result.describe())
    }

    @Test
    fun findsFrameRateScale() {
        val ref = reference(5)
        val scale = 25.0 / 23.976
        val result = AutoSyncCueStartAligner.align(addonFrom(ref, scale, 0.8, 6), ref)
        assertTrue(result.confident, result.describe())
        assertEquals(scale, result.scale)
        assertTrue(abs(result.offsetSeconds - 0.8) < 0.15, result.describe())
    }

    @Test
    fun rejectsAnUnrelatedSubtitle() {
        val result = AutoSyncCueStartAligner.align(reference(7), reference(8))
        assertFalse(result.confident, result.describe())
    }

    @Test
    fun rejectsTooFewCues() {
        val ref = reference(9, count = 30)
        assertFalse(AutoSyncCueStartAligner.align(ref, ref).confident)
    }

    @Test
    fun retimesSrtKeepingText() {
        val body = "1\n00:00:01,000 --> 00:00:02,500\n<i>Olá</i>\n\n2\n01:00:00,000 --> 01:00:01,000\nTchau\n"
        val out = AutoSyncLinearRetimer.retime(body, emptyList(), 2.0, 500)
        assertEquals("srt", out.extension)
        assertTrue(out.text.contains("00:00:02,500 --> 00:00:05,500\n<i>Olá</i>"), out.text)
        assertTrue(out.text.contains("02:00:00,500 --> 02:00:02,500\nTchau"), out.text)
    }

    @Test
    fun retimesAssDialogue() {
        val body = "[Script Info]\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:10.00,0:00:12.50,Default,,0,0,0,,{\\i1}Oi, tudo bem?\n"
        val out = AutoSyncLinearRetimer.retime(body, emptyList(), 1.0, -1_000)
        assertEquals("ass", out.extension)
        assertTrue(out.text.contains("Dialogue: 0,0:00:09.00,0:00:11.50,Default,,0,0,0,,{\\i1}Oi, tudo bem?"), out.text)
    }

    @Test
    fun retimesOtherFormatsFromCues() {
        val cues = listOf(SubtitleSyncCue(1_000, 2_000, "Hello"))
        val out = AutoSyncLinearRetimer.retime("<tt/>", cues, 1.0, 250)
        assertTrue(out.text.contains("00:00:01,250 --> 00:00:02,250\nHello"), out.text)
    }

    // --- Matroska -------------------------------------------------------------------------------

    private fun vint(size: Long): ByteArray = when {
        size < 0x7F -> byteArrayOf((0x80 or size.toInt()).toByte())
        size < 0x3FFF -> byteArrayOf((0x40 or (size shr 8).toInt()).toByte(), size.toByte())
        else -> byteArrayOf(0x08, 0, 0, 0, (size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte())
    }

    private fun idBytes(id: Long): ByteArray {
        val n = when {
            id > 0xFFFFFF -> 4
            id > 0xFFFF -> 3
            id > 0xFF -> 2
            else -> 1
        }
        return ByteArray(n) { (id shr (8 * (n - 1 - it))).toByte() }
    }

    private fun el(id: Long, vararg children: ByteArray): ByteArray {
        val body = children.fold(ByteArray(0)) { acc, b -> acc + b }
        return idBytes(id) + vint(body.size.toLong()) + body
    }

    private fun uint(id: Long, v: Long): ByteArray {
        val bytes = ByteArray(8) { (v shr (8 * (7 - it))).toByte() }.dropWhile { it == 0.toByte() }.toByteArray()
        return el(id, if (bytes.isEmpty()) byteArrayOf(0) else bytes)
    }

    private fun text(id: Long, s: String) = el(id, s.encodeToByteArray())

    @Test
    fun readsSubtitleTimelineFromCues() = runTest {
        val tracks = el(
            0x1654AE6B,
            el(0xAE, uint(0xD7, 1), uint(0x83, 1), text(0x86, "V_MPEG4/ISO/AVC")),
            el(0xAE, uint(0xD7, 3), uint(0x83, 17), text(0x86, "S_TEXT/UTF8"), text(0x22B59C, "por"), text(0x536E, "Forced")),
        )
        val info = el(0x1549A966, uint(0x2AD7B1, 1_000_000))
        val points = (1..50).map { i ->
            el(0xBB, uint(0xB3, i * 2_000L), el(0xB7, uint(0xF7, 3), uint(0xF1, 0), uint(0xB2, 1_500)))
        } + el(0xBB, uint(0xB3, 999), el(0xB7, uint(0xF7, 1), uint(0xF1, 0)))
        val cues = el(0x1C53BB6B, *points.toTypedArray())
        // Segment body: Info, Tracks, Cues (before the Clusters, not in a SeekHead), then a Cluster.
        val segmentBody = info + tracks + cues + el(0x1F43B675, uint(0xE7, 0))
        val file = el(0x1A45DFA3, text(0x4282, "matroska")) + el(0x18538067, segmentBody)
        val reader = MatroskaSubtitleCueIndex.RangeReader { start, end ->
            file.copyOfRange(start.toInt(), minOf(end + 1, file.size.toLong()).toInt())
        }
        val index = MatroskaSubtitleCueIndex.load(reader)
        assertEquals(MatroskaSubtitleCueIndex.Outcome.OK, index.outcome)
        val timeline = index.timelines.single()
        assertEquals(3, timeline.track.number)
        assertEquals("por", timeline.track.language)
        assertEquals(50, timeline.size)
        assertEquals(2.0, timeline.starts.first())
        assertEquals(1.5, timeline.durations.first())
    }

    @Test
    fun notMatroska() = runTest {
        val reader = MatroskaSubtitleCueIndex.RangeReader { _, _ -> "WEBVTT\n\n".encodeToByteArray() }
        assertEquals(MatroskaSubtitleCueIndex.Outcome.NOT_MATROSKA, MatroskaSubtitleCueIndex.load(reader).outcome)
    }
}
