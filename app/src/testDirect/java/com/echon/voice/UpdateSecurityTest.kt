package com.echon.voice

import com.echon.voice.core.update.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class UpdateSecurityTest {
    @Test fun missingOrMalformedDigestFailsClosed() {
        for (hash in listOf(null, "", "abc", "z".repeat(64))) {
            assertThrows(IllegalArgumentException::class.java) { requireValidUpdateHash(hash) }
        }
        requireValidUpdateHash("AB".repeat(32))
    }
    @Test fun updatesMustMatchPackageAndManifestAndMoveForward() {
        requireSelfUpdate("com.echon.voice", "com.echon.voice", 25, 24, 25)
        assertThrows(IllegalArgumentException::class.java) { requireSelfUpdate("evil.app", "com.echon.voice", 25, 24, 25) }
        assertThrows(IllegalArgumentException::class.java) { requireSelfUpdate("app", "app", 24, 24, 24) }
        assertThrows(IllegalArgumentException::class.java) { requireSelfUpdate("app", "app", 23, 24, 25) }
        assertThrows(IllegalArgumentException::class.java) { requireSelfUpdate("app", "app", 26, 24, 25) }
    }
    @Test fun downloadLimitAppliesWithoutContentLength() {
        val output = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            copyUpdateCapped(ByteArrayInputStream(ByteArray(9000)), output, 8500)
        }
        assertTrue(output.size() <= 8500)
        val exact = ByteArrayOutputStream()
        copyUpdateCapped(ByteArrayInputStream(ByteArray(8500)), exact, 8500)
        assertEquals(8500, exact.size())
    }
    @Test fun cancellationStopsCopyBeforeWriting() {
        val output = ByteArrayOutputStream()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            copyUpdateCapped(ByteArrayInputStream(ByteArray(10)), output, 100) {
                throw kotlinx.coroutines.CancellationException()
            }
        }
        assertEquals(0, output.size())
    }
}
