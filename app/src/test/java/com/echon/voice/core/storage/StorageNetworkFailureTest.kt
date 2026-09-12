package com.echon.voice.core.storage

import com.echon.voice.core.network.RefreshCookieInterceptor
import com.echon.voice.core.network.SessionStore
import com.echon.voice.core.network.TokenRefresher
import java.io.IOException
import okhttp3.*
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class StorageNetworkFailureTest {
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
