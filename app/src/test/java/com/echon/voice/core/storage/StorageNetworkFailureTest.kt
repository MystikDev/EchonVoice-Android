package com.echon.voice.core.storage

import com.echon.voice.core.network.RefreshCookieInterceptor
import com.echon.voice.core.network.SessionStore
import com.echon.voice.core.network.TokenRefresher
import com.echon.voice.core.network.TokenAuthenticator
import com.echon.voice.core.network.SessionGeneration
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import okhttp3.*
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class StorageNetworkFailureTest {
    private fun storage() = object : TokenStorage {
        override var accessToken: String? = "old-access"
        override var refreshToken: String? = "old-refresh"
        override fun clear() { accessToken = null; refreshToken = null }
    }

    private fun unauthorized(session: SessionStore) = Response.Builder()
        .request(Request.Builder().url("https://echon-voice.com/v1/me")
            .header("Authorization", "Bearer old-access")
            .tag(SessionGeneration::class.java, SessionGeneration(session.generation)).build())
        .protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized").body("".toResponseBody()).build()

    @Test fun networkServerAndMalformedResponseFailuresPreserveStoredSession() {
        for (failure in listOf("network", "503", "429", "malformed")) {
            val persisted = storage()
            val session = SessionStore(persisted)
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                if (failure == "network") throw java.net.SocketTimeoutException("test timeout")
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(failure.toIntOrNull() ?: 200).message("Test")
                    .body("invalid-json".toResponseBody()).build()
            }.build()
            assertThrows(IOException::class.java) {
                TokenAuthenticator(session, TokenRefresher(client)).authenticate(null, unauthorized(session))
            }
            assertTrue(failure, session.hasSession)
            assertFalse(session.storageUnavailable.value)
            assertEquals("old-access", persisted.accessToken)
            assertEquals("old-refresh", persisted.refreshToken)
        }
    }

    @Test fun definitiveServerRejectionStillClearsCredentials() {
        for (code in listOf(401, 403)) {
            val persisted = storage()
            val session = SessionStore(persisted)
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("Rejected").body("".toResponseBody()).build()
            }.build()
            assertNull(TokenAuthenticator(session, TokenRefresher(client)).authenticate(null, unauthorized(session)))
            assertFalse(session.hasSession)
            assertEquals(StoredTokens(), persisted.readTokens())
        }
    }

    @Test fun failedCookiePersistenceClosesResponseAndSurfacesStorageFailure() {
        val failure = TokenStorageException(IOException("disk full"))
        val storage = object : TokenStorage {
            override var accessToken: String? = "old-access"
            override var refreshToken: String? = "old-refresh"
            override fun writeTokens(tokens: StoredTokens) { throw failure }
            override fun clear() { error("Must not erase the saved session") }
        }
        val session = SessionStore(storage)
        var closed = false
        val source = object : ForwardingSource(Buffer().writeUtf8("{}")) {
            override fun close() { closed = true; super.close() }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = 2L
            override fun source(): BufferedSource = source
        }
        val client = OkHttpClient.Builder().addInterceptor(RefreshCookieInterceptor(session))
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").header("Set-Cookie", "refresh_token=rotated; HttpOnly")
                    .body(body).build()
            }.build()
        assertSame(failure, assertThrows(TokenStorageException::class.java) {
            TokenRefresher(client).refresh("old-refresh", session.generation)
        })
        assertTrue(closed)
        assertTrue(session.storageUnavailable.value)
        assertFalse(session.hasSession)
        assertEquals("old-access", storage.accessToken)
        assertEquals("old-refresh", storage.refreshToken)
    }
}
