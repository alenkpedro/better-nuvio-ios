package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.settings.ThemeSettingsRepository

/**
 * Cor viva, a assinatura do Better Nuvio na TV (corviva.c): a cor tirada da arte do
 * título pinta o botão principal, o foco, a aba ativa, a temporada ativa, a barra de
 * progresso e a fonte recomendada.
 *
 * Com a Cor de destaque em "Imersiva" ela vem da arte; com uma cor fixa, é a cor fixa.
 * Telas que mostram um título chamam [ProvideCorViva] com a arte dele; o resto da árvore
 * lê [corViva].
 */
val LocalCorViva = compositionLocalOf<Color?> { null }

/** A cor viva em vigor: a do título mais próximo ou, sem título, a cor de destaque. */
@Composable
fun corViva(): Color = LocalCorViva.current ?: MaterialTheme.colorScheme.primary

/** Texto e ícones em cima da cor viva: preto em cores claras, branco nas escuras. */
fun Color.onCorViva(): Color =
    if (luminance() > 0.55f) Color(0xFF0A0A0B) else Color.White

/**
 * Resolve a cor viva da arte [artUrl] (logo, pôster ou fundo do título). Respeita a
 * Cor de destaque: só segue a arte quando a Imersiva está ligada.
 */
@Composable
fun rememberCorViva(artUrl: String?): Color {
    val immersive by ThemeSettingsRepository.dynamicArtworkBackgroundEnabled.collectAsStateWithLifecycle()
    val fallback = MaterialTheme.colorScheme.primary
    val fromArt = rememberArtworkDominantColor(artUrl, enabled = immersive)
    val target = if (immersive) fromArt ?: fallback else fallback
    val animated by animateColorAsState(target, animationSpec = tween(420), label = "corViva")
    return animated
}

/** Dá a cor viva de [artUrl] para tudo que estiver dentro de [content]. */
@Composable
fun ProvideCorViva(artUrl: String?, content: @Composable () -> Unit) {
    val color = rememberCorViva(artUrl)
    CompositionLocalProvider(LocalCorViva provides color, content = content)
}

/** Mesma coisa, quando a cor já foi resolvida (ex.: o destaque atual da Home). */
@Composable
fun ProvideCorViva(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCorViva provides color, content = content)
}
