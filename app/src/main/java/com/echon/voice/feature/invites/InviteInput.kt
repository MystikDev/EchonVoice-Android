package com.echon.voice.feature.invites

import java.net.URI

/** Accept a code or a link to our invite page; never send an arbitrary URL to the API. */
internal fun inviteCode(input: String): String? {
    val text = input.trim()
    val code = if (text.contains("://")) {
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host?.lowercase() !in setOf("echon-voice.com", "www.echon-voice.com") ||
            uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        val path = uri.path.trim('/').split('/')
        if (path.size != 2 || path[0] !in setOf("invite", "invites")) return null
        path[1]
    } else text
    return code.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }
}
