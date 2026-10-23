package com.nuvio.app.features.autosync

/**
 * Embedded subtitle timelines from a Matroska file's index (Cues), without downloading video.
 * Port of the TV's mkv_tempos_legendas (Better Nuvio Release/src/mkv.c).
 *
 * mkvmerge writes one CuePoint per subtitle frame (CueTime, and CueDuration for text), so the whole
 * embedded timeline costs the file head (64 KB, 320 KB when Tracks does not fit) plus the Cues
 * element (usually tens to hundreds of KB). Files without Cues give nothing and AutoSync stays off.
 *
 * Network/file access is injected through [RangeReader] so this stays platform independent.
 */
internal object MatroskaSubtitleCueIndex {
    fun interface RangeReader {
        /** Bytes [start, endInclusive] of the source, or null on failure. May return fewer bytes. */
        suspend fun read(start: Long, endInclusive: Long): ByteArray?
    }

    enum class Outcome { OK, NETWORK, NOT_MATROSKA, NO_SUBTITLE_TRACKS, NO_CUES, CUES_WITHOUT_SUBTITLES }

    class Track(
        val number: Int,
        val type: Int,
        val codecId: String,
        val language: String,
        val name: String,
    ) {
        val isSubtitle: Boolean
            get() = type == 17 && (codecId.startsWith("S_TEXT/") || codecId == "S_HDMV/PGS" || codecId == "S_VOBSUB")
        val isText: Boolean get() = codecId.startsWith("S_TEXT/")
    }

    class Timeline(val track: Track, val starts: DoubleArray, val durations: DoubleArray) {
        val size: Int get() = starts.size
    }

    class Index(val outcome: Outcome, val timelines: List<Timeline>)

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

    private const val SIZE_INVALID = -1L
    private const val SIZE_UNKNOWN = -2L

    suspend fun load(reader: RangeReader): Index {
        var head = reader.read(0, HEAD_BYTES - 1) ?: return Index(Outcome.NETWORK, emptyList())
        if (head.size < 4 || readId(head, 0, head.size)?.first != ID_EBML) {
            return Index(Outcome.NOT_MATROSKA, emptyList())
        }
        var header = parseHeader(head, head.size)
        if (!header.complete && (header.tracksCut || header.tracks.isEmpty()) && head.size >= HEAD_BYTES) {
            head = reader.read(0, HEAD_BYTES_LARGE - 1) ?: return Index(Outcome.NETWORK, emptyList())
            header = parseHeader(head, head.size)
        }
        val subtitleTracks = header.tracks.filter { it.isSubtitle }
        if (subtitleTracks.isEmpty()) return Index(Outcome.NO_SUBTITLE_TRACKS, emptyList())

        var cuesAt = if (header.cues >= 0 && header.segment >= 0) header.segment + header.cues else -1L
        // Some remuxers keep only a SeekHead at the start pointing to ANOTHER one at the end.
        if (cuesAt < 0 && header.secondSeekHead >= 0 && header.segment >= 0) {
            val at = header.segment + header.secondSeekHead
            val bytes = reader.read(at, at + 16L * 1024 - 1)
            if (bytes != null && bytes.size > 8) {
                val id = readId(bytes, 0, bytes.size)
                if (id?.first == ID_SEEKHEAD) {
                    val size = readSize(bytes, id.second, bytes.size)
                    if (size != null && size.first > 0) {
                        val start = id.second + size.second
                        val end = minOf(bytes.size.toLong(), start + size.first).toInt()
                        val pointers = SeekPointers()
                        parseSeekHead(bytes, start, end, pointers)
                        if (pointers.cues >= 0) cuesAt = header.segment + pointers.cues
                    }
                }
            }
        }
        // Cues after the Clusters and not listed in any SeekHead: search the file tail backwards,
        // accepting only a Cues whose first child is a CuePoint.
        if (cuesAt < 0 && header.segment >= 0 && header.segmentSize > 0) {
            val segmentEnd = header.segment + header.segmentSize
            val tailStart = maxOf(header.segment, segmentEnd - TAIL_BYTES)
            val tail = reader.read(tailStart, segmentEnd - 1)
            if (tail != null) {
                var k = tail.size - 8
                while (k >= 0 && cuesAt < 0) {
                    if ((tail[k].toInt() and 0xFF) == 0x1C && (tail[k + 1].toInt() and 0xFF) == 0x53 &&
                        (tail[k + 2].toInt() and 0xFF) == 0xBB && (tail[k + 3].toInt() and 0xFF) == 0x6B
                    ) {
                        val size = readSize(tail, k + 4, tail.size)
                        if (size != null && size.first in 1..CUES_MAX) {
                            val child = k + 4 + size.second
                            if (child + 2 <= tail.size && readId(tail, child, tail.size)?.first == ID_CUEPOINT) {
                                cuesAt = tailStart + k
                            }
                        }
                    }
                    k--
                }
            }
        }
        if (cuesAt < 0) return Index(Outcome.NO_CUES, emptyList())

        var cues = reader.read(cuesAt, cuesAt + 64L * 1024 - 1) ?: return Index(Outcome.NETWORK, emptyList())
        if (cues.size >= 12) {
            val id = readId(cues, 0, cues.size)
            val size = if (id?.first == ID_CUES) readSize(cues, id.second, cues.size) else null
            if (id != null && size != null && size.first > 0) {
                val total = id.second + size.second + size.first
                if (total > cues.size && total <= CUES_MAX) {
                    cues = reader.read(cuesAt, cuesAt + total - 1) ?: return Index(Outcome.NETWORK, emptyList())
                }
            }
        }
        val timelines = parseCues(header, cues)
        val outcome = if (timelines.all { it.size == 0 }) Outcome.CUES_WITHOUT_SUBTITLES else Outcome.OK
        return Index(outcome, timelines)
    }

