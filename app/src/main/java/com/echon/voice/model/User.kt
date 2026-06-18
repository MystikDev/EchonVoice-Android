package com.echon.voice.model

import com.echon.voice.core.network.ApiConfig
import kotlinx.serialization.Serializable

/**
 * A user. As on iOS, nearly every field is optional so partial payloads (login
 * vs /me vs embedded message author) all decode cleanly.
 */
@Serializable
data class User(
    val id: String,
    val username: String? = null,
    val discriminator: String? = null,
    val email: String? = null,
    val avatar: String? = null,
    val about: String? = null,
    val allowDmsFrom: String? = null,
    val tosAccepted: Boolean? = null,
    val createdAt: String? = null,
) {
    val displayHandle: String
        get() {
            val name = username ?: return "Unknown"
            return if (discriminator != null) "$name#$discriminator" else name
        }

    /** Absolute avatar URL, resolving server-relative paths against the API host. */
    val avatarUrl: String?
        get() {
            val a = avatar?.takeIf { it.isNotEmpty() } ?: return null
            return if (a.startsWith("http")) a else ApiConfig.BASE_URL.trimEnd('/') + "/" + a.trimStart('/')
        }
}
