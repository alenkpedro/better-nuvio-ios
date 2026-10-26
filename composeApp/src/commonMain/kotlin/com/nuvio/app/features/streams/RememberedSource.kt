package com.nuvio.app.features.streams

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// Better Nuvio: a fonte que a pessoa ESCOLHEU na lista, lembrada por título e por
// perfil (fontepref.c). Só a escolha manual vira preferência: o que o automático
// escolhe não, para quem nunca escolheu nada continuar com a regra da pontuação.
//
// "A mesma fonte" no episódio seguinte não é a url (muda e expira) nem o arquivo.
// A ordem de casamento é a da TV:
//   1. bingeGroup igual — o addon declarou que é a mesma fonte;
//   2. provedor (addon) + trilha de áudio (bandeiras, "Dual Audio", "Dublado"...),
//      primeiro igual, depois "a de hoje contém as marcas da lembrada";
//   3. nada casou: a escolha automática assume.
// Desempate nas duas camadas: rótulo igual vale 4, resolução igual vale 2; empate
// fica com a primeira da lista.
// ---------------------------------------------------------------------------

@Serializable
data class RememberedSource(
    val provider: String,
    val audio: String = "",
    val bingeGroup: String? = null,
    val height: Int = 0,
    val label: String = "",
)

object RememberedSourceRepository {
    private val json = Json { ignoreUnknownKeys = true }

    /** Guarda a escolha manual de [stream] para o título [parentMetaId]. */
    fun save(parentMetaId: String, stream: StreamItem) {
        val id = parentMetaId.trim().takeIf { it.isNotEmpty() } ?: return
        val remembered = RememberedSourceMatcher.fromStream(stream) ?: return
        runCatching {
            BingeGroupCacheStorage.save(key(id), json.encodeToString(RememberedSource.serializer(), remembered))
        }
    }

    fun get(parentMetaId: String?): RememberedSource? {
        val id = parentMetaId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            BingeGroupCacheStorage.load(key(id))
                ?.let { json.decodeFromString(RememberedSource.serializer(), it) }
        }.getOrNull()
    }

    fun forget(parentMetaId: String) {
        runCatching { BingeGroupCacheStorage.remove(key(parentMetaId.trim())) }
    }

    private fun key(contentId: String): String {
        val hash = contentId.fold(0L) { acc, c -> acc * 31 + c.code }.toULong()
        return "remembered_source_$hash"
    }
}

internal object RememberedSourceMatcher {

    fun fromStream(stream: StreamItem): RememberedSource? {
        val provider = stream.addonName.trim().takeIf { it.isNotEmpty() } ?: return null
        // Link compartilhado pela sessão em grupo não vira preferência (pode levar o debrid de outra pessoa).
        if (stream.addonId == "party-shared-stream") return null
        return RememberedSource(
            provider = provider,
            audio = audioSignature(stream),
            bingeGroup = stream.behaviorHints.bingeGroup?.trim()?.takeIf { it.isNotEmpty() },
            height = StreamQualityRanking.height(stream),
            label = stream.name.orEmpty().trim(),
        )
    }

    /** A fonte da lista que é "a mesma" que a lembrada, ou null. */
    fun pick(streams: List<StreamItem>, remembered: RememberedSource): StreamItem? =
        byBingeGroup(streams, remembered) ?: byProviderAndAudio(streams, remembered)

    private fun note(stream: StreamItem, remembered: RememberedSource): Int {
        var n = 0
        if (StreamQualityRanking.height(stream) == remembered.height) n += 2
        if (stream.name.orEmpty().trim() == remembered.label) n += 4
        return n
    }

    private fun bestOf(candidates: List<StreamItem>, remembered: RememberedSource): StreamItem? {
        var best: StreamItem? = null
        var bestNote = -1
        for (stream in candidates) {
            val n = note(stream, remembered)
            if (n > bestNote) {
                bestNote = n
                best = stream
            }
        }
        return best
    }

    private fun byBingeGroup(streams: List<StreamItem>, remembered: RememberedSource): StreamItem? {
        val group = remembered.bingeGroup?.takeIf { it.isNotEmpty() } ?: return null
        return bestOf(streams.filter { it.behaviorHints.bingeGroup == group }, remembered)
    }

