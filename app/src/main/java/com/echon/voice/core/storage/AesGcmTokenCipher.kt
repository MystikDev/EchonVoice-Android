package com.echon.voice.core.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated envelope with a fresh provider-generated IV on every write. */
internal class AesGcmTokenCipher(
    private val key: (create: Boolean) -> SecretKey,
    private val associatedData: ByteArray,
) : TokenCipher {
    override fun encrypt(tokens: StoredTokens): ByteArray {
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                for (token in listOf(tokens.access, tokens.refresh)) {
                    val encoded = token?.toByteArray(Charsets.UTF_8)
                    try {
                        require(encoded == null || encoded.size <= MAX_TOKEN_BYTES) { "Token too large" }
                        out.writeInt(encoded?.size ?: -1)
                        if (encoded != null) out.write(encoded)
                    } finally { encoded?.fill(0) }
                }
            }
            bytes.toByteArray()
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(true))
            cipher.updateAAD(associatedData)
            cipher.updateAAD(byteArrayOf(VERSION))
            check(cipher.iv.size == IV_BYTES)
            return byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(payload)
        } finally { payload.fill(0) }
    }
    override fun decrypt(record: ByteArray): StoredTokens {
        require(record.size in (1 + IV_BYTES + TAG_BYTES + 8)..MAX_RECORD_BYTES) { "Invalid token record size" }
        require(record[0] == VERSION) { "Unsupported token record version" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Do not replace a missing key when reading an existing record.
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, record, 1, IV_BYTES))
        cipher.updateAAD(associatedData)
        cipher.updateAAD(byteArrayOf(VERSION))
        val payload = cipher.doFinal(record, 1 + IV_BYTES, record.size - 1 - IV_BYTES)
        try {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                fun token(): String? {
                    val size = input.readInt()
                    if (size == -1) return null
                    require(size in 0..MAX_TOKEN_BYTES && size <= input.available()) { "Invalid token length" }
                    val bytes = ByteArray(size)
                    return try { input.readFully(bytes); bytes.toString(Charsets.UTF_8) }
                    finally { bytes.fill(0) }
                }
                val tokens = StoredTokens(token(), token())
                require(input.available() == 0) { "Unexpected token record data" }
                return tokens
            }
        } finally { payload.fill(0) }
    }
    companion object {
        private const val VERSION: Byte = 1
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val MAX_TOKEN_BYTES = 32 * 1024
        const val MAX_RECORD_BYTES = 1 + IV_BYTES + TAG_BYTES + 8 + 2 * MAX_TOKEN_BYTES
    }
}
