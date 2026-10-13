package com.echon.voice.core.util

/** Avatar/profile images accept an upload URL verbatim, or an empty string to clear. */
internal fun isEchonUploadUrl(value: String): Boolean {
    if (value.isEmpty()) return true
    if (value.length > 512 || value.any { it.isISOControl() || it == '\\' }) return false
    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
    if (uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) return false
    val path = uri.path ?: return false
    if (!path.startsWith("/uploads/") || path.split('/').any { it == ".." || it == "." }) return false
    return if (uri.isAbsolute) uri.scheme == "https" && uri.host == "echon-voice.com" && (uri.port == -1 || uri.port == 443)
    else uri.rawAuthority == null
}