    private fun byProviderAndAudio(streams: List<StreamItem>, remembered: RememberedSource): StreamItem? {
        val sameProvider = streams.filter { it.addonName.trim().equals(remembered.provider, ignoreCase = true) }
        if (sameProvider.isEmpty()) return null
        val withAudio = sameProvider.map { it to audioSignature(it) }
        bestOf(withAudio.filter { it.second == remembered.audio }.map { it.first }, remembered)?.let { return it }
        return bestOf(withAudio.filter { containsMarks(it.second, remembered.audio) }.map { it.first }, remembered)
    }

    /** A trilha de hoje contém todas as marcas da lembrada? "BR+DUB" contém "DUB"; "" é contida por tudo. */
    private fun containsMarks(today: String, remembered: String): Boolean {
        if (remembered.isEmpty()) return true
        val marks = today.split('+').toSet()
        return remembered.split('+').all { it in marks }
    }

    // --- assinatura de idioma/áudio (fontepref_trilha) ---------------------

    private val terms = listOf(
        "dual audio" to "DUAL", "dual-audio" to "DUAL", "dualaudio" to "DUAL",
        "multi audio" to "MULTI", "multi-audio" to "MULTI", "multiaudio" to "MULTI",
        "multi-subs" to "MULTI",
        "dublado" to "DUB", "dublagem" to "DUB", "dubbed" to "DUB",
        "nacional" to "POR",
        "portuguese" to "POR", "portugues" to "POR",
        "brazilian" to "POR", "brasileiro" to "POR",
        "pt-br" to "POR", "ptbr" to "POR", "pt_br" to "POR",
        "english" to "ENG", "ingles" to "ENG",
        "spanish" to "SPA", "espanol" to "SPA",
        "latino" to "SPA", "castellano" to "SPA",
        "french" to "FRA", "frances" to "FRA", "francais" to "FRA",
        "german" to "GER", "deutsch" to "GER", "alemao" to "GER",
        "italian" to "ITA", "italiano" to "ITA",
        "japanese" to "JPN", "japones" to "JPN",
        "korean" to "KOR", "coreano" to "KOR",
        "hindi" to "HIN", "tamil" to "TAM", "telugu" to "TEL",
        "russian" to "RUS", "russo" to "RUS",
        "legendado" to "LEG", "subbed" to "LEG",
    ).map { (term, code) -> Regex("(?<![a-z0-9])" + Regex.escape(term) + "(?![a-z0-9])") to code }

    private const val MaxMarks = 10

    /** Bandeiras (🇧🇷 → "BR") e termos de idioma, sem repetir e em ordem, unidos por "+". */
    fun audioSignature(stream: StreamItem): String {
        val raw = listOfNotNull(stream.name, stream.description, stream.behaviorHints.filename).joinToString(" ")
        val text = normalize(raw)
        val marks = mutableListOf<String>()
        var i = 0
        while (i < text.length && marks.size < MaxMarks) {
            val first = text.codePointAtOrNull(i)
            if (first != null && first.isRegionalIndicator()) {
                val next = text.codePointAtOrNull(i + 2)
                if (next != null && next.isRegionalIndicator()) {
                    val code = "${('A' + (first - 0x1F1E6))}${('A' + (next - 0x1F1E6))}"
                    if (code !in marks) marks += code
                    i += 4
                    continue
                }
            }
            i++
        }
        for ((regex, code) in terms) {
            if (marks.size >= MaxMarks) break
            if (code !in marks && regex.containsMatchIn(text)) marks += code
        }
        return marks.sorted().joinToString("+")
    }

    private fun Int.isRegionalIndicator(): Boolean = this in 0x1F1E6..0x1F1FF

    private fun String.codePointAtOrNull(index: Int): Int? {
        val high = getOrNull(index) ?: return null
        if (!high.isHighSurrogate()) return null
        val low = getOrNull(index + 1) ?: return null
        if (!low.isLowSurrogate()) return null
        return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
    }

    private const val AccentFrom = "àáâãäåçèéêëìíîïñòóôõöùúûüýÿ"
    private const val AccentTo = "aaaaaaceeeeiiiinooooouuuuyy"

    /** Minúscula e sem acento: "Português", "PORTUGUÊS" e "Portugues" viram o mesmo texto. */
    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val out = StringBuilder(lower.length)
        for (c in lower) {
            val idx = AccentFrom.indexOf(c)
            out.append(if (idx >= 0) AccentTo[idx] else c)
        }
        return out.toString()
    }
}
