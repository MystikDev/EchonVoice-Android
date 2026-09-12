package com.echon.voice.core.storage

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Real Android Keystore and legacy Tink keysets, in a namespace isolated from app credentials. */
@Suppress("DEPRECATION")
class KeystoreMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fixture(block: (String, String, String, String) -> Unit) {
        val prefix = "migration-test-${UUID.randomUUID()}"
        val file = "$prefix.enc"
        val alias = "$prefix-key"
        val legacy = "$prefix-legacy"
        val oldAlias = "$prefix-old-key"
        try { block(file, alias, legacy, oldAlias) }
        finally {
            context.deleteSharedPreferences(legacy)
            listOf("", ".bak", ".new").forEach { File(context.noBackupFilesDir, file + it).delete() }
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias); deleteEntry(oldAlias) }
        }
    }
    private fun seed(legacy: String, oldAlias: String) {
        val prefs = EncryptedSharedPreferences.create(context, legacy,
            MasterKey.Builder(context, oldAlias).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        assertTrue(prefs.edit().putString("session-token", "test-access").putString("refresh-token", "test-refresh").commit())
    }

    @Test fun legacyUpgradeRotationAndLogoutSurviveReopening() = fixture { file, alias, legacy, oldAlias ->
        seed(legacy, oldAlias)
        fun open() = SecureTokenStore.create(context, file, alias, legacy, oldAlias)
        assertEquals(StoredTokens("test-access", "test-refresh"), open().readTokens())
        assertFalse(File(context.applicationInfo.dataDir, "shared_prefs/$legacy.xml").exists())
        val disk = File(context.noBackupFilesDir, file)
        assertTrue(disk.exists())
        assertFalse(disk.readBytes().toString(Charsets.UTF_8).contains("test-access"))
        assertFalse(disk.readBytes().toString(Charsets.UTF_8).contains("test-refresh"))
        open().writeTokens(StoredTokens("rotated-access", "rotated-refresh"))
        assertEquals(StoredTokens("rotated-access", "rotated-refresh"), open().readTokens())
        open().clear()
        assertEquals(StoredTokens(), open().readTokens())
    }

    @Test fun incompleteLegacyKeysetsAreRetainedWithoutGeneratingReplacements() = fixture { file, alias, legacy, oldAlias ->
        seed(legacy, oldAlias)
        val raw = context.getSharedPreferences(legacy, android.content.Context.MODE_PRIVATE)
        val missing = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        assertTrue(raw.edit().remove(missing).commit())
        val before = File(context.applicationInfo.dataDir, "shared_prefs/$legacy.xml").readBytes()
        assertThrows(TokenStorageException::class.java) {
            SecureTokenStore.create(context, file, alias, legacy, oldAlias).readTokens()
        }
        assertFalse(raw.contains(missing))
        assertArrayEquals(before, File(context.applicationInfo.dataDir, "shared_prefs/$legacy.xml").readBytes())
        assertFalse(File(context.noBackupFilesDir, file).exists())
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
    }

    @Test fun missingKeyAndTamperingFailClosedWithoutLegacyFallback() = fixture { file, alias, legacy, oldAlias ->
        seed(legacy, oldAlias)
        fun open() = SecureTokenStore.create(context, file, alias, legacy, oldAlias)
        open().readTokens()
        seed(legacy, oldAlias) // Old data left by an interrupted cleanup must not win.
        val disk = File(context.noBackupFilesDir, file)
        val original = disk.readBytes()
        disk.writeBytes(original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
        assertThrows(TokenStorageException::class.java) { open().readTokens() }
        assertTrue(File(context.applicationInfo.dataDir, "shared_prefs/$legacy.xml").exists())
        disk.writeBytes(original)
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        assertThrows(TokenStorageException::class.java) { open().readTokens() }
        assertFalse("A read must never replace a missing key", keys.containsAlias(alias))
        // Explicit logout/recovery can create a fresh key and an authoritative empty record.
        open().clear()
        assertEquals(StoredTokens(), open().readTokens())
        assertFalse(File(context.applicationInfo.dataDir, "shared_prefs/$legacy.xml").exists())
    }
}
