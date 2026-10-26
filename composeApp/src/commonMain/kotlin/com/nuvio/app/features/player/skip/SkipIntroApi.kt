package com.nuvio.app.features.player.skip

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.addons.httpPostJsonWithHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

internal object SkipIntroApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val ANISKIP_BASE = "https://api.aniskip.com/v2/"
    private const val ARM_BASE = "https://arm.haglund.dev/api/v2/"
    private const val ANIMESKIP_BASE = "https://api.anime-skip.com/"

    // --- TheIntroDB (leitura) ---

    // Better Nuvio: os marcadores vem do TheIntroDB, como intro.c na TV. Sem chave e sem
    // cadastro; aceita `imdb_id` sozinho (filme) ou com season/episode (serie). Titulo
    // desconhecido responde 404, que aqui vira null ("zero marcadores").
    suspend fun getTheIntroDbMedia(
        imdbId: String,
        season: Int? = null,
        episode: Int? = null,
    ): TheIntroDbMediaResponse? {
        return try {
            val text = httpGetText(theIntroDbMediaUrl(imdbId, season, episode))
            json.decodeFromString<TheIntroDbMediaResponse>(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    // --- IntroDb (envio) ---

    suspend fun submitIntro(
        apiKey: String,
        request: SubmitIntroRequest,
    ): Boolean {
        val baseUrl = IntroDbConfig.URL.trimEnd('/')
        if (baseUrl.isBlank() || apiKey.isBlank()) return false
        val url = "$baseUrl/submit"
        val body = json.encodeToString(SubmitIntroRequest.serializer(), request)
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
        return try {
            val response = com.nuvio.app.features.addons.httpRequestRaw(
                method = "POST",
                url = url,
                headers = headers,
                body = body
            )
            response.status == 200 || response.status == 201
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    suspend fun verifyIntroDbApiKey(apiKey: String): Boolean {
        val baseUrl = IntroDbConfig.URL.trimEnd('/')
        if (baseUrl.isBlank() || apiKey.isBlank()) return false
        val url = "$baseUrl/submit"
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
        return try {
            val response = com.nuvio.app.features.addons.httpRequestRaw(
                method = "POST",
                url = url,
                headers = headers,
                body = "{}"
            )
            
            // 400 means Auth passed but payload was empty/invalid -> Key is Valid
            if (response.status == 400) return true
            
            // 200/201 would also mean valid (though unexpected with empty body)
            if (response.status == 200 || response.status == 201) return true
            
            // Explicitly handle auth failures
            if (response.status == 401 || response.status == 403) return false
            
            false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    // --- AniSkip ---

    suspend fun getAniSkipTimes(
        malId: String,
        episode: Int,
    ): AniSkipResponse? {
        val types = "types=op&types=ed&types=recap&types=mixed-op&types=mixed-ed"
        val url = "${ANISKIP_BASE}skip-times/$malId/$episode?$types&episodeLength=0"
        return try {
            val text = httpGetText(url)
            json.decodeFromString<AniSkipResponse>(text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    // --- ARM API (ID resolution) ---

    suspend fun resolveImdbToAll(imdbId: String): List<ArmEntry> {
        val url = "${ARM_BASE}imdb?id=$imdbId&include=myanimelist,anilist,kitsu"
        return try {
            val text = httpGetText(url)
            json.decodeFromString<List<ArmEntry>>(text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun resolveMalToImdb(malId: String): ArmEntry? {
        val url = "${ARM_BASE}ids?source=myanimelist&id=$malId&include=imdb"
        return try {
            val text = httpGetText(url)
            json.decodeFromString<ArmEntry>(text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveMalToAnilist(malId: String): ArmEntry? {
        val url = "${ARM_BASE}ids?source=myanimelist&id=$malId&include=anilist"
        return try {
            val text = httpGetText(url)
            json.decodeFromString<ArmEntry>(text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveKitsuToMal(kitsuId: String): ArmEntry? {
        val url = "${ARM_BASE}ids?source=kitsu&id=$kitsuId&include=myanimelist"
        return try {
            val text = httpGetText(url)
            json.decodeFromString<ArmEntry>(text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveKitsuToAnilist(kitsuId: String): ArmEntry? {
        val url = "${ARM_BASE}ids?source=kitsu&id=$kitsuId&include=anilist"
        return try {
            val text = httpGetText(url)
            json.decodeFromString<ArmEntry>(text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveKitsuToImdb(kitsuId: String): ArmEntry? {
        val url = "${ARM_BASE}ids?source=kitsu&id=$kitsuId&include=imdb"
        return try {
            val text = httpGetText(url)
            json.decodeFromString<ArmEntry>(text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    // --- Anime-Skip GraphQL ---

    suspend fun queryAnimeSkip(clientId: String, graphqlQuery: String): AnimeSkipGraphqlResponse? {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("query", kotlinx.serialization.json.JsonPrimitive(graphqlQuery))
            }
        )
        val headers = mapOf(
            "X-Client-ID" to clientId,
            "Content-Type" to "application/json",
        )
        return try {
            val text = httpPostJsonWithHeaders(ANIMESKIP_BASE + "graphql", body, headers)
            json.decodeFromString<AnimeSkipGraphqlResponse>(text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}

private const val THE_INTRO_DB_MEDIA_URL = "https://api.theintrodb.org/v3/media"

/** Better Nuvio: o id pode vir como "tt123:1:2"; a API quer so a parte do IMDb (intro.c). */
internal fun theIntroDbMediaUrl(imdbId: String, season: Int?, episode: Int?): String {
    val id = imdbId.substringBefore(':')
    return if (season != null && episode != null && season > 0 && episode > 0) {
        "$THE_INTRO_DB_MEDIA_URL?imdb_id=$id&season=$season&episode=$episode"
    } else {
        "$THE_INTRO_DB_MEDIA_URL?imdb_id=$id"
    }
}
