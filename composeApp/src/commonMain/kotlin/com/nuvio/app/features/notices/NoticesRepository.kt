package com.nuvio.app.features.notices

import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.core.storage.ProfileTextStorage
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Better Nuvio: um aviso do canal do dono (avisos.json), já no idioma do app. */
data class AppNotice(
    val id: String,
    val title: String,
    val text: String,
    val since: String?,
    val seen: Boolean,
)

data class NoticesUiState(
    val notices: List<AppNotice> = emptyList(),
    val isLoading: Boolean = false,
    val loadedOnce: Boolean = false,
)

/**
 * Better Nuvio: avisos.c da TV, só a parte do canal do dono. O mesmo avisos.json
 * do repositório; vale a entrada cuja "plataforma" inclui "ios" (ou "todas"),
 * dentro das datas "desde"/"ate" e da faixa de versão ("desde_versao"/"ate_versao",
 * comparada com a versão do app). Os ids já vistos ficam neste aparelho, não no perfil.
 */
object NoticesRepository {
    const val CHANNEL_URL = "https://raw.githubusercontent.com/alenkpedro/better-nuvio/main/avisos.json"
    const val PLATFORM = "ios"
    private const val CACHE_PATH = "bn_avisos.json"
    private const val SEEN_STORAGE_NAME = "avisos_vistos"
    // Os vistos são do aparelho: "perfil" 0 não existe, então a chave não se mistura com nenhum perfil.
    private const val DEVICE_SCOPE = 0
    private const val MAX_SEEN = 200

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _uiState = MutableStateFlow(NoticesUiState())
    val uiState: StateFlow<NoticesUiState> = _uiState.asStateFlow()

    /**
     * Baixa o canal (ou usa a última cópia sem rede) e devolve os avisos que
     * ainda não foram vistos neste aparelho.
     */
    suspend fun refresh(portuguese: Boolean): List<AppNotice> {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val body = withContext(Dispatchers.Default) {
            val fresh = runCatching { httpGetText(CHANNEL_URL) }.getOrNull()
                ?.takeIf { it.trimStart().startsWith("[") }
            if (fresh != null) {
                runCatching { AppFileCache.write(CACHE_PATH, fresh) }
                fresh
            } else {
                runCatching { AppFileCache.read(CACHE_PATH) }.getOrNull()
            }
        }
        val seen = loadSeen()
        val notices = body?.let { parse(it, portuguese, seen) }.orEmpty()
        _uiState.value = NoticesUiState(notices = notices, isLoading = false, loadedOnce = true)
        return notices.filterNot { it.seen }
    }

    fun markSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val seen = loadSeen()
        val updated = (seen + ids).toList().takeLast(MAX_SEEN)
        ProfileTextStorage.save(SEEN_STORAGE_NAME, DEVICE_SCOPE, json.encodeToString(updated))
        val now = updated.toSet()
        _uiState.value = _uiState.value.copy(
            notices = _uiState.value.notices.map { it.copy(seen = it.id in now) },
        )
    }

    fun markAllSeen() = markSeen(_uiState.value.notices.filterNot { it.seen }.map { it.id })

    private fun loadSeen(): Set<String> =
        runCatching {
            ProfileTextStorage.load(SEEN_STORAGE_NAME, DEVICE_SCOPE)
                ?.let { json.decodeFromString<List<String>>(it) }
        }.getOrNull().orEmpty().toSet()

    internal fun parse(body: String, portuguese: Boolean, seen: Set<String>): List<AppNotice> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray ?: return emptyList()
        val today = CurrentDateProvider.todayIsoDate()
        return root.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val id = entry.text("id") ?: return@mapNotNull null
            val titlePt = entry.text("titulo") ?: return@mapNotNull null
            if (!matchesPlatform(entry["plataforma"])) return@mapNotNull null
            val since = entry.text("desde")
            val until = entry.text("ate")
            if (since != null && today < since) return@mapNotNull null
            if (until != null && today > until) return@mapNotNull null
            val minVersion = entry.text("desde_versao")
            val maxVersion = entry.text("ate_versao")
            if (minVersion != null && compareVersions(AppVersionConfig.VERSION_NAME, minVersion) < 0) return@mapNotNull null
            if (maxVersion != null && compareVersions(AppVersionConfig.VERSION_NAME, maxVersion) > 0) return@mapNotNull null
            val title = if (!portuguese) entry.text("titulo_en") ?: titlePt else titlePt
            val text = if (!portuguese) entry.text("texto_en") ?: entry.text("texto") else entry.text("texto")
            AppNotice(id = id, title = title, text = text.orEmpty(), since = since, seen = id in seen)
        }.sortedByDescending { it.since.orEmpty() }
    }

    /** "ios", "todas", "lg,ios", "lg ios" ou ["lg","ios"]; sem plataforma vale para todas, como na TV. */
    internal fun matchesPlatform(value: JsonElement?): Boolean {
        val names = when (value) {
            null -> return true
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> value.contentOrNull?.split(',', ' ', ';', '|').orEmpty()
            else -> return false
        }.map { it.trim().lowercase() }.filter(String::isNotEmpty)
        if (names.isEmpty()) return true
        return PLATFORM in names || "todas" in names || "all" in names
    }

    /** versaoMaior da TV: só major.minor.patch, o resto do texto não conta. */
    internal fun compareVersions(left: String, right: String): Int {
        fun parts(raw: String): List<Int> =
            raw.trim().removePrefix("v").split('.', '-', '+').take(3).map { part ->
                part.takeWhile(Char::isDigit).toIntOrNull() ?: 0
            }.let { it + List(3 - it.size) { 0 } }
        val a = parts(left)
        val b = parts(right)
        for (i in 0 until 3) {
            if (a[i] != b[i]) return a[i].compareTo(b[i])
        }
        return 0
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
}
