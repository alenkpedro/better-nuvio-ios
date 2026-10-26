package com.nuvio.app.features.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.i18n.localizedByteUnit
import com.nuvio.app.core.ui.BetterNuvioTitleFont
import com.nuvio.app.core.ui.corViva
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.onCorViva
import com.nuvio.app.features.player.PlayerSettingsUiState
import kotlin.math.round
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.bn_streams_addon_loading
import nuvio.composeapp.generated.resources.bn_streams_addons_count
import nuvio.composeapp.generated.resources.bn_streams_found_count
import nuvio.composeapp.generated.resources.bn_streams_recommended
import nuvio.composeapp.generated.resources.bn_streams_recommended_usual
import nuvio.composeapp.generated.resources.bn_streams_resolution_all
import nuvio.composeapp.generated.resources.bn_streams_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------
// Better Nuvio · Função 3 (Fontes): recomendada em cima, filtros por addon e
// por resolução, e a mesma ordem de informação da TV em cada linha.
// ---------------------------------------------------------------------------

/** Filtros de resolução da folha: null = Todas. */
internal val BnResolutionFilters = listOf("4K", "1080p", "720p")

private fun tokenRegex(token: String) =
    Regex("(?<![a-z0-9])" + Regex.escape(token) + "(?![a-z0-9])")

private val Res4k = listOf("2160p", "2160", "4k", "uhd").map(::tokenRegex)
private val Res1080 = listOf("1080p", "1080i", "1080").map(::tokenRegex)
private val Res720 = listOf("720p", "720").map(::tokenRegex)
private val Res480 = listOf("480p", "576p").map(::tokenRegex)

private fun String.has(token: String) = tokenRegex(token).containsMatchIn(this)

private fun resolutionIn(text: String): String? {
    val s = text.lowercase()
    val has1080 = Res1080.any { it.containsMatchIn(s) }
    val has720 = Res720.any { it.containsMatchIn(s) }
    return when {
        Res4k.any { it.containsMatchIn(s) } && !has1080 && !has720 -> "4K"
        has1080 -> "1080p"
        has720 -> "720p"
        Res480.any { it.containsMatchIn(s) } -> "480p"
        else -> null
    }
}

/** Resolução (4K / 1080p / 720p / 480p) achada num texto livre, como o nome salvo da fonte. */
internal fun bnResolutionInText(text: String?): String? = text?.let(::resolutionIn)

private fun StreamItem.qualityHaystack(): String = buildString {
    name?.let { append(it).append(' ') }
    title?.let { append(it).append(' ') }
    description?.let { append(it).append(' ') }
    behaviorHints.filename?.let { append(it).append(' ') }
    clientResolve?.filename?.let { append(it).append(' ') }
    clientResolve?.torrentName?.let { append(it).append(' ') }
}.lowercase()

/** Resolução da fonte: primeiro o que o addon diz (parsed/nome), depois o resto do texto. */
internal fun StreamItem.bnResolution(): String? {
    clientResolve?.stream?.raw?.parsed?.resolution?.let { resolutionIn(it) }?.let { return it }
    name?.let { resolutionIn(it) }?.let { return it }
    return resolutionIn(qualityHaystack())
}

/**
 * Selos de qualidade na ordem da TV (badges.c): resolução, origem, vídeo,
 * áudio, canais e codec.
 */
