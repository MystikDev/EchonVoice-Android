package com.echon.voice.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/** AES-256-GCM tokens in credential-protected, non-backed-up storage. */
@Singleton
class SecureTokenStore @Inject constructor(@ApplicationContext context: Context) : TokenStorage {
    private val delegate = create(context)
    override var accessToken: String?
        get() = delegate.accessToken
        set(value) { delegate.accessToken = value }
    override var refreshToken: String?
        get() = delegate.refreshToken
        set(value) { delegate.refreshToken = value }
    override fun readTokens() = delegate.readTokens()
    override fun writeTokens(tokens: StoredTokens) = delegate.writeTokens(tokens)
    override fun clear() = delegate.clear()
    override fun reset() = synchronized(storageLock) {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(KEY_ALIAS) }
            delegate.clear()
        } catch (e: Exception) { throw TokenStorageException(e) }
    }

    companion object {
        internal const val FILE_NAME = "echon-session-v2.enc"
        internal const val KEY_ALIAS = "echon-session-v2-aes256"
        internal const val LEGACY_FILE = "echon-session"
        private val storageLock = Any()

        @Suppress("DEPRECATION") // Read-only bridge for upgrades from <= 2.0.26.
        internal fun create(
            context: Context,
            fileName: String = FILE_NAME,
            keyAlias: String = KEY_ALIAS,
            legacyFile: String = LEGACY_FILE,
            legacyKeyAlias: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS,
        ): TokenStorage {
            val file = File(context.noBackupFilesDir, fileName)
            val atomic = AtomicFile(file)
            val record = object : TokenRecord {
                override fun exists() = file.exists() || File(file.path + ".bak").exists()
                override fun read(): ByteArray = atomic.openRead().use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= AesGcmTokenCipher.MAX_RECORD_BYTES) { "Token record too large" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                override fun write(bytes: ByteArray) {
                    val stream = atomic.startWrite()
                    try {
                        stream.write(bytes)
                        stream.fd.sync()
                        atomic.finishWrite(stream)
                    } catch (e: Exception) {
                        atomic.failWrite(stream)
                        throw e
                    }
                }
            }
            val cipher = AesGcmTokenCipher(
                key = { create ->
                    val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    (keys.getKey(keyAlias, null) as? SecretKey) ?: run {
                        check(create) { "Saved sign-in key is unavailable" }
                        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setRandomizedEncryptionRequired(true).build())
                        }.generateKey()
                    }
                },
                associatedData = "${context.packageName}:$fileName".toByteArray(Charsets.UTF_8),
            )
            val legacy = object : LegacyTokens {
                private val oldFile = File(context.applicationInfo.dataDir, "shared_prefs/$legacyFile.xml")
                override fun read(): StoredTokens {
                    if (!oldFile.exists() && !File(oldFile.path + ".bak").exists()) return StoredTokens()
                    val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    check(keys.containsAlias(legacyKeyAlias)) { "Legacy sign-in key is unavailable" }
                    // The deprecated factory can generate keysets when absent. Migration
                    // must not mutate an incomplete legacy record before recovery succeeds.
                    val raw = context.getSharedPreferences(legacyFile, Context.MODE_PRIVATE)
                    check(raw.contains("__androidx_security_crypto_encrypted_prefs_key_keyset__") &&
                        raw.contains("__androidx_security_crypto_encrypted_prefs_value_keyset__")) {
                        "Legacy sign-in keysets are unavailable"
                    }
                    val prefs = EncryptedSharedPreferences.create(context, legacyFile,
                        MasterKey.Builder(context, legacyKeyAlias).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
                    return StoredTokens(prefs.getString("session-token", null), prefs.getString("refresh-token", null))
                }
                override fun delete() {
                    if (!context.deleteSharedPreferences(legacyFile)) throw IOException("Legacy cleanup failed")
                }
            }
            return MigratingTokenStorage(record, cipher, legacy, storageLock)
        }
    }
}
