package com.nuvio.app.features.streams

import com.nuvio.app.features.debrid.DebridPlanGuard

// ---------------------------------------------------------------------------
// Better Nuvio: a escolha automática da TV (streams.c: pontos(), stream_automatico,
// alturaMax/cabeNoTeto) e os avisos de debrid (stream_parse.c).
// ---------------------------------------------------------------------------

/** "Qualidade máxima" dos ajustes (TV: ajustes_qualidade). [height] 0 = sem teto. */
enum class StreamMaxQuality(val height: Int, val label: String?) {
    AUTO(0, null),
    UHD_4K(2160, "4K"),
    FHD_1080P(1080, "1080p"),
    HD_720P(720, "720p"),
}

internal object StreamQualityRanking {

    private fun tokenRegex(token: String) =
        Regex("(?<![a-z0-9])" + Regex.escape(token) + "(?![a-z0-9])")

    private val Token4k = tokenRegex("4k")
    private val TokenUhd = tokenRegex("uhd")
    private val TokenDv = tokenRegex("dv")
    private val TokenDovi = tokenRegex("dovi")
    private val TokenAtmos = tokenRegex("atmos")
    private val TokenMp4 = tokenRegex("mp4")

    /** O texto que a TV olha: rótulo, descrição, título e arquivo (stream_parse.c). */
    private fun StreamItem.rankingText(): String = buildString {
        name?.let { append(it).append(' ') }
        description?.let { append(it).append(' ') }
        title?.let { append(it).append(' ') }
        behaviorHints.filename?.let { append(it).append(' ') }
        clientResolve?.filename?.let { append(it).append(' ') }
    }.lowercase()

    /** Altura declarada (2160/1440/1080/720/480), 0 quando o addon não diz. */
    fun height(stream: StreamItem): Int {
        val text = stream.rankingText()
        return when {
            "2160" in text || Token4k.containsMatchIn(text) || TokenUhd.containsMatchIn(text) -> 2160
            "1440" in text -> 1440
            "1080" in text -> 1080
            "720" in text -> 720
            "480" in text -> 480
            else -> 0
        }
    }

    fun isMp4(stream: StreamItem): Boolean =
        TokenMp4.containsMatchIn(stream.rankingText()) ||
            stream.url?.contains(".mp4", ignoreCase = true) == true

    fun isDolbyVision(stream: StreamItem): Boolean {
        val text = stream.rankingText()
        return TokenDv.containsMatchIn(text) || TokenDovi.containsMatchIn(text) ||
            "dolby vision" in text || "dolbyvision" in text
    }

    fun isAtmos(stream: StreamItem): Boolean = TokenAtmos.containsMatchIn(stream.rankingText())

    /**
     * Fora de cache no debrid (stream_texto_fora_de_cache): "⏳" do AIOStreams, "⬇",
     * "[RD download]" do Torrentio, "uncached"; ou a conferência do próprio app.
     */
    fun isUncached(stream: StreamItem): Boolean {
        if (stream.debridCacheStatus?.state == StreamDebridCacheState.NOT_CACHED) return true
        val text = stream.rankingText()
        return "⏳" in text || "⬇" in text || " download]" in text || "uncached" in text
    }

    /** Cabe no teto: sem teto, sem altura declarada, ou altura até o teto (cabeNoTeto). */
    fun fitsCeiling(stream: StreamItem, maxHeight: Int): Boolean {
        if (maxHeight <= 0) return true
        val h = height(stream)
        return h == 0 || h <= maxHeight
    }

    /**
     * A pontuação da TV: MP4 4K DV > 4K > MP4 DV > MP4 > Atmos > altura. Acima do teto
     * vai para o fim (não sai), e fora de cache vem depois das cacheadas.
     */
    fun score(stream: StreamItem, maxHeight: Int = 0): Long {
        val h = height(stream)
        val mp4 = isMp4(stream)
        val dv = isDolbyVision(stream)
        var p = 0L
        if (mp4 && h >= 2160 && dv) p += 100_000
        if (h >= 2160) p += 20_000
        if (mp4 && dv) p += 10_000
        if (mp4) p += 5_000
        if (isAtmos(stream)) p += 2_000
        p += h
        if (!fitsCeiling(stream, maxHeight)) p -= 1_000_000
        if (isUncached(stream)) p -= 500_000
        return p
    }

