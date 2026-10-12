package com.nuvio.app.betternuvio.streams

import com.nuvio.app.features.streams.StreamItem

/**
 * What the Better Nuvio TV app reads out of a stream's text (stream_parse.c,
 * badges.c, fontepref.c): resolution, Dolby Vision, Atmos, container, size,
 * "not cached on debrid", the badge set and the AUDIO languages. Pure functions,
 * so the list can compute it once per stream.
 */
data class StreamInsights(
    val height: Int,
    val dolbyVision: Boolean,
    val atmos: Boolean,
    val container: String?,
    val sizeBytes: Long?,
    val uncached: Boolean,
    val badges: List<String>,
    val audioLanguages: List<String>,
)

object StreamInsightsParser {
    /** Badge ids in drawing order (the TV's badges.c `ids[]`). */
    val BADGE_IDS = listOf(
        "r-4k", "r-1080", "r-720", "q-remux", "q-bluray", "q-webdl", "q-webrip", "q-seadex",
        "v-dv", "v-hdr10plus", "v-hdr10", "v-hdr", "v-imax-enhanced", "v-imax", "v-sdr",
        "a-atmos-dv", "a-atmos", "a-truehd-dv", "a-truehd", "a-dtsx", "a-dtshdma", "a-dtshd", "a-dts",
        "a-dd-dv", "a-ddp", "a-dd", "c-71", "c-51", "co-x265", "co-x264", "co-av1",
        "p-netflix", "p-prime", "p-appletv", "p-disney", "p-max", "p-hulu", "p-peacock",
        "p-paramount", "p-crave", "p-crunchyroll",
    )

    private val cache = HashMap<String, StreamInsights>()

    fun of(stream: StreamItem): StreamInsights {
        val key = listOf(stream.addonId, stream.url, stream.infoHash, stream.fileIdx, stream.name, stream.description)
            .joinToString("\u0001")
        cache[key]?.let { return it }
        return parse(stream).also {
            if (cache.size > 600) cache.clear()
            cache[key] = it
        }
    }

    fun parse(stream: StreamItem): StreamInsights {
        val name = stream.name.orEmpty()
        val description = stream.description?.takeIf { it.isNotBlank() } ?: stream.title.orEmpty()
        val filename = stream.behaviorHints.filename.orEmpty()
        val text = "$name $description ${stream.title.orEmpty()} $filename"
        val url = stream.url.orEmpty()
        val height = when {
            "2160" in text || token(text, "4k") || token(text, "uhd") -> 2160
            "1440" in text -> 1440
            "1080" in text -> 1080
            "720" in text -> 720
            "480" in text -> 480
            else -> 0
        }
        val dv = token(text, "dv") || token(text, "dovi") ||
            text.contains("dolby vision", true) || text.contains("dolbyvision", true)
        val mp4 = token(text, "mp4") || url.contains(".mp4", true)
        val container = when {
            mp4 -> "MP4"
            url.contains(".mkv", true) || filename.contains(".mkv", true) || description.contains(".mkv", true) -> "MKV"
            url.contains(".m3u8", true) || name.contains("HLS") -> "HLS"
            else -> null
        }
        return StreamInsights(
            height = height,
            dolbyVision = dv,
            atmos = token(text, "atmos"),
            container = container,
            sizeBytes = stream.behaviorHints.videoSize?.takeIf { it > 0 } ?: sizeFromText(text),
            uncached = isUncachedText(text),
            badges = detectBadges(text),
            audioLanguages = audioLanguages(name, description, filename),
        )
    }

    /** ⏳ (AIOStreams), ⬇, "[TB download]" / "[RD download]", "uncached". */
    fun isUncachedText(t: String): Boolean =
        "⏳" in t || "⬇" in t || t.contains(" download]", true) || t.contains("uncached", true)

    private fun sizeFromText(text: String): Long? {
        var at = text.indexOf(" GB")
        var scale = 1024.0 * 1024 * 1024
        if (at < 0) { at = text.indexOf(" MB"); scale = 1024.0 * 1024 }
        if (at <= 0) return null
        var start = at
        while (start > 0 && (text[start - 1].isDigit() || text[start - 1] == '.' || text[start - 1] == ',')) start--
        val number = text.substring(start, at).replace(',', '.').toDoubleOrNull() ?: return null
        return (number * scale).toLong().takeIf { it > 0 }
    }

    /** Isolated token, case-insensitive: "dv" never matches inside "dvd". */
    private fun token(s: String, t: String): Boolean {
        var from = 0
        while (true) {
            val i = s.indexOf(t, from, ignoreCase = true)
            if (i < 0) return false
            val before = if (i > 0) s[i - 1] else ' '
            val after = if (i + t.length < s.length) s[i + t.length] else ' '
            if (!before.isAsciiAlnum() && !after.isAsciiAlnum()) return true
            from = i + 1
        }
    }

