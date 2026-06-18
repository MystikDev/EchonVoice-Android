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
        if (e.code() == 401) throw ApiException.Unauthorized()
        val envelope = runCatching {
            e.response()?.errorBody()?.string()?.let {
                EchonJson.decodeFromString(ServerErrorEnvelope.serializer(), it)
            }
        }.getOrNull()
        throw ApiException.Http(e.code(), envelope?.text, envelope?.code)
    } catch (e: SerializationException) {
        throw ApiException.Decoding(e)
    } catch (e: IOException) {
        throw ApiException.Transport(e)
    }
}
