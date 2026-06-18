package com.echon.voice.core.network

import kotlinx.serialization.Serializable

/**
 * Typed API failure, mirroring the iOS `APIError`. `message` carries the
 * server's `{error|code}` envelope text when present.
 */
sealed class ApiException(message: String?) : Exception(message) {
    /** Non-2xx response. [code] is the server's machine-readable error code, if any. */
    class Http(val status: Int, message: String?, val code: String? = null) :
        ApiException(message ?: "Request failed ($status).")

    /** Response body could not be decoded into the expected type. */
    class Decoding(cause: Throwable) :
        ApiException("Unexpected response from the server.") {
        init {
            initCause(cause)
        }
    }

    /** Connectivity / TLS / pinning failure. */
    class Transport(cause: Throwable) : ApiException(cause.message) {
        init {
            initCause(cause)
        }
    }

    /** 401 and refresh failed — the session is irrecoverable. */
    class Unauthorized :
        ApiException("Your session has expired. Please sign in again.")
}

/** Server error envelope, e.g. {"error": "Invalid credentials", "code": "invalid_credentials"}. */
@Serializable
data class ServerErrorEnvelope(
    val error: String? = null,
    val message: String? = null,
    val code: String? = null,
) {
    val text: String? get() = error ?: message
}
