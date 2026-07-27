package com.echon.voice

import com.echon.voice.core.network.SessionStore
import com.echon.voice.core.storage.TokenStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression test for the "constantly signed out" bug: the backend delivers the
 * refresh token ONLY as an HttpOnly cookie, so the login body's refresh field is
 * always null. Adopting the login session must therefore PRESERVE the refresh
 * token the cookie interceptor captured moments earlier — nulling it silently
 * killed every session at the first access-token expiry.
 */
class SessionPersistenceTest {

    private class FakeStorage : TokenStorage {
        override var accessToken: String? = null
        override var refreshToken: String? = null
        override fun clear() { accessToken = null; refreshToken = null }
    }

    @Test
    fun `login with cookie-only refresh token keeps it through setTokens`() {
        val storage = FakeStorage()
        val session = SessionStore(storage)

        // Wire order at login: the cookie interceptor captures the refresh token
        // from Set-Cookie while the response is in flight...
        session.onRefreshCookie("COOKIE_REFRESH")
        // ...then AuthStore adopts the body, whose refresh field is null.
        session.setTokens(access = "ACCESS", refresh = null)

        assertEquals("ACCESS", session.accessToken)
        assertEquals("refresh token must survive a null-refresh login body", "COOKIE_REFRESH", session.refreshToken)
        assertEquals("and must be persisted", "COOKIE_REFRESH", storage.refreshToken)
        assertEquals("ACCESS", storage.accessToken)
    }

    @Test
    fun `explicit refresh token still overwrites`() {
        val session = SessionStore(FakeStorage())
        session.onRefreshCookie("OLD")
        session.setTokens(access = "ACCESS", refresh = "FROM_BODY")
        assertEquals("FROM_BODY", session.refreshToken)
    }

    @Test
    fun `clear removes both tokens`() {
        val storage = FakeStorage()
        val session = SessionStore(storage)
        session.onRefreshCookie("R")
        session.setTokens(access = "A", refresh = null)
        session.clear()
        assertNull(session.accessToken)
        assertNull(session.refreshToken)
        assertNull(storage.refreshToken)
    }
}
