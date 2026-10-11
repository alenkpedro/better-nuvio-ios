package com.nuvio.app.features.player.autosync

import com.nuvio.app.core.network.createApiHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable

/**
 * SUBTITLE TIMINGS FROM THE MKV INDEX (Cues), for AutoSync. Port of mkv.c from the
 * Better Nuvio TV app.
 *
 * mkvmerge writes one Cues point PER LINE of every subtitle track (CueTime and
 * CueDuration). A whole movie's index fits in a few hundred KB, so the embedded
 * subtitle's full timeline comes out without downloading a single video byte. Only
 * the start of the file (64 KB, 320 KB when Tracks is large) and the Cues element
 * are read by HTTP Range — never Clusters. Text and IMAGE (PGS, VobSub) tracks count:
 * only the timing is used, never the content.
 */
data class MkvTrack(
    val number: Int,
    val type: Int,
    val language: String,
    val name: String,
    val codec: String,
) {
    val isImage: Boolean get() = !codec.startsWith("S_TEXT/")
}

class MkvSubtitleTimes(val track: MkvTrack, val starts: DoubleArray, val durations: DoubleArray) {
    val count: Int get() = starts.size
}

enum class MkvIndexReason { OK, NETWORK, NOT_MKV, NO_SUBTITLES, NO_CUES, CUES_WITHOUT_SUBTITLES }

class MkvIndexResult(val tracks: List<MkvSubtitleTimes>, val reason: MkvIndexReason, val httpStatus: Int = 0)

/** Bounded HTTP Range GET. Never reads past the requested length, even from a server that ignores Range. */
internal class RangeFetcher(private val requestHeaders: Map<String, String>) {
    private val client: HttpClient by lazy { createApiHttpClient() }
    var lastStatus = 0
        private set

    suspend fun fetch(url: String, start: Long, endInclusive: Long): ByteArray? {
        val wanted = (endInclusive - start + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        var target = url
        repeat(6) {
            val response = runCatching { get(target, start, endInclusive, wanted) }.getOrNull()
                ?: return null.also { lastStatus = 0 }
            lastStatus = response.first
            val location = response.third
            if (response.first in 300..399 && location != null) {
                target = URLBuilder().takeFrom(target).takeFrom(location).buildString()
                return@repeat
            }
            // 200 is only acceptable from the start of the file: a server that
            // ignores Range would otherwise hand us the wrong bytes.
            if (response.first == 206 || (response.first == 200 && start == 0L)) return response.second
            return null
        }
        return null
    }

    private suspend fun get(url: String, start: Long, end: Long, wanted: Int): Triple<Int, ByteArray, String?> =
        client.prepareGet(url) {
            requestHeaders.forEach { (k, v) ->
                if (!k.equals(HttpHeaders.Range, true) && !k.equals(HttpHeaders.AcceptEncoding, true)) header(k, v)
            }
            header(HttpHeaders.Range, "bytes=$start-$end")
            header(HttpHeaders.AcceptEncoding, "identity")
        }.execute { response ->
            val status = response.status.value
            if (status in 300..399) return@execute Triple(status, ByteArray(0), response.headers[HttpHeaders.Location])
            if (status != 200 && status != 206) return@execute Triple(status, ByteArray(0), null)
            val channel = response.bodyAsChannel()
            val out = ByteArray(wanted)
            var total = 0
            while (total < wanted) {
                val n = channel.readAvailable(out, total, wanted - total)
                if (n == -1) break
                total += n
            }
            Triple(status, out.copyOf(total), null)
        }
}

object MkvSubtitleIndex {
    private const val HEAD_BYTES = 64L * 1024
    private const val HEAD_BYTES_LARGE = 320L * 1024
    private const val CUES_MAX = 8L * 1024 * 1024
    private const val TAIL_BYTES = 4L * 1024 * 1024

    private const val ID_EBML = 0x1A45DFA3L
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_SEEKHEAD = 0x114D9B74L
    private const val ID_SEEK = 0x4DBBL
    private const val ID_SEEKID = 0x53ABL
    private const val ID_SEEKPOS = 0x53ACL
    private const val ID_INFO = 0x1549A966L
    private const val ID_TCSCALE = 0x2AD7B1L
    private const val ID_TRACKS = 0x1654AE6BL
    private const val ID_TRACKENTRY = 0xAEL
    private const val ID_TRACKNUMBER = 0xD7L
    private const val ID_TRACKTYPE = 0x83L
    private const val ID_LANGUAGE = 0x22B59CL
    private const val ID_LANG_BCP47 = 0x22B59DL
    private const val ID_NAME = 0x536EL
    private const val ID_CODECID = 0x86L
    private const val ID_CLUSTER = 0x1F43B675L
    private const val ID_CUES = 0x1C53BB6BL
    private const val ID_CUEPOINT = 0xBBL
    private const val ID_CUETIME = 0xB3L
    private const val ID_CUETRACKPOS = 0xB7L
    private const val ID_CUETRACK = 0xF7L
    private const val ID_CUEDURATION = 0xB2L

