package com.echon.voice.core.network

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs a Retrofit call and normalizes failures into [ApiException], parsing the
 * server's `{error|code}` envelope on non-2xx — the Android analog of the iOS
 * `APIClient.sendRaw` error handling. 401s are surfaced as [ApiException.Unauthorized]
 * (the [TokenAuthenticator] has already attempted recovery by this point).
 */
suspend fun <T> apiCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        val envelope = runCatching {
            e.response()?.errorBody()?.string()?.let {
                EchonJson.decodeFromString(ServerErrorEnvelope.serializer(), it)
            }
        }.getOrNull()
        if (e.code() == 401) {
            // A login 401 means bad credentials, not an expired session — showing
            // "session expired" on the sign-in screen is badly misleading.
            if (envelope?.code == "invalid_credentials") {
                throw ApiException.Http(401, "Incorrect email or password.", envelope.code)
            }
            throw ApiException.Unauthorized()
        }
        throw ApiException.Http(e.code(), envelope?.text, envelope?.code)
    } catch (e: SerializationException) {
        throw ApiException.Decoding(e)
    } catch (e: IOException) {
        throw ApiException.Transport(e)
    }
}