internal fun StreamItem.bnQualityTags(): List<String> {
    val s = qualityHaystack()
    val tags = mutableListOf<String>()
    bnResolution()?.takeIf { it != "480p" }?.let(tags::add)
    when {
        s.has("remux") -> tags += "REMUX"
        s.has("bluray") || s.has("blu-ray") -> tags += "BLURAY"
        s.has("web-dl") || s.has("webdl") || s.has("web.dl") || s.has("web dl") -> tags += "WEB-DL"
        s.has("webrip") || s.has("web-rip") || s.has("web.rip") -> tags += "WEBRIP"
    }
    when {
        s.has("hdr10+") || s.has("hdr10plus") || s.has("hdr10p") -> tags += "HDR10+"
        s.has("hdr10") -> tags += "HDR10"
        s.has("hdr") || s.has("hlg") -> tags += "HDR"
    }
    val dv = s.has("dv") || s.has("dovi") || s.contains("dolby vision") || s.contains("dolby.vision") ||
        s.contains("dolby-vision") || s.contains("dolby_vision")
    if (dv) tags += "DV"
    if (s.has("imax")) tags += "IMAX"
    when {
        s.has("atmos") -> tags += "ATMOS"
        s.has("truehd") || s.has("true-hd") || s.has("true hd") -> tags += "TRUEHD"
        s.has("ddp") || s.contains("ddp5") || s.contains("ddp7") || s.has("dd+") || s.has("eac3") ||
            s.has("e-ac-3") || s.has("eac-3") -> tags += "DD+"
        s.has("ac3") || s.has("ac-3") || s.has("dd5.1") || s.has("dd2.0") -> tags += "DD"
    }
    when {
        s.has("dts:x") || s.has("dts-x") || s.has("dts.x") -> tags += "DTS:X"
        s.has("dts-hd ma") || s.has("dts-hd.ma") || s.has("dts.hd.ma") || s.has("dts-ma") -> tags += "DTS-HD MA"
        s.has("dts-hd") || s.has("dts.hd") -> tags += "DTS-HD"
        s.has("dts") -> tags += "DTS"
    }
    if (tags.none { it in setOf("ATMOS", "TRUEHD", "DD+", "DD") || it.startsWith("DTS") } && s.has("aac")) {
        tags += "AAC"
    }
    when {
        s.has("7.1") -> tags += "7.1"
        s.has("5.1") -> tags += "5.1"
    }
    when {
        s.has("hevc") || s.has("x265") || s.has("h265") || s.has("h.265") -> tags += "HEVC"
        s.has("av1") -> tags += "AV1"
        s.has("avc") || s.has("x264") || s.has("h264") || s.has("h.264") -> tags += "AVC"
    }
    return tags
}

private val BitrateRegex = Regex("(\\d+(?:[.,]\\d+)?)\\s*(mbps|mb/s|mbit/s|kbps)", RegexOption.IGNORE_CASE)
private val SizeRegex = Regex("(\\d+(?:[.,]\\d+)?)\\s*(tb|gb|gib|mb|mib)(?![a-z/])", RegexOption.IGNORE_CASE)

/** Tamanho e velocidade, a segunda linha de cada fonte. [includeSize] segue o ajuste "mostrar tamanho". */
internal fun StreamItem.bnSizeAndBitrate(includeSize: Boolean = true): String? {
    val text = listOfNotNull(name, title, description).joinToString(" ")
    val bitrate = BitrateRegex.find(text)?.let { match ->
        "${match.groupValues[1]} ${if (match.groupValues[2].lowercase() == "kbps") "kbps" else "Mbps"}"
    }
    val bytes = behaviorHints.videoSize ?: clientResolve?.stream?.raw?.size
    val size = if (!includeSize) {
        null
    } else if (bytes != null && bytes > 0) {
        val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        if (gib >= 1.0) {
            "${round(gib * 10.0) / 10.0} ${localizedByteUnit("GB")}"
        } else {
            "${round(bytes.toDouble() / (1024.0 * 1024.0)).toInt()} ${localizedByteUnit("MB")}"
        }
    } else {
        SizeRegex.find(text)?.let { "${it.groupValues[1]} ${it.groupValues[2].uppercase().replace("IB", "B")}" }
    }
    return listOfNotNull(bitrate, size).joinToString(" · ").ifBlank { null }
}

/** Nome e resolução, a primeira linha: as linhas do `name` viram uma só. */
internal fun StreamItem.bnNameLine(): String {
    val label = streamLabel.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
        .ifBlank { streamLabel }
    val res = bnResolution() ?: return label
    return if (label.lowercase().contains(res.lowercase()) ||
        (res == "4K" && Res4k.any { it.containsMatchIn(label.lowercase()) })
    ) label else "$label · $res"
}

internal fun StreamsUiState.withResolutionFilter(resolution: String?): StreamsUiState {
    if (resolution == null) return this
    return copy(groups = groups.map { group -> group.copy(streams = group.streams.filter { it.bnResolution() == resolution }) })
}

// ---------------------------------------------------------------------------
// Recomendada
// ---------------------------------------------------------------------------

internal data class RecommendedStream(
    val stream: StreamItem,
    /** "A de sempre": a mesma fonte que tocou da última vez neste título (bingeGroup). */
    val isUsual: Boolean,
)

/**
 * A fonte em destaque. Como na TV (fontepref.c + fonteauto.c): primeiro a que a
 * pessoa escolheu a mão neste título (bingeGroup, depois addon + áudio); senão a
 * que tocou da última vez (mesmo bingeGroup, desempatando por arquivo e infoHash);
 * senão a que o auto-play escolheria com os ajustes atuais (no modo manual, a
 * melhor qualidade da TV); senão a primeira da lista (fixadas primeiro).
 */