    private fun Char.isAsciiAlnum() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    fun detectBadges(metadata: String): List<String> {
        val s = metadata.lowercase()
        val m = HashSet<String>()
        fun has(t: String) = token(s, t)
        val dv = has("dv") || has("dovi") || has("dolby vision") || has("dolby.vision") ||
            has("dolby-vision") || has("dolby_vision")
        val atmos = has("atmos")
        val thd = has("truehd") || has("true-hd") || has("true hd")
        val ddp = has("ddp") || "ddp5" in s || "ddp7" in s || has("dd+") || has("eac3") || has("eac-3") || has("e-ac-3")
        val dd = has("ac3") || has("ac-3") || has("dd5.1") || has("dd2.0")
        if ((has("4k") || has("2160p") || has("2160") || has("uhd")) && !has("1080p") && !has("720p")) m += "r-4k"
        if (has("1080p") || has("1080i") || has("1080")) m += "r-1080"
        if (has("720p") || has("720")) m += "r-720"
        if (has("remux")) m += "q-remux" else if (has("bluray") || has("blu-ray")) m += "q-bluray"
        if (has("web-dl") || has("webdl") || has("web.dl") || has("web dl")) m += "q-webdl"
        if (has("webrip") || has("web-rip") || has("web.rip")) m += "q-webrip"
        if (has("seadex")) m += "q-seadex"
        if (has("hdr10+") || has("hdr10plus") || has("hdr10p")) m += "v-hdr10plus"
        else if (has("hdr10")) m += "v-hdr10"
        else if (has("hdr") || has("hlg")) m += "v-hdr"
        if (has("imax enhanced") || has("imax.enhanced")) m += "v-imax-enhanced" else if (has("imax")) m += "v-imax"
        if (has("sdr")) m += "v-sdr"
        when {
            atmos -> m += if (dv) "a-atmos-dv" else "a-atmos"
            thd -> m += if (dv) "a-truehd-dv" else "a-truehd"
            ddp || dd -> m += if (dv) "a-dd-dv" else if (ddp) "a-ddp" else "a-dd"
            dv -> m += "v-dv"
        }
        if (has("dts:x") || has("dts-x") || has("dts.x")) m += "a-dtsx"
        else if (has("dts-hd ma") || has("dts-hd.ma") || has("dts.hd.ma") || has("dts-ma")) m += "a-dtshdma"
        else if (has("dts-hd") || has("dts.hd")) m += "a-dtshd"
        else if (has("dts")) m += "a-dts"
        if (has("7.1") || has("7.0")) m += "c-71" else if (has("5.1") || has("5.0")) m += "c-51"
        if (has("hevc") || has("x265") || has("h265") || has("h.265")) m += "co-x265"
        if (has("avc") || has("x264") || has("h264") || has("h.264")) m += "co-x264"
        if (has("av1")) m += "co-av1"
        if (has("netflix") || has("nflx")) m += "p-netflix"
        if (has("amzn") || has("amazon") || has("prime video") || has("primevideo")) m += "p-prime"
        if (has("atvp") || has("atv+") || has("apple tv") || has("appletv")) m += "p-appletv"
        if (has("dsnp") || has("disney")) m += "p-disney"
        if (has("hmax") || has("hbo") || has("max")) m += "p-max"
        if (has("hulu")) m += "p-hulu"
        if (has("pcok") || has("peacock")) m += "p-peacock"
        if (has("pmtp") || has("paramount")) m += "p-paramount"
        if (has("crave")) m += "p-crave"
        if (has("crunchyroll")) m += "p-crunchyroll"
        return BADGE_IDS.filter { it in m }
    }

    // --- audio languages (fontepref.c fontepref_trilha / fontepref_sem_legendas) ---

    private val TERMS = listOf(
        "dual audio" to "DUAL", "dual-audio" to "DUAL", "dualaudio" to "DUAL",
        "multi audio" to "MULTI", "multi-audio" to "MULTI", "multiaudio" to "MULTI", "multi-subs" to "MULTI",
        "dublado" to "DUB", "dublagem" to "DUB", "dubbed" to "DUB", "nacional" to "POR",
        "portuguese" to "POR", "portugues" to "POR", "brazilian" to "POR", "brasileiro" to "POR",
        "pt-br" to "POR", "ptbr" to "POR", "pt_br" to "POR", "english" to "ENG", "ingles" to "ENG",
        "spanish" to "SPA", "espanol" to "SPA", "latino" to "SPA", "castellano" to "SPA",
        "french" to "FRA", "frances" to "FRA", "francais" to "FRA", "german" to "GER", "deutsch" to "GER",
        "alemao" to "GER", "italian" to "ITA", "italiano" to "ITA", "japanese" to "JPN", "japones" to "JPN",
        "korean" to "KOR", "coreano" to "KOR", "hindi" to "HIN", "tamil" to "TAM", "telugu" to "TEL",
        "russian" to "RUS", "russo" to "RUS", "legendado" to "LEG", "subbed" to "LEG",
    )

