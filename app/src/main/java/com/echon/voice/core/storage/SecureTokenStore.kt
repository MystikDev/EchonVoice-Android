package com.echon.voice.core.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted persistence for the session tokens — the Android analog of the iOS
 * `KeychainStore`. Backed by [EncryptedSharedPreferences] (AES-256), keyed by a
 * hardware-backed master key. Access tokens live 15 minutes; the refresh token
 * is what actually keeps a user signed in across launches.
 */
@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_ACCESS) else putString(KEY_ACCESS, value) }.apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_REFRESH) else putString(KEY_REFRESH, value) }.apply()

    fun clear() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).apply()
    }

    private companion object {
        const val FILE_NAME = "echon-session"
        const val KEY_ACCESS = "session-token"
        const val KEY_REFRESH = "refresh-token"
    }
}