    // --- EBML variable-size integers ------------------------------------------------
    // The first byte tells, by the position of its highest 1 bit, how many bytes the
    // number takes. In an ID that bit IS part of the value; in a SIZE it is a marker.
    private class Vint(val value: Long, val width: Int)

    private fun widthOf(b: Int): Int {
        for (i in 0 until 8) if (b and (0x80 ushr i) != 0) return i + 1
        return 0
    }

    private fun readId(p: ByteArray, o: Int, end: Int): Vint? {
        if (o >= end) return null
        val w = widthOf(p[o].toInt() and 0xFF)
        if (w < 1 || w > 4 || end - o < w) return null
        var v = 0L
        for (i in 0 until w) v = (v shl 8) or (p[o + i].toLong() and 0xFF)
        return Vint(v, w)
    }

    /** -2 = unknown size (all data bits set), null = invalid. */
    private fun readSize(p: ByteArray, o: Int, end: Int): Vint? {
        if (o >= end) return null
        val first = p[o].toInt() and 0xFF
        val w = widthOf(first)
        if (w < 1 || w > 8 || end - o < w) return null
        val mask = 0xFF ushr w
        var v = (first and mask).toLong()
        var allOnes = (first and mask) == mask
        for (i in 1 until w) {
            val b = p[o + i].toInt() and 0xFF
            if (b != 0xFF) allOnes = false
            v = (v shl 8) or b.toLong()
        }
        return Vint(if (allOnes) -2 else v, w)
    }

    private fun readUint(p: ByteArray, o: Int, n: Int): Long {
        if (n < 1 || n > 8) return 0
        var v = 0L
        for (i in 0 until n) v = (v shl 8) or (p[o + i].toLong() and 0xFF)
        return v
    }

    private fun readText(p: ByteArray, o: Int, n: Int): String {
        var k = 0
        while (k < n && p[o + k] != 0.toByte()) k++
        return p.decodeToString(o, o + k)
    }

    /** Iterates the children of [start, end): (id, dataOffset, dataSize). Stops on invalid data. */
    private inline fun children(p: ByteArray, start: Int, end: Int, visit: (Long, Int, Int) -> Unit) {
        var o = start
        while (o < end) {
            val id = readId(p, o, end) ?: return
            val size = readSize(p, o + id.width, end) ?: return
            if (size.value < 0) return
            val data = o + id.width + size.width
            if (data + size.value > end) return
            visit(id.value, data, size.value.toInt())
            o = data + size.value.toInt()
        }
    }

    private fun readTracks(p: ByteArray, start: Int, end: Int): List<MkvTrack> {
        val out = ArrayList<MkvTrack>()
        children(p, start, end) { id, o, n ->
            if (id != ID_TRACKENTRY) return@children
            var number = 0
            var type = 0
            var language = ""
            var name = ""
            var codec = ""
            children(p, o, o + n) { fid, v, fn ->
                when (fid) {
                    ID_TRACKNUMBER -> number = readUint(p, v, fn).toInt()
                    ID_TRACKTYPE -> type = readUint(p, v, fn).toInt()
                    // BCP47 ("pt-BR") wins over ISO 639-2 when both exist.
                    ID_LANG_BCP47 -> language = readText(p, v, fn)
                    ID_LANGUAGE -> if (language.isEmpty()) language = readText(p, v, fn)
                    ID_NAME -> name = readText(p, v, fn)
                    ID_CODECID -> codec = readText(p, v, fn)
                }
            }
            if (number > 0) out += MkvTrack(number, type, language, name, codec)
        }
        return out
    }

    private class Header {
        var segment = -1L        // offset of the Segment DATA in the file
        var cues = -1L           // Cues position relative to the segment
        var seek2 = -1L          // SECONDARY SeekHead (relative to the segment)
        var segmentSize = -1L    // -2 = unknown
        var timecodeScale = 1_000_000L
        var tracks: List<MkvTrack> = emptyList()
        var tracksCut = false
        val ok get() = segment >= 0 && cues >= 0 && tracks.isNotEmpty()
    }

    private fun readSeekHead(p: ByteArray, start: Int, end: Int, h: Header) {
        children(p, start, end) { id, o, n ->
            if (id != ID_SEEK) return@children
            var target = 0L
            var pos = -1L
            children(p, o, o + n) { fid, v, fn ->
                if (fid == ID_SEEKID) target = readUint(p, v, fn)
                else if (fid == ID_SEEKPOS) pos = readUint(p, v, fn)
            }
            if (target == ID_CUES && pos >= 0) h.cues = pos
            // Some remuxers leave at the start only a SeekHead that points to ANOTHER
            // one, at the end of the file, where the Cues is.
            else if (target == ID_SEEKHEAD && pos >= 0) h.seek2 = pos
        }
    }

