package com.echon.voice.feature.auth

import com.echon.voice.core.network.*
import com.echon.voice.core.storage.TokenStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class LoginSessionTest {
    private class Storage : TokenStorage {
        override var accessToken: String? = null
        override var refreshToken: String? = null
        override fun clear() { accessToken = null; refreshToken = null }
    }

    private data class Reply(val body: String, val status: Int = 200, val cookie: String? = null)

    private class Fixture(vararg replies: Reply) {
        val storage = Storage()
        val session = SessionStore(storage)
        val requests = mutableListOf<Request>()
        private val responses = ArrayDeque(replies.toList())
        // Terminal interceptor: exercise production serialization and auth/cookie
        // boundaries without making any network requests to the production URL.
        private val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .addInterceptor(RefreshCookieInterceptor(session))
            .addInterceptor { chain ->
                requests += chain.request()
                val next = responses.removeFirst()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(next.status).message("Test response")
                    .apply { next.cookie?.let { header("Set-Cookie", "refresh_token=$it; HttpOnly; Secure") } }
                    .body(next.body.toResponseBody("application/json".toMediaType())).build()
            }.build()
        val api = Retrofit.Builder().baseUrl("https://echon-voice.com/").client(client)
            .addConverterFactory(EchonJson.asConverterFactory("application/json".toMediaType()))
            .build().create(EchonApi::class.java)
        val login = LoginSession(api, session)
    }

    @Test fun emailChallengeIssuesNoSessionThenVerificationPersistsCookieAcrossRestart() = runBlocking {
        val fixture = Fixture(
            Reply("""{"two_factor_required":true,"method":"email","challenge_id":"challenge-1"}"""),
            Reply("""{"token":"verified-access"}""", cookie = "verified-refresh"),
        )
        assertEquals("challenge-1", fixture.login.logIn("member@example.test", "password")?.id)
        assertFalse(fixture.session.hasSession)
        assertNull(fixture.storage.accessToken)
        assertNull(fixture.storage.refreshToken)
        fixture.login.verify("challenge-1", "012345")
        assertEquals("verified-access", fixture.session.accessToken)
        assertEquals("verified-refresh", SessionStore(fixture.storage).refreshToken)

        val start = fixture.requests[0]
        assertEquals("/v1/auth/login", start.url.encodedPath)
        assertEquals(JsonPrimitive(true), body(start)["remember_me"])
        val verification = fixture.requests[1]
        assertEquals("POST", verification.method)
        assertEquals("/v1/auth/login/verify", verification.url.encodedPath)
        assertEquals(buildJsonObject {
            put("challenge_id", "challenge-1"); put("code", "012345")
        }, body(verification))
        for (request in fixture.requests) {
            assertNull(request.header("Authorization"))
            assertNull(request.header("X-Echon-No-Auth"))
        }
    }

    @Test fun ordinaryLoginStillAdoptsSession() = runBlocking {
        val fixture = Fixture(Reply("""{"token":"access","refresh_token":"refresh"}"""))
        assertNull(fixture.login.logIn("member@example.test", "password"))
        assertEquals("access", fixture.session.accessToken)
        assertEquals("refresh", fixture.storage.refreshToken)
    }

    @Test fun incorrectOrExpiredCodeNeverCreatesASession() = runBlocking {
        for (error in listOf(
            """{"error":"invalid_or_expired_code"}""",
            """{"error":"Invalid code","code":"invalid_or_expired_code"}""",
        )) {
            val fixture = Fixture(Reply(error, status = 400))
            try {
                fixture.login.verify("challenge-1", "000000")
                fail("Verification must fail")
            } catch (error: ApiException.Http) {
                assertEquals(400, error.status)
            }
            assertFalse(fixture.session.hasSession)
            assertNull(fixture.storage.accessToken)
        }
    }

    @Test fun malformedOrUnsupportedLoginResponsesFailClosed() = runBlocking {
        for (body in listOf(
            "{}",
            """{"token":""}""",
            """{"two_factor_required":true,"method":"email"}""",
            """{"two_factor_required":true,"method":"email","challenge_id":" "}""",
            """{"two_factor_required":true,"method":"totp","challenge_id":"id","token":"ignored"}""",
        )) {
            val fixture = Fixture(Reply(body))
            try {
                fixture.login.logIn("member@example.test", "password")
                fail("Invalid response must fail")
            } catch (_: ApiException.Decoding) { }
            assertFalse(fixture.session.hasSession)
        }
    }

    @Test fun verificationRequiresANonemptyToken() = runBlocking {
        for (body in listOf("{}", """{"token":" "}""")) {
            val fixture = Fixture(Reply(body))
            try {
                fixture.login.verify("challenge-1", "012345")
                fail("Verification must issue a token")
            } catch (_: ApiException.Decoding) { }
            assertFalse(fixture.session.hasSession)
        }
    }

    private fun body(request: Request): JsonObject = EchonJson.parseToJsonElement(
        Buffer().also { request.body!!.writeTo(it) }.readUtf8(),
    ).jsonObject
}
