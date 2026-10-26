package com.nuvio.app.features.debrid

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.epochMs
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Conta de debrid SEM PLANO, como na TV (debrid.c, debrid_conferir_planos).
 *
 * O TorBox gratuito responde PLAN_RESTRICTED_FEATURE e o addon (Comet, Torrentio…)
 * devolve um vídeo de 8 segundos escrito "PLAN RESTRICTED FEATURE" no lugar do filme.
 * Para não tocar isso, cada busca de fontes pergunta ao serviço o plano de cada chave
 * (as que vêm na URL dos addons e a da conta), e as fontes daquele serviço saem da
 * lista, do play automático e do Retomar com o último link.
 *
 * O veredito vale [VERDICT_VALIDITY_MS] e é perguntado de novo depois: quem voltar a
 * pagar volta a ver as fontes sem fechar o app. Falha de rede não apaga o que se sabia
 * (e, sem veredito nenhum, a fonte fica disponível).
 */
enum class DebridPlanService(val displayName: String) {
    REAL_DEBRID("Real-Debrid"),
    TORBOX("TorBox"),
    PREMIUMIZE("Premiumize"),
    ALL_DEBRID("AllDebrid"),
}

internal data class DebridPlanKey(val service: DebridPlanService, val key: String)

object DebridPlanGuard {
    private const val VERDICT_VALIDITY_MS = 60_000L

    private data class Verdict(val paid: Boolean, val atMs: Long)

    private val mutex = Mutex()
    private val verdicts = mutableMapOf<DebridPlanKey, Verdict>()
    private val warned = mutableSetOf<DebridPlanService>()
    private val json = Json { ignoreUnknownKeys = true }

    // Recalculado sob a trava sempre que um veredito muda; lido de qualquer fio.
    private var blocked: Set<DebridPlanService> = emptySet()

    /** Serviços com chave sem plano pago e nenhuma chave paga. */
    fun blockedServices(): Set<DebridPlanService> = blocked

    private fun recomputeBlocked() {
        val unpaid = verdicts.filterValues { !it.paid }.keys.mapTo(mutableSetOf()) { it.service }
        val paid = verdicts.filterValues { it.paid }.keys.mapTo(mutableSetOf()) { it.service }
        blocked = unpaid - paid
    }

    /** Pergunta o plano das chaves dessas URLs de addon e das chaves da conta. */
    suspend fun refresh(addonUrls: List<String>, settings: DebridSettings) {
        val keys = (addonUrls.flatMap(::keysFromUrl) + accountKeys(settings)).distinct()
        if (keys.isEmpty()) return
        val now = epochMs()
        val stale = mutex.withLock {
            keys.filter { key ->
                val verdict = verdicts[key]
                verdict == null || now - verdict.atMs >= VERDICT_VALIDITY_MS || now < verdict.atMs
            }
        }
        if (stale.isEmpty()) return
        val results = coroutineScope {
            stale.map { key -> async { key to runCatching { askPlan(key) }.getOrNull() } }.awaitAll()
        }
        mutex.withLock {
            results.forEach { (key, paid) ->
                if (paid != null) {
                    verdicts[key] = Verdict(paid = paid, atMs = epochMs())
                    if (paid) warned.remove(key.service)
                }
            }
            recomputeBlocked()
        }
    }

    /** Os serviços sem plano que ainda não foram avisados nesta sessão (e marca como avisados). */
    suspend fun takeNewWarnings(): Set<DebridPlanService> = mutex.withLock {
        val fresh = blockedServices() - warned
        warned += fresh
        fresh
    }

    fun isBlocked(stream: StreamItem, blocked: Set<DebridPlanService> = blockedServices()): Boolean {
        if (blocked.isEmpty()) return false
        // Fontes resolvidas pela conta (ex.: "4K TB Instant", só infoHash) dizem o serviço no
        // debridCacheStatus/clientResolve, não no nome nem na URL.
        val accountServices = listOfNotNull(
            stream.debridCacheStatus?.providerId?.let(::serviceFromName),
            stream.clientResolve?.service?.let(::serviceFromName),
        )
        return accountServices.any { it in blocked } ||
            servicesOf(stream.name, stream.playableDirectUrl ?: stream.url).any { it in blocked }
    }

    fun isBlocked(streamName: String?, url: String?, blocked: Set<DebridPlanService> = blockedServices()): Boolean {
        if (blocked.isEmpty()) return false
        return servicesOf(streamName, url).any { it in blocked }
    }

    suspend fun clear() = mutex.withLock {
        verdicts.clear()
        warned.clear()
        blocked = emptySet()
    }

    // ---- De que serviço é a fonte (stream_parse.c: stream_texto_debrid e stream_url_debrid)

