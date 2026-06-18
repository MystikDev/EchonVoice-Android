package com.echon.voice

import com.echon.voice.core.network.ApiConfig
import com.echon.voice.core.network.EchonJson
import com.echon.voice.core.network.TlsPinning
import com.echon.voice.model.LoginRequest
import com.echon.voice.model.LoginResponse
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Phase 0 verification — runs against the live echon-voice.com host over the
 * network (no emulator needed). Proves the cert-pinning spine:
 *   1. the real chain validates against the pinned ISRG roots, and
 *   2. a wrong pin fails closed.
 * The optional login check exercises the full request path when test-account
 * credentials are supplied via env (never committed).
 */
class TlsPinningTest {

    private fun pinnedClient(pinner: CertificatePinner) =
        OkHttpClient.Builder().certificatePinner(pinner).build()

    @Test
    fun pinnedClient_connectsToHost() {
        val client = pinnedClient(TlsPinning.certificatePinner())
        val request = Request.Builder().url(ApiConfig.BASE_URL + "v1/me").build()
        client.newCall(request).execute().use { response ->
            // Any HTTP response (401 expected, unauthenticated) proves the TLS
            // handshake passed pinning — only an SSL failure would throw above.
            assertTrue(
                "Expected a real HTTP response, got ${response.code}",
                response.code in 200..499,
            )
        }
    }

    @Test
    fun wrongPin_failsClosed() {
        val badPinner = CertificatePinner.Builder()
            .add(TlsPinning.HOST, "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .build()
        val client = pinnedClient(badPinner)
        val request = Request.Builder().url(ApiConfig.BASE_URL + "v1/me").build()
        try {
            client.newCall(request).execute().use {
                throw AssertionError("Expected pinning to reject the connection, but it succeeded.")
            }
        } catch (expected: SSLPeerUnverifiedException) {
            // Correct: a non-matching pin must fail the handshake.
        }
    }

    @Test
    fun login_returnsToken() {
        val email = System.getenv("ECHON_TEST_EMAIL")
        val password = System.getenv("ECHON_TEST_PASSWORD")
        assumeTrue("Set ECHON_TEST_EMAIL/ECHON_TEST_PASSWORD to run the live login check", email != null && password != null)

        val client = pinnedClient(TlsPinning.certificatePinner())
        val payload = EchonJson.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(email = email!!, password = password!!),
        )
        val request = Request.Builder()
            .url(ApiConfig.BASE_URL + "v1/auth/login")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals("login should return 200", 200, response.code)
            val body = response.body!!.string()
            val login = EchonJson.decodeFromString(LoginResponse.serializer(), body)
            assertNotNull("login response must carry an access token", login.token)
            assertTrue("access token must be non-empty", login.token.isNotEmpty())
        }
    }
}