    private val LABEL = mapOf(
        "BR" to "PT-BR", "PT" to "PT", "POR" to "PT", "US" to "EN", "GB" to "EN", "UK" to "EN", "ENG" to "EN",
        "ES" to "ES", "SPA" to "ES", "MX" to "ES-LA", "AR" to "ES-LA", "FR" to "FR", "FRA" to "FR",
        "DE" to "DE", "GER" to "DE", "IT" to "IT", "ITA" to "IT", "JP" to "JA", "JPN" to "JA",
        "KR" to "KO", "KOR" to "KO", "CN" to "ZH", "TW" to "ZH", "IN" to "HI", "HIN" to "HI",
        "TAM" to "TA", "TEL" to "TE", "RU" to "RU", "RUS" to "RU", "HR" to "HR", "RS" to "SR",
        "DUAL" to "DUAL", "MULTI" to "MULTI", "DUB" to "DUB", "LEG" to "LEG",
    )

    /** Uppercase isolated codes as AIOStreams and release names write them ("EN", "DUAL"). */
    private val RAW_CODES = listOf(
        "DUAL" to "DUAL", "MULTI" to "MULTI", "PT-BR" to "PT-BR", "PTBR" to "PT-BR", "PT" to "PT",
        "EN" to "EN", "ENG" to "EN", "ES" to "ES", "LATINO" to "ES-LA", "FR" to "FR", "DE" to "DE",
        "IT" to "IT", "JA" to "JA", "JP" to "JA", "KO" to "KO", "ZH" to "ZH", "RU" to "RU", "HI" to "HI",
    )

    private const val MAX_LANGUAGES = 4

    fun audioLanguages(name: String, description: String, filename: String): List<String> {
        val n = stripSubtitles(name)
        val d = stripSubtitles(description)
        val f = stripSubtitles(filename)
        val out = ArrayList<String>()
        for (code in signature("$n $d $f")) {
            val label = LABEL[code] ?: code
            if (label !in out && out.size < MAX_LANGUAGES) out += label
        }
        val raw = "$n $d $f"
        for ((t, label) in RAW_CODES) {
            if (out.size >= MAX_LANGUAGES) break
            if (label in out) continue
            var from = 0
            while (true) {
                val i = raw.indexOf(t, from)
                if (i < 0) break
                val before = if (i > 0) raw[i - 1] else ' '
                val after = if (i + t.length < raw.length) raw[i + t.length] else ' '
                // A '-' after only blocks language codes ("PT" inside "PT-BR"); "DUAL-GROUP" is the group glued on.
                val blocked = before.isAsciiAlnum() || after.isAsciiAlnum() ||
                    (after == '-' && label != "DUAL" && label != "MULTI")
                if (!blocked) { out += label; break }
                from = i + t.length
            }
        }
        return out
    }