    private val serviceTags = listOf(
        "RD" to DebridPlanService.REAL_DEBRID,
        "TB" to DebridPlanService.TORBOX,
        "TRB" to DebridPlanService.TORBOX,
        "PM" to DebridPlanService.PREMIUMIZE,
        "AD" to DebridPlanService.ALL_DEBRID,
    )
    private val serviceNames = listOf(
        "real-debrid" to DebridPlanService.REAL_DEBRID,
        "realdebrid" to DebridPlanService.REAL_DEBRID,
        "torbox" to DebridPlanService.TORBOX,
        "premiumize" to DebridPlanService.PREMIUMIZE,
        "alldebrid" to DebridPlanService.ALL_DEBRID,
    )
    private val urlTags = listOf(
        "/rd/" to DebridPlanService.REAL_DEBRID,
        "/tb/" to DebridPlanService.TORBOX,
        "/pm/" to DebridPlanService.PREMIUMIZE,
        "/ad/" to DebridPlanService.ALL_DEBRID,
    )
    private val cacheMarks = listOf("⚡", "☁", "⏳", "⬇")

    internal fun servicesOf(name: String?, url: String?): Set<DebridPlanService> {
        val found = mutableSetOf<DebridPlanService>()
        if (!name.isNullOrBlank()) {
            val lower = name.lowercase()
            serviceNames.forEach { (text, service) -> if (text in lower) found += service }
            serviceTags.forEach { (tag, service) -> if (hasServiceTag(name, tag)) found += service }
        }
        if (!url.isNullOrBlank() && url.startsWith("http")) {
            val lower = url.lowercase()
            serviceNames.forEach { (text, service) ->
                val compact = text.replace("-", "")
                if ("/resolve/$compact/" in lower || "debrid=$compact" in lower || "$compact=" in lower) {
                    found += service
                }
            }
            urlTags.forEach { (tag, service) -> if (tag in lower) found += service }
            if ("torbox.app" in lower || "tb-cdn" in lower) found += DebridPlanService.TORBOX
        }
        return found
    }

