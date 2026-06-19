package com.echon.voice.model

import com.echon.voice.core.network.ApiConfig
import kotlinx.serialization.Serializable

@Serializable
data class Server(
    val id: String,
    val name: String? = null,
    val icon: String? = null,
    val banner: String? = null,
    val description: String? = null,
    val ownerId: String? = null,
    val isPublic: Boolean? = null,
    val createdAt: String? = null,
) {
    val iconUrl: String?
        get() {
            val i = icon?.takeIf { it.isNotEmpty() } ?: return null
            return if (i.startsWith("http")) i else ApiConfig.BASE_URL.trimEnd('/') + "/" + i.trimStart('/')
        }

    val initials: String
        get() = (name ?: "?").split(" ").take(2)
            .joinToString("") { it.take(1).uppercase() }
}

@Serializable
data class ServersResponse(val servers: List<Server>)
