package com.nuvio.app.features.autosync

import com.nuvio.app.core.logging.InAppLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToFileOffset
import platform.Foundation.writeToFile
import platform.posix.memcpy

private const val LOG = "AutoSync/iOS"
private const val MAX_SUBTITLE_BYTES = 8L * 1024 * 1024

/** Network and file access for [AutoSyncIosCoordinator]; nothing here knows about subtitles' content. */
internal object AutoSyncIosIo {
    private val client: HttpClient by lazy {
        HttpClient(Darwin) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 45_000
            }
        }
    }

    /**
     * HTTP Range reader (206 required, or 200 for a read from byte 0, cut at the requested size),
     * or a direct file reader for local sources. Null for anything else (HLS playlists, etc.).
     */
    fun rangeReaderFor(url: String, headers: Map<String, String>): MatroskaSubtitleCueIndex.RangeReader? {
        val lower = url.lowercase()
        if (lower.substringBefore('?').endsWith(".m3u8")) return null
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> MatroskaSubtitleCueIndex.RangeReader { start, end ->
                httpRange(url, headers, start, end)
            }
            lower.startsWith("file://") || url.startsWith("/") -> {
                val path = if (url.startsWith("/")) url else NSURL.URLWithString(url)?.path ?: return null
                MatroskaSubtitleCueIndex.RangeReader { start, end -> fileRange(path, start, end) }
            }
            else -> null
        }
    }

    private suspend fun httpRange(url: String, headers: Map<String, String>, start: Long, end: Long): ByteArray? =
        try {
            val wanted = end - start + 1
            client.prepareGet(url) {
                headers.forEach { (k, v) -> if (!k.equals(HttpHeaders.Range, ignoreCase = true)) header(k, v) }
                header(HttpHeaders.Range, "bytes=$start-$end")
            }.execute { response ->
                val ok = response.status == HttpStatusCode.PartialContent ||
                    (response.status == HttpStatusCode.OK && start == 0L)
                if (!ok) {
                    InAppLogger.info(LOG, "range $start-$end: HTTP ${response.status.value}")
                    null
                } else {
                    readBounded(response.bodyAsChannel(), wanted)
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            InAppLogger.info(LOG, "range $start-$end failed: ${error.message}")
            null
        }

    private suspend fun readBounded(channel: io.ktor.utils.io.ByteReadChannel, limit: Long): ByteArray {
        val out = ByteArray(limit.toInt())
        var filled = 0
        while (filled < out.size) {
            val n = channel.readAvailable(out, filled, out.size - filled)
            if (n <= 0) break
            filled += n
        }
        return if (filled == out.size) out else out.copyOf(filled)
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun fileRange(path: String, start: Long, end: Long): ByteArray? = withContext(Dispatchers.IO) {
        val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: return@withContext null
        try {
            handle.seekToFileOffset(start.toULong())
            handle.readDataOfLength((end - start + 1).toULong()).toByteArray()
        } finally {
            handle.closeFile()
        }
    }

    /** The add-on subtitle as text (bounded); strict UTF-8, else Latin-1 like most old SRTs. */
    suspend fun downloadSubtitle(url: String, headers: Map<String, String>): String? {
        val bytes = try {
            if (url.startsWith("file://") || url.startsWith("/")) {
                val path = if (url.startsWith("/")) url else NSURL.URLWithString(url)?.path ?: return null
                fileRange(path, 0, MAX_SUBTITLE_BYTES - 1)
            } else {
                client.prepareGet(url) {
                    headers.forEach { (k, v) -> header(k, v) }
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        InAppLogger.info(LOG, "subtitle download: HTTP ${response.status.value}")
                        null
                    } else {
                        readBounded(response.bodyAsChannel(), MAX_SUBTITLE_BYTES)
                    }
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            InAppLogger.info(LOG, "subtitle download failed: ${error.message}")
            null
        } ?: return null
        if (bytes.isEmpty()) return null
        return decodeSubtitle(bytes)
    }

    @OptIn(ExperimentalForeignApi::class)
    fun writeRetimedSubtitle(name: String, text: String): String? {
        val path = NSTemporaryDirectory().trimEnd('/') + "/" + name
        @Suppress("CAST_NEVER_SUCCEEDS")
        val ok = (text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        return if (ok) NSURL.fileURLWithPath(path).absoluteString ?: path else null
    }
}

internal fun decodeSubtitle(bytes: ByteArray): String {
    var start = 0
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) start = 3
    return try {
        bytes.decodeToString(start, bytes.size, throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        CharArray(bytes.size - start) { (bytes[start + it].toInt() and 0xFF).toChar() }.concatToString()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}