    /** A lista na ordem da escolha automática; empate fica na ordem dos addons (sort estável). */
    fun rankByScore(streams: List<StreamItem>, maxHeight: Int = 0): List<StreamItem> =
        streams.withIndex()
            .sortedWith(compareByDescending<IndexedValue<StreamItem>> { score(it.value, maxHeight) }.thenBy { it.index })
            .map { it.value }

    /**
     * Ordem do addon, com as de dentro do teto antes das de fora e as cacheadas antes das
     * fora de cache (fonteauto.c, modo "primeira"). Nada sai da lista.
     */
    fun preferenceOrder(streams: List<StreamItem>, maxHeight: Int = 0): List<StreamItem> =
        streams.withIndex()
            .sortedWith(
                compareBy<IndexedValue<StreamItem>> { if (fitsCeiling(it.value, maxHeight)) 0 else 1 }
                    .thenBy { if (isUncached(it.value)) 1 else 0 }
                    .thenBy { it.index },
            )
            .map { it.value }

    /** Fontes acima do teto vão para o fim do grupo, na mesma ordem (alturaMax/cabeNoTeto). */
    fun moveAboveCeilingToEnd(streams: List<StreamItem>, maxHeight: Int): List<StreamItem> {
        if (maxHeight <= 0 || streams.isEmpty()) return streams
        val (fits, above) = streams.partition { fitsCeiling(it, maxHeight) }
        return if (above.isEmpty()) streams else fits + above
    }
}

internal object StreamWarnings {

    /**
     * O vídeo de "sem acesso" de cada addon (stream_url_sem_assinatura): Torrentio
     * failed_access/blocked_access, MediaFusion exceptions/invalid_token e need_premium, AIOStreams e StremThru
     * static/401, 403 e payment_required.
     */
    fun isNoSubscriptionUrl(url: String?): Boolean {
        val u = url ?: return false
        return "/failed_access" in u || "/blocked_access" in u ||
            "/exceptions/invalid_token" in u || "/exceptions/need_premium" in u ||
            "/exceptions/invalid_credentials" in u ||
            "/payment_required.mp4" in u || "static/401.mp4" in u || "static/403.mp4" in u
    }

    /**
     * Endereço de aviso e não de conteúdo (streams.c, enderecoDeAviso): o AIOStreams manda
     * para slate.mp4/slate.m3u8 quando o link expirou e o Debridio para downloading.mp4
     * quando o arquivo não está em cache. Tocam normalmente; só o endereço denuncia.
     */
    fun isWarningUrl(url: String?): Boolean {
        val u = url ?: return false
        return "downloading.mp4" in u || "/slate" in u || "slate.mp4" in u || "slate.m3u8" in u ||
            isNoSubscriptionUrl(u)
    }

    private val noSubscriptionPhrases = listOf(
        "subscription!", "no active subscription", "subscription expired",
        "expired subscription", "expired/invalid", "invalid api key",
        "apikey/token", "invalid credentials", "invalid token", "not premium",
        "need premium", "premium account required", "must be premium",
        "free trial limit", "payment required", "payment issue", "is blocked!",
    )

    /**
     * O aviso que o addon manda no lugar das fontes quando a chave do debrid não vale mais
     * (stream_aviso_sem_assinatura). A frase sozinha não basta (um filme pode se chamar
     * "Expired"): precisa do link de aviso, de uma marca de erro ("[❌]", "[ERROR]") ou de
     * um serviço de debrid nomeado no mesmo texto.
     */
    fun isNoSubscriptionWarning(stream: StreamItem): Boolean {
        val url = stream.url ?: stream.externalUrl
        if (isNoSubscriptionUrl(url)) return true
        val text = listOfNotNull(stream.name, stream.title, stream.description).joinToString("\n")
        val lower = text.lowercase()
        if (noSubscriptionPhrases.none { it in lower }) return false
        val error = "❌" in text || "[error]" in lower || " error\n" in lower || "error]" in lower
        return error || DebridPlanGuard.servicesOf(text, null).isNotEmpty()
    }

    /** Aviso puro: não traz nada que toque (sem link http, torrent nem resolução do debrid). */
    fun isPureWarning(stream: StreamItem): Boolean =
        isNoSubscriptionWarning(stream) &&
            stream.playableDirectUrl?.startsWith("http", ignoreCase = true) != true &&
            stream.p2pInfoHash == null &&
            stream.clientResolve == null &&
            stream.serverTarget == null
}
