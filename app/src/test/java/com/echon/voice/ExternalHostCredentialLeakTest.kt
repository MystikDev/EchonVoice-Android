package com.echon.voice

import com.echon.voice.core.network.AuthInterceptor
import com.echon.voice.core.network.RefreshCookieInterceptor
import com.echon.voice.core.network.SessionStore
import com.echon.voice.core.network.TlsPinning
import com.echon.voice.core.network.TokenAuthenticator
import com.echon.voice.core.network.TokenRefresher
import com.echon.voice.core.storage.TokenStorage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression test for the security review's finding #1: the app must never send
 * the Echon bearer to — or accept an auth cookie from — a non-Echon host. The
 * MockWebServer runs on 127.0.0.1, which the host guard treats as "external", so
 * exercising the real AuthInterceptor + TokenAuthenticator + RefreshCookieInterceptor
 * against it proves credentials don't leak to a third-party image host.
 */
class ExternalHostCredentialLeakTest {

    private class FakeStorage : TokenStorage {
        override var accessToken: String? = "ACCESS_TOKEN"
        override var refreshToken: String? = "REFRESH_TOKEN"
        override fun clear() { accessToken = null; refreshToken = null }
    }

    private lateinit var server: MockWebServer
    private lateinit var session: SessionStore
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        session = SessionStore(FakeStorage())
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .authenticator(TokenAuthenticator(session, TokenRefresher(OkHttpClient())))
            .addInterceptor(RefreshCookieInterceptor(session))
            .build()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `external 401 never receives Authorization and never refreshes`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200)) // would be the credentialed retry — must NOT happen

        client.newCall(Request.Builder().url(server.url("/image.png")).build()).execute().close()

        val recorded = server.takeRequest()
        assertNull("bearer must never leak to a third-party host", recorded.getHeader("Authorization"))
        assertEquals("no credentialed retry to an external 401", 1, server.requestCount)
        assertEquals("session access token unchanged", "ACCESS_TOKEN", session.accessToken)
        assertEquals("session refresh token unchanged", "REFRESH_TOKEN", session.refreshToken)
    }

    @Test
    fun `refresh cookie from an external host is ignored`() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Set-Cookie", "refresh_token=FORGED_BY_ATTACKER; Path=/v1/auth"),
        )

        client.newCall(Request.Builder().url(server.url("/v1/auth/refresh")).build()).execute().close()

        assertEquals(
            "a refresh cookie from a non-Echon host must not replace the session token",
            "REFRESH_TOKEN", session.refreshToken,
        )
    }

    @Test
    fun `host guard matches only the Echon API origin`() {
        assertTrue(TlsPinning.isApiHost("echon-voice.com"))
        assertTrue(TlsPinning.isApiHost("api.echon-voice.com"))
        assertFalse(TlsPinning.isApiHost("attacker.example"))
        assertFalse(TlsPinning.isApiHost("echon-voice.com.attacker.example"))
        assertFalse(TlsPinning.isApiHost("notechon-voice.com"))
    }
}
