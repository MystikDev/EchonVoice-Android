package com.echon.voice.core.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Upload byte caps — reject oversized content before it can exhaust memory. */
object UploadLimits {
    const val ATTACHMENT_MAX_BYTES = 25 * 1024 * 1024 // 25 MB
    const val AVATAR_MAX_BYTES = 8 * 1024 * 1024 // 8 MB
}

/**
 * Reads at most [maxBytes] from the stream, returning null if the source exceeds
 * that (rather than buffering an unbounded — possibly malicious — content-provider
 * stream into memory and OOM-crashing). The reported size from a provider can lie,
 * so this caps the *actual* streamed bytes.
 */
fun InputStream.readBytesCapped(maxBytes: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
