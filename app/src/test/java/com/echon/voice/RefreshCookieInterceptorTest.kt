package com.echon.voice

import com.echon.voice.core.network.RefreshCookieInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The refresh token is delivered ONLY as this Set-Cookie; parsing it correctly
 * is what keeps users signed in across restarts (session-persistence bug fix).
 */
class RefreshCookieInterceptorTest {

    @Test
    fun `extracts the token from a real login Set-Cookie`() {
        val header = "refresh_token=eyJ0eXAiOiJKV1Q.abc.def; HttpOnly; Secure; " +
            "SameSite=Lax; Path=/v1/auth; Max-Age=5184000"
        assertEquals("eyJ0eXAiOiJKV1Q.abc.def", RefreshCookieInterceptor.parseRefreshToken(header))
    }

    @Test
    fun `ignores other cookies`() {
        assertNull(RefreshCookieInterceptor.parseRefreshToken("session=xyz; Path=/; HttpOnly"))
        assertNull(RefreshCookieInterceptor.parseRefreshToken("csrf_refresh_token=nope; Path=/")) // not a prefix match
    }

    @Test
    fun `treats a cleared cookie as no token`() {
        assertNull(RefreshCookieInterceptor.parseRefreshToken("refresh_token=; Max-Age=0; Path=/v1/auth"))
    }
}