    // --- header ------------------------------------------------------------------------------

    private class SeekPointers {
        var cues = -1L
        var secondSeekHead = -1L
    }

    class Header internal constructor(
        val segment: Long,
        val segmentSize: Long,
        val cues: Long,
        val secondSeekHead: Long,
        val timestampScaleNs: Long,
        val tracks: List<Track>,
        val tracksCut: Boolean,
    ) {
        val complete: Boolean get() = segment >= 0 && cues >= 0 && tracks.isNotEmpty()
    }

    /** Walks the top of the Segment up to the first Cluster. */
    fun parseHeader(p: ByteArray, n: Int): Header {
        var o = 0
        var segment = -1L
        var segmentSize = 0L
        var timestampScale = 1_000_000L
        val pointers = SeekPointers()
        var tracks: List<Track> = emptyList()
        var tracksCut = false
        while (o < n) {
            val id = readId(p, o, n) ?: break
            val size = readSize(p, o + id.second, n) ?: break
            o += id.second + size.second
            val tam = size.first
            if (id.first == ID_SEGMENT) {
                segment = o.toLong()
                segmentSize = tam
                continue
            }
            if (id.first == ID_CLUSTER || tam == SIZE_UNKNOWN) break
            // Cues BEFORE the Clusters without a SeekHead entry: its position is right here.
            if (id.first == ID_CUES && segment >= 0) {
                if (pointers.cues < 0) pointers.cues = (o - id.second - size.second) - segment
                if (o + tam > n) break
                o += tam.toInt()
                continue
            }
            if (id.first == ID_TRACKS) {
                val end = if (o + tam > n) n else (o + tam).toInt()
                tracks = parseTracks(p, o, end)
                if (o + tam > n) { tracksCut = true; break }
            } else if (o + tam > n) {
                break
            } else if (id.first == ID_SEEKHEAD) {
                parseSeekHead(p, o, (o + tam).toInt(), pointers)
            } else if (id.first == ID_INFO) {
                forEachChild(p, o, (o + tam).toInt()) { childId, start, len ->
                    if (childId == ID_TCSCALE) {
                        val e = readUint(p, start, len)
                        if (e > 0) timestampScale = e
                    }
                }
            } else if (id.first != ID_EBML && segment < 0) {
                break // did not start with EBML + Segment: not Matroska
            }
            o += tam.toInt()
        }
        return Header(segment, segmentSize, pointers.cues, pointers.secondSeekHead, timestampScale, tracks, tracksCut)
    }

    private fun parseSeekHead(p: ByteArray, from: Int, to: Int, out: SeekPointers) {
        forEachChild(p, from, to) { id, start, len ->
            if (id != ID_SEEK) return@forEachChild
            var target = 0L
            var position = -1L
            forEachChild(p, start, start + len) { fid, fstart, flen ->
                if (fid == ID_SEEKID) target = readUint(p, fstart, flen)
                else if (fid == ID_SEEKPOS) position = readUint(p, fstart, flen)
            }
            if (target == ID_CUES && position >= 0) out.cues = position
            else if (target == ID_SEEKHEAD && position >= 0) out.secondSeekHead = position
        }
    }

    private fun parseTracks(p: ByteArray, from: Int, to: Int): List<Track> {
        val out = ArrayList<Track>()
        forEachChild(p, from, to) { id, start, len ->
            if (id != ID_TRACKENTRY) return@forEachChild
            var number = 0
            var type = 0
            var codec = ""
            var language = ""
            var bcp47 = ""
            var name = ""
            forEachChild(p, start, start + len) { fid, fstart, flen ->
                when (fid) {
                    ID_TRACKNUMBER -> number = readUint(p, fstart, flen).toInt()
                    ID_TRACKTYPE -> type = readUint(p, fstart, flen).toInt()
                    ID_LANGUAGE -> language = readText(p, fstart, flen)
                    ID_LANG_BCP47 -> bcp47 = readText(p, fstart, flen)
                    ID_NAME -> name = readText(p, fstart, flen)
                    ID_CODECID -> codec = readText(p, fstart, flen)
                }
            }
            // BCP47 ("pt-BR") wins over ISO 639-2 ("por") when both exist.
            if (number > 0) out += Track(number, type, codec, bcp47.ifBlank { language }, name)
        }
        return out
    }

