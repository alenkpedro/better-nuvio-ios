package com.nuvio.app.betternuvio

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** The title's logo for the player controls (provided by the player runtime). */
val LocalPlayerTitleLogo = compositionLocalOf<String?> { null }

/**
 * Better Nuvio: in the player, the title's LOGO replaces the written title when the
 * artwork exists (TV app, player.c: "o logo do titulo substitui o texto quando ja
 * esta carregado"). Until the logo loads, or when it fails, the text stays.
 */
@Composable
fun BetterPlayerTitle(
    maxHeight: Dp,
    maxWidth: Dp = 240.dp,
    text: @Composable () -> Unit,
) {
    val logo = LocalPlayerTitleLogo.current?.takeIf { it.isNotBlank() }
    var failed by remember(logo) { mutableStateOf(false) }
    var loaded by remember(logo) { mutableStateOf(false) }
    if (logo == null || failed) {
        text()
        return
    }
    if (!loaded) text()
    AsyncImage(
        model = logo,
        contentDescription = null,
        modifier = Modifier.heightIn(max = maxHeight).widthIn(max = maxWidth),
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        onSuccess = { loaded = true },
        onError = { failed = true },
    )
}
