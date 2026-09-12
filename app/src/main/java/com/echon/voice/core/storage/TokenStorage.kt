package com.echon.voice.core.storage

import java.io.IOException

data class StoredTokens(val access: String? = null, val refresh: String? = null) {
    override fun toString() = "StoredTokens(redacted)"
}

/** No credentials in error messages; persisted data is retained on failure. */
class TokenStorageException(cause: Exception) : IOException("Saved sign-in is unavailable.", cause)

interface TokenStorage {
    var accessToken: String?
    var refreshToken: String?
    fun readTokens() = StoredTokens(accessToken, refreshToken)
    fun writeTokens(tokens: StoredTokens) {
        accessToken = tokens.access
        refreshToken = tokens.refresh
    }
    fun clear()
    /** Explicit user recovery; may replace an unusable key. */
    fun reset() = clear()
}

internal interface TokenRecord {
    fun exists(): Boolean
    fun read(): ByteArray
    fun write(bytes: ByteArray)
}
internal interface LegacyTokens {
    fun read(): StoredTokens
    fun delete()
}
internal interface TokenCipher {
    fun encrypt(tokens: StoredTokens): ByteArray
    fun decrypt(record: ByteArray): StoredTokens
}

/** The shared lock covers migration and read/modify/write across store instances. */
internal class MigratingTokenStorage(
    private val record: TokenRecord,
    private val cipher: TokenCipher,
    private val legacy: LegacyTokens,
    private val lock: Any,
) : TokenStorage {
    override var accessToken: String?
        get() = readTokens().access
        set(value) = synchronized(lock) { writeTokens(readTokens().copy(access = value)) }
    override var refreshToken: String?
        get() = readTokens().refresh
        set(value) = synchronized(lock) { writeTokens(readTokens().copy(refresh = value)) }

    override fun readTokens(): StoredTokens = guarded {
        if (record.exists()) {
            // Never fall back to old credentials when the authoritative record fails.
            cipher.decrypt(record.read()).also { removeLegacyAfterVerification() }
        } else {
            legacy.read().also { persistAndVerify(it) }
        }
    }
    override fun writeTokens(tokens: StoredTokens) = guarded { persistAndVerify(tokens) }
    // Keep an encrypted empty record, so failed legacy cleanup cannot undo logout.
    override fun clear() = writeTokens(StoredTokens())

    private fun persistAndVerify(tokens: StoredTokens) {
        record.write(cipher.encrypt(tokens))
        check(cipher.decrypt(record.read()) == tokens) { "Saved sign-in verification failed" }
        removeLegacyAfterVerification()
    }
    private fun removeLegacyAfterVerification() {
        try { legacy.delete() } catch (_: IOException) { /* Retry on the next read/write. */ }
    }
    private fun <T> guarded(block: () -> T): T = synchronized(lock) {
        try { block() } catch (e: TokenStorageException) { throw e }
        catch (e: Exception) { throw TokenStorageException(e) }
    }
}
