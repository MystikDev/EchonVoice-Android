package com.echon.voice.feature.chat

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A URL found in message text, including its exact range in the source string. */
internal data class MessageLink(
    val url: String,
    val start: Int,
    val endExclusive: Int,
)

private val urlPattern = Regex("""(?i)\bhttps?://[^\s<>{}"']+""")
private val trailingUrlPunctuation = setOf('.', ',', '!', '?', ';', ':', ')', ']', '}')

/**
 * Extract links without swallowing punctuation that commonly follows a URL in prose.
 * Only http(s) is accepted so rendered messages cannot invoke file/content/custom URIs.
 */
internal fun findMessageLinks(text: String): List<MessageLink> =
    urlPattern.findAll(text).mapNotNull { match ->
        val url = match.value.trimEnd { it in trailingUrlPunctuation }
        url.takeIf { it.isNotEmpty() }?.let {
            MessageLink(it, match.range.first, match.range.first + it.length)
        }
    }.toList()

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/** Returns a YouTube video id for watch, share, Shorts, and embed URLs. */
internal fun youtubeVideoId(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) && !uri.scheme.equals("http", ignoreCase = true)) return null
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
    val candidate = when (host) {
        "youtu.be" -> uri.path.orEmpty().trim('/').substringBefore('/')
        "youtube.com", "m.youtube.com", "music.youtube.com" -> when {
            uri.path == "/watch" -> queryParameter(uri.rawQuery, "v")
            uri.path.orEmpty().startsWith("/shorts/") -> uri.path.substringAfter("/shorts/").substringBefore('/')
            uri.path.orEmpty().startsWith("/embed/") -> uri.path.substringAfter("/embed/").substringBefore('/')
            uri.path.orEmpty().startsWith("/live/") -> uri.path.substringAfter("/live/").substringBefore('/')
            else -> null
        }
        "youtube-nocookie.com" -> uri.path.orEmpty().substringAfter("/embed/", "").substringBefore('/')
        else -> null
    }
    return candidate?.takeIf { youtubeVideoIdPattern.matches(it) }
}

private fun queryParameter(query: String?, name: String): String? =
    query?.split('&')?.firstNotNullOfOrNull { part ->
        val key = part.substringBefore('=')
        if (key != name) null else runCatching {
            URLDecoder.decode(part.substringAfter('=', ""), StandardCharsets.UTF_8.name())
        }.getOrNull()
    }

internal data class ActiveMention(
    val start: Int,
    val endExclusive: Int,
    val query: String,
)

/**
 * Finds a mention token currently being typed at the end of the composer.
 * An at-sign inside an email/word is deliberately ignored.
 */
internal fun activeMention(text: String): ActiveMention? {
    val start = text.lastIndexOf('@')
    if (start < 0) return null
    if (start > 0 && !text[start - 1].isWhitespace() && text[start - 1] !in "([{\n") return null
    val query = text.substring(start + 1)
    if (query.any { it.isWhitespace() || it == '@' }) return null
    return ActiveMention(start, text.length, query)
}

internal fun replaceActiveMention(text: String, mention: ActiveMention, username: String): String =
    buildString(text.length + username.length + 2) {
        append(text, 0, mention.start)
        append('@')
        append(username)
        append(' ')
        append(text, mention.endExclusive, text.length)
    }
