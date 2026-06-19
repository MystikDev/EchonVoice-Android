package com.echon.voice.model

import kotlinx.serialization.Serializable

/** POST /v1/messages/{id}/report and /v1/users/{id}/report body. */
@Serializable
data class ReportRequest(
    val reason: String,
    val description: String? = null,
)

/** Report reasons, mirroring the web + iOS clients. */
enum class ReportReason(val value: String, val label: String) {
    SPAM("spam", "Spam"),
    HARASSMENT("harassment", "Harassment or bullying"),
    NSFW("nsfw", "Sexual or explicit content"),
    ILLEGAL("illegal", "Violence or illegal activity"),
    OTHER("other", "Something else"),
}

/** POST /v1/me/delete body. Requires the password and the literal "DELETE". */
@Serializable
data class DeleteAccountRequest(
    val currentPassword: String,
    val confirmation: String = "DELETE",
)
