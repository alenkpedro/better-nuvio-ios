package com.nuvio.app.features.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.ArtworkTint
import com.nuvio.app.core.ui.NativeTabBridge
import com.nuvio.app.core.ui.ThemeColors
import com.nuvio.app.core.ui.formatHexColor
import com.nuvio.app.core.ui.nativeAccentGradientHex
import com.nuvio.app.core.ui.onCorViva
import com.nuvio.app.core.ui.rememberArtworkDominantColor
import com.nuvio.app.core.ui.rememberArtworkTint
import com.nuvio.app.core.ui.rememberDynamicArtworkBackgroundEnabled
import com.nuvio.app.features.player.PlayerSettingsUiState
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.streams.StreamLinkCacheRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Vidro da TV (38,38,44 a 50%), usado nos botões secundários e nas pílulas. */
internal val BetterNuvioGlass = Color(0x8026262C)
internal val BetterNuvioGlassEdge = Color.White.copy(alpha = 0.14f)

/**
 * Cor viva de um título, como na TV (home.c: "Cor da logo"): primeiro o logo; se o logo
 * não tiver cor (branco, cinza) ou não existir, a arte. Sem a Imersiva, a cor de destaque.
 * Devolve o alvo, sem animação, para quem precisa publicar a cor (aba ativa).
 */
@Composable
internal fun rememberTitleCorVivaTarget(logoUrl: String?, artUrl: String?): Color {
    val immersive = rememberDynamicArtworkBackgroundEnabled()
    val fallback = MaterialTheme.colorScheme.primary
    val logoTint = rememberArtworkTint(logoUrl?.takeIf { it.isNotBlank() }, enabled = immersive)
    val artColor = rememberArtworkDominantColor(
        url = artUrl,
        enabled = immersive && logoTint == ArtworkTint.None,
    )
    if (!immersive) return fallback
    return when (logoTint) {
        is ArtworkTint.Tinted -> logoTint.value
        ArtworkTint.None -> artColor ?: fallback
        ArtworkTint.Pending -> fallback
    }
}

/** Mesma cor de [rememberTitleCorVivaTarget], com a transição suave da cor viva. */
@Composable
internal fun rememberTitleCorViva(logoUrl: String?, artUrl: String?): Color {
    val target = rememberTitleCorVivaTarget(logoUrl, artUrl)
    val animated by animateColorAsState(target, animationSpec = tween(420), label = "titleCorViva")
    return animated
}

/**
 * A aba ativa da barra Liquid Glass nativa pega a cor viva do destaque enquanto a Home está
 * na frente. Só mexe na barra com a Imersiva ligada; ao sair, volta para a cor do tema.
 */
@Composable
internal fun HeroTabAccentEffect(color: Color, active: Boolean) {
    val immersive = rememberDynamicArtworkBackgroundEnabled()
    val publishing = active && immersive
    val state = remember { HeroTabAccentState() }
    LaunchedEffect(publishing, color) {
        if (publishing) {
            val hex = formatHexColor(color.toArgb() and 0xFFFFFF)
            if (state.publishedHex != hex) {
                state.publishedHex = hex
                NativeTabBridge.publishAccentColor(hex, listOf(hex))
            }
        } else if (state.publishedHex != null) {
            state.publishedHex = null
            restoreThemeTabAccent()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (state.publishedHex != null) {
                state.publishedHex = null
                restoreThemeTabAccent()
            }
        }
    }
}

private class HeroTabAccentState {
    var publishedHex: String? = null
}

private fun restoreThemeTabAccent() {
    val palette = ThemeColors.getColorPalette(
        ThemeSettingsRepository.selectedTheme.value,
        ThemeSettingsRepository.customThemeColors.value,
    )
    NativeTabBridge.publishAccentColor(
        hexColor = palette.nativeAccentHex,
        gradientHexColors = palette.nativeAccentGradientHex(),
    )
}

/** "58 min" ou "1 h 12 min" que faltam, ou null sem duração conhecida. */
@Composable
internal fun bnRemainingTimeText(item: ContinueWatchingItem): String? {
    if (item.progressFraction <= 0f || item.durationMs <= 0L) return null
    val positionMs = when {
        item.resumePositionMs > 0L -> item.resumePositionMs
        else -> (item.durationMs * item.progressFraction.coerceIn(0f, 1f)).toLong()
    }
    val remainingMinutes = ((item.durationMs - positionMs).coerceAtLeast(0L) / 60_000L).coerceAtLeast(1L)
    val hours = remainingMinutes / 60L
    val minutes = remainingMinutes % 60L
    return if (hours > 0L) {
        stringResource(Res.string.bn_time_hours_minutes, hours.toInt(), minutes.toInt())
    } else {
        stringResource(Res.string.bn_time_minutes, remainingMinutes.toInt())
    }
}

/**
 * A fonte que o Play vai usar: o último link guardado ("Reusar último link"), quando ele
 * ainda vale. Mesma chave que o StreamDestination consulta antes de abrir a lista.
 */
internal fun heroPlaySourceLabel(
    type: String,
    videoId: String,
    parentMetaId: String?,
    season: Int?,
    episode: Int?,
    settings: PlayerSettingsUiState,
): String? {
    if (!settings.streamReuseLastLinkEnabled) return null
    val key = StreamLinkCacheRepository.contentKey(
        type = type,
        videoId = videoId,
        parentMetaId = parentMetaId,
        season = season,
        episode = episode,
    )
    val maxAgeMs = settings.streamReuseLastLinkCacheHours * 60L * 60L * 1000L
    val cached = runCatching { StreamLinkCacheRepository.getValid(key, maxAgeMs) }.getOrNull() ?: return null
    val name = cached.streamName.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
    return listOfNotNull(cached.addonName.trim().takeIf { it.isNotEmpty() }, name)
        .distinct()
        .joinToString(" · ")
        .takeIf { it.isNotEmpty() }
}

@Composable
internal fun HeroPrimaryButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = color.onCorViva()
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
internal fun HeroGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(BetterNuvioGlass)
            .border(1.dp, BetterNuvioGlassEdge, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
internal fun HeroImdbBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFF5C518))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = "IMDb",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.Black,
            color = Color(0xFF0A0A0B),
            maxLines = 1,
        )
    }
}

/** O contador "2 / 10" da TV no lugar dos pontinhos. */
@Composable
internal fun HeroPageCounter(
    itemCount: Int,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val position = pagerState.targetPage % itemCount + 1
    Text(
        text = "$position / $itemCount",
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}