    /** "[TB]", "[TB+]", "TB+", "TB⚡", "[TB ⚡]", "TB download": a sigla como marca, não dentro de uma palavra. */
    private fun hasServiceTag(text: String, tag: String): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf(tag, from)
            if (at < 0) return false
            from = at + 1
            val before = text.getOrNull(at - 1)
            val after = text.substring(at + tag.length)
            val inBracket = before == '[' || before == '('
            val freeBefore = before == null || !before.isLetterOrDigit()
            val mark = after.startsWith("+") ||
                cacheMarks.any { after.startsWith(it) || after.startsWith(" $it") } ||
                after.startsWith(" download", ignoreCase = true) ||
                after.startsWith(" error")
            if (inBracket && (mark || after.startsWith("]") || after.startsWith(")"))) return true
            if (at >= 1 && cacheMarks.any { text.substring(0, at).trimEnd().endsWith(it) } &&
                (after.isEmpty() || !after[0].isLetterOrDigit())
            ) return true
            if (freeBefore && mark) return true
        }
    }

    // ---- As chaves que vêm na URL do addon (debrid.c: debrid_chaves_da_url)

    private fun serviceFromName(name: String): DebridPlanService? = when (name.trim().lowercase()) {
        "realdebrid", "real-debrid", "rd" -> DebridPlanService.REAL_DEBRID
        "torbox", "tor-box", "tb" -> DebridPlanService.TORBOX
        "premiumize", "premiumize-me", "premiumizeme", "pm" -> DebridPlanService.PREMIUMIZE
        "alldebrid", "all-debrid", "ad" -> DebridPlanService.ALL_DEBRID
        else -> null
    }

    /** Chave de debrid é alfanumérica (RD 52, AllDebrid 20, Premiumize 16) ou UUID (TorBox 36). */
    private fun looksLikeKey(key: String): Boolean =
        key.length in 12..199 && key.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    internal fun keysFromUrl(url: String): List<DebridPlanKey> {
        val decoded = runCatching { url.decodeURLQueryComponent() }.getOrDefault(url)
        val body = decoded.substringAfter("://", decoded)
        val segments = body.split('/', '|', '&', '?', ';')
        val keys = mutableListOf<DebridPlanKey>()
        fun add(service: DebridPlanService?, key: String) {
            val trimmed = key.trim()
            if (service != null && looksLikeKey(trimmed)) keys += DebridPlanKey(service, trimmed)
        }
        segments.forEachIndexed { index, segment ->
            val eq = segment.indexOf('=')
            if (eq > 0) {
                // nome=valor: "torbox=<chave>" ou "debrid=torbox,<chave>"
                val name = segment.substring(0, eq)
                val value = segment.substring(eq + 1)
                val service = serviceFromName(name).takeIf { name.length > 2 }
                if (service != null) {
                    add(service, value)
                } else if (name.equals("debrid", ignoreCase = true)) {
                    val sep = value.indexOfFirst { it == ',' || it == ':' }
                    if (sep > 0) add(serviceFromName(value.substring(0, sep)), value.substring(sep + 1))
                }
            }
            // /resolve/<servico>/<chave>/
            if (index >= 2 && segments[index - 2] == "resolve") {
                add(serviceFromName(segments[index - 1]), segment)
            }
            // Segmento longo em base64 com JSON dentro (o Comet e o AIOStreams).
            val trailingPadding = eq < 0 || segment.substring(eq).all { it == '=' }
            if (segment.length >= 40 && trailingPadding) {
                decodeBase64Json(segment)?.let { keys += keysFromJson(it) }
            }
        }
        return keys.distinct()
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun decodeBase64Json(segment: String): JsonObject? = runCatching {
        val normalized = segment.trimEnd('=').replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        val text = kotlin.io.encoding.Base64.decode(padded).decodeToString()
        if (!text.trimStart().startsWith("{")) return null
        json.parseToJsonElement(text).jsonObject
    }.getOrNull()

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun keysFromJson(root: JsonObject): List<DebridPlanKey> {
        val keys = mutableListOf<DebridPlanKey>()
        fun add(service: String?, key: String?) {
            val parsed = service?.let(::serviceFromName) ?: return
            val trimmed = key?.trim() ?: return
            if (looksLikeKey(trimmed)) keys += DebridPlanKey(parsed, trimmed)
        }
        (root["debridServices"] as? JsonArray)?.forEach { item ->
            val obj = item as? JsonObject ?: return@forEach
            add(obj["service"].text(), obj["apiKey"].text())
        }
        add(root["debridService"].text(), root["debridApiKey"].text())
        return keys
    }

    private fun accountKeys(settings: DebridSettings): List<DebridPlanKey> = listOfNotNull(
        settings.torboxApiKey.takeIf { it.isNotBlank() }?.let { DebridPlanKey(DebridPlanService.TORBOX, it.trim()) },
        settings.realDebridApiKey.takeIf { it.isNotBlank() }?.let { DebridPlanKey(DebridPlanService.REAL_DEBRID, it.trim()) },
        settings.premiumizeApiKey.takeIf { it.isNotBlank() }?.let { DebridPlanKey(DebridPlanService.PREMIUMIZE, it.trim()) },
    )

    // ---- O plano da chave (debrid.c: perguntarPlano)

    /** true = pago, false = sem plano (ou chave recusada), null = não deu para saber. */
    private suspend fun askPlan(key: DebridPlanKey): Boolean? {
        val url = when (key.service) {
            DebridPlanService.TORBOX -> "https://api.torbox.app/v1/api/user/me"
            DebridPlanService.REAL_DEBRID -> "https://api.real-debrid.com/rest/1.0/user"
            DebridPlanService.PREMIUMIZE -> "https://www.premiumize.me/api/account/info"
            DebridPlanService.ALL_DEBRID -> "https://api.alldebrid.com/v4/user?agent=betternuvio"
        }
        val response = httpRequestRaw(
            method = "GET",
            url = url,
            headers = mapOf("Authorization" to "Bearer ${key.key}"),
            body = "",
        )
        val body = response.body
        val lower = body.lowercase()
        if (response.status == 401 || response.status == 403 ||
            "BAD_TOKEN" in body || "AUTH_ERROR" in body || "AUTH_BAD_APIKEY" in body ||
            "AUTH_USER_BANNED" in body || "PLAN_RESTRICTED" in body ||
            "bad_token" in lower || "not logged in" in lower
        ) return false
        if (response.status !in 200..299) return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val data = root["data"] as? JsonObject
        return when (key.service) {
            DebridPlanService.TORBOX -> {
                // data.plan: 0 = gratuito (1 Essential, 2 Pro, 3 Standard); premium_expires_at
                // no passado também é sem plano.
                val plan = (data?.get("plan") as? JsonPrimitive)?.doubleOrNull ?: return null
                if (plan < 1) return false
                val expires = data["premium_expires_at"].text()?.let(::isoToEpochMs)
                !(expires != null && expires < epochMs())
            }
            DebridPlanService.REAL_DEBRID ->
                root["type"].text()?.let { it.equals("premium", ignoreCase = true) }
            DebridPlanService.ALL_DEBRID -> {
                val user = data?.get("user") as? JsonObject ?: data
                (user?.get("isPremium") as? JsonPrimitive)?.booleanOrNull
            }
            DebridPlanService.PREMIUMIZE -> {
                val until = (root["premium_until"] as? JsonPrimitive)?.longOrNull ?: 0L
                if (root["status"].text() == "success" || until > 0) until * 1000 > epochMs() else null
            }
        }
    }

    /** "2026-10-18T12:00:00Z" (ou com fração/fuso) em ms; só a data e a hora UTC importam aqui. */
    internal fun isoToEpochMs(iso: String): Long? {
        val match = Regex("""^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?""").find(iso.trim())
            ?: return null
        val (y, mo, d) = match.destructured.toList().take(3).map { it.toInt() }
        val h = match.groupValues[4].toIntOrNull() ?: 0
        val mi = match.groupValues[5].toIntOrNull() ?: 0
        val s = match.groupValues[6].toIntOrNull() ?: 0
        // Dias desde 1970-01-01 (algoritmo de Howard Hinnant).
        val yy = if (mo <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * (mo + (if (mo > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era * 146097L + doe - 719468
        return ((days * 24 + h) * 60 + mi) * 60_000L + s * 1000L
    }
}