    /** Walks the top of the Segment until the first Cluster. */
    private fun readHeader(p: ByteArray, n: Int): Header {
        val h = Header()
        var o = 0
        while (o < n) {
            val id = readId(p, o, n) ?: break
            val size = readSize(p, o + id.width, n) ?: break
            val tam = size.value
            val head = id.width + size.width
            o += head
            if (id.value == ID_SEGMENT) { h.segment = o.toLong(); h.segmentSize = tam; continue }
            if (id.value == ID_CLUSTER || tam == -2L) break
            // Cues BEFORE the Clusters with no SeekHead entry: its position is this.
            if (id.value == ID_CUES && h.segment >= 0) {
                if (h.cues < 0) h.cues = o - head - h.segment
                if (o + tam > n) break
                o += tam.toInt()
                continue
            }
            if (id.value == ID_TRACKS) {
                val t = if (tam > n - o) n - o else tam.toInt()
                h.tracks = readTracks(p, o, o + t)
                if (tam > n - o) { h.tracksCut = true; break }
            } else if (o + tam > n) {
                break
            } else if (id.value == ID_SEEKHEAD) {
                readSeekHead(p, o, o + tam.toInt(), h)
            } else if (id.value == ID_INFO) {
                children(p, o, o + tam.toInt()) { fid, v, fn ->
                    if (fid == ID_TCSCALE) readUint(p, v, fn).takeIf { it > 0 }?.let { h.timecodeScale = it }
                }
            } else if (id.value != ID_EBML && h.segment < 0) {
                break // did not start with EBML + Segment: not Matroska
            }
            o += tam.toInt()
        }
        return h
    }

    private fun isSubtitle(t: MkvTrack): Boolean =
        t.type == 17 && (t.codec.startsWith("S_TEXT/") || t.codec == "S_HDMV/PGS" || t.codec == "S_VOBSUB")

    private fun isEbml(b: ByteArray?): Boolean =
        b != null && b.size >= 4 && b[0] == 0x1A.toByte() && b[1] == 0x45.toByte() &&
            b[2] == 0xDF.toByte() && b[3] == 0xA3.toByte()

    /** Same reading over bytes already in memory (the file start and the whole Cues). Pure. */
    fun timesFromIndex(head: ByteArray, cues: ByteArray): List<MkvSubtitleTimes> {
        val h = readHeader(head, head.size)
        val subs = h.tracks.filter(::isSubtitle)
        if (subs.isEmpty()) return emptyList()
        val starts = subs.associate { it.number to ArrayList<Double>() }
        val durs = subs.associate { it.number to ArrayList<Double>() }
        val scale = h.timecodeScale.toDouble() / 1e9
        val id = readId(cues, 0, cues.size)
        val size = if (id?.value == ID_CUES) readSize(cues, id.width, cues.size) else null
        if (id != null && size != null && size.value >= 0) {
            val begin = id.width + size.width
            val end = minOf(cues.size.toLong(), begin + size.value).toInt()
            children(cues, begin, end) { pid, po, pn ->
                if (pid != ID_CUEPOINT) return@children
                var time = -1.0
                // CueTime comes before the positions in mkvmerge files, but the
                // format promises no order: two passes over the (small) point.
                children(cues, po, po + pn) { fid, v, fn -> if (fid == ID_CUETIME) time = readUint(cues, v, fn) * scale }
                if (time < 0) return@children
                children(cues, po, po + pn) { fid, v, fn ->
                    if (fid != ID_CUETRACKPOS) return@children
                    var track = -1
                    var dur = 0.0
                    children(cues, v, v + fn) { gid, g, gn ->
                        if (gid == ID_CUETRACK) track = readUint(cues, g, gn).toInt()
                        else if (gid == ID_CUEDURATION) dur = readUint(cues, g, gn) * scale
                    }
                    starts[track]?.add(time)
                    durs[track]?.add(dur)
                }
            }
        }
        return subs.map { t ->
            MkvSubtitleTimes(t, starts.getValue(t.number).toDoubleArray(), durs.getValue(t.number).toDoubleArray())
        }
    }

    /** Downloads the file start and the Cues element of [url]. Blocks on network: call off the main thread. */
    suspend fun read(url: String, headers: Map<String, String>): MkvIndexResult {
        val net = RangeFetcher(headers)
        return read(RangeSource { start, end -> net.fetch(url, start, end) }) { net.lastStatus }
    }

    /** A byte-range reader: the network in the app, a file in tests. */
    fun interface RangeSource {
        suspend fun fetch(start: Long, endInclusive: Long): ByteArray?
    }

