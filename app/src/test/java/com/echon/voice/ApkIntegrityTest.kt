package com.echon.voice

import com.echon.voice.core.update.sha256Hex
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the SHA-256 hex digest used to verify downloaded update APKs
 * (security review finding #2) against published NIST/RFC test vectors, so the
 * hex formatting (lowercase, zero-padded, 64 chars) can't silently regress.
 */
class ApkIntegrityTest {

    @Test
    fun `empty input matches the known SHA-256 vector`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ByteArray(0).sha256Hex(),
        )
    }

    @Test
    fun `abc matches the known SHA-256 vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".toByteArray().sha256Hex(),
        )
    }

    @Test
    fun `digest is lowercase and zero-padded to 64 hex chars`() {
        // 0x00 byte would drop a nibble without zero-padding.
        val hex = byteArrayOf(0, 1, 2).sha256Hex()
        assertEquals(64, hex.length)
        assertEquals(hex.lowercase(), hex)
    }
}
