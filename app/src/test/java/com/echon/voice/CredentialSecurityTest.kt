package com.echon.voice

import com.echon.voice.core.network.*
import com.echon.voice.core.storage.TokenStorage
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class CredentialSecurityTest {
    private class Storage : TokenStorage {
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
        override fun clear() { accessToken = null; refreshToken = null }
    }

    @Test fun onlyExactHttpsOriginAcceptsCredentials() {
        assertTrue(TlsPinning.isApiOrigin("https://echon-voice.com:443/v1/me".toHttpUrl()))
        for (url in listOf("http://echon-voice.com", "https://echon-voice.com:8443",
            "https://api.echon-voice.com", "https://nested.api.echon-voice.com",
            "https://echon-voice.com.evil.test", "https://echon-voice.com@evil.test",
            "https://name@echon-voice.com")) {
            assertFalse(url, TlsPinning.isApiOrigin(url.toHttpUrl()))
        }
    }

    @Test fun credentialsAreStrippedOnRedirectNetworkHops() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/destination")))
            server.enqueue(MockResponse())
            val client = OkHttpClient.Builder().addNetworkInterceptor(CredentialBoundaryInterceptor()).build()
            client.newCall(Request.Builder().url(server.url("/start"))
                .header("Authorization", "Bearer secret").header("X-Refresh-Token", "refresh")
                .header("Cookie", "refresh_token=secret").build()).execute().close()
            repeat(2) {
                val request = server.takeRequest()
                for (header in listOf("Authorization", "X-Refresh-Token", "Cookie")) assertNull(request.getHeader(header))
            }
        }
    }

    @Test fun redirectedResponseCannotForgeRefreshCookie() {
        val session = SessionStore(Storage())
        val client = OkHttpClient.Builder().addInterceptor(RefreshCookieInterceptor(session))
            .addInterceptor { chain ->
                Response.Builder().request(chain.request().newBuilder().url("https://evil.test/v1/auth/refresh").build())
                    .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .header("Set-Cookie", "refresh_token=forged; Secure; HttpOnly")
                    .body("".toResponseBody()).build()
            }.build()
        client.newCall(Request.Builder().url("https://echon-voice.com/v1/auth/refresh").build()).execute().close()
        assertEquals("refresh", session.refreshToken)
    }

    @Test fun authPrefixLookalikeCannotSetCookie() {
        val session = SessionStore(Storage())
        val client = OkHttpClient.Builder().addInterceptor(RefreshCookieInterceptor(session))
            .addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").header("Set-Cookie", "refresh_token=forged")
                .body("".toResponseBody()).build() }.build()
        client.newCall(Request.Builder().url("https://echon-voice.com/v1/auth-attacker").build()).execute().close()
        assertEquals("refresh", session.refreshToken)
    }

    @Test fun refreshAfterLogoutCannotResurrectSession() {
        val session = SessionStore(Storage())
        val generation = session.generation
        session.clear()
        session.onRefreshCookie("late-cookie", generation)
        assertFalse(session.updateAfterRefresh("late-access", "late-refresh", generation))
        assertFalse(session.hasSession)
    }

    @Test fun oldRefreshCannotOverwriteOrClearNewAccount() {
        val session = SessionStore(Storage())
        val generation = session.generation
        session.clear()
        session.setTokens("new-access", "new-refresh")
        session.onRefreshCookie("old-cookie", generation)
        assertFalse(session.updateAfterRefresh("old-access", "old-refresh", generation))
        assertFalse(session.clearIfCurrent(generation))
        assertEquals("new-access", session.accessToken)
        assertEquals("new-refresh", session.refreshToken)
    }
}
