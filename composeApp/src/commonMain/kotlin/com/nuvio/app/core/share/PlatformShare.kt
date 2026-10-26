package com.nuvio.app.core.share

/**
 * Better Nuvio: a folha de compartilhar do sistema (UIActivityViewController no iOS,
 * ACTION_SEND no Android). Usada pelo "Recomendar" da página do título.
 */
internal expect object PlatformShare {
    fun shareText(text: String)
}
