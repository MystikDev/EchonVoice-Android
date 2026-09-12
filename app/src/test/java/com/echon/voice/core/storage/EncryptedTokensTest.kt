package com.echon.voice.core.storage

import com.echon.voice.core.network.SessionStore
import java.io.IOException
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class EncryptedTokensTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun cipher(context: String = "test:session") = AesGcmTokenCipher({ key }, context.toByteArray())
    private val tokens = StoredTokens("ACCESS-秘密", "REFRESH-🔑")

    @Test fun authenticatedRoundTripUsesFreshIvAndHidesTokens() {
        val cipher = cipher()
        val first = cipher.encrypt(tokens)
        val second = cipher.encrypt(tokens)
        assertFalse(first.contentEquals(second))
        assertEquals(tokens, cipher.decrypt(first))
        assertEquals(StoredTokens(), cipher.decrypt(cipher.encrypt(StoredTokens())))
        assertEquals(StoredTokens("", null), cipher.decrypt(cipher.encrypt(StoredTokens("", null))))
        assertFalse(first.toString(Charsets.UTF_8).contains("ACCESS"))
        assertFalse(tokens.toString().contains("REFRESH"))
    }

    @Test fun rejectsTamperingTruncationWrongKeyAndWrongContext() {
        val record = cipher().encrypt(tokens)
        for (offset in listOf(0, 1, 13, record.lastIndex)) {
            val changed = record.copyOf().apply { this[offset] = (this[offset].toInt() xor 1).toByte() }
            assertThrows(Exception::class.java) { cipher().decrypt(changed) }
        }
        assertThrows(Exception::class.java) { cipher().decrypt(record.copyOf(20)) }
        assertThrows(Exception::class.java) { cipher("other-package:session").decrypt(record) }
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(Exception::class.java) { AesGcmTokenCipher({ otherKey }, "test:session".toByteArray()).decrypt(record) }
        assertThrows(Exception::class.java) { cipher().encrypt(StoredTokens("x".repeat(32769))) }
    }

    private class Record : TokenRecord {
        var bytes: ByteArray? = null
        var writeFails = false
        var readFails = false
        override fun exists() = bytes != null
        override fun read(): ByteArray { if (readFails) throw IOException("read"); return bytes!!.copyOf() }
        override fun write(bytes: ByteArray) { if (writeFails) throw IOException("disk full"); this.bytes = bytes.copyOf() }
    }
    private class Legacy(var tokens: StoredTokens) : LegacyTokens {
        var reads = 0
        var deletes = 0
        var deleteFails = false
        override fun read(): StoredTokens { reads++; return tokens }
        override fun delete() { if (deleteFails) throw IOException("cleanup"); deletes++; tokens = StoredTokens() }
    }
    private fun store(record: Record, legacy: Legacy) = MigratingTokenStorage(record, cipher(), legacy, Any())

    @Test fun migrationSurvivesNewInstanceAndRemovesLegacyOnlyAfterVerification() {
        val record = Record()
        val legacy = Legacy(tokens)
        assertEquals(tokens, store(record, legacy).readTokens())
        assertEquals(1, legacy.reads)
        assertEquals(1, legacy.deletes)
        assertEquals(tokens, store(record, legacy).readTokens())
        assertEquals(1, legacy.reads)
    }

    @Test fun failedMigrationKeepsLegacyAndCanRetry() {
        val record = Record().apply { writeFails = true }
        val legacy = Legacy(tokens)
        assertThrows(TokenStorageException::class.java) { store(record, legacy).readTokens() }
        assertEquals(tokens, legacy.tokens)
        assertEquals(0, legacy.deletes)
        record.writeFails = false
        assertEquals(tokens, store(record, legacy).readTokens())
    }

    @Test fun failedReadBackKeepsLegacyButNeverFallsBackOverNewRecord() {
        val record = Record().apply { readFails = true }
        val legacy = Legacy(tokens)
        assertThrows(TokenStorageException::class.java) { store(record, legacy).readTokens() }
        assertEquals(0, legacy.deletes)
        assertEquals(1, legacy.reads)
        assertThrows(TokenStorageException::class.java) { store(record, legacy).readTokens() }
        assertEquals(1, legacy.reads)
        record.readFails = false
        assertEquals(tokens, store(record, legacy).readTokens())
    }

    @Test fun corruptNewRecordCannotResurrectLegacySession() {
        val record = Record().apply { bytes = byteArrayOf(1, 2, 3) }
        val legacy = Legacy(tokens)
        assertThrows(TokenStorageException::class.java) { store(record, legacy).readTokens() }
        assertEquals(0, legacy.reads)
        assertEquals(0, legacy.deletes)
    }

    @Test fun logoutTombstoneWinsWhenLegacyDeletionFails() {
        val record = Record()
        val legacy = Legacy(tokens).apply { deleteFails = true }
        val store = store(record, legacy)
        assertEquals(tokens, store.readTokens())
        store.clear()
        assertEquals(tokens, legacy.tokens)
        assertEquals(StoredTokens(), store(record, legacy).readTokens())
    }

    @Test fun failedAtomicRotationRetainsPriorDiskPairAndStopsCredentialUseUntilRetry() {
        val record = Record()
        val legacy = Legacy(tokens)
        val storage = store(record, legacy)
        val session = SessionStore(storage)
        val generation = session.generation
        record.writeFails = true
        assertThrows(TokenStorageException::class.java) {
            session.updateAfterRefresh("NEW-A", "NEW-R", generation)
        }
        assertTrue(session.storageUnavailable.value)
        assertFalse(session.hasSession)
        assertTrue(session.generation > generation)
        record.writeFails = false
        assertEquals(tokens, storage.readTokens())
        assertTrue(session.restoreTokens())
        assertEquals(tokens.access, session.accessToken)
        assertEquals(tokens.refresh, session.refreshToken)
        assertFalse(session.updateAfterRefresh("STALE", null, generation))
    }

    @Test fun unavailableStartupDoesNotThrowOrEraseAndExplicitRetryRestores() {
        val record = Record().apply { bytes = cipher().encrypt(tokens); readFails = true }
        val legacy = Legacy(StoredTokens())
        val session = SessionStore(store(record, legacy))
        assertTrue(session.storageUnavailable.value)
        assertFalse(session.hasSession)
        assertEquals(0, legacy.deletes)
        record.readFails = false
        assertTrue(session.restoreTokens())
        assertEquals(tokens.access, session.accessToken)
    }

    @Test fun failedLogoutIsNotReportedAsDurableSignOut() {
        val record = Record()
        val legacy = Legacy(tokens)
        val session = SessionStore(store(record, legacy))
        record.writeFails = true
        assertThrows(TokenStorageException::class.java) { session.clear() }
        assertTrue(session.storageUnavailable.value)
        assertFalse(session.hasSession)
        record.writeFails = false
        assertTrue(session.resetStorage())
        assertEquals(StoredTokens(), store(record, legacy).readTokens())
    }
}
