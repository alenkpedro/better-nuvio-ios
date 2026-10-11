package com.nuvio.app.features.details.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.features.streams.CachedStreamLink

/**
 * Better Nuvio: the source Play is going to use (the reused last link), shown under
 * the detail buttons. A screen-wide holder instead of a new parameter threaded through
 * every layout of the details screen, to keep the upstream diff small.
 */
object DetailSourcePreview {
    var current by mutableStateOf<String?>(null)
        private set

    fun set(link: CachedStreamLink?) {
        current = link?.let(::describe)
    }

    internal fun describe(link: CachedStreamLink): String? {
        val name = link.streamName.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        val file = link.filename?.trim()?.takeIf { it.isNotEmpty() }
        val size = link.videoSize?.takeIf { it > 0 }?.let(::formatSize)
        val parts = listOfNotNull(
            name,
            size,
            link.addonName.trim().takeIf { it.isNotEmpty() && it != name },
            file?.takeIf { it != name },
        )
        return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
    }

    private fun formatSize(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        if (gb >= 1) {
            val tenths = (gb * 10).toLong()
            return "${tenths / 10}.${tenths % 10} GB"
        }
        return "${bytes / 1_048_576} MB"
    }
}
