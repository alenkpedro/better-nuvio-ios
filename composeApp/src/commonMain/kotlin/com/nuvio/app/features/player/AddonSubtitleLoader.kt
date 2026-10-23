package com.nuvio.app.features.player

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddonResource
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.features.servers.ServerItemRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_addon_subtitle_display_format
import org.jetbrains.compose.resources.getString

@Serializable
data class SubtitleAddonRequest(
    val url: String,
    val addonId: String,
    val addonName: String,
)

internal fun addonSubtitleRequests(type: String, videoId: String): List<SubtitleAddonRequest> {
    if (ServerItemRef.isServerId(videoId)) return emptyList()
    val requestType = canonicalSubtitleType(type)
    return AddonRepository.uiState.value.addons.enabledAddons().mapNotNull { addon ->
        val manifest = addon.manifest ?: return@mapNotNull null
        if (manifest.resources.none { resource ->
                (resource.name.equals("subtitles", true) || resource.name.equals("subtitle", true)) &&
                    resource.supportsSubtitleType(requestType, videoId)
            }) return@mapNotNull null
        SubtitleAddonRequest(
            url = buildAddonResourceUrl(manifest.transportUrl, "subtitles", requestType, videoId),
            addonId = manifest.id,
            addonName = addon.displayTitle,
        )
    }
}

internal suspend fun loadAddonSubtitles(
    requests: List<SubtitleAddonRequest>,
    onLoaded: (SubtitleAddonRequest, List<AddonSubtitle>) -> Unit = { _, _ -> },
): List<AddonSubtitle> = supervisorScope {
    requests.map { request ->
        async {
            val subtitles = try {
                withTimeoutOrNull(10_000L) {
                    parseAddonSubtitles(fetchAddonResponseText(request.url), request)
                }.orEmpty()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                emptyList()
            }
            currentCoroutineContext().ensureActive()
            onLoaded(request, subtitles)
            subtitles
        }
    }.awaitAll().flatten()
}

private suspend fun parseAddonSubtitles(response: String, request: SubtitleAddonRequest): List<AddonSubtitle> {
    val subtitles = Json.parseToJsonElement(response).jsonObject["subtitles"]?.jsonArray.orEmpty()
    return subtitles.mapIndexedNotNull { index, element ->
        val obj = element as? JsonObject ?: return@mapIndexedNotNull null
        val url = obj.stringValue("url") ?: return@mapIndexedNotNull null
        val language = listOf("lang", "language", "languageCode", "locale", "label")
            .firstNotNullOfOrNull(obj::stringValue) ?: "unknown"
        AddonSubtitle(
            id = obj.stringValue("id") ?: "${request.addonId}_$index",
            url = url,
            language = normalizeLanguageCode(language) ?: language,
            display = getString(
                Res.string.player_addon_subtitle_display_format,
                getLanguageLabelForCode(language),
                request.addonName,
            ),
            addonName = request.addonName,
            fileName = obj.subtitleFileName(url),
            releaseName = obj.subtitleReleaseName(),
            fps = obj.subtitleFps(),
        )
    }
}

// Same key order as the TV build (faixas.c nomesLegenda).
private fun JsonObject.subtitleReleaseName(): String? =
    listOf("movieReleaseName", "releaseName", "release").firstNotNullOfOrNull(::safeStringValue)

private fun JsonObject.subtitleFileName(url: String): String? {
    listOf("subtitleFileName", "fileName", "filename", "name", "title")
        .firstNotNullOfOrNull(::safeStringValue)
        ?.takeUnless(::looksEncodedName)
        ?.let { return it }
    val base = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val lower = base.lowercase()
    if (base.length >= 9 && listOf(".srt", ".ass", ".ssa", ".vtt").any(lower::endsWith)) {
        if (!looksEncodedName(base)) return base
        nameInsideEncoded(base)?.let { return it }
    }
    subtitleReleaseName()?.let { return it }
    return safeStringValue("id")?.takeIf { '.' in it && !looksEncodedName(it) }
}

// Better Nuvio: alguns addons põem no fim da URL um JSON em base64 ("eyJjb250ZW50X3R5cGUi…
// .srt"). Isso não é nome de arquivo; mostrado na lista, vira uma linha ilegível.
private val EncodedNameRegex = Regex("^[A-Za-z0-9+/_=-]{24,}$")

private fun looksEncodedName(name: String): Boolean {
    val stem = name.substringBeforeLast('.')
    return stem.startsWith("eyJ") || (EncodedNameRegex.matches(stem) && stem.none { it == ' ' } &&
        stem.count { it.isDigit() } + stem.count { it.isUpperCase() } > stem.length / 3)
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private fun nameInsideEncoded(name: String): String? = runCatching {
    val stem = name.substringBeforeLast('.').replace('-', '+').replace('_', '/').trimEnd('=')
    val padded = stem + "=".repeat((4 - stem.length % 4) % 4)
    val json = kotlin.io.encoding.Base64.decode(padded).decodeToString()
    val obj = Json.parseToJsonElement(json).jsonObject
    listOf("file_name", "fileName", "filename", "release_name", "releaseName", "release", "name", "title")
        .firstNotNullOfOrNull(obj::safeStringValue)
        ?.takeUnless(::looksEncodedName)
}.getOrNull()

private fun JsonObject.subtitleFps(): Double? {
    val fps = safeStringValue("fps")?.toDoubleOrNull()
        ?: safeStringValue("frameRate")?.toDoubleOrNull()
        ?: safeStringValue("fpsMilli")?.toDoubleOrNull()?.div(1000.0)
    return fps?.takeIf { it in 15.0..120.0 }
}

private fun JsonObject.safeStringValue(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

private fun canonicalSubtitleType(type: String): String =
    if (type.equals("tv", ignoreCase = true)) "series" else type.lowercase()

private fun AddonResource.supportsSubtitleType(type: String, videoId: String): Boolean {
    val canonical = canonicalSubtitleType(type)
    val typeMatches = types.isEmpty() || types.any { canonicalSubtitleType(it).equals(canonical, ignoreCase = true) }
    return typeMatches && (idPrefixes.isEmpty() || idPrefixes.any { videoId.startsWith(it) })
}

private fun JsonObject.stringValue(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