    /** Sorted language marks: flags first, then written terms (the TV's audio "signature"). */
    fun signature(raw: String): List<String> {
        val text = normalize(raw)
        val marks = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val cp = codePointAt(text, i)
            if (cp in 0x1F1E6..0x1F1FF) {
                val next = i + Character.charCount(cp)
                if (next < text.length) {
                    val cp2 = codePointAt(text, next)
                    if (cp2 in 0x1F1E6..0x1F1FF) {
                        val code = "${'A' + (cp - 0x1F1E6)}${'A' + (cp2 - 0x1F1E6)}"
                        if (code !in marks) marks += code
                        i = next + Character.charCount(cp2)
                        continue
                    }
                }
            }
            i++
        }
        for ((term, code) in TERMS) if (code !in marks && token(text, term)) marks += code
        return marks.take(10).sorted()
    }

    private fun codePointAt(s: String, i: Int): Int {
        val c = s[i]
        if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
            return ((c.code - 0xD800) shl 10) + (s[i + 1].code - 0xDC00) + 0x10000
        }
        return c.code
    }

    private object Character {
        fun charCount(cp: Int) = if (cp >= 0x10000) 2 else 1
    }

    /** Lowercase without Latin accents, so "Português" and "PORTUGUES" sign the same. */
    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c.lowercaseChar()) {
                    'á', 'à', 'â', 'ã', 'ä', 'å' -> 'a'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'í', 'ì', 'î', 'ï' -> 'i'
                    'ó', 'ò', 'ô', 'õ', 'ö' -> 'o'
                    'ú', 'ù', 'û', 'ü' -> 'u'
                    'ç' -> 'c'
                    'ñ' -> 'n'
                    else -> c.lowercaseChar()
                },
            )
        }
        return sb.toString()
    }

    private val LOOSE_LANGUAGES = setOf(
        "pt", "pt-br", "ptbr", "pt_br", "por", "br", "bra", "portugues", "portuguese", "português", "brazilian",
        "en", "eng", "english", "ingles", "inglês", "es", "spa", "esp", "spanish", "espanol", "español", "lat",
        "latino", "castellano", "fr", "fra", "fre", "french", "de", "ger", "deu", "german", "it", "ita",
        "italian", "italiano", "ja", "jp", "jpn", "japanese", "ko", "kor", "korean", "zh", "chi", "chs", "cht",
        "chinese", "ru", "rus", "russian", "uk", "ukr", "ua", "hi", "hin", "hindi", "nl", "dut", "nld", "dutch",
        "pl", "pol", "polish", "tr", "tur", "turkish", "ar", "ara", "arabic", "sv", "swe", "no", "nor", "da",
        "dan", "fi", "fin", "cz", "cze", "ces", "hu", "hun", "ro", "rum", "ron", "el", "gre", "ell", "he", "heb",
        "th", "tha", "vi", "vie", "id", "ind", "ms", "may", "ta", "tam", "te", "tel", "multi", "all", "and", "e", "y",
    )

    /** Words that open a run of SUBTITLE languages; true = the word itself goes too. */
    private val SUB_WORDS = mapOf(
        "sub" to true, "subs" to true, "esub" to true, "esubs" to true, "msub" to true, "msubs" to true,
        "multisub" to true, "multisubs" to true, "multi-sub" to true, "multi-subs" to true,
        "subtitle" to true, "subtitles" to true, "subtitulos" to true, "legenda" to true, "legendas" to true,
        "legendado" to false, "legendada" to false, "subbed" to false, "subtitulado" to false,
    )

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '-' || c == '_' || c.code >= 0x80

    private fun isFlagAt(s: CharSequence, i: Int): Boolean {
        if (i + 1 >= s.length) return false
        val c = s[i]
        if (!c.isHighSurrogate()) return false
        val cp = ((c.code - 0xD800) shl 10) + (s[i + 1].code - 0xDC00) + 0x10000
        return cp in 0x1F1E6..0x1F1FF
    }

    /**
     * Blanks out what talks about SUBTITLES: "≡ PT-BR · ES" / "💬 …" fields (AIOStreams)
     * and runs like "sub ita eng", "Sub (Rus, Eng)", "Legendado (PT-BR)", so the chips
     * show only audio.
     */
    fun stripSubtitles(input: String): String {
        val t = StringBuilder(input)
        val len = t.length
        var i = 0
        while (i < len) {
            val c = t[i]
            val markLen = when {
                c == '≡' -> 1
                c == '\uD83D' && i + 1 < len && t[i + 1] == '\uDCAC' -> 2
                else -> 0
            }
            if (markLen > 0) {
                var j = i + markLen
                while (j < len) {
                    val d = t[j]
                    if (d == '\n' || d == '|' || (d == ' ' && j + 1 < len && t[j + 1] == ' ')) break
                    if (d.code in 0x2000..0x2BFF) break
                    if (d.isHighSurrogate()) { if (isFlagAt(t, j)) { j += 2; continue }; break }
                    j++
                }
                for (k in i until j) t[k] = ' '
                i = j
                continue
            }
            if ((i > 0 && isWordChar(t[i - 1])) || !c.isLetter()) { i++; continue }
            var n = 0
            while (i + n < len && isWordChar(t[i + n])) n++
            val word = t.substring(i, i + n).lowercase()
            val erase = SUB_WORDS[word]
            if (erase == null) { i += maxOf(n, 1); continue }
            if (erase) for (k in i until i + n) t[k] = ' '
            var j = i + n
            var endErase = j
            while (true) {
                while (j < len && t[j] in " \t,.;:()[]+/&") j++
                if (j < len && isFlagAt(t, j)) { j += 4.coerceAtMost(len - j); endErase = j; continue }
                var w = 0
                while (j + w < len && isWordChar(t[j + w])) w++
                if (w == 0 || t.substring(j, j + w).lowercase() !in LOOSE_LANGUAGES) break
                j += w
                endErase = j
            }
            if (endErase < len && t[endErase] == ')') endErase++
            for (k in i + n until endErase) t[k] = ' '
            i = maxOf(endErase, i + 1)
        }
        return t.toString()
    }
}