    internal suspend fun read(net: RangeSource, lastStatus: () -> Int = { 0 }): MkvIndexResult {
        fun fail(reason: MkvIndexReason) = MkvIndexResult(emptyList(), reason, lastStatus())
        var head = net.fetch(0, HEAD_BYTES - 1) ?: return fail(MkvIndexReason.NETWORK)
        if (!isEbml(head)) {
            println("[autosync] index: not Matroska (${head.size} bytes at start)")
            return fail(MkvIndexReason.NOT_MKV)
        }
        var h = readHeader(head, head.size)
        // 64 KB first: SeekHead, Info and Tracks usually fit in the first 10. Only a
        // huge Tracks (dozens of subtitles) asks for the larger chunk.
        if (!h.ok && (h.tracksCut || h.tracks.isEmpty()) && head.size >= HEAD_BYTES) {
            head = net.fetch(0, HEAD_BYTES_LARGE - 1) ?: return fail(MkvIndexReason.NETWORK)
            h = readHeader(head, head.size)
        }
        val subtitleCount = h.tracks.count(::isSubtitle)
        if (subtitleCount == 0) {
            println("[autosync] index: ${h.tracks.size} track(s) and no subtitles")
            return fail(MkvIndexReason.NO_SUBTITLES)
        }
        var cuesAt = if (h.cues >= 0) h.segment + h.cues else -1L
        if (cuesAt < 0 && h.seek2 >= 0 && h.segment >= 0) {
            val at = h.segment + h.seek2
            val s2 = net.fetch(at, at + 16L * 1024 - 1)
            if (s2 != null && s2.size > 8) {
                val id = readId(s2, 0, s2.size)
                val size = if (id?.value == ID_SEEKHEAD) readSize(s2, id.width, s2.size) else null
                if (id != null && size != null && size.value > 0) {
                    val begin = id.width + size.width
                    readSeekHead(s2, begin, minOf(s2.size.toLong(), begin + size.value).toInt(), h)
                    if (h.cues >= 0) cuesAt = h.segment + h.cues
                }
            }
            println("[autosync] index: secondary SeekHead at $at: Cues ${if (cuesAt >= 0) "found" else "missing"}")
        }
        // CUES WITH NO POINTER, AT THE END OF THE FILE. Some remuxers write the Cues
        // after the Clusters and never list it in the SeekHead. The Segment end comes
        // from the header: fetch the last MB and search backwards, accepting only a
        // Cues whose first child is a CuePoint.
        val segmentEnd = if (h.segment >= 0 && h.segmentSize > 0) h.segment + h.segmentSize else -1L
        if (cuesAt < 0 && segmentEnd > 0) {
            val tailStart = maxOf(segmentEnd - TAIL_BYTES, h.segment)
            val tail = net.fetch(tailStart, segmentEnd - 1)
            if (tail != null) {
                var k = tail.size - 8
                while (k >= 0 && cuesAt < 0) {
                    if (tail[k] == 0x1C.toByte() && tail[k + 1] == 0x53.toByte() &&
                        tail[k + 2] == 0xBB.toByte() && tail[k + 3] == 0x6B.toByte()
                    ) {
                        val size = readSize(tail, k + 4, tail.size)
                        if (size != null && size.value in 1..CUES_MAX &&
                            k + 4 + size.width + 2 <= tail.size &&
                            readId(tail, k + 4 + size.width, tail.size)?.value == ID_CUEPOINT
                        ) cuesAt = tailStart + k
                    }
                    k--
                }
            }
            println("[autosync] index: Cues without pointer, searched the last ${segmentEnd - tailStart} bytes: ${if (cuesAt >= 0) "found" else "missing"}")
        }
        if (cuesAt < 0) {
            println("[autosync] index: $subtitleCount subtitle(s) in Tracks, but the file has no Cues")
            return fail(MkvIndexReason.NO_CUES)
        }
        var cues = net.fetch(cuesAt, cuesAt + 64L * 1024 - 1) ?: return fail(MkvIndexReason.NETWORK)
        val id = readId(cues, 0, cues.size)
        val size = if (id?.value == ID_CUES) readSize(cues, id.width, cues.size) else null
        if (id != null && size != null && size.value > 0) {
            val total = id.width + size.width + size.value
            if (total > cues.size && total <= CUES_MAX) {
                cues = net.fetch(cuesAt, cuesAt + total - 1) ?: return fail(MkvIndexReason.NETWORK)
            }
        }
        val tracks = timesFromIndex(head, cues)
        val points = tracks.sumOf { it.count }
        println("[autosync] index: ${tracks.size} subtitle(s), $points points, Cues of ${cues.size} bytes at $cuesAt")
        return MkvIndexResult(tracks, if (points > 0) MkvIndexReason.OK else MkvIndexReason.CUES_WITHOUT_SUBTITLES)
    }
}