    // --- cues --------------------------------------------------------------------------------

    fun parseCues(header: Header, cues: ByteArray): List<Timeline> {
        val tracks = header.tracks.filter { it.isSubtitle }
        if (tracks.isEmpty()) return emptyList()
        val starts = tracks.associate { it.number to ArrayList<Double>() }
        val durations = tracks.associate { it.number to ArrayList<Double>() }
        val scale = header.timestampScaleNs.toDouble() / 1e9
        val id = readId(cues, 0, cues.size)
        val size = if (id?.first == ID_CUES) readSize(cues, id.second, cues.size) else null
        if (id != null && size != null && size.first >= 0) {
            val begin = id.second + size.second
            val end = minOf(cues.size.toLong(), begin + size.first).toInt()
            forEachChild(cues, begin, end) { pid, pstart, plen ->
                if (pid != ID_CUEPOINT) return@forEachChild
                var time = -1.0
                // CueTime precedes the positions in mkvmerge output, but the format does not
                // promise it: two passes over the (small) point.
                forEachChild(cues, pstart, pstart + plen) { fid, fstart, flen ->
                    if (fid == ID_CUETIME) time = readUint(cues, fstart, flen) * scale
                }
                if (time < 0) return@forEachChild
                forEachChild(cues, pstart, pstart + plen) { fid, fstart, flen ->
                    if (fid != ID_CUETRACKPOS) return@forEachChild
                    var track = -1
                    var duration = 0.0
                    forEachChild(cues, fstart, fstart + flen) { gid, gstart, glen ->
                        if (gid == ID_CUETRACK) track = readUint(cues, gstart, glen).toInt()
                        else if (gid == ID_CUEDURATION) duration = readUint(cues, gstart, glen) * scale
                    }
                    starts[track]?.add(time)
                    durations[track]?.add(duration)
                }
            }
        }
        return tracks.map { track ->
            Timeline(
                track = track,
                starts = starts.getValue(track.number).toDoubleArray(),
                durations = durations.getValue(track.number).toDoubleArray(),
            )
        }
    }

    // --- EBML primitives ---------------------------------------------------------------------

    private fun widthOf(b: Int): Int {
        for (i in 0 until 8) if (b and (0x80 shr i) != 0) return i + 1
        return 0
    }

    /** ID keeping its marker bit, and its width; null at the end or on invalid data. */
    private fun readId(p: ByteArray, at: Int, limit: Int): Pair<Long, Int>? {
        if (at >= limit) return null
        val w = widthOf(p[at].toInt() and 0xFF)
        if (w < 1 || w > 4 || at + w > limit) return null
        var v = 0L
        for (i in 0 until w) v = (v shl 8) or (p[at + i].toLong() and 0xFF)
        return v to w
    }

    /** Size without the marker bit, and its width; [SIZE_UNKNOWN] when all data bits are 1. */
    private fun readSize(p: ByteArray, at: Int, limit: Int): Pair<Long, Int>? {
        if (at >= limit) return null
        val first = p[at].toInt() and 0xFF
        val w = widthOf(first)
        if (w < 1 || w > 8 || at + w > limit) return null
        val mask = 0xFF shr w
        var v = (first and mask).toLong()
        var allOnes = (first and mask) == mask
        for (i in 1 until w) {
            val b = p[at + i].toInt() and 0xFF
            if (b != 0xFF) allOnes = false
            v = (v shl 8) or b.toLong()
        }
        return (if (allOnes) SIZE_UNKNOWN else v) to w
    }

    private fun readUint(p: ByteArray, at: Int, len: Int): Long {
        if (len < 1 || len > 8) return 0
        var v = 0L
        for (i in 0 until len) v = (v shl 8) or (p[at + i].toLong() and 0xFF)
        return v
    }

    private fun readText(p: ByteArray, at: Int, len: Int): String {
        var end = at
        while (end < at + len && p[end].toInt() != 0) end++
        return p.decodeToString(at, end).trim()
    }

    /** Calls [block] with (id, dataStart, dataLength) for each complete child in [from, to). */
    private inline fun forEachChild(p: ByteArray, from: Int, to: Int, block: (Long, Int, Int) -> Unit) {
        var o = from
        val limit = minOf(to, p.size)
        while (o < limit) {
            val id = readId(p, o, limit) ?: return
            val size = readSize(p, o + id.second, limit) ?: return
            if (size.first < 0) return
            val start = o + id.second + size.second
            if (start + size.first > limit) return
            block(id.first, start, size.first.toInt())
            o = start + size.first.toInt()
        }
    }
}
