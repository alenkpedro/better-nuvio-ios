package com.nuvio.app.betternuvio

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.request.SuccessResult
import coil3.toBitmap

internal actual fun roomLightBitmap(result: SuccessResult): ImageBitmap? =
    runCatching {
        val bitmap = result.image.toBitmap()
        // Hardware bitmaps can't be read back; copy to a software one first.
        val readable = if (bitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
            bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        readable.asImageBitmap()
    }.getOrNull()