internal fun pickRecommendedStream(
    orderedStreams: List<StreamItem>,
    autoPlayStream: StreamItem?,
    playerSettings: PlayerSettingsUiState,
    debridEnabled: Boolean,
    activeResolverProviderId: String?,
    usualBingeGroup: String?,
    usualFilename: String?,
    usualInfoHash: String?,
    rememberedSource: RememberedSource? = null,
    maxQualityHeight: Int = 0,
): RecommendedStream? {
    val selectable = orderedStreams.filter { it.isSelectableForPlayback(debridEnabled) }
    if (selectable.isEmpty()) return null

    // A que a pessoa escolheu a mão neste título (fontepref.c) vem antes de tudo.
    rememberedSource?.let { RememberedSourceMatcher.pick(selectable, it) }
        ?.let { return RecommendedStream(it, isUsual = true) }

    val bingeGroup = usualBingeGroup?.trim()?.takeIf { it.isNotEmpty() }
    if (bingeGroup != null) {
        val sameGroup = selectable.filter { it.behaviorHints.bingeGroup == bingeGroup }
        val usual = sameGroup.firstOrNull { !usualFilename.isNullOrBlank() && it.behaviorHints.filename == usualFilename }
            ?: sameGroup.firstOrNull { !usualInfoHash.isNullOrBlank() && it.p2pInfoHash.equals(usualInfoHash, ignoreCase = true) }
            ?: sameGroup.firstOrNull()
        if (usual != null) return RecommendedStream(usual, isUsual = true)
    }

    autoPlayStream?.takeIf { it in selectable }?.let { return RecommendedStream(it, isUsual = false) }

    val mode = playerSettings.streamAutoPlayMode.takeIf {
        it != StreamAutoPlayMode.MANUAL &&
            !(it == StreamAutoPlayMode.REGEX_MATCH &&
                !StreamAutoPlayPolicy.isRegexSelectionConfigured(playerSettings.streamAutoPlayRegex))
    } ?: StreamAutoPlayMode.BEST_QUALITY
    val installedAddonNames = orderedStreams.filter { it.isInstalledAddonStream }.mapTo(mutableSetOf()) { it.addonName }
    val auto = StreamAutoPlaySelector.selectAutoPlayStream(
        streams = selectable,
        mode = mode,
        regexPattern = playerSettings.streamAutoPlayRegex,
        source = playerSettings.streamAutoPlaySource,
        installedAddonNames = installedAddonNames,
        selectedAddons = playerSettings.streamAutoPlaySelectedAddons,
        selectedPlugins = playerSettings.streamAutoPlaySelectedPlugins,
        debridEnabled = debridEnabled,
        activeResolverProviderId = activeResolverProviderId,
        maxQualityHeight = maxQualityHeight,
    )
    return RecommendedStream(auto ?: selectable.first(), isUsual = false)
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

@Composable
internal fun FontesHeader(
    streamCount: Int,
    addonCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.bn_streams_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = BetterNuvioTitleFont,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.025).em,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (streamCount > 0) {
            Text(
                text = pluralStringResource(Res.plurals.bn_streams_found_count, streamCount, streamCount) +
                    " · " + pluralStringResource(Res.plurals.bn_streams_addons_count, addonCount, addonCount),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.nuvio.colors.textMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
internal fun ResolutionFilterRow(
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (listOf<String?>(null) + BnResolutionFilters).forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSelected(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option ?: stringResource(Res.string.bn_streams_resolution_all),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (isSelected) Color.White else MaterialTheme.nuvio.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun QualityTagRow(
    tags: List<String>,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            Text(
                text = tag,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(background)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.04.em,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun RecommendedStreamCard(
    recommended: RecommendedStream,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stream = recommended.stream
    val cor = corViva()
    val ink = cor.onCorViva()
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(cor, lerp(cor, Color.Black, 0.3f))))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    if (recommended.isUsual) Res.string.bn_streams_recommended_usual else Res.string.bn_streams_recommended,
                ).uppercase(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.em,
                ),
                color = ink.copy(alpha = 0.9f),
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stream.bnNameLine(),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val secondLine = listOfNotNull(stream.addonName.takeIf { it.isNotBlank() }, stream.bnSizeAndBitrate())
            .joinToString(" · ")
        if (secondLine.isNotBlank()) {
            Text(
                text = secondLine,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = ink.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        stream.streamSubtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = ink.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val tags = stream.bnQualityTags()
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            QualityTagRow(tags = tags, background = Color.Black.copy(alpha = 0.25f), contentColor = ink)
        }
    }
}

@Composable
internal fun AddonLoadingRow(
    addonName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
        )
        Text(
            text = stringResource(Res.string.bn_streams_addon_loading, addonName),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.nuvio.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
